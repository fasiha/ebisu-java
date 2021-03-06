package me.aldebrn.ebisu;

/**
 * Interface that an Ebisu model must meet.
 *
 * Recall that, mathematically, an Ebisu model consists of a Beta distribution
 * (conventionally parameterized by `alpha` and `beta`) that describes the
 * recall probability at some elapsed `time` in the future. Any class meeting
 * this interface simply exposes three getters, for each of these three model
 * parameters.
 */
public interface EbisuInterface {
  /**
   * `alpha` getter
   *
   * @return alpha
   */
  public double getAlpha();
  /**
   * `beta` getter
   *
   * @return beta
   */
  public double getBeta();
  /**
   * `time` getter
   *
   * @return time
   */
  public double getTime();
}
