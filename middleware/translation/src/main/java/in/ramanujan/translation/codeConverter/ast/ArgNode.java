package in.ramanujan.translation.codeConverter.ast;

/**
 * Represents a single function parameter (argument) in Python.
 * 
 * <p>ArgNode holds the name of a function parameter. It is used within ArgumentsNode
 * to specify the parameter list for a function definition.</p>
 * 
 * <h3>Python Examples</h3>
 * <pre>
 * # Single parameter
 * def square(x):
 *     return x * x
 * # ArgNode: arg='x'
 * 
 * # Multiple parameters
 * def add(a, b):
 *     return a + b
 * # ArgNode: arg='a'
 * # ArgNode: arg='b'
 * 
 * # Descriptive parameter names
 * def calculate_area(width, height):
 *     return width * height
 * # ArgNode: arg='width'
 * # ArgNode: arg='height'
 * </pre>
 * 
 * <h3>AST Structure</h3>
 * <pre>
 * # For parameter 'x' in: def func(x):
 * arg(arg='x')
 * 
 * # For parameter 'count' in: def increment(count):
 * arg(arg='count')
 * 
 * # Within full function:
 * FunctionDef(
 *   name='add',
 *   args=arguments(
 *     args=[
 *       arg(arg='a'),    # First parameter
 *       arg(arg='b')     # Second parameter
 *     ]))
 * </pre>
 *
 * 
 * @see ArgumentsNode
 * @see FunctionDefNode
 */
public class ArgNode {
    private String arg;
    
    public String getArg() { 
        return arg; 
    }
    
    public void setArg(String arg) { 
        this.arg = arg; 
    }
}
