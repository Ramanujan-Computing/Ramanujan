package in.ramanujan.translation.codeConverter.ast;

public class NameNode extends AstNode {
    private String id;
    private String ctx;  // Load, Store
    
    public String getId() { 
        return id; 
    }
    
    public void setId(String id) { 
        this.id = id; 
    }
    
    public String getCtx() { 
        return ctx; 
    }
    
    public void setCtx(String ctx) { 
        this.ctx = ctx; 
    }
}
