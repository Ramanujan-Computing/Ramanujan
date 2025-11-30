package in.ramanujan.translation.codeConverter.ast;

import java.util.*;

/**
 * Represents a comparison operation in Python.
 * 
 * <p>CompareNode handles all comparison expressions, including simple comparisons and
 * chained comparisons which are unique to Python. Supports all comparison operators
 * including equality, inequality, and membership testing.</p>
 * 
 * <h3>Python Examples</h3>
 * <pre>
 * # Simple comparisons
 * x > 5              # Greater than
 * a == b             # Equality
 * y <= 10            # Less than or equal
 * count != 0         # Not equal
 * age >= 18          # Greater than or equal
 * value < MAX        # Less than
 * 
 * # Chained comparisons (Python specific)
 * 0 < x < 10         # Equivalent to: (0 < x) and (x < 10)
 * a <= b <= c        # Equivalent to: (a <= b) and (b <= c)
 * 1 < x <= 5 < y     # Multiple comparisons
 * 
 * # Membership testing
 * item in list       # Check if item is in list
 * key not in dict    # Check if key is not in dict
 * 
 * # Identity testing
 * obj is None        # Check if obj is None
 * x is not y         # Check if x is not the same object as y
 * 
 * # In conditionals
 * if score >= 90:
 *     grade = 'A'
 * 
 * while count > 0:
 *     count -= 1
 * </pre>
 * 
 * <h3>AST Structure</h3>
 * <pre>
 * # Simple: x > 5
 * Compare(
 *   left=Name(id='x', ctx=Load()),
 *   ops=[Gt()],
 *   comparators=[Constant(value=5)])
 * 
 * # Chained: 0 < x < 10
 * Compare(
 *   left=Constant(value=0),
 *   ops=[Lt(), Lt()],
 *   comparators=[Name(id='x', ctx=Load()), Constant(value=10)])
 * 
 * # Multiple operators: a <= b != c
 * Compare(
 *   left=Name(id='a', ctx=Load()),
 *   ops=[LtE(), NotEq()],
 *   comparators=[Name(id='b', ctx=Load()), Name(id='c', ctx=Load())])
 * </pre>
 * 
 * <h3>Field Details</h3>
 * <ul>
 *   <li><b>left:</b> The leftmost value in the comparison chain</li>
 *   <li><b>ops:</b> List of comparison operators as strings:
 *     <ul>
 *       <li><b>"Lt"</b> (&lt;) - Less than</li>
 *       <li><b>"LtE"</b> (&lt;=) - Less than or equal</li>
 *       <li><b>"Gt"</b> (&gt;) - Greater than</li>
 *       <li><b>"GtE"</b> (&gt;=) - Greater than or equal</li>
 *       <li><b>"Eq"</b> (==) - Equal</li>
 *       <li><b>"NotEq"</b> (!=) - Not equal</li>
 *       <li><b>"Is"</b> (is) - Identity check</li>
 *       <li><b>"IsNot"</b> (is not) - Negated identity</li>
 *       <li><b>"In"</b> (in) - Membership test</li>
 *       <li><b>"NotIn"</b> (not in) - Negated membership</li>
 *     </ul>
 *   </li>
 *   <li><b>comparators:</b> List of values being compared against. Length equals ops.size()</li>
 * </ul>
 * 
 * <h3>Chained Comparison Logic</h3>
 * <p>For a chained comparison like <code>a &lt; b &lt; c</code>:</p>
 * <ul>
 *   <li>left = a</li>
 *   <li>ops = [Lt, Lt]</li>
 *   <li>comparators = [b, c]</li>
 *   <li>Evaluates as: (a &lt; b) AND (b &lt; c)</li>
 * </ul>
 * 
 * <h3>Usage Example</h3>
 * <pre>
 * CompareNode compare = (CompareNode) condition;
 * AstNode left = compare.getLeft();
 * List<String> operators = compare.getOps();
 * List<AstNode> comparators = compare.getComparators();
 * 
 * // Simple comparison (one operator)
 * if (operators.size() == 1) {
 *     String op = operators.get(0);
 *     AstNode right = comparators.get(0);
 *     System.out.println("Comparing: left " + op + " right");
 * }
 * 
 * // Chained comparison
 * else {
 *     System.out.println("Chained comparison with " + operators.size() + " operators");
 *     // Process each comparison in the chain
 *     for (int i = 0; i < operators.size(); i++) {
 *         System.out.println("Op " + i + ": " + operators.get(i));
 *     }
 * }
 * </pre>
 * 
 * @see AstNode
 * @see IfNode
 * @see WhileNode
 * @see BinOpNode
 */
public class CompareNode extends AstNode {
    private AstNode left;
    private List<String> ops = new ArrayList<>();  // Lt, Gt, Eq, NotEq, LtE, GtE
    private List<AstNode> comparators = new ArrayList<>();
    
    public AstNode getLeft() { 
        return left; 
    }
    
    public void setLeft(AstNode left) { 
        this.left = left; 
    }
    
    public List<String> getOps() { 
        return ops; 
    }
    
    public void setOps(List<String> ops) { 
        this.ops = ops; 
    }
    
    public List<AstNode> getComparators() { 
        return comparators; 
    }
    
    public void setComparators(List<AstNode> comparators) { 
        this.comparators = comparators; 
    }
}
