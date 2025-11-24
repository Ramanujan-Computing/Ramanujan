package in.ramanujan.translation.codeConverter.ast;

import java.util.*;

public class ListNode extends AstNode {
    private List<AstNode> elts = new ArrayList<>();
    private String ctx;
    
    public List<AstNode> getElts() { 
        return elts; 
    }
    
    public void setElts(List<AstNode> elts) { 
        this.elts = elts; 
    }
    
    public String getCtx() { 
        return ctx; 
    }
    
    public void setCtx(String ctx) { 
        this.ctx = ctx; 
    }
}
