package in.ramanujan.translation.codeConverter.ast;

import java.util.*;

public class FunctionDefNode extends AstNode {
    private String name;
    private ArgumentsNode args;
    private List<AstNode> body = new ArrayList<>();
    
    public String getName() { 
        return name; 
    }
    
    public void setName(String name) { 
        this.name = name; 
    }
    
    public ArgumentsNode getArgs() { 
        return args; 
    }
    
    public void setArgs(ArgumentsNode args) { 
        this.args = args; 
    }
    
    public List<AstNode> getBody() { 
        return body; 
    }
    
    public void setBody(List<AstNode> body) { 
        this.body = body; 
    }
}
