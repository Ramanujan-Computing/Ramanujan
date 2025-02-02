package in.ramanujan.rule.engine.pojo.ruleEngineInputUnits;

public interface DataOperation {
    public  double add(double value);
    public  double minus(double value);
    public  double power(double value);
    public  double mul(double value);
    public  double divide(double value);

    public Object set(double value, String processId);

    public double get();
    public boolean greaterThan(double val);
    public boolean greaterThanOrEqual(double val);
    public boolean isEqual(double val);
    public boolean isNotEqual(double val);
    public boolean lessThanOrEqual(double val);
    public boolean lessThan(double val);
}
