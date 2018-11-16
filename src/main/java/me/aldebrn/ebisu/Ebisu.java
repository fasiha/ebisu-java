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

  public static void main(String[] args) {
    System.out.println(Gamma.logGamma(3.3));
    System.out.println("Hello World!");
    EbisuModel m = new EbisuModel(2, 2, 2);
    System.out.println(predictRecall(m, 2));

    System.out.println(Math.exp(3.3) + Math.exp(4.4) - Math.exp(5.5));
    System.out.println(
        Math.log(Math.abs(Math.exp(3.3) + Math.exp(4.4) - Math.exp(5.5))));
    System.out.println(Arrays.toString(
        logSumExp(List.of(3.3, 4.4, 5.5), List.of(1., 1., -1.))));
  }
}
