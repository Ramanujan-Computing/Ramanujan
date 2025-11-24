package in.ramanujan.translation.codeConverter.ast;

public class AugAssignNode extends AstNode {
    private AstNode target;
    private String op;  // Add, Sub, Mult, Div
    private AstNode value;
    
    public AstNode getTarget() { 
        return target; 
    }
    
    public void setTarget(AstNode target) { 
        this.target = target; 
    }
    
    public String getOp() { 
        return op; 
    }
    
    public void setOp(String op) { 
        this.op = op; 
    }
    
    public AstNode getValue() { 
        return value; 
    }
    
    public void setValue(AstNode value) { 
        this.value = value; 
    }
}
