package in.ramanujan.translation.codeConverter.ast;

import java.util.*;

/**
 * Represents a list literal in Python.
 * 
 * <p>ListNode represents list literals created using square brackets. Lists are mutable,
 * ordered sequences that can contain elements of any type. This node captures the list
 * at creation time with its initial elements.</p>
 * 
 * <h3>Python Examples</h3>
 * <pre>
 * # Empty list
 * empty = []
 * 
 * # List of constants
 * numbers = [1, 2, 3, 4, 5]
 * names = ["Alice", "Bob", "Charlie"]
 * mixed = [1, "two", 3.0, True]
 * 
 * # List of variables
 * values = [x, y, z]
 * coords = [latitude, longitude]
 * 
 * # List with expressions
 * results = [x * 2, y + 1, z - 3]
 * computed = [a + b, a - b, a * b]
 * 
 * # Nested lists
 * matrix = [[1, 2], [3, 4], [5, 6]]
 * table = [[a, b], [c, d]]
 * 
 * # In assignment
 * my_list = [10, 20, 30]
 * 
 * # As function argument
 * process([1, 2, 3])
 * calculate_sum([x, y, z])
 * 
 * # In return statement
 * def get_coordinates():
 *     return [x, y, z]
 * </pre>
 * 
 * <h3>AST Structure</h3>
 * <pre>
 * # Empty: []
 * List(elts=[], ctx=Load())
 * 
 * # Constants: [1, 2, 3]
 * List(
 *   elts=[
 *     Constant(value=1),
 *     Constant(value=2),
 *     Constant(value=3)],
 *   ctx=Load())
 * 
 * # Variables: [x, y]
 * List(
 *   elts=[
 *     Name(id='x', ctx=Load()),
 *     Name(id='y', ctx=Load())],
 *   ctx=Load())
 * 
 * # Expressions: [x * 2, y + 1]
 * List(
 *   elts=[
 *     BinOp(
 *       left=Name(id='x', ctx=Load()),
 *       op=Mult(),
 *       right=Constant(value=2)),
 *     BinOp(
 *       left=Name(id='y', ctx=Load()),
 *       op=Add(),
 *       right=Constant(value=1))],
 *   ctx=Load())
 * 
 * # Nested: [[1, 2], [3, 4]]
 * List(
 *   elts=[
 *     List(elts=[Constant(1), Constant(2)], ctx=Load()),
 *     List(elts=[Constant(3), Constant(4)], ctx=Load())],
 *   ctx=Load())
 * </pre>
 * 
 * <h3>Field Details</h3>
 * <ul>
 *   <li><b>elts:</b> List of element nodes that make up the list:
 *     <ul>
 *       <li>ConstantNode - Literal values: 5, "text", True</li>
 *       <li>NameNode - Variables: x, counter, data</li>
 *       <li>BinOpNode - Expressions: x+1, a*b</li>
 *       <li>ListNode - Nested lists: [[1,2], [3,4]]</li>
 *       <li>CallNode - Function calls: [func(), other()]</li>
 *       <li>Any expression type</li>
 *       <li>Empty for empty list []</li>
 *     </ul>
 *   </li>
 *   <li><b>ctx:</b> Context is always "Load" for list literals
 *     <ul>
 *       <li>Lists are values being created/loaded</li>
 *       <li>Individual elements may have different contexts</li>
 *     </ul>
 *   </li>
 * </ul>
 * 
 * <h3>Common List Patterns</h3>
 * <table border="1">
 *   <tr><th>Pattern</th><th>Example</th><th>Use Case</th></tr>
 *   <tr><td>Empty list</td><td>[]</td><td>Initialize empty collection</td></tr>
 *   <tr><td>Constants</td><td>[1, 2, 3]</td><td>Fixed values</td></tr>
 *   <tr><td>Variables</td><td>[x, y, z]</td><td>Group variables</td></tr>
 *   <tr><td>Expressions</td><td>[x*2, y+1]</td><td>Computed values</td></tr>
 *   <tr><td>Mixed</td><td>[1, x, x+1]</td><td>Combination</td></tr>
 *   <tr><td>Nested</td><td>[[1,2], [3,4]]</td><td>Matrices, tables</td></tr>
 * </table>
 * 
 * <h3>Usage Example</h3>
 * <pre>
 * ListNode listNode = (ListNode) expression;
 * List<AstNode> elements = listNode.getElts();
 * String context = listNode.getCtx();
 * 
 * System.out.println("List with " + elements.size() + " elements");
 * 
 * if (elements.isEmpty()) {
 *     System.out.println("Empty list []");
 * } else {
 *     // Process each element
 *     for (int i = 0; i < elements.size(); i++) {
 *         AstNode element = elements.get(i);
 *         System.out.println("Element " + i + ": " + element.getClass().getSimpleName());
 *         
 *         // Check element type
 *         if (element instanceof ConstantNode) {
 *             Object value = ((ConstantNode) element).getValue();
 *             System.out.println("  Constant: " + value);
 *         } else if (element instanceof NameNode) {
 *             String varName = ((NameNode) element).getId();
 *             System.out.println("  Variable: " + varName);
 *         } else if (element instanceof ListNode) {
 *             System.out.println("  Nested list");
 *         }
 *     }
 * }
 * 
 * // Check if all elements are constants
 * boolean allConstants = elements.stream()
 *     .allMatch(e -> e instanceof ConstantNode);
 * if (allConstants) {
 *     System.out.println("List contains only constant values");
 * }
 * </pre>
 * 
 * @see AstNode
 * @see ConstantNode
 * @see NameNode
 * @see SubscriptNode
 */
public class ListNode extends AstNode {
    private List<AstNode> elts = new ArrayList<>();
    private String ctx;
    
    public List<AstNode> getElts() { 
        return elts; 
    }
    
    public void setElts(List<AstNode> elts) { 
        this.elts = elts; 
    }
    
    public String getCtx() { 
        return ctx; 
    }
    
    public void setCtx(String ctx) { 
        this.ctx = ctx; 
    }
}
