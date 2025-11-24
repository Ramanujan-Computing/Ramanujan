package in.ramanujan.translation.codeConverter.ast;

public class AttributeNode extends AstNode {
    private AstNode value;
    private String attr;
    private String ctx;
    
    public AstNode getValue() { 
        return value; 
    }
    
    public void setValue(AstNode value) { 
        this.value = value; 
    }
    
    public String getAttr() { 
        return attr; 
    }
    
    public void setAttr(String attr) { 
        this.attr = attr; 
    }
    
    public String getCtx() { 
        return ctx; 
    }
    
    public void setCtx(String ctx) { 
        this.ctx = ctx; 
    }
}
