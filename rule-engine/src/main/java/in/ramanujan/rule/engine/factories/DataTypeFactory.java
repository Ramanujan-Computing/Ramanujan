package in.ramanujan.rule.engine.factories;

import in.ramanujan.enums.DataType;
import in.ramanujan.rule.engine.functioning.DataTypeFunctioning;
import in.ramanujan.rule.engine.functioning.dataTypeFunctioningImpl.DoubleImpl;
import in.ramanujan.rule.engine.functioning.dataTypeFunctioningImpl.IntegerImpl;
import in.ramanujan.rule.engine.functioning.dataTypeFunctioningImpl.StringImpl;

public class DataTypeFactory {
    public static  DataTypeFunctioning getDataTypeFunctioningImpl(DataType dataType) {
        if(dataType == DataType.Double) {
            return new DoubleImpl();
        }
        if(dataType == DataType.Integer) {
            return new IntegerImpl();
        }
        if(dataType == DataType.String) {
            return new StringImpl();
        }
        return new DataTypeFunctioning() {
        };
    }
}
