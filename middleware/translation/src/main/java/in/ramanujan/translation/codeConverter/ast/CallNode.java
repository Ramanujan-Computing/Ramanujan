package in.ramanujan.translation.codeConverter.ast;

import java.util.*;

/**
 * Represents a function call in Python.
 * 
 * <p>CallNode handles all types of function calls including regular functions, methods,
 * built-in functions, and constructors. The func field identifies what is being called,
 * and args contains the arguments passed to the function.</p>
 * 
 * <h3>Python Examples</h3>
 * <pre>
 * # Simple function call
 * result = add(3, 5)
 * 
 * # Built-in function
 * length = len(my_list)
 * text = str(42)
 * maximum = max(10, 20, 30)
 * 
 * # Method call (appears as Call with AttributeNode as func)
 * value = obj.get_value()
 * text = message.upper()
 * 
 * # Nested calls
 * result = int(input("Enter number: "))
 * 
 * # Call with expression arguments
 * sum_val = add(x * 2, y + 3)
 * 
 * # Call in condition
 * if is_valid(user_input):
 *     process(user_input)
 * 
 * # Call as statement (side effect)
 * print("Hello, World!")
 * list.append(item)
 * 
 * # Multiple arguments
 * formatted = format_string(name, age, city)
 * </pre>
 * 
 * <h3>AST Structure</h3>
 * <pre>
 * # Simple: add(3, 5)
 * Call(
 *   func=Name(id='add', ctx=Load()),
 *   args=[
 *     Constant(value=3),
 *     Constant(value=5)],
 *   keywords=[])
 * 
 * # Built-in: len(my_list)
 * Call(
 *   func=Name(id='len', ctx=Load()),
 *   args=[Name(id='my_list', ctx=Load())],
 *   keywords=[])
 * 
 * # Method: obj.method(x)
 * Call(
 *   func=Attribute(
 *     value=Name(id='obj', ctx=Load()),
 *     attr='method',
 *     ctx=Load()),
 *   args=[Name(id='x', ctx=Load())],
 *   keywords=[])
 * 
 * # Nested: int(input("prompt"))
 * Call(
 *   func=Name(id='int', ctx=Load()),
 *   args=[
 *     Call(
 *       func=Name(id='input', ctx=Load()),
 *       args=[Constant(value='prompt')],
 *       keywords=[])],
 *   keywords=[])
 * 
 * # No arguments: func()
 * Call(
 *   func=Name(id='func', ctx=Load()),
 *   args=[],
 *   keywords=[])
 * </pre>
 * 
 * <h3>Field Details</h3>
 * <ul>
 *   <li><b>func:</b> The function/method being called. Can be:
 *     <ul>
 *       <li>NameNode - Regular function: add, len, print</li>
 *       <li>AttributeNode - Method call: obj.method, list.append</li>
 *       <li>SubscriptNode - Array of functions: funcs[i]()</li>
 *       <li>CallNode - Return value is callable: get_func()()</li>
 *     </ul>
 *   </li>
 *   <li><b>args:</b> List of argument expressions passed to the function:
 *     <ul>
 *       <li>ConstantNode - Literal values: 5, "text", True</li>
 *       <li>NameNode - Variables: x, my_list, counter</li>
 *       <li>BinOpNode - Expressions: x + 1, a * b</li>
 *       <li>CallNode - Nested calls: inner_func()</li>
 *       <li>ListNode - List literals: [1, 2, 3]</li>
 *       <li>Empty list for no arguments</li>
 *     </ul>
 *   </li>
 * </ul>
 * 
 * <h3>Common Function Call Patterns</h3>
 * <table border="1">
 *   <tr><th>Pattern</th><th>Example</th><th>func type</th></tr>
 *   <tr><td>Regular function</td><td>add(x, y)</td><td>NameNode</td></tr>
 *   <tr><td>Built-in</td><td>len(list), print(msg)</td><td>NameNode</td></tr>
 *   <tr><td>Method</td><td>obj.method(arg)</td><td>AttributeNode</td></tr>
 *   <tr><td>Constructor</td><td>MyClass(args)</td><td>NameNode</td></tr>
 *   <tr><td>Nested</td><td>f(g(x))</td><td>NameNode (inner call in args)</td></tr>
 *   <tr><td>No args</td><td>func()</td><td>NameNode (empty args)</td></tr>
 * </table>
 * 
 * <h3>Usage Example</h3>
 * <pre>
 * CallNode callNode = (CallNode) expression;
 * AstNode func = callNode.getFunc();
 * List<AstNode> arguments = callNode.getArgs();
 * 
 * // Determine what is being called
 * if (func instanceof NameNode) {
 *     String funcName = ((NameNode) func).getId();
 *     System.out.println("Calling function: " + funcName);
 * } else if (func instanceof AttributeNode) {
 *     AttributeNode attr = (AttributeNode) func;
 *     System.out.println("Calling method: " + attr.getAttr());
 * }
 * 
 * // Process arguments
 * System.out.println("Number of arguments: " + arguments.size());
 * for (int i = 0; i < arguments.size(); i++) {
 *     AstNode arg = arguments.get(i);
 *     System.out.println("Argument " + i + ": " + arg.getClass().getSimpleName());
 *     
 *     // Extract constant values
 *     if (arg instanceof ConstantNode) {
 *         Object value = ((ConstantNode) arg).getValue();
 *         System.out.println("  Constant value: " + value);
 *     }
 * }
 * 
 * // Check for common built-in functions
 * if (func instanceof NameNode) {
 *     String name = ((NameNode) func).getId();
 *     if ("print".equals(name) || "len".equals(name) || "str".equals(name)) {
 *         System.out.println("Built-in function call: " + name);
 *     }
 * }
 * </pre>
 * 
 * @see AstNode
 * @see NameNode
 * @see AttributeNode
 * @see FunctionDefNode
 */
public class CallNode extends AstNode {
    private AstNode func;
    private List<AstNode> args = new ArrayList<>();
    
    public AstNode getFunc() { 
        return func; 
    }
    
    public void setFunc(AstNode func) { 
        this.func = func; 
    }
    
    public List<AstNode> getArgs() { 
        return args; 
    }
    
    public void setArgs(List<AstNode> args) { 
        this.args = args; 
    }
    
    @Override
    public String toString(int indent) {
        StringBuilder sb = new StringBuilder();
        sb.append(getIndent(indent)).append("Call(\n");
        sb.append(getIndent(indent + 1)).append("func=\n");
        if (func != null) {
            sb.append(func.toString(indent + 2));
        } else {
            sb.append(getIndent(indent + 2)).append("null");
        }
        sb.append(",\n");
        sb.append(getIndent(indent + 1)).append("args=[\n");
        for (int i = 0; i < args.size(); i++) {
            AstNode arg = args.get(i);
            if (arg != null) {
                sb.append(arg.toString(indent + 2));
            } else {
                sb.append(getIndent(indent + 2)).append("null");
            }
            if (i < args.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append(getIndent(indent + 1)).append("]\n");
        sb.append(getIndent(indent)).append(")");
        return sb.toString();
    }
}
