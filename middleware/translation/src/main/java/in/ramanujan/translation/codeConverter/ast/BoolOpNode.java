package in.ramanujan.translation.codeConverter.ast;

import java.util.*;

/**
 * Represents a boolean operation in Python (and, or).
 * Examples: x and y, a or b or c
 */
public class BoolOpNode extends AstNode {
    private String op;  // And, Or
    private List<AstNode> values = new ArrayList<>();
    
    public String getOp() { 
        return op; 
    }
    
    public void setOp(String op) { 
        this.op = op; 
    }
    
    public List<AstNode> getValues() { 
        return values; 
    }
    
    public void setValues(List<AstNode> values) { 
        this.values = values; 
    }
    
    @Override
    public String toString(int indent) {
        StringBuilder sb = new StringBuilder();
        sb.append(getIndent(indent)).append("BoolOp(\n");
        sb.append(getIndent(indent + 1)).append("op=").append(op).append(",\n");
        sb.append(getIndent(indent + 1)).append("values=[\n");
        for (int i = 0; i < values.size(); i++) {
            if (values.get(i) != null) {
                sb.append(values.get(i).toString(indent + 2));
            }
            if (i < values.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append(getIndent(indent + 1)).append("]\n");
        sb.append(getIndent(indent)).append(")");
        return sb.toString();
    }
}
