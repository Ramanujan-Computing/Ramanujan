package in.ramanujan.translation.codeConverter.ast;

import java.util.*;

public class ArgumentsNode {
    private List<ArgNode> args = new ArrayList<>();
    
    public List<ArgNode> getArgs() { 
        return args; 
    }
    
    public void setArgs(List<ArgNode> args) { 
        this.args = args; 
    }
}
