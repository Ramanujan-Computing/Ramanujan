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
    private List<TypeIgnoreNode> typeIgnores = new ArrayList<>();
    
    public List<AstNode> getBody() { 
        return body; 
    }
    
    public void setBody(List<AstNode> body) { 
        this.body = body; 
    }
    
    public List<TypeIgnoreNode> getTypeIgnores() {
        return typeIgnores;
    }

    public void setTypeIgnores(List<TypeIgnoreNode> typeIgnores) {
        this.typeIgnores = typeIgnores != null ? typeIgnores : new ArrayList<>();
    }
    
    @Override
    public String toString(int indent) {
        StringBuilder sb = new StringBuilder();
        sb.append(getIndent(indent)).append("Module(\n");
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
        sb.append(getIndent(indent + 1)).append("],\n");
        sb.append(getIndent(indent + 1)).append("type_ignores=[\n");
        for (int i = 0; i < typeIgnores.size(); i++) {
            TypeIgnoreNode ti = typeIgnores.get(i);
            if (ti != null) {
                sb.append(ti.toString(indent + 2));
            } else {
                sb.append(getIndent(indent + 2)).append("null");
            }
            if (i < typeIgnores.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append(getIndent(indent + 1)).append("]\n");
        sb.append(getIndent(indent)).append(")");
        return sb.toString();
    }
}
