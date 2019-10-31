package me.aldebrn.ebisu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.math3.exception.TooManyEvaluationsException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
class EbisuTests {
  private double eps = Math.ulp(1.0);

  private static double relerr(double dirt, double gold) {
    return (dirt == gold) ? 0 : Math.abs(dirt - gold) / Math.abs(gold);
  }

  @Test
  @DisplayName("compare against reference implementation's test.json")
  void testAgainstReference() {
    // All this boilerplate is just to load JSON
    ClassLoader classLoader = getClass().getClassLoader();
    InputStream inputStream = classLoader.getResourceAsStream("test.json");
    String result = new BufferedReader(new InputStreamReader(inputStream)).lines().collect(Collectors.joining("\n"));
    ObjectMapper mapper = new ObjectMapper();
    try {
      JsonNode jsonRoot = mapper.readTree(result);
      double maxTol = 1e-3;
      for (JsonNode subtest : jsonRoot) {
        // subtest might be either
        // a) ["update", [3.3, 4.4, 1.0], [false, 0.1], {"post": [3.0014993214093426, 5.492666532778273, 1.0]}
        // or
        // b) ["predict", [34.4, 34.4, 1.0], [5.5], {"mean": 0.026134289032202798}]
        //
        // In both cases, the first two elements are a string and an array of numbers. Then the remaining vary depend on
        // what that string is. where the numbers are arbitrary. So here we go...
        String operation = subtest.get(0).asText();

        JsonNode second = subtest.get(1);
        EbisuModel ebisu = new EbisuModel(second.get(0).asDouble(), second.get(1).asDouble(), second.get(2).asDouble());

        if (operation.equals("update")) {
          boolean quiz = subtest.get(2).get(0).asBoolean();
          double t = subtest.get(2).get(1).asDouble();
          JsonNode third = subtest.get(3).get("post");
          EbisuModel expected =
              new EbisuModel(third.get(0).asDouble(), third.get(1).asDouble(), third.get(2).asDouble());

          EbisuInterface actual = Ebisu.updateRecall(ebisu, quiz, t);

          assertEquals(expected.getAlpha(), actual.getAlpha(), maxTol);
          assertEquals(expected.getBeta(), actual.getBeta(), maxTol);
          assertEquals(expected.getTime(), actual.getTime(), maxTol);
        } else if (operation.equals("predict")) {
          double t = subtest.get(2).get(0).asDouble();
          double expected = subtest.get(3).get("mean").asDouble();
          double actual = Ebisu.predictRecall(ebisu, t, true);
          assertEquals(expected, actual, maxTol);
        } else {
          throw new Exception("unknown operation");
        }
      }
    } catch (Exception e) {
      System.out.println(e.getStackTrace());
      assert false;
    }
  }

  @Test
  @DisplayName("verify halflife")
  void testHalflife() {
    double hl = 20.0;
    EbisuModel m = new EbisuModel(2, 2, hl);
    assertTrue(Math.abs(Ebisu.modelToPercentileDecay(m, .5, true) - hl) > 1e-2);
    assertTrue(relerr(Ebisu.modelToPercentileDecay(m, .5, 1e-6), hl) < 1e-3);
    assertThrows(TooManyEvaluationsException.class, () -> Ebisu.modelToPercentileDecay(m, .5, 1e-150));
  }

  @Test
  @DisplayName("Ebisu predict at exactly half-life")
  void predict() {
    EbisuModel m = new EbisuModel(2, 2, 2);
    double p = Ebisu.predictRecall(m, 2, true);
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
    double[] actual = Ebisu.logSumExp(List.of(3.3, 4.4, 5.5), List.of(1., 1., -1.));

    double epsilon = Math.ulp(actual[0]);
    assertEquals(Math.log(Math.abs(expected)), actual[0], epsilon, "Magnitude of logSumExp");

    assertEquals(Math.signum(expected), actual[1], "Sign of logSumExp");
  }
}