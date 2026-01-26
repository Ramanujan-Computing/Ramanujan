package in.ramanujan.translation.codeConverter.ast;

import java.util.*;

/**
 * Represents a function definition in Python.
 * 
 * <p>FunctionDefNode encapsulates a complete function definition including the function name,
 * parameters, and body. Functions are first-class objects in Python and can be assigned to
 * variables, passed as arguments, and returned from other functions.</p>
 * 
 * <h3>Python Examples</h3>
 * <pre>
 * # Simple function
 * def add(a, b):
 *     return a + b
 * 
 * # No parameters
 * def greet():
 *     print("Hello, World!")
 * 
 * # Multiple statements
 * def calculate_area(width, height):
 *     area = width * height
 *     perimeter = 2 * (width + height)
 *     return area
 * 
 * # With default arguments (not yet fully supported in basic AST)
 * def power(base, exponent=2):
 *     return base ** exponent
 * 
 * # With docstring
 * def factorial(n):
 *     """Calculate factorial of n"""
 *     if n <= 1:
 *         return 1
 *     return n * factorial(n - 1)
 * 
 * # Nested function
 * def outer(x):
 *     def inner(y):
 *         return x + y
 *     return inner
 * 
 * # Multiple return paths
 * def classify(score):
 *     if score >= 90:
 *         return 'A'
 *     elif score >= 80:
 *         return 'B'
 *     else:
 *         return 'F'
 * </pre>
 * 
 * <h3>AST Structure</h3>
 * <pre>
 * # Simple: def add(a, b): return a + b
 * FunctionDef(
 *   name='add',
 *   args=arguments(
 *     posonlyargs=[],
 *     args=[arg(arg='a'), arg(arg='b')],
 *     kwonlyargs=[],
 *     kw_defaults=[],
 *     defaults=[]),
 *   body=[
 *     Return(
 *       value=BinOp(
 *         left=Name(id='a', ctx=Load()),
 *         op=Add(),
 *         right=Name(id='b', ctx=Load())))],
 *   decorator_list=[])
 * 
 * # No parameters: def greet(): print("Hello")
 * FunctionDef(
 *   name='greet',
 *   args=arguments(
 *     posonlyargs=[],
 *     args=[],
 *     kwonlyargs=[],
 *     kw_defaults=[],
 *     defaults=[]),
 *   body=[
 *     Expr(value=Call(func=Name('print'), args=[Constant('Hello')]))],
 *   decorator_list=[])
 * 
 * # Multiple statements
 * FunctionDef(
 *   name='calculate',
 *   args=arguments(args=[arg(arg='x'), arg(arg='y')]),
 *   body=[
 *     Assign(targets=[Name('temp')], value=BinOp(...)),
 *     Assign(targets=[Name('result')], value=BinOp(...)),
 *     Return(value=Name('result'))],
 *   decorator_list=[])
 * </pre>
 * 
 * <h3>Field Details</h3>
 * <ul>
 *   <li><b>name:</b> The function name as a string (e.g., "add", "calculate", "greet")</li>
 *   <li><b>args:</b> ArgumentsNode containing the function's parameter list:
 *     <ul>
 *       <li>Positional arguments (args)</li>
 *       <li>Default arguments (defaults)</li>
 *       <li>Keyword-only arguments (kwonlyargs) - Python 3+</li>
 *       <li>Position-only arguments (posonlyargs) - Python 3.8+</li>
 *     </ul>
 *   </li>
 *   <li><b>body:</b> List of statements in the function body:
 *     <ul>
 *       <li>Can contain any statements: assignments, if/while, function calls, return</li>
 *       <li>First statement often a docstring (Expr with Constant string value)</li>
 *       <li>Must have at least one statement (use 'pass' for empty functions)</li>
 *       <li>Return statements can appear anywhere in body</li>
 *     </ul>
 *   </li>
 * </ul>
 * 
 * <h3>Common Patterns</h3>
 * <table border="1">
 *   <tr><th>Pattern</th><th>Example</th><th>Description</th></tr>
 *   <tr><td>Simple return</td><td>return x + y</td><td>Single expression returned</td></tr>
 *   <tr><td>Multiple returns</td><td>if..return, else..return</td><td>Different paths return different values</td></tr>
 *   <tr><td>Void function</td><td>No return statement</td><td>Returns None implicitly</td></tr>
 *   <tr><td>Side effects</td><td>print(), modify globals</td><td>Function used for actions, not values</td></tr>
 *   <tr><td>Recursive</td><td>Calls itself</td><td>Functions can call themselves</td></tr>
 * </table>
 * 
 * <h3>Usage Example</h3>
 * <pre>
 * FunctionDefNode funcDef = (FunctionDefNode) statement;
 * String funcName = funcDef.getName();
 * ArgumentsNode params = funcDef.getArgs();
 * List<AstNode> funcBody = funcDef.getBody();
 * 
 * System.out.println("Function: " + funcName);
 * System.out.println("Parameters: " + params.getArgs().size());
 * 
 * // List parameter names
 * for (ArgNode arg : params.getArgs()) {
 *     System.out.println("  Parameter: " + arg.getArg());
 * }
 * 
 * // Check for docstring (first statement is Expr with Constant string)
 * if (!funcBody.isEmpty() && funcBody.get(0) instanceof ExprNode) {
 *     ExprNode firstStmt = (ExprNode) funcBody.get(0);
 *     if (firstStmt.getValue() instanceof ConstantNode) {
 *         ConstantNode constant = (ConstantNode) firstStmt.getValue();
 *         if (constant.getValue() instanceof String) {
 *             System.out.println("Docstring: " + constant.getValue());
 *         }
 *     }
 * }
 * 
 * // Find return statements
 * for (AstNode stmt : funcBody) {
 *     if (stmt instanceof ReturnNode) {
 *         System.out.println("Found return statement");
 *     }
 * }
 * </pre>
 * 
 * @see AstNode
 * @see ArgumentsNode
 * @see ArgNode
 * @see ReturnNode
 * @see CallNode
 */
public class FunctionDefNode extends AstNode {
    private String name;
    private ArgumentsNode args;
    private List<AstNode> body = new ArrayList<>();
    
    public String getName() { 
        return name; 
    }
    
    public void setName(String name) { 
        this.name = name; 
    }
    
    public ArgumentsNode getArgs() { 
        return args; 
    }
    
    public void setArgs(ArgumentsNode args) { 
        this.args = args; 
    }
    
    public List<AstNode> getBody() { 
        return body; 
    }
    
    public void setBody(List<AstNode> body) { 
        this.body = body; 
    }
    
    @Override
    public String toString(int indent) {
        StringBuilder sb = new StringBuilder();
        sb.append(getIndent(indent)).append("FunctionDef(\n");
        sb.append(getIndent(indent + 1)).append("name='").append(name).append("',\n");
        sb.append(getIndent(indent + 1)).append("args=");
        if (args != null) {
            sb.append(args.toString());
        } else {
            sb.append("null");
        }
        sb.append(",\n");
        sb.append(getIndent(indent + 1)).append("body=[\n");
        for (int i = 0; i < body.size(); i++) {
            AstNode node = body.get(i);
            if (node != null) {
                sb.append(node.toString(indent + 2));
            } else {
                sb.append(getIndent(indent + 2)).append("null");
            }
            if (i < body.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append(getIndent(indent + 1)).append("]\n");
        sb.append(getIndent(indent)).append(")");
        return sb.toString();
    }
}
