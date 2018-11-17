package me.aldebrn.ebisu;
/**
 * Wrapper class to store three numbers representing an Ebisu model.
 *
 * The model is encoded by a Beta distribution, parameterized by `alpha` and
 * `beta`, which defines the probability of recall after a certain amount of
 * elapsed `time` (units are left to the user).
 *
 * See `Ebisu.predictRecall` and `Ebisu.updateRecall` functions that can consume
 * an object of this class.
 *
 * N.B. In the Python and JavaScript implementations of Ebisu, this class (and
 * the interface it implements) doesn't exist: those versions just store the
 * three numeric parameters in a 3-tuple array. This Java implementation seeks
 * to be a bit more formal about it.
 */
public class EbisuModel implements EbisuInterface {
  private double alpha;
  private double beta;
  private double time;

  /**
   * Plain object constructor.
   *
   * @param alpha first parameter of the 3-tuple model
   * @param beta second
   * @param time third
   */
  public EbisuModel(double alpha, double beta, double time) {
    this.alpha = alpha;
    this.beta = beta;
    this.time = time;
  }

  /**
   * `alpha` getter
   */
  public double getAlpha() { return this.alpha; }

  /**
   * `beta` getter
   */
  public double getBeta() { return this.beta; }

  /**
   * `time` getter
   */
  public double getTime() { return this.time; }

  /**
   * Printable representation of the model
   *
   * @return stringy representation
   */
  @Override
  public String toString() {
    return "Model(" + this.alpha + ", " + this.beta + ", " + this.time + ")";
  }
}
