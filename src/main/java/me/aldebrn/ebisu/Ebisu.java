package me.aldebrn.ebisu;
import org.apache.commons.math3.special.Gamma;
import java.lang.Math;
import java.util.List;
import java.util.Collections;
import java.util.Arrays;
import java.util.stream.IntStream;

/**
 * Hello world!
 *
 */
public class Ebisu {
  public static double predictRecall(EbisuInterface prior, double tnow) {
    double alpha = prior.getAlpha();
    double beta = prior.getBeta();
    double dt = tnow / prior.getT();
    return Math.exp(Gamma.logGamma(alpha + dt) -
                    Gamma.logGamma(alpha + beta + dt) -
                    (Gamma.logGamma(alpha) - Gamma.logGamma(alpha + beta)));
  }
  public static double[] logSumExp(List<Double> a, List<Double> b) {
    double amax = Collections.max(a);
    double sum = IntStream.range(0, a.size())
                     .mapToDouble(i
                                  -> Math.exp(a.get(i) - amax) *
                                         (i < b.size() ? b.get(i) : 1.0))
                     .reduce(0.0, Double::sum);
    double sign = Math.signum(sum);
    sum *= sign;
    double out = Math.log(sum) + amax;
    double[] ret = {out, sign};
    return ret;
  }
  public static double subtractexp(double x, double y) {
    var maxval = Math.max(x, y);
    return Math.exp(maxval) * (Math.exp(x - maxval) - Math.exp(y - maxval));
  }
  public static List<Double> meanVarToBeta(double mean, double v) {
    double tmp = mean * (1 - mean) / v - 1;
    double alpha = mean * tmp;
    double beta = (1 - mean) * tmp;
    return List.of(alpha, beta);
  }
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
}
