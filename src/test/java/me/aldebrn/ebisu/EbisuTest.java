package me.aldebrn.ebisu;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
class EbisuTests {
  private double eps = Math.ulp(1.0);

  @Test
  @DisplayName("Ebisu predict at exactly half-life")
  void predict() {
    EbisuModel m = new EbisuModel(2, 2, 2);
    double p = Ebisu.predictRecall(m, 2);
    assertEquals(0.5, p, eps, "1 + 1 should equal 2");
  }

  @Test
  @DisplayName("Ebisu update at exactly half-life")
  void update() {
    EbisuInterface m = new EbisuModel(2, 2, 2);
    EbisuInterface success = Ebisu.updateRecall(m, true, 2.0);
    EbisuInterface failure = Ebisu.updateRecall(m, false, 2.0);

    assertEquals(3.0, success.getAlpha(), 500 * eps, "success/alpha");
    assertEquals(2.0, success.getBeta(), 500 * eps, "success/beta");

    assertEquals(2.0, failure.getAlpha(), 500 * eps, "failure/alpha");
    assertEquals(3.0, failure.getBeta(), 500 * eps, "failure/beta");
  }

  @Test
  @DisplayName("Check logSumExp")
  void checkLogSumExp() {
    double expected = Math.exp(3.3) + Math.exp(4.4) - Math.exp(5.5);
    double[] actual =
        Ebisu.logSumExp(List.of(3.3, 4.4, 5.5), List.of(1., 1., -1.));

    double epsilon = Math.ulp(actual[0]);
    assertEquals(Math.log(Math.abs(expected)), actual[0], epsilon,
                 "Magnitude of logSumExp");

    assertEquals(Math.signum(expected), actual[1], "Sign of logSumExp");
  }
}