package in.ramanujan.translation.codeConverter.ast;

/**
 * Represents a constant literal value in Python code.
 * 
 * <p>ConstantNode contains literal values that appear directly in the source code,
 * including numbers (integers, floats), strings, booleans, and None. In Python 3.8+,
 * all literal values are represented as Constant nodes.</p>
 * 
 * <h3>Python Examples</h3>
 * <pre>
 * # Integer constants
 * x = 42              # Constant(value=42)
 * count = 0           # Constant(value=0)
 * bignum = 1000000    # Constant(value=1000000)
 * 
 * # Float constants
 * pi = 3.14159        # Constant(value=3.14159)
 * ratio = 0.5         # Constant(value=0.5)
 * scientific = 1.5e10 # Constant(value=15000000000.0)
 * 
 * # String constants
 * name = "Alice"      # Constant(value='Alice')
 * msg = 'Hello'       # Constant(value='Hello')
 * multi = """text""" # Constant(value='text')
 * 
 * # Boolean constants
 * flag = True         # Constant(value=True)
 * active = False      # Constant(value=False)
 * 
 * # None constant
 * result = None       # Constant(value=None)
 * 
 * # In expressions
 * total = x + 100     # 100 is Constant(value=100)
 * arr = [1, 2, 3]     # Each number is a Constant
 * </pre>
 * 
 * <h3>AST Structure</h3>
 * <pre>
 * # Integer: x = 42
 * Assign(
 *   targets=[Name(id='x', ctx=Store())],
 *   value=Constant(value=42))
 * 
 * # Float: pi = 3.14
 * Assign(
 *   targets=[Name(id='pi', ctx=Store())],
 *   value=Constant(value=3.14))
 * 
 * # String: msg = "Hello"
 * Assign(
 *   targets=[Name(id='msg', ctx=Store())],
 *   value=Constant(value='Hello'))
 * 
 * # Boolean: flag = True
 * Assign(
 *   targets=[Name(id='flag', ctx=Store())],
 *   value=Constant(value=True))
 * </pre>
 * 
 * <h3>Field Details</h3>
 * <ul>
 *   <li><b>value:</b> The constant value as an Object. Can be:
 *     <ul>
 *       <li><b>Integer:</b> For whole numbers (e.g., 42, 0, -10)</li>
 *       <li><b>Double:</b> For floating-point numbers (e.g., 3.14, 0.5, 1.5e10)</li>
 *       <li><b>String:</b> For text literals (e.g., "hello", 'world')</li>
 *       <li><b>Boolean:</b> For True/False (in Python)</li>
 *       <li><b>null:</b> For None (in Python)</li>
 *     </ul>
 *   </li>
 * </ul>
 * 
 * <h3>Type Detection</h3>
 * <pre>
 * ConstantNode constant = (ConstantNode) node;
 * Object value = constant.getValue();
 * 
 * if (value instanceof Integer) {
 *     int intValue = (Integer) value;
 *     System.out.println("Integer: " + intValue);
 * } else if (value instanceof Double) {
 *     double doubleValue = (Double) value;
 *     System.out.println("Float: " + doubleValue);
 * } else if (value instanceof String) {
 *     String strValue = (String) value;
 *     System.out.println("String: " + strValue);
 * } else if (value instanceof Boolean) {
 *     boolean boolValue = (Boolean) value;
 *     System.out.println("Boolean: " + boolValue);
 * } else if (value == null) {
 *     System.out.println("None");
 * }
 * </pre>
 * 
 * @see AstNode
 * @see AssignNode
 * @see BinOpNode
 */
public class ConstantNode extends AstNode {
    private Object value;
    
    public Object getValue() { 
        return value; 
    }
    
    public void setValue(Object value) { 
        this.value = value; 
    }
}
