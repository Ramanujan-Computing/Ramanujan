package in.ramanujan.translation.codeConverter.ast;

/**
 * Represents a unary operation in Python (operation with one operand).
 * Examples: -x, +x, not x, ~x
 */
public class UnaryOpNode extends AstNode {
    private String op;  // USub, UAdd, Not, Invert
    private AstNode operand;
    
    public String getOp() { 
        return op; 
    }
    
    public void setOp(String op) { 
        this.op = op; 
    }
    
    public AstNode getOperand() { 
        return operand; 
    }
    
    public void setOperand(AstNode operand) { 
        this.operand = operand; 
    }
    
    @Override
    public String toString(int indent) {
        StringBuilder sb = new StringBuilder();
        sb.append(getIndent(indent)).append("UnaryOp(\n");
        sb.append(getIndent(indent + 1)).append("op=").append(op).append(",\n");
        sb.append(getIndent(indent + 1)).append("operand=\n");
        if (operand != null) {
            sb.append(operand.toString(indent + 2));
        }
        sb.append("\n").append(getIndent(indent)).append(")");
        return sb.toString();
    }
}
