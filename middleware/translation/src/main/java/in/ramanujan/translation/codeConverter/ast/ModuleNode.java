package in.ramanujan.translation.codeConverter.ast;

import java.util.*;

public class ModuleNode extends AstNode {
    private List<AstNode> body = new ArrayList<>();
    
    public List<AstNode> getBody() { 
        return body; 
    }
    
    public void setBody(List<AstNode> body) { 
        this.body = body; 
    }
}
