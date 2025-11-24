package in.ramanujan.translation.codeConverter.ast;

public class SubscriptNode extends AstNode {
    private AstNode value;
    private AstNode slice;
    private String ctx;
    
    public AstNode getValue() { 
        return value; 
    }
    
    public void setValue(AstNode value) { 
        this.value = value; 
    }
    
    public AstNode getSlice() { 
        return slice; 
    }
    
    public void setSlice(AstNode slice) { 
        this.slice = slice; 
    }
    
    public String getCtx() { 
        return ctx; 
    }
    
    public void setCtx(String ctx) { 
        this.ctx = ctx; 
    }
}
