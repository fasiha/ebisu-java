package me.aldebrn.ebisu;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.IntStream;
import me.aldebrn.gamma.Gamma;
import org.apache.commons.math3.analysis.solvers.BisectionSolver;

/**
 * Noninstantiable class to provides `predictRecall` and `updateRecall` methods
 * that operate on Ebisu model objects (implementing `EbisuInterface`).
 */
public class Ebisu {
  /**
   * Evaluates `log(Beta(a1, b) / Beta(a, b))`
   */
  private static Double logBetaRatio(Double a1, Double a, Double b) {
    return Gamma.gammaln(a1) - Gamma.gammaln(a1 + b) + Gamma.gammaln(a + b) - Gamma.gammaln(a);
  }

  /**
   * Evaluates `log(Beta(a,b)) = Gamma(a) Gamma(b) / Gamma(a+b)`
   */
  private static Double logBeta(Double a, Double b) {
    return Gamma.gammaln(a) + Gamma.gammaln(b) - Gamma.gammaln(a + b);
  }

  /**
   * Estimate recall log-probability (real number between -∞ and +∞)
   * @param prior the
   * @param prior the existing Ebisu model
   * @param tnow the time elapsed since this model was last reviewed
   * @return log-probability of recall
   */
  public static double predictRecall(EbisuInterface prior, double tnow) { return predictRecall(prior, tnow, false); }

  /**
   * Estimate recall probability.
   *
   * Given a learned fact, encoded by an Ebisu model, estimate its probability
   * of recall given how long it's been since it was studied/learned.
   *
   * @param prior the existing Ebisu model
   * @param tnow the time elapsed since this model was last reviewed
   * @param exact if false, return log-probabilities (faster)
   * @return the probability of recall (0 (will fail) to 1 (will pass))
   */
  public static double predictRecall(EbisuInterface prior, double tnow, boolean exact) {
    double alpha = prior.getAlpha();
    double beta = prior.getBeta();
    double dt = tnow / prior.getTime();
    double ret = prior.gammalnDiff() + Gamma.gammaln(alpha + dt) - Gamma.gammaln(alpha + beta + dt);
    return exact ? Math.exp(ret) : ret;
  }

  /**
   * Stably evaluate the log of the sum of the exponentials of inputs.
   *
   * The basic idea is, you have a bunch of numbers in the log domain, e.g., the
   * results of `logGamma`. Then you want to sum them, but you cannot sum in the
   * log domain: you have to apply `exp` first before summing. But if you have
   * very big values, `exp` might overflow (this is probably why you started out
   * with the log domain in the first place!). This function lets you do the sum
   * more stably, and returns the result of the sum in the log domain.
   *
   * See
   * https://docs.scipy.org/doc/scipy/reference/generated/scipy.special.logsumexp.html
   *
   * Analogous to `log(sum(b .* exp(a)))` (in Python/Julia notation). `b`'s
   * values default to 1.0 if `b` is not as long as `a`.
   *
   * Because the elements of `b` can be negative, to effect subtraction, the
   * result might be negative. Therefore, two numbers are returned: the absolute
   * value of the result, and its sign.
   *
   * @param a logs of the values to be summed
   * @param b scalars to be applied element-wise to `exp(a)`
   * @return 2-array containing result's absolute value and its sign (1 or -1)
   */
  public static double[] logSumExp(List<Double> a, List<Double> b) {
    double amax = Collections.max(a);
    double sum = IntStream.range(0, a.size())
                     .mapToDouble(i -> Math.exp(a.get(i) - amax) * (i < b.size() ? b.get(i) : 1.0))
                     .reduce(0.0, Double::sum);
    double sign = Math.signum(sum);
    sum *= sign;
    double abs = Math.log(sum) + amax;
    double[] ret = {abs, sign};
    return ret;
  }

  /**
   * Stably calculate `log(exp(a) - exp(b))`.
   */
  private static double logsubexp(Double a, Double b) { return logSumExp(List.of(a, b), List.of(1.0, -1.0))[0]; }

  /**
   * Given two numbers in the log domain, subtract them and leave them in the
   * linear domain.
   *
   * Analogous to `exp(x) - exp(y)`, but more stable when `x` and `y` are huge.
   *
   * @param x First value (in log domain)
   * @param y Second value (in log domain)
   * @return result in the linear domain, Analogous to `exp(x) - exp(y)`
   */
  private static double subtractexp(double x, double y) {
    double maxval = Math.max(x, y);
    return Math.exp(maxval) * (Math.exp(x - maxval) - Math.exp(y - maxval));
  }

  /**
   * Convert the mean and variance of a Beta distribution to its parameters.
   *
   * See
   * https://en.wikipedia.org/w/index.php?title=Beta_distribution&oldid=774237683#Two_unknown_parameters
   *
   * @param mean x̄ in the Wikipedia reference above
   * @param v v̄ in the Wikipedia reference above
   * @return a 2-element `List<Double>`, containing `alpha` and `beta`
   */
  private static List<Double> meanVarToBeta(double mean, double v) {
    double tmp = mean * (1 - mean) / v - 1;
    double alpha = mean * tmp;
    double beta = (1 - mean) * tmp;
    return List.of(alpha, beta);
  }

  /**
   * Update recall probability.
   *
   * Given an Ebisu model, a quiz result, and the time elapsed since the quiz or
   * fact was last seen, yield a new Ebisu model.
   *
   * @param prior the existing Ebisu model
   * @param result the result of the quiz: true means pass, false means failed
   * @param tnow time elapsed since quiz was last visited
   * @return new posterior (updated) Ebisu model
   */
  public static EbisuInterface updateRecall(EbisuInterface prior, boolean result, double tnow) {
    return updateRecall(prior, result, tnow, true, prior.getTime());
  }

  /**
   * Actual worker method that calculates the posterior memory model at the same
   * time in the future as the prior, and rebalances as necessary.
   */
  private static EbisuInterface updateRecall(EbisuInterface prior, boolean result, double tnow, boolean rebalance,
                                             double tback) {
    double alpha = prior.getAlpha();
    double beta = prior.getBeta();
    double t = prior.getTime();
    double dt = tnow / t;
    double et = tnow / tback;
    double mean = 0;
    double sig2 = 0;
    if (result) {
      if (tback == t) {
        EbisuModel proposed = new EbisuModel(t, alpha + dt, beta);
        return rebalance ? rebalance(prior, result, tnow, proposed) : proposed;
      }
      double fixed = Gamma.gammaln(alpha + dt + beta) - Gamma.gammaln(alpha + dt);

      double meanTopleft = alpha + dt / et * (1 + et);
      double logmean = fixed + Gamma.gammaln(meanTopleft) - Gamma.gammaln(meanTopleft + beta);

      double m2TopLeft = meanTopleft + dt / et;
      double logm2 = fixed + Gamma.gammaln(m2TopLeft) - Gamma.gammaln(m2TopLeft + beta);

      mean = Math.exp(logmean);
      sig2 = subtractexp(logm2, 2 * logmean);
    } else {
      double logDenominator = logsubexp(logBeta(alpha, beta), logBeta(alpha + dt, beta));
      mean = subtractexp(logBeta(alpha + dt / et, beta) - logDenominator,
                         logBeta(alpha + dt / et * (et + 1), beta) - logDenominator);
      double m2 = subtractexp(logBeta(alpha + 2 * dt / et, beta) - logDenominator,
                              logBeta(alpha + dt / et * (et + 2), beta) - logDenominator);
      if (m2 <= 0) { throw new RuntimeException("invalid second moment found"); }
      sig2 = m2 - mean * mean;
    }
    if (mean <= 0) { throw new RuntimeException("invalid mean found"); }
    if (sig2 <= 0) { throw new RuntimeException("invalid variance found"); }
    List<Double> newAlphaBeta = meanVarToBeta(mean, sig2);
    EbisuModel proposed = new EbisuModel(tback, newAlphaBeta.get(0), newAlphaBeta.get(1));
    return rebalance ? rebalance(prior, result, tnow, proposed) : proposed;
  }

  /**
   * Given a prior Ebisu model, a quiz result, the time of a quiz, and a
   * proposed posterior model, rebalance the posterior so its alpha and beta
   * parameters are close. In other words, move the posterior closer to its
   * approximate halflife for numerical stability.
   */
  private static EbisuInterface rebalance(EbisuInterface prior, boolean result, double tnow, EbisuInterface proposed) {
    double newAlpha = proposed.getAlpha();
    double newBeta = proposed.getBeta();
    if (newAlpha > 2 * newBeta || newBeta > 2 * newAlpha) {
      double roughHalflife = modelToPercentileDecay(proposed, 0.5, true);
      return updateRecall(prior, result, tnow, false, roughHalflife);
    }
    return proposed;
  }

  /**
   * Compute an Ebisu memory model's half-life
   *
   * @param model Ebisu memory model
   * @return time at which `predictRecall` would return 0.5
   */
  public static double modelToPercentileDecay(EbisuInterface model) { return modelToPercentileDecay(model, 0.5); }

  /**
   * Compute time at which an Ebisu memory model predicts a given percentile
   *
   * @param model Ebisu memory model
   * @param percentile between 0 and 1 (0.5 corresponds to half-life)
   * @return time at which `predictRecall` would return `percentile`
   */
  public static double modelToPercentileDecay(EbisuInterface model, double percentile) {
    return modelToPercentileDecay(model, percentile, 1e-4);
  }

  /**
   * Compute time at which an Ebisu memory model predicts a given percentile with some tolerance
   *
   * @param model Ebisu memory model
   * @param percentile between 0 and 1 (0.5 corresponds to half-life)
   * @param tolerance accuracy of the search for this `percentile`. This should be less than 0.01 (roughly), but
   *     definitely greater than 2e-16 (machine precision)
   * @return time at which `predictRecall` would return `percentile`
   */
  public static double modelToPercentileDecay(EbisuInterface model, double percentile, double tolerance) {
    return modelToPercentileDecay(model, percentile, false, tolerance);
  }

  /**
   * Optionally-coarse, within order-of-magnitude, estimate of model decay
   *
   * @param model Ebisu memory model
   * @param percentile between 0 and 1 (0.5 corresponds to half-life)
   * @param coarse if true, returns an approximate solution (within an order of magnitude)
   */
  public static double modelToPercentileDecay(EbisuInterface model, double percentile, boolean coarse) {
    return modelToPercentileDecay(model, percentile, coarse, 1e-4);
  }

  /**
   * Compute time at which an Ebisu memory model predicts a given percentile at
   * a given accuracy
   *
   * @param model Ebisu memory model
   * @param percentile between 0 and 1 (0.5 corresponds to half-life)
   * @param coarse if true, returns an approximate solution (within an order of magnitude)
   * @param tolerance accuracy of the search for this `percentile`. Ignored if `coarse`.
   * @return time at which `predictRecall` would return `percentile`
   */
  public static double modelToPercentileDecay(EbisuInterface model, double percentile, boolean coarse,
                                              double tolerance) {
    if (percentile < 0 || percentile > 1) {
      throw new RuntimeException("percentiles must be between (0, 1) exclusive");
    }
    double alpha = model.getAlpha();
    double beta = model.getBeta();
    double t0 = model.getTime();

    double logBab = logBeta(alpha, beta);
    double logPercentile = Math.log(percentile);
    Function<Double, Double> f = lndelta -> (logBeta(alpha + Math.exp(lndelta), beta) - logBab) - logPercentile;

    double bracket_width = coarse ? 1.0 : 6.0;
    double blow = -bracket_width / 2.0;
    double bhigh = bracket_width / 2.0;
    double flow = f.apply(blow);
    double fhigh = f.apply(bhigh);
    while (flow > 0 && fhigh > 0) {
      // Move the bracket up.
      blow = bhigh;
      flow = fhigh;
      bhigh += bracket_width;
      fhigh = f.apply(bhigh);
    }
    while (flow < 0 && fhigh < 0) {
      // Move the bracket down.
      bhigh = blow;
      fhigh = flow;
      blow -= bracket_width;
      flow = f.apply(blow);
    }

    if (!(flow > 0 && fhigh < 0)) { throw new RuntimeException("failed to bracket"); }
    if (coarse) { return (Math.exp(blow) + Math.exp(bhigh)) / 2 * t0; }
    BisectionSolver solver = new BisectionSolver(tolerance);
    double sol = solver.solve(10000, y -> f.apply(y), blow, bhigh);
    return Math.exp(sol) * t0;
  }

  /**
   * Forbidden constructor.
   */
  private Ebisu() { throw new AssertionError(); }
}
