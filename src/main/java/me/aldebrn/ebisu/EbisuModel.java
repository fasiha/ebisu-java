package me.aldebrn.ebisu;
public class EbisuModel implements EbisuInterface {
  private double a;
  private double b;
  private double t;
  public EbisuModel(double alpha, double beta, double time) {
    this.a = alpha;
    this.b = beta;
    this.t = time;
  }
  public double getAlpha() { return this.a; }
  public double getBeta() { return this.b; }
  public double getT() { return this.t; }
  @Override
  public String toString() {
    return "Model(" + this.a + ", " + this.b + ", " + this.t + ")";
  }
}
