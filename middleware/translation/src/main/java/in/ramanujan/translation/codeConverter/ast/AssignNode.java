package in.ramanujan.translation.codeConverter.ast;

import java.util.*;

public class AssignNode extends AstNode {
    private List<AstNode> targets = new ArrayList<>();
    private AstNode value;
    
    public List<AstNode> getTargets() { 
        return targets; 
    }
    
    public void setTargets(List<AstNode> targets) { 
        this.targets = targets; 
    }
    
    public AstNode getValue() { 
        return value; 
    }
    
    public void setValue(AstNode value) { 
        this.value = value; 
    }
}
