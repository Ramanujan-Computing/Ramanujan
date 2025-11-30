package in.ramanujan.translation.codeConverter.ast;

import java.util.*;

/**
 * Represents a while loop in Python.
 * 
 * <p>WhileNode handles iterative control flow where a block of code executes repeatedly
 * as long as a condition is true. Python's while loops can optionally include an else
 * clause that executes when the loop completes normally (not via break).</p>
 * 
 * <h3>Python Examples</h3>
 * <pre>
 * # Simple while loop
 * while x < 10:
 *     x = x + 1
 * 
 * # Count down
 * count = 5
 * while count > 0:
 *     print(count)
 *     count -= 1
 * 
 * # Infinite loop with break
 * while True:
 *     user_input = input("Enter command: ")
 *     if user_input == "quit":
 *         break
 *     process(user_input)
 * 
 * # Multiple conditions
 * while x > 0 and y < 100:
 *     x -= 1
 *     y += 2
 * 
 * # While-else (else executes if loop completes without break)
 * while count < 10:
 *     if found_match(count):
 *         break
 *     count += 1
 * else:
 *     print("No match found")  # Only prints if break wasn't hit
 * 
 * # Nested loop
 * while i < rows:
 *     j = 0
 *     while j < cols:
 *         process(i, j)
 *         j += 1
 *     i += 1
 * </pre>
 * 
 * <h3>AST Structure</h3>
 * <pre>
 * # Simple: while x < 10: x = x + 1
 * While(
 *   test=Compare(left=Name('x'), ops=[Lt()], comparators=[Constant(10)]),
 *   body=[
 *     Assign(
 *       targets=[Name('x')],
 *       value=BinOp(left=Name('x'), op=Add(), right=Constant(1)))],
 *   orelse=[])
 * 
 * # With augmented assignment: while count > 0: count -= 1
 * While(
 *   test=Compare(left=Name('count'), ops=[Gt()], comparators=[Constant(0)]),
 *   body=[
 *     AugAssign(target=Name('count'), op=Sub(), value=Constant(1))],
 *   orelse=[])
 * 
 * # While-else
 * While(
 *   test=Compare(left=Name('count'), ops=[Lt()], comparators=[Constant(10)]),
 *   body=[
 *     If(test=Call(func=Name('found_match'), args=[Name('count')]),
 *        body=[Break()],
 *        orelse=[]),
 *     AugAssign(target=Name('count'), op=Add(), value=Constant(1))],
 *   orelse=[
 *     Expr(value=Call(func=Name('print'), args=[Constant('No match found')]))])
 * </pre>
 * 
 * <h3>Field Details</h3>
 * <ul>
 *   <li><b>test:</b> The loop condition (evaluated before each iteration). Common types:
 *     <ul>
 *       <li>CompareNode - x < 10, count > 0</li>
 *       <li>NameNode - while flag: (checks truthiness)</li>
 *       <li>CallNode - while has_data(): (function return value)</li>
 *       <li>ConstantNode - while True: (infinite loop)</li>
 *       <li>BoolOpNode - while x > 0 and y < 100: (complex condition)</li>
 *     </ul>
 *   </li>
 *   <li><b>body:</b> List of statements to execute in each iteration. Can contain:
 *     <ul>
 *       <li>Assignments - modify loop variables</li>
 *       <li>Function calls - perform operations</li>
 *       <li>If statements - conditional logic within loop</li>
 *       <li>Nested while/for - nested loops</li>
 *       <li>Break/Continue - control flow statements</li>
 *     </ul>
 *   </li>
 *   <li><b>orelse:</b> Statements to execute if loop completes normally (Python-specific):
 *     <ul>
 *       <li>Empty list - no else clause (most common)</li>
 *       <li>List of statements - executes only if loop exits normally (not via break)</li>
 *       <li>Rarely used in practice but unique to Python</li>
 *     </ul>
 *   </li>
 * </ul>
 * 
 * <h3>Loop Control</h3>
 * <table border="1">
 *   <tr><th>Statement</th><th>Effect</th><th>Else Clause</th></tr>
 *   <tr><td>Normal completion</td><td>Loop condition becomes false</td><td>Executes</td></tr>
 *   <tr><td>break</td><td>Exit loop immediately</td><td>Skipped</td></tr>
 *   <tr><td>continue</td><td>Skip rest of iteration, recheck condition</td><td>Executes if loop eventually completes</td></tr>
 *   <tr><td>return</td><td>Exit function (and loop)</td><td>Skipped</td></tr>
 * </table>
 * 
 * <h3>Usage Example</h3>
 * <pre>
 * WhileNode whileNode = (WhileNode) statement;
 * AstNode condition = whileNode.getTest();
 * List<AstNode> loopBody = whileNode.getBody();
 * List<AstNode> elseBody = whileNode.getOrelse();
 * 
 * System.out.println("While condition: " + condition.getClass().getSimpleName());
 * System.out.println("Loop body has " + loopBody.size() + " statements");
 * 
 * // Check for infinite loop
 * if (condition instanceof ConstantNode) {
 *     Object value = ((ConstantNode) condition).getValue();
 *     if (Boolean.TRUE.equals(value)) {
 *         System.out.println("Warning: Infinite loop (while True:)");
 *     }
 * }
 * 
 * // Check for else clause
 * if (!elseBody.isEmpty()) {
 *     System.out.println("Has else clause with " + elseBody.size() + " statements");
 * }
 * 
 * // Process loop body
 * for (AstNode stmt : loopBody) {
 *     // Analyze each statement
 *     // Look for break/continue statements
 *     // Track variable modifications
 * }
 * </pre>
 * 
 * @see AstNode
 * @see IfNode
 * @see CompareNode
 */
public class WhileNode extends AstNode {
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
}
