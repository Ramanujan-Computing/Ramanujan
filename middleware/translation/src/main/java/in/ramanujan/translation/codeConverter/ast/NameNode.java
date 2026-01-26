package in.ramanujan.translation.codeConverter.ast;

/**
 * Represents a variable or identifier reference in Python code.
 * 
 * <p>NameNode is used for all variable names in Python, whether they appear on the left side
 * of an assignment (Store context) or the right side (Load context). This includes function names,
 * variable names, and any other identifiers.</p>
 * 
 * <h3>Python Examples</h3>
 * <pre>
 * # Store context (assignment target)
 * x = 5              # Name(id='x', ctx=Store())
 * result = 10        # Name(id='result', ctx=Store())
 * 
 * # Load context (reading value)
 * y = x + 1          # Name(id='x', ctx=Load())
 * print(result)      # Name(id='result', ctx=Load())
 * z = myFunc()       # Name(id='myFunc', ctx=Load())
 * 
 * # In expressions
 * total = x + y + z  # Three Name nodes with Load context
 * 
 * # In conditions
 * if count > 0:      # Name(id='count', ctx=Load())
 *     pass
 * </pre>
 * 
 * <h3>AST Structure</h3>
 * <pre>
 * # Variable assignment: x = 5
 * Assign(
 *   targets=[Name(id='x', ctx=Store())],  # Store: being assigned
 *   value=Constant(value=5))
 * 
 * # Variable usage: y = x + 1
 * Assign(
 *   targets=[Name(id='y', ctx=Store())],  # Store: assignment target
 *   value=BinOp(
 *     left=Name(id='x', ctx=Load()),      # Load: reading value
 *     op=Add(),
 *     right=Constant(value=1)))
 * </pre>
 * 
 * <h3>Field Details</h3>
 * <ul>
 *   <li><b>id:</b> The identifier name (variable name, function name, etc.)</li>
 *   <li><b>ctx:</b> The context in which the name appears:
 *     <ul>
 *       <li><b>"Store":</b> Name is being assigned to (left side of =)</li>
 *       <li><b>"Load":</b> Name's value is being read (right side of =, in expressions)</li>
 *       <li><b>"Del":</b> Name is being deleted (del statement, rare)</li>
 *     </ul>
 *   </li>
 * </ul>
 * 
 * <h3>Context Usage</h3>
 * <table border="1">
 *   <tr><th>Python Code</th><th>Context</th><th>Description</th></tr>
 *   <tr><td>x = 5</td><td>Store</td><td>Assigning to x</td></tr>
 *   <tr><td>y = x</td><td>Load (for x)</td><td>Reading x's value</td></tr>
 *   <tr><td>y = x</td><td>Store (for y)</td><td>Assigning to y</td></tr>
 *   <tr><td>func(x)</td><td>Load</td><td>Passing x to function</td></tr>
 *   <tr><td>if x > 0:</td><td>Load</td><td>Using x in condition</td></tr>
 * </table>
 * 
 * <h3>Usage Example</h3>
 * <pre>
 * NameNode name = (NameNode) node;
 * String varName = name.getId();
 * String context = name.getCtx();
 * 
 * if ("Store".equals(context)) {
 *     System.out.println("Assigning to variable: " + varName);
 * } else if ("Load".equals(context)) {
 *     System.out.println("Reading variable: " + varName);
 * }
 * </pre>
 * 
 * @see AstNode
 * @see AssignNode
 * @see SubscriptNode
 */
public class NameNode extends AstNode {
    private String id;
    private String ctx;  // Load, Store
    
    public String getId() { 
        return id; 
    }
    
    public void setId(String id) { 
        this.id = id; 
    }
    
    public String getCtx() { 
        return ctx; 
    }
    
    public void setCtx(String ctx) { 
        this.ctx = ctx; 
    }
    
    @Override
    public String toString(int indent) {
        return getIndent(indent) + "Name(id='" + id + "', ctx=" + ctx + "())";
    }
}
