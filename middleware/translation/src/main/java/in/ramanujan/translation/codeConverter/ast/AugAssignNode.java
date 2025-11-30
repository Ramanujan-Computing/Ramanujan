package in.ramanujan.translation.codeConverter.ast;

/**
 * Represents a Python augmented assignment statement (compound assignment operator).
 * 
 * <p>AugAssignNode handles all augmented assignment operations where a binary operation
 * is combined with assignment: +=, -=, *=, /=, //=, %=, **=, &=, |=, ^=, <<=, >>=</p>
 * 
 * <h3>Python Examples</h3>
 * <pre>
 * # Addition assignment
 * x += 5        # Equivalent to: x = x + 5
 * 
 * # Subtraction assignment
 * count -= 1    # Equivalent to: count = count - 1
 * 
 * # Multiplication assignment
 * value *= 2    # Equivalent to: value = value * 2
 * 
 * # Division assignment
 * total /= 10   # Equivalent to: total = total / 10
 * 
 * # Modulo assignment
 * index %= len(arr)  # Equivalent to: index = index % len(arr)
 * 
 * # Power assignment
 * x **= 2       # Equivalent to: x = x ** 2
 * 
 * # Array element augmented assignment
 * arr[i] += 10  # Equivalent to: arr[i] = arr[i] + 10
 * </pre>
 * 
 * <h3>AST Structure</h3>
 * <pre>
 * # For: x += 5
 * AugAssign(
 *   target=Name(id='x', ctx=Store()),
 *   op=Add(),
 *   value=Constant(value=5))
 * 
 * # For: count *= 2
 * AugAssign(
 *   target=Name(id='count', ctx=Store()),
 *   op=Mult(),
 *   value=Constant(value=2))
 * 
 * # For: arr[i] += 1
 * AugAssign(
 *   target=Subscript(value=Name(id='arr', ctx=Load()), 
 *                    slice=Name(id='i', ctx=Load()), ctx=Store()),
 *   op=Add(),
 *   value=Constant(value=1))
 * </pre>
 * 
 * <h3>Field Details</h3>
 * <ul>
 *   <li><b>target:</b> The variable or array element being modified (NameNode or SubscriptNode)</li>
 *   <li><b>op:</b> The binary operation: "Add", "Sub", "Mult", "Div", "FloorDiv", "Mod", "Pow", 
 *       "LShift", "RShift", "BitOr", "BitXor", "BitAnd"</li>
 *   <li><b>value:</b> The value on the right side of the operator</li>
 * </ul>
 * 
 * <h3>Operator Mapping</h3>
 * <table border="1">
 *   <tr><th>Python</th><th>Op String</th><th>Meaning</th></tr>
 *   <tr><td>+=</td><td>Add</td><td>Addition</td></tr>
 *   <tr><td>-=</td><td>Sub</td><td>Subtraction</td></tr>
 *   <tr><td>*=</td><td>Mult</td><td>Multiplication</td></tr>
 *   <tr><td>/=</td><td>Div</td><td>Division</td></tr>
 *   <tr><td>//=</td><td>FloorDiv</td><td>Floor Division</td></tr>
 *   <tr><td>%=</td><td>Mod</td><td>Modulo</td></tr>
 *   <tr><td>**=</td><td>Pow</td><td>Power</td></tr>
 * </table>
 * 
 * <h3>Usage Example</h3>
 * <pre>
 * AugAssignNode augAssign = (AugAssignNode) statement;
 * 
 * // Get target variable
 * if (augAssign.getTarget() instanceof NameNode) {
 *     String varName = ((NameNode) augAssign.getTarget()).getId();
 *     String operation = augAssign.getOp();
 *     System.out.println(varName + " " + operation + "= ...");
 * }
 * 
 * // Convert to regular assignment equivalent
 * // x += 5  becomes  x = x + 5
 * </pre>
 * 
 * @see AstNode
 * @see AssignNode
 * @see BinOpNode
 */
public class AugAssignNode extends AstNode {
    private AstNode target;
    private String op;  // Add, Sub, Mult, Div
    private AstNode value;
    
    public AstNode getTarget() { 
        return target; 
    }
    
    public void setTarget(AstNode target) { 
        this.target = target; 
    }
    
    public String getOp() { 
        return op; 
    }
    
    public void setOp(String op) { 
        this.op = op; 
    }
    
    public AstNode getValue() { 
        return value; 
    }
    
    public void setValue(AstNode value) { 
        this.value = value; 
    }
}
