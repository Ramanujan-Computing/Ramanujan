package in.ramanujan.translation.codeConverter.ast;

public class ExprNode extends AstNode {
    private AstNode value;
    
    public AstNode getValue() { 
        return value; 
    }
    
    public void setValue(AstNode value) { 
        this.value = value; 
    }
}
