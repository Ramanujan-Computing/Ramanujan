package in.ramanujan.translation.codeConverter.ast;

import java.util.*;

public class CallNode extends AstNode {
    private AstNode func;
    private List<AstNode> args = new ArrayList<>();
    
    public AstNode getFunc() { 
        return func; 
    }
    
    public void setFunc(AstNode func) { 
        this.func = func; 
    }
    
    public List<AstNode> getArgs() { 
        return args; 
    }
    
    public void setArgs(List<AstNode> args) { 
        this.args = args; 
    }
}
