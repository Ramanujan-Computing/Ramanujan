package in.ramanujan.translation.codeConverter.codeConverterLogicImpl;

import in.ramanujan.translation.codeConverter.CodeConverter;
import in.ramanujan.translation.codeConverter.CodeConverterLogic;
import in.ramanujan.pojo.RuleEngineInput;
import in.ramanujan.pojo.RuleEngineInputUnits;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.Command;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.Constant;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.array.Array;
import in.ramanujan.translation.codeConverter.exception.CompilationException;
import in.ramanujan.translation.codeConverter.grammar.DebugLevelCodeCreator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CsvImporter implements CodeConverterLogic {

    @Override
    public RuleEngineInputUnits convertCode(String code, RuleEngineInput ruleEngineInput, CodeConverter codeConverter,
                                            List<String> variableScope, DebugLevelCodeCreator debugLevelCodeCreator,
                                            Map<Integer, RuleEngineInputUnits> functionFrameVariableMap, Integer[] frameVariableCounterId) throws CompilationException {
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
