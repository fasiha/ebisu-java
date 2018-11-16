package me.aldebrn.ebisu;

import org.apache.commons.math3.special.Gamma;
import java.lang.Math;
import java.util.List;
import java.util.Collections;
import java.util.Arrays;
import java.util.stream.IntStream;

/**
 * Noninstantiable class to provides `predictRecall` and `updateRecall`.
 */
public class Ebisu {
  /**
   * Estimate recall probability.
   *
   * Given a learned fact, encoded by an Ebisu model, estimate its probability
   * of recall given how long it's been since it was studied/learned.
   *
   * @param prior the existing Ebisu model
   * @param tnow the time elapsed since this model was last reviewed
   * @return the probability of recall (0 (will fail) to 1 (will pass))
   */
  public static double predictRecall(EbisuInterface prior, double tnow) {
    double alpha = prior.getAlpha();
    double beta = prior.getBeta();
    double dt = tnow / prior.getT();
    return Math.exp(Gamma.logGamma(alpha + dt) -
                    Gamma.logGamma(alpha + beta + dt) -
                    (Gamma.logGamma(alpha) - Gamma.logGamma(alpha + beta)));
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
                     .mapToDouble(i
                                  -> Math.exp(a.get(i) - amax) *
                                         (i < b.size() ? b.get(i) : 1.0))
                     .reduce(0.0, Double::sum);
    double sign = Math.signum(sum);
    sum *= sign;
    double abs = Math.log(sum) + amax;
    double[] ret = {abs, sign};
    return ret;
  }

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
  public static double subtractexp(double x, double y) {
    var maxval = Math.max(x, y);
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
  public static List<Double> meanVarToBeta(double mean, double v) {
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
  public static EbisuInterface updateRecall(EbisuInterface prior,
                                            boolean result, double tnow) {
    double alpha = prior.getAlpha();
    double beta = prior.getBeta();
    double dt = tnow / prior.getT();
    double mu = 0;
    double v = 0;
    if (result) {
      double same =
          Gamma.logGamma(alpha + beta + dt) - Gamma.logGamma(alpha + dt);
      double muln = Gamma.logGamma(alpha + 2 * dt) -
                    Gamma.logGamma(alpha + beta + 2 * dt) + same;
      mu = Math.exp(muln);
      v = subtractexp(same + Gamma.logGamma(alpha + 3 * dt) -
                          Gamma.logGamma(alpha + beta + 3 * dt),
                      2 * muln);
    } else {
      double[] s =
          IntStream.range(0, 4)
              .mapToDouble(n
                           -> Gamma.logGamma(alpha + n * dt) -
                                  Gamma.logGamma(alpha + beta + n * dt))
              .toArray();
      mu = Math.expm1(s[2] - s[1]) / -Math.expm1(s[0] - s[1]);

      double[] n1 = logSumExp(List.of(s[1], s[0]), List.of(1., -1.));
      n1[0] += s[3];

      var n2 = logSumExp(List.of(s[0], s[1], s[2]), List.of(1., 1., -1.));
      n2[0] += s[2];

      double[] n3 = {s[1] * 2, 1.};

      var d = logSumExp(List.of(s[1], s[0]), List.of(1., -1.));
      d[0] *= 2;

      var n = logSumExp(List.of(n1[0], n2[0], n3[0]),
                        List.of(n1[1], n2[1], -n3[1]));

      v = Math.exp(n[0] - d[0]);
    }
    List<Double> newAlphaBeta = meanVarToBeta(mu, v);
    return new EbisuModel(newAlphaBeta.get(0), newAlphaBeta.get(1), tnow);
  }

  /**
   * Forbidden constructor.
   */
  private Ebisu() { throw new AssertionError(); }
}
