package in.ramanujan.translation.codeConverter.ast;

import java.util.*;

/**
 * Represents a list comprehension in Python.
 * 
 * <p>List comprehensions provide a concise way to create lists based on existing sequences.
 * They consist of an expression followed by one or more for/if clauses.</p>
 * 
 * <h3>Python Examples</h3>
 * <pre>
 * # Simple comprehension
 * squares = [x**2 for x in range(10)]
 * 
 * # With condition
 * evens = [x for x in range(20) if x % 2 == 0]
 * 
 * # Nested comprehension (2D array)
 * matrix = [[0] * 10 for _ in range(10)]
 * 
 * # Multiple generators
 * pairs = [(x, y) for x in range(3) for y in range(3)]
 * </pre>
 * 
 * <h3>AST Structure</h3>
 * <pre>
 * # [[0] * 10 for _ in range(10)]
 * ListComp(
 *   elt=BinOp(
 *     left=List(elts=[Constant(0)], ctx=Load()),
 *     op=Mult(),
 *     right=Constant(10)),
 *   generators=[
 *     comprehension(
 *       target=Name(id='_', ctx=Store()),
 *       iter=Call(func=Name(id='range'), args=[Constant(10)]),
 *       ifs=[],
 *       is_async=0)])
 * </pre>
 * 
 * @see AstNode
 * @see ListNode
 */
public class ListCompNode extends AstNode {
    private AstNode elt;  // The element expression
    private List<ComprehensionNode> generators = new ArrayList<>();
    
    public AstNode getElt() { 
        return elt; 
    }
    
    public void setElt(AstNode elt) { 
        this.elt = elt; 
    }
    
    public List<ComprehensionNode> getGenerators() { 
        return generators; 
    }
    
    public void setGenerators(List<ComprehensionNode> generators) { 
        this.generators = generators; 
    }
    
    @Override
    public String toString(int indent) {
        StringBuilder sb = new StringBuilder();
        sb.append(getIndent(indent)).append("ListComp(\n");
        sb.append(getIndent(indent + 1)).append("elt=\n");
        if (elt != null) {
            sb.append(elt.toString(indent + 2));
        } else {
            sb.append(getIndent(indent + 2)).append("null");
        }
        sb.append(",\n");
        sb.append(getIndent(indent + 1)).append("generators=[\n");
        for (int i = 0; i < generators.size(); i++) {
            ComprehensionNode gen = generators.get(i);
            if (gen != null) {
                sb.append(gen.toString(indent + 2));
            } else {
                sb.append(getIndent(indent + 2)).append("null");
            }
            if (i < generators.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append(getIndent(indent + 1)).append("]\n");
        sb.append(getIndent(indent)).append(")");
        return sb.toString();
    }
}
