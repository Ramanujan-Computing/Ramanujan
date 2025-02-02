package in.robinhood.ramanujan.middleware.base.pojo.grammar;

import lombok.Data;

import java.util.List;

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
