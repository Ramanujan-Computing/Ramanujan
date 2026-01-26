package in.ramanujan.translation.codeConverter.ast;

import java.util.*;

/**
 * Represents an if statement in Python, including if-elif-else chains.
 * 
 * <p>IfNode handles conditional branching in Python. The test condition determines which
 * branch executes: body (if test is true) or orelse (if test is false). The orelse branch
 * can contain another IfNode to implement elif chains.</p>
 * 
 * <h3>Python Examples</h3>
 * <pre>
 * # Simple if
 * if x > 5:
 *     y = 10
 * 
 * # If-else
 * if score >= 60:
 *     result = "Pass"
 * else:
 *     result = "Fail"
 * 
 * # If-elif-else chain
 * if score >= 90:
 *     grade = 'A'
 * elif score >= 80:
 *     grade = 'B'
 * elif score >= 70:
 *     grade = 'C'
 * else:
 *     grade = 'F'
 * 
 * # Nested if
 * if logged_in:
 *     if is_admin:
 *         show_admin_panel()
 *     else:
 *         show_user_panel()
 * 
 * # Complex condition
 * if (age >= 18 and citizen) or has_permit:
 *     allow_entry = True
 * 
 * # Multiple statements in body
 * if found:
 *     count += 1
 *     total += value
 *     print("Found!")
 * </pre>
 * 
 * <h3>AST Structure</h3>
 * <pre>
 * # Simple if: if x > 5: y = 10
 * If(
 *   test=Compare(left=Name('x'), ops=[Gt()], comparators=[Constant(5)]),
 *   body=[
 *     Assign(targets=[Name('y')], value=Constant(10))],
 *   orelse=[])
 * 
 * # If-else
 * If(
 *   test=Compare(left=Name('score'), ops=[GtE()], comparators=[Constant(60)]),
 *   body=[
 *     Assign(targets=[Name('result')], value=Constant('Pass'))],
 *   orelse=[
 *     Assign(targets=[Name('result')], value=Constant('Fail'))])
 * 
 * # If-elif-else (elif is another If in orelse)
 * If(
 *   test=Compare(left=Name('score'), ops=[GtE()], comparators=[Constant(90)]),
 *   body=[Assign(targets=[Name('grade')], value=Constant('A'))],
 *   orelse=[
 *     If(  # This is the elif
 *       test=Compare(left=Name('score'), ops=[GtE()], comparators=[Constant(80)]),
 *       body=[Assign(targets=[Name('grade')], value=Constant('B'))],
 *       orelse=[...])])
 * </pre>
 * 
 * <h3>Field Details</h3>
 * <ul>
 *   <li><b>test:</b> The condition expression (usually CompareNode, but can be any expression
 *       that evaluates to boolean). Common types:
 *     <ul>
 *       <li>CompareNode - x > 5, a == b</li>
 *       <li>NameNode - if flag: (checks truthiness)</li>
 *       <li>CallNode - if is_valid(): (function return value)</li>
 *       <li>BoolOpNode - if a and b: (complex boolean)</li>
 *     </ul>
 *   </li>
 *   <li><b>body:</b> List of statements to execute if test is true. Can contain any statement
 *       types: assignments, function calls, nested if/while, etc.</li>
 *   <li><b>orelse:</b> List of statements to execute if test is false (else block). Can be:
 *     <ul>
 *       <li>Empty list - no else clause</li>
 *       <li>List of statements - else block</li>
 *       <li>Single IfNode - elif clause (elif is represented as If in orelse)</li>
 *     </ul>
 *   </li>
 * </ul>
 * 
 * <h3>Elif Chain Structure</h3>
 * <p>Python elif statements are represented as nested If nodes in the orelse:</p>
 * <pre>
 * if A:
 *     body_A
 * elif B:
 *     body_B
 * elif C:
 *     body_C
 * else:
 *     body_else
 * 
 * # Becomes:
 * If(test=A, body=[body_A], 
 *    orelse=[If(test=B, body=[body_B],
 *               orelse=[If(test=C, body=[body_C],
 *                          orelse=[body_else])])])
 * </pre>
 * 
 * <h3>Usage Example</h3>
 * <pre>
 * IfNode ifNode = (IfNode) statement;
 * AstNode condition = ifNode.getTest();
 * List<AstNode> ifBody = ifNode.getBody();
 * List<AstNode> elseBody = ifNode.getOrelse();
 * 
 * System.out.println("If condition: " + condition.getClass().getSimpleName());
 * System.out.println("If body has " + ifBody.size() + " statements");
 * 
 * if (!elseBody.isEmpty()) {
 *     if (elseBody.size() == 1 && elseBody.get(0) instanceof IfNode) {
 *         System.out.println("This is an elif");
 *     } else {
 *         System.out.println("This has an else block with " + elseBody.size() + " statements");
 *     }
 * }
 * 
 * // Process body statements
 * for (AstNode stmt : ifBody) {
 *     // Process each statement in if body
 * }
 * </pre>
 * 
 * @see AstNode
 * @see CompareNode
 * @see WhileNode
 */
public class IfNode extends AstNode {
    private AstNode test;
    private List<AstNode> body = new ArrayList<>();
    private List<AstNode> orelse = new ArrayList<>();
    
    public AstNode getTest() { 
        return test; 
    }
    
    public void setTest(AstNode test) { 
        this.test = test; 
    }
    
    public List<AstNode> getBody() { 
        return body; 
    }
    
    public void setBody(List<AstNode> body) { 
        this.body = body; 
    }
    
    public List<AstNode> getOrelse() { 
        return orelse; 
    }
    
    public void setOrelse(List<AstNode> orelse) { 
        this.orelse = orelse; 
    }
    
    @Override
    public String toString(int indent) {
        StringBuilder sb = new StringBuilder();
        sb.append(getIndent(indent)).append("If(\n");
        sb.append(getIndent(indent + 1)).append("test=\n");
        if (test != null) {
            sb.append(test.toString(indent + 2));
        } else {
            sb.append(getIndent(indent + 2)).append("null");
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
        sb.append(getIndent(indent + 1)).append("],\n");
        sb.append(getIndent(indent + 1)).append("orelse=[\n");
        for (int i = 0; i < orelse.size(); i++) {
            AstNode node = orelse.get(i);
            if (node != null) {
                sb.append(node.toString(indent + 2));
            } else {
                sb.append(getIndent(indent + 2)).append("null");
            }
            if (i < orelse.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append(getIndent(indent + 1)).append("]\n");
        sb.append(getIndent(indent)).append(")");
        return sb.toString();
    }
}
