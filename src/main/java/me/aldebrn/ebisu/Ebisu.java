package me.aldebrn.ebisu;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import me.aldebrn.gamma.Gamma;
import me.aldebrn.mingolden.Min;
import me.aldebrn.mingolden.Status;

/**
 * Noninstantiable class to provides `predictRecall` and `updateRecall` methods
 * that operate on Ebisu model objects (implementing `EbisuInterface`).
 */
public class Ebisu {
  /**
   * This will cache calls to logGamma
   */
  private static Map<Double, Double> LOGGAMMA_CACHE = new ConcurrentHashMap<>();

  /**
   * Memoized logGamma
   */
  private static Double logGammaCached(Double x) { return LOGGAMMA_CACHE.computeIfAbsent(x, y -> Gamma.gammaln(y)); }

  /**
   * Evaluates `log(Beta(a1, b) / Beta(a, b))`
   */
  private static Double logBetaRatio(Double a1, Double a, Double b) {
    return Gamma.gammaln(a1) - Gamma.gammaln(a1 + b) + logGammaCached(a + b) - logGammaCached(a);
  }

  /**
   * Evaluates `log(Beta(a,b)) = Gamma(a) Gamma(b) / Gamma(a+b)`
   */
  private static Double logBeta(Double a, Double b) {
    return logGammaCached(a) + logGammaCached(b) - logGammaCached(a + b);
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
    double ret = logBetaRatio(alpha + dt, alpha, beta);
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
   * @param successes number of successful reviews out of `total`
   * @param total number of times this fact was reviewed
   * @param tnow time elapsed since quiz was last visited
   * @return new posterior (updated) Ebisu model
   */
  public static EbisuInterface updateRecall(EbisuInterface prior, int successes, int total, double tnow) {
    return updateRecall(prior, successes, total, tnow, true, prior.getTime());
  }

  /** log of the binomial coefficient */
  private static double logBinom(int n, int k) { return -logBeta(1.0 + n - k, 1.0 + k) - Math.log(n + 1.0); }

  /**
   * Actual worker method that calculates the posterior memory model at the same
   * time in the future as the prior, and rebalances as necessary.
   */
  private static EbisuInterface updateRecall(EbisuInterface prior, int successes, int total, double tnow,
                                             boolean rebalance, double tback) {
    double alpha = prior.getAlpha();
    double beta = prior.getBeta();
    double t = prior.getTime();
    double dt = tnow / t;
    double et = tback / tnow;

    double[] binomlns =
        IntStream.range(0, total - successes + 1).mapToDouble(i -> logBinom(total - successes, i)).toArray();
    double[] logs =
        IntStream.range(0, 3)
            .mapToDouble(m -> {
              List<Double> a =
                  IntStream.range(0, total - successes + 1)
                      .mapToDouble(i -> binomlns[i] + logBeta(beta, alpha + dt * (successes + i) + m * dt * et))
                      .boxed()
                      .collect(Collectors.toList());
              List<Double> b = IntStream.range(0, total - successes + 1)
                                   .mapToDouble(i -> Math.pow(-1.0, i))
                                   .boxed()
                                   .collect(Collectors.toList());
              return logSumExp(a, b)[0];
            })
            .toArray();

    double logDenominator = logs[0];
    double logMeanNum = logs[1];
    double logM2Num = logs[2];

    double mean = Math.exp(logMeanNum - logDenominator);
    double m2 = Math.exp(logM2Num - logDenominator);
    double meanSq = Math.exp(2 * (logMeanNum - logDenominator));
    double sig2 = m2 - meanSq;

    if (mean <= 0) { throw new RuntimeException("invalid mean found"); }
    if (m2 <= 0) { throw new RuntimeException("invalid second moment found"); }
    if (sig2 <= 0) {
      throw new RuntimeException("invalid variance found " +
                                 String.format("a=%g, b=%g, t=%g, k=%d, n=%d, tnow=%g, mean=%g, m2=%g, sig2=%g", alpha,
                                               beta, t, successes, total, tnow, mean, m2, sig2));
    }
    List<Double> newAlphaBeta = meanVarToBeta(mean, sig2);
    EbisuModel proposed = new EbisuModel(tback, newAlphaBeta.get(0), newAlphaBeta.get(1));
    return rebalance ? rebalance(prior, successes, total, tnow, proposed) : proposed;
  }

  /**
   * Given a prior Ebisu model, a quiz result, the time of a quiz, and a
   * proposed posterior model, rebalance the posterior so its alpha and beta
   * parameters are close. In other words, move the posterior closer to its
   * approximate halflife for numerical stability.
   */
  private static EbisuInterface rebalance(EbisuInterface prior, int successes, int total, double tnow,
                                          EbisuInterface proposed) {
    double newAlpha = proposed.getAlpha();
    double newBeta = proposed.getBeta();
    if (newAlpha > 2 * newBeta || newBeta > 2 * newAlpha) {
      double roughHalflife = modelToPercentileDecay(proposed, 0.5, true);
      return updateRecall(prior, successes, total, tnow, false, roughHalflife);
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
    Status status = Min.min(y -> Math.abs(f.apply(y)), blow, bhigh, tolerance, 10000);
    assert status.converged;
    double sol = status.argmin;
    return Math.exp(sol) * t0;
  }

  /**
   * Forbidden constructor.
   */
  private Ebisu() { throw new AssertionError(); }
}
