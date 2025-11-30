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
}
