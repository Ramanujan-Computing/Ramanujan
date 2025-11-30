package in.ramanujan.translation.codeConverter.ast;

/**
 * Represents a return statement in Python.
 * 
 * <p>ReturnNode exits a function and optionally returns a value to the caller. If no
 * value is specified, Python returns None implicitly. Return statements can appear
 * anywhere in a function body and immediately exit the function.</p>
 * 
 * <h3>Python Examples</h3>
 * <pre>
 * # Return constant value
 * def get_pi():
 *     return 3.14159
 * 
 * # Return variable
 * def get_value():
 *     result = calculate()
 *     return result
 * 
 * # Return expression
 * def add(a, b):
 *     return a + b
 * 
 * # Return comparison
 * def is_positive(x):
 *     return x > 0
 * 
 * # Return None implicitly (empty return)
 * def log_message(msg):
 *     print(msg)
 *     return           # Returns None
 * 
 * # Return None explicitly
 * def validate(data):
 *     if not data:
 *         return None
 *     return process(data)
 * 
 * # Multiple return statements (different paths)
 * def get_grade(score):
 *     if score >= 90:
 *         return 'A'
 *     elif score >= 80:
 *         return 'B'
 *     else:
 *         return 'F'
 * 
 * # Return complex expression
 * def distance(x1, y1, x2, y2):
 *     return ((x2 - x1)**2 + (y2 - y1)**2) ** 0.5
 * 
 * # Return list
 * def get_coordinates():
 *     return [x, y, z]
 * 
 * # Return function call result
 * def transform(data):
 *     return process(validate(data))
 * </pre>
 * 
 * <h3>AST Structure</h3>
 * <pre>
 * # return 42
 * Return(value=Constant(value=42))
 * 
 * # return x + 5
 * Return(
 *   value=BinOp(
 *     left=Name(id='x', ctx=Load()),
 *     op=Add(),
 *     right=Constant(value=5)))
 * 
 * # return x > 0
 * Return(
 *   value=Compare(
 *     left=Name(id='x', ctx=Load()),
 *     ops=[Gt()],
 *     comparators=[Constant(value=0)]))
 * 
 * # return (empty or None)
 * Return(value=None)
 * # or
 * Return(value=Constant(value=None))
 * 
 * # return [a, b, c]
 * Return(
 *   value=List(
 *     elts=[
 *       Name(id='a', ctx=Load()),
 *       Name(id='b', ctx=Load()),
 *       Name(id='c', ctx=Load())],
 *     ctx=Load()))
 * </pre>
 * 
 * <h3>Field Details</h3>
 * <ul>
 *   <li><b>value:</b> The expression to return (can be null/None):
 *     <ul>
 *       <li><b>null or ConstantNode(None)</b> - Empty return, returns None</li>
 *       <li><b>ConstantNode</b> - Literal value: 42, "text", True</li>
 *       <li><b>NameNode</b> - Variable: result, output, data</li>
 *       <li><b>BinOpNode</b> - Arithmetic: a + b, x * 2</li>
 *       <li><b>CompareNode</b> - Boolean: x > 5, a == b</li>
 *       <li><b>CallNode</b> - Function result: func(), obj.method()</li>
 *       <li><b>ListNode</b> - List literal: [1, 2, 3]</li>
 *       <li><b>SubscriptNode</b> - Array element: arr[i]</li>
 *       <li>Any expression type</li>
 *     </ul>
 *   </li>
 * </ul>
 * 
 * <h3>Return Statement Patterns</h3>
 * <table border="1">
 *   <tr><th>Pattern</th><th>Example</th><th>Returns</th></tr>
 *   <tr><td>Return value</td><td>return x</td><td>Value of x</td></tr>
 *   <tr><td>Return expression</td><td>return a + b</td><td>Result of expression</td></tr>
 *   <tr><td>Return None</td><td>return or return None</td><td>None</td></tr>
 *   <tr><td>Early return</td><td>if error: return None</td><td>Exit function early</td></tr>
 *   <tr><td>Return boolean</td><td>return x > 5</td><td>True or False</td></tr>
 *   <tr><td>Return function</td><td>return func()</td><td>Function's return value</td></tr>
 * </table>
 * 
 * <h3>Usage Example</h3>
 * <pre>
 * ReturnNode returnNode = (ReturnNode) statement;
 * AstNode returnValue = returnNode.getValue();
 * 
 * if (returnValue == null) {
 *     System.out.println("Empty return (returns None)");
 * } else if (returnValue instanceof ConstantNode) {
 *     Object value = ((ConstantNode) returnValue).getValue();
 *     if (value == null) {
 *         System.out.println("Explicit return None");
 *     } else {
 *         System.out.println("Returns constant: " + value);
 *     }
 * } else if (returnValue instanceof NameNode) {
 *     String varName = ((NameNode) returnValue).getId();
 *     System.out.println("Returns variable: " + varName);
 * } else if (returnValue instanceof BinOpNode) {
 *     System.out.println("Returns arithmetic expression");
 * } else if (returnValue instanceof CompareNode) {
 *     System.out.println("Returns boolean comparison");
 * } else if (returnValue instanceof CallNode) {
 *     System.out.println("Returns function call result");
 * } else if (returnValue instanceof ListNode) {
 *     System.out.println("Returns list");
 * } else {
 *     System.out.println("Returns: " + returnValue.getClass().getSimpleName());
 * }
 * </pre>
 * 
 * @see AstNode
 * @see FunctionDefNode
 * @see ConstantNode
 * @see BinOpNode
 * @see CompareNode
 */
public class ReturnNode extends AstNode {
    private AstNode value;
    
    public AstNode getValue() { 
        return value; 
    }
    
    public void setValue(AstNode value) { 
        this.value = value; 
    }
}
