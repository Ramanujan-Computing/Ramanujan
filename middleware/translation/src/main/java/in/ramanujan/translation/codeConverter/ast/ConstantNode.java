package in.ramanujan.translation.codeConverter.ast;

public class ConstantNode extends AstNode {
    private Object value;
    
    public Object getValue() { 
        return value; 
    }
    
    public void setValue(Object value) { 
        this.value = value; 
    }
}
