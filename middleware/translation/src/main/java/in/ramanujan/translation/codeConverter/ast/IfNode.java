package in.ramanujan.translation.codeConverter.ast;

import java.util.*;

public class IfNode extends AstNode {
    private AstNode test;
    private List<AstNode> body = new ArrayList<>();
    private List<AstNode> orelse = new ArrayList<>();
    
    public AstNode getTest() { 
        return test; 
    }
    
    public void setTest(AstNode test) { 
        this.test = test; 
    }
    
    public List<AstNode> getBody() { 
        return body; 
    }
    
    public void setBody(List<AstNode> body) { 
        this.body = body; 
    }
    
    public List<AstNode> getOrelse() { 
        return orelse; 
    }
    
    public void setOrelse(List<AstNode> orelse) { 
        this.orelse = orelse; 
    }
}
