package in.ramanujan.translation.codeConverter.ast;

import java.util.*;

public class CompareNode extends AstNode {
    private AstNode left;
    private List<String> ops = new ArrayList<>();  // Lt, Gt, Eq, NotEq, LtE, GtE
    private List<AstNode> comparators = new ArrayList<>();
    
    public AstNode getLeft() { 
        return left; 
    }
    
    public void setLeft(AstNode left) { 
        this.left = left; 
    }
    
    public List<String> getOps() { 
        return ops; 
    }
    
    public void setOps(List<String> ops) { 
        this.ops = ops; 
    }
    
    public List<AstNode> getComparators() { 
        return comparators; 
    }
    
    public void setComparators(List<AstNode> comparators) { 
        this.comparators = comparators; 
    }
}
