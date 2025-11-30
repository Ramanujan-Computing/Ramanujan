package in.ramanujan.translation.codeConverter.ast;

import java.util.*;

/**
 * Represents a comprehension clause in a list/set/dict comprehension.
 * 
 * <p>Each comprehension has a target variable, an iterable, and optional conditions.</p>
 * 
 * <h3>Structure</h3>
 * <pre>
 * comprehension(
 *   target=Name(id='x', ctx=Store()),
 *   iter=Call(func=Name(id='range'), args=[Constant(10)]),
 *   ifs=[],
 *   is_async=0)
 * </pre>
 */
public class ComprehensionNode extends AstNode {
    private AstNode target;  // The loop variable
    private AstNode iter;    // The iterable expression
    private List<AstNode> ifs = new ArrayList<>();  // Optional conditions
    private int isAsync;     // 0 for sync, 1 for async
    
    public AstNode getTarget() { 
        return target; 
    }
    
    public void setTarget(AstNode target) { 
        this.target = target; 
    }
    
    public AstNode getIter() { 
        return iter; 
    }
    
    public void setIter(AstNode iter) { 
        this.iter = iter; 
    }
    
    public List<AstNode> getIfs() { 
        return ifs; 
    }
    
    public void setIfs(List<AstNode> ifs) { 
        this.ifs = ifs; 
    }
    
    public int getIsAsync() { 
        return isAsync; 
    }
    
    public void setIsAsync(int isAsync) { 
        this.isAsync = isAsync; 
    }
    
    @Override
    public String toString(int indent) {
        StringBuilder sb = new StringBuilder();
        sb.append(getIndent(indent)).append("comprehension(\n");
        sb.append(getIndent(indent + 1)).append("target=\n");
        if (target != null) {
            sb.append(target.toString(indent + 2));
        } else {
            sb.append(getIndent(indent + 2)).append("null");
        }
        sb.append(",\n");
        sb.append(getIndent(indent + 1)).append("iter=\n");
        if (iter != null) {
            sb.append(iter.toString(indent + 2));
        } else {
            sb.append(getIndent(indent + 2)).append("null");
        }
        sb.append(",\n");
        sb.append(getIndent(indent + 1)).append("ifs=[\n");
        for (int i = 0; i < ifs.size(); i++) {
            AstNode ifNode = ifs.get(i);
            if (ifNode != null) {
                sb.append(ifNode.toString(indent + 2));
            } else {
                sb.append(getIndent(indent + 2)).append("null");
            }
            if (i < ifs.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append(getIndent(indent + 1)).append("],\n");
        sb.append(getIndent(indent + 1)).append("is_async=").append(isAsync).append("\n");
        sb.append(getIndent(indent)).append(")");
        return sb.toString();
    }
}
