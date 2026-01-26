package in.ramanujan.translation.codeConverter.ast;

import java.util.*;

/**
 * Represents a Python assignment statement.
 * 
 * <p>AssignNode handles all forms of assignment in Python, including simple variable assignments,
 * multiple target assignments, array element assignments, and complex expression assignments.</p>
 * 
 * <h3>Python Examples</h3>
 * <pre>
 * # Simple assignment
 * x = 5
 * 
 * # Multiple targets (chained assignment)
 * a = b = c = 10
 * 
 * # Array element assignment
 * arr[0] = 100
 * arr[i + 1] = 200
 * 
 * # Expression assignment
 * result = x + y * z
 * total = sum(numbers)
 * 
 * # Complex type assignment
 * matrix = [[1, 2], [3, 4]]
 * </pre>
 * 
 * <h3>AST Structure</h3>
 * <pre>
 * # For: x = 5
 * Assign(
 *   targets=[Name(id='x', ctx=Store())],
 *   value=Constant(value=5))
 * 
 * # For: a = b = 10
 * Assign(
 *   targets=[Name(id='a', ctx=Store()), Name(id='b', ctx=Store())],
 *   value=Constant(value=10))
 * 
 * # For: arr[0] = 100
 * Assign(
 *   targets=[Subscript(value=Name(id='arr', ctx=Load()), 
 *                      slice=Constant(value=0), ctx=Store())],
 *   value=Constant(value=100))
 * </pre>
 * 
 * <h3>Field Details</h3>
 * <ul>
 *   <li><b>targets:</b> List of assignment targets (left-hand side). Can be NameNode for variables 
 *       or SubscriptNode for array elements. Multiple targets indicate chained assignment.</li>
 *   <li><b>value:</b> The value being assigned (right-hand side). Can be any expression: 
 *       ConstantNode, NameNode, BinOpNode, CallNode, ListNode, etc.</li>
 * </ul>
 * 
 * <h3>Usage Example</h3>
 * <pre>
 * AssignNode assign = (AssignNode) statement;
 * 
 * // Get target variable name
 * AstNode firstTarget = assign.getTargets().get(0);
 * if (firstTarget instanceof NameNode) {
 *     String varName = ((NameNode) firstTarget).getId();
 *     System.out.println("Assigning to variable: " + varName);
 * }
 * 
 * // Get assigned value
 * AstNode value = assign.getValue();
 * if (value instanceof ConstantNode) {
 *     Object constantValue = ((ConstantNode) value).getValue();
 *     System.out.println("Constant value: " + constantValue);
 * }
 * </pre>
 * 
 * @see AstNode
 * @see NameNode
 * @see SubscriptNode
 */
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
    
    @Override
    public String toString(int indent) {
        StringBuilder sb = new StringBuilder();
        sb.append(getIndent(indent)).append("Assign(\n");
        sb.append(getIndent(indent + 1)).append("targets=[\n");
        for (int i = 0; i < targets.size(); i++) {
            AstNode target = targets.get(i);
            if (target != null) {
                sb.append(target.toString(indent + 2));
            } else {
                sb.append(getIndent(indent + 2)).append("null");
            }
            if (i < targets.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append(getIndent(indent + 1)).append("],\n");
        sb.append(getIndent(indent + 1)).append("value=");
        if (value != null) {
            sb.append("\n").append(value.toString(indent + 2));
        } else {
            sb.append("null");
        }
        sb.append("\n").append(getIndent(indent)).append(")");
        return sb.toString();
    }
}
