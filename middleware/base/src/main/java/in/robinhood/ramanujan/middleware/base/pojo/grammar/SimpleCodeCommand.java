package in.robinhood.ramanujan.middleware.base.pojo.grammar;

import lombok.Data;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Data
public class SimpleCodeCommand {
    private String codeCommand;
    private String placeHolder;
    private List<String> arguments;

    public Boolean isEqual(SimpleCodeCommand simpleCodeCommand) {
        if(isStringEqual(codeCommand, simpleCodeCommand.getCodeCommand()) &&
                isStringEqual(placeHolder, simpleCodeCommand.getPlaceHolder())) {
            return isListEqual(arguments, simpleCodeCommand.getArguments());
        } else {
            return false;
        }
    }

    private Boolean isListEqual(List<String> list1, List<String> list2) {
        Boolean x = isComaredObjectNullXoredTrue(list1, list2);
        if (x != null) return x;
        Set<String> arguments1 = new HashSet<>(list1);
        Set<String> arguments2 = new HashSet<>(list2);

        for(String arg : arguments1) {
            if(!arguments2.contains(arg)) {
                return  false;
            }
        }

        for(String arg: arguments2) {
            if(!arguments1.contains(arg)) {
                return  false;
            }
        }
        return true;
    }

    protected Boolean isStringEqual(String s1, String s2) {
        Boolean x = isComaredObjectNullXoredTrue(s1, s2);
        if (x != null) return x;
        return s1.equals(s2);
    }

    private Boolean isComaredObjectNullXoredTrue(Object s1, Object s2) {
        if(s1 == null && s2 != null) {
            return false;
        }
        if(s1 == null && s2 != null) {
            return false;
        }
        if(s1 == null && s2 == null) {
            return true;
        }
        return null;
    }

}
