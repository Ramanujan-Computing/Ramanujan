package in.ramanujan.translation.codeConverter.ast;

import java.util.*;

/**
 * Represents the root node of a Python AST (Abstract Syntax Tree).
 * 
 * <p>ModuleNode is the top-level container for all statements in a Python program.
 * Every Python AST starts with a Module node that contains a body list of statements.</p>
 * 
 * <h3>Python Examples</h3>
 * <pre>
 * # Simple program
 * x = 5
 * y = 10
 * print(x + y)
 * 
 * # Function definition
 * def greet(name):
 *     return "Hello " + name
 * 
 * # Control flow
 * if x > 0:
 *     y = x * 2
 * </pre>
 * 
 * <h3>AST Structure</h3>
 * <pre>
 * Module(
 *   body=[
 *     Assign(targets=[Name(id='x', ctx=Store())], value=Constant(value=5)),
 *     Assign(targets=[Name(id='y', ctx=Store())], value=Constant(value=10)),
 *     Expr(value=Call(func=Name(id='print', ctx=Load()), 
 *                     args=[BinOp(left=Name(id='x', ctx=Load()), op=Add(), 
 *                                 right=Name(id='y', ctx=Load()))]))],
 *   type_ignores=[])
 * </pre>
 * 
 * <h3>Usage</h3>
 * <pre>
 * ModuleNode module = parser.parse(astDump);
 * for (AstNode statement : module.getBody()) {
 *     // Process each top-level statement
 *     if (statement instanceof AssignNode) {
 *         // Handle assignment
 *     } else if (statement instanceof IfNode) {
 *         // Handle if statement
 *     }
 * }
 * </pre>
 * 
 * @see AstNode
 * @see AstParser
 */
public class ModuleNode extends AstNode {
    private List<AstNode> body = new ArrayList<>();
    
    public List<AstNode> getBody() { 
        return body; 
    }
    
    public void setBody(List<AstNode> body) { 
        this.body = body; 
    }
}
