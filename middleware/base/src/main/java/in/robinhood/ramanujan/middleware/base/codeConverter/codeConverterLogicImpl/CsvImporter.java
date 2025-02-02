package in.robinhood.ramanujan.middleware.base.codeConverter.codeConverterLogicImpl;

import in.robinhood.ramanujan.middleware.base.codeConverter.CodeConverter;
import in.robinhood.ramanujan.middleware.base.codeConverter.CodeConverterLogic;
import in.robinhood.ramanujan.middleware.base.exception.CompilationException;
import in.robinhood.ramanujan.middleware.base.pojo.grammar.DebugLevelCodeCreator;
import in.robinhood.ramanujan.pojo.RuleEngineInput;
import in.robinhood.ramanujan.pojo.RuleEngineInputUnits;
import in.robinhood.ramanujan.pojo.ruleEngineInputUnitsExt.Command;
import in.robinhood.ramanujan.pojo.ruleEngineInputUnitsExt.Constant;
import in.robinhood.ramanujan.pojo.ruleEngineInputUnitsExt.array.Array;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class CsvImporter implements CodeConverterLogic {

    @Override
    public RuleEngineInputUnits convertCode(String code, RuleEngineInput ruleEngineInput, CodeConverter codeConverter,
                                            List<String> variableScope, DebugLevelCodeCreator debugLevelCodeCreator, Map<Integer, RuleEngineInputUnits> functionFrameVariableMap, Integer[] frameVariableCounterId) throws CompilationException {
        /*
        * import_csv fileName as arrayName
        * */
        debugLevelCodeCreator.concat(code + ";");
        final String alias = code.split("as")[1].trim();
        final String fileName = code.split("as")[0].split("import_csv")[1].trim();

        Array array = new Array();
        array.setId(UUID.randomUUID().toString());
        array.setName(alias);
        array.setValues(getValues(fileName, codeConverter));
        codeConverter.setArray(array, "");
        ruleEngineInput.getArrays().add(array);
        return null;
    }

    private Map<String, Object> getValues(String fileName, CodeConverter codeConverter) {
        String data = codeConverter.getCsvData(fileName);
        String[] lines = data.split("\n");
        Map<String, Object> values = new HashMap<>();
        int row = 0;
        for(String line : lines) {
            String[] cols = line.split(",");
            int column = 0;
            for(String col : cols) {
                Constant constant = new Constant();
                constant.setValueAndDataType(col.trim());
                values.put(row + "_" + column, constant.getValue());
                column++;
            }
            row++;
        }
        return values;
    }

    @Override
    public void populateCommand(Command command, RuleEngineInputUnits ruleEngineInputUnits) {

    }
}
