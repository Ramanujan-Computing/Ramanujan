package in.ramanujan.translation.codeConverter.ast;

public class ReturnNode extends AstNode {
    private AstNode value;
    
    public AstNode getValue() { 
        return value; 
    }
    
    public void setValue(AstNode value) { 
        this.value = value; 
    }
}
