package in.ramanujan.translation.codeConverter.ast;

public class BinOpNode extends AstNode {
    private AstNode left;
    private String op;  // Add, Sub, Mult, Div, Mod, Pow
    private AstNode right;
    
    public AstNode getLeft() { 
        return left; 
    }
    
    public void setLeft(AstNode left) { 
        this.left = left; 
    }
    
    public String getOp() { 
        return op; 
    }
    
    public void setOp(String op) { 
        this.op = op; 
    }
    
    public AstNode getRight() { 
        return right; 
    }
    
    public void setRight(AstNode right) { 
        this.right = right; 
    }
}
