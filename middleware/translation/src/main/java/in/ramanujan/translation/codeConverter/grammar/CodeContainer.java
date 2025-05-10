package in.ramanujan.translation.codeConverter.grammar;

import lombok.Data;

@Data
public class CodeContainer extends SimpleCodeCommand{
    private String code;

    public Boolean isEqual(CodeContainer codeContainer) {
        if(!super.isEqual(codeContainer)) {
            return false;
        }
        return isStringEqual(code, codeContainer.getCode());
    }
    public  CodeContainer() {}
    public CodeContainer(SimpleCodeCommand simpleCodeCommand)  {
        this.setArguments(simpleCodeCommand.getArguments());
        this.setPlaceHolder(simpleCodeCommand.getPlaceHolder());
        this.setCodeCommand(simpleCodeCommand.getCodeCommand());
    }
}
