package in.ramanujan.translation.codeConverter.ast;

/**
 * Represents an expression statement in Python.
 * 
 * <p>ExprNode wraps an expression that appears as a statement (not part of an assignment,
 * condition, or other construct). This commonly occurs with function calls that are
 * invoked for their side effects rather than their return values.</p>
 * 
 * <h3>Python Examples</h3>
 * <pre>
 * # Function calls as statements (most common)
 * print("Hello, World!")      # Print for side effect
 * list.append(item)            # Modify list
 * dict.clear()                 # Clear dictionary
 * file.close()                 # Close file
 * 
 * # Standalone expressions (rare, usually not meaningful)
 * 42                           # Constant expression (no effect)
 * x + 5                        # Binary operation (no effect)
 * "hello"                      # String constant (no effect)
 * 
 * # Method calls
 * obj.method()
 * instance.update()
 * 
 * # Constructor calls (object created but not assigned)
 * MyClass()                    # Creates instance, then discarded
 * 
 * # Nested function calls
 * print(calculate(x, y))
 * process(transform(data))
 * </pre>
 * 
 * <h3>AST Structure</h3>
 * <pre>
 * # print("Hello")
 * Expr(
 *   value=Call(
 *     func=Name(id='print', ctx=Load()),
 *     args=[Constant(value='Hello')],
 *     keywords=[]))
 * 
 * # list.append(item)
 * Expr(
 *   value=Call(
 *     func=Attribute(
 *       value=Name(id='list', ctx=Load()),
 *       attr='append',
 *       ctx=Load()),
 *     args=[Name(id='item', ctx=Load())],
 *     keywords=[]))
 * 
 * # Standalone constant: 42
 * Expr(value=Constant(value=42))
 * 
 * # Standalone expression: x + 5
 * Expr(
 *   value=BinOp(
 *     left=Name(id='x', ctx=Load()),
 *     op=Add(),
 *     right=Constant(value=5)))
 * </pre>
 * 
 * <h3>Field Details</h3>
 * <ul>
 *   <li><b>value:</b> The expression being evaluated. Common types:
 *     <ul>
 *       <li><b>CallNode</b> - Function/method calls (most common): print(), append()</li>
 *       <li><b>ConstantNode</b> - Standalone literals (rare): 42, "text"</li>
 *       <li><b>BinOpNode</b> - Arithmetic expressions (rare): x + 5</li>
 *       <li><b>NameNode</b> - Variable reference (rare): variable_name</li>
 *       <li>Any expression type</li>
 *     </ul>
 *   </li>
 * </ul>
 * 
 * <h3>Expression Statement vs Other Statements</h3>
 * <table border="1">
 *   <tr><th>Statement Type</th><th>Example</th><th>Purpose</th></tr>
 *   <tr><td>Expr (this class)</td><td>print(x)</td><td>Expression evaluated for side effects</td></tr>
 *   <tr><td>Assign</td><td>x = print(y)</td><td>Assign result to variable</td></tr>
 *   <tr><td>Return</td><td>return x + 5</td><td>Return value from function</td></tr>
 *   <tr><td>AugAssign</td><td>x += 5</td><td>Modify and assign variable</td></tr>
 * </table>
 * 
 * <h3>Common Patterns</h3>
 * <pre>
 * # Function calls for side effects
 * print(message)              # Output to console
 * file.write(data)            # Write to file
 * list.sort()                 # Sort in place
 * dict.update(other)          # Update dictionary
 * 
 * # Docstrings (first statement in function/class/module)
 * def my_function():
 *     """This is a docstring"""    # Expr with Constant string
 *     pass
 * 
 * # Interactive/REPL expressions
 * 2 + 2                       # Shows result in interactive mode
 * x                           # Shows variable value
 * </pre>
 * 
 * <h3>Usage Example</h3>
 * <pre>
 * ExprNode exprNode = (ExprNode) statement;
 * AstNode expression = exprNode.getValue();
 * 
 * System.out.println("Expression statement: " + expression.getClass().getSimpleName());
 * 
 * // Most commonly a function call
 * if (expression instanceof CallNode) {
 *     CallNode call = (CallNode) expression;
 *     AstNode func = call.getFunc();
 *     
 *     if (func instanceof NameNode) {
 *         String funcName = ((NameNode) func).getId();
 *         System.out.println("Function call: " + funcName);
 *         
 *         // Check for common side-effect functions
 *         if ("print".equals(funcName)) {
 *             System.out.println("Print statement");
 *         }
 *     } else if (func instanceof AttributeNode) {
 *         AttributeNode attr = (AttributeNode) func;
 *         System.out.println("Method call: " + attr.getAttr());
 *     }
 * }
 * 
 * // Check for docstring (Constant string as first statement)
 * else if (expression instanceof ConstantNode) {
 *     Object value = ((ConstantNode) expression).getValue();
 *     if (value instanceof String) {
 *         System.out.println("Potential docstring: " + value);
 *     }
 * }
 * </pre>
 * 
 * @see AstNode
 * @see CallNode
 * @see AssignNode
 * @see ReturnNode
 */
public class ExprNode extends AstNode {
    private AstNode value;
    
    public AstNode getValue() { 
        return value; 
    }
    
    public void setValue(AstNode value) { 
        this.value = value; 
    }
    
    @Override
    public String toString(int indent) {
        StringBuilder sb = new StringBuilder();
        sb.append(getIndent(indent)).append("Expr(\n");
        sb.append(getIndent(indent + 1)).append("value=\n");
        if (value != null) {
            sb.append(value.toString(indent + 2));
        } else {
            sb.append(getIndent(indent + 2)).append("null");
        }
        sb.append("\n").append(getIndent(indent)).append(")");
        return sb.toString();
    }
}
