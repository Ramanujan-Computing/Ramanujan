package in.ramanujan.translation.codeConverter.ast;

/**
 * Represents array/list subscripting (indexing) in Python.
 * 
 * <p>SubscriptNode handles accessing elements in sequences (lists, strings, tuples) or
 * mappings (dictionaries) using square bracket notation. The context determines whether
 * this is a read (Load), write (Store), or delete (Del) operation.</p>
 * 
 * <h3>Python Examples</h3>
 * <pre>
 * # Array element access (Load context)
 * value = arr[0]          # First element
 * item = my_list[5]       # Element at index 5
 * last = items[-1]        # Last element (negative index)
 * 
 * # Array element assignment (Store context)
 * arr[0] = 10             # Set first element
 * matrix[i] = row         # Set element at index i
 * scores[3] = 95          # Set specific index
 * 
 * # Using variable index
 * element = data[index]
 * arr[counter] = value
 * 
 * # Using expression as index
 * value = arr[i + 1]
 * item = list[count * 2]
 * element = data[len(data) - 1]
 * 
 * # Dictionary access
 * value = my_dict["key"]
 * my_dict["name"] = "Alice"
 * 
 * # String indexing
 * char = text[0]          # First character
 * letter = word[position]
 * 
 * # In expressions
 * result = arr[0] + arr[1]
 * if arr[i] > threshold:
 *     process(arr[i])
 * </pre>
 * 
 * <h3>AST Structure</h3>
 * <pre>
 * # Read: value = arr[0]
 * Assign(
 *   targets=[Name(id='value', ctx=Store())],
 *   value=Subscript(
 *     value=Name(id='arr', ctx=Load()),
 *     slice=Constant(value=0),
 *     ctx=Load()))
 * 
 * # Write: arr[5] = 10
 * Assign(
 *   targets=[
 *     Subscript(
 *       value=Name(id='arr', ctx=Load()),
 *       slice=Constant(value=5),
 *       ctx=Store())],
 *   value=Constant(value=10))
 * 
 * # Variable index: arr[i]
 * Subscript(
 *   value=Name(id='arr', ctx=Load()),
 *   slice=Name(id='i', ctx=Load()),
 *   ctx=Load())
 * 
 * # Expression index: arr[i + 1]
 * Subscript(
 *   value=Name(id='arr', ctx=Load()),
 *   slice=BinOp(
 *     left=Name(id='i', ctx=Load()),
 *     op=Add(),
 *     right=Constant(value=1)),
 *   ctx=Load())
 * 
 * # Negative index: arr[-1]
 * Subscript(
 *   value=Name(id='arr', ctx=Load()),
 *   slice=UnaryOp(op=USub(), operand=Constant(value=1)),
 *   ctx=Load())
 * </pre>
 * 
 * <h3>Field Details</h3>
 * <ul>
 *   <li><b>value:</b> The array/list/dict being subscripted:
 *     <ul>
 *       <li>NameNode - Variable: arr, my_list, data</li>
 *       <li>SubscriptNode - Nested subscripting: matrix[i][j]</li>
 *       <li>CallNode - Function return value: get_array()[0]</li>
 *       <li>ListNode - Literal list: [1,2,3][0]</li>
 *     </ul>
 *   </li>
 *   <li><b>slice:</b> The index expression:
 *     <ul>
 *       <li>ConstantNode - Literal index: 0, 5, -1</li>
 *       <li>NameNode - Variable index: i, index, counter</li>
 *       <li>BinOpNode - Calculated index: i+1, n*2, len(arr)-1</li>
 *       <li>Can be any expression that evaluates to an integer</li>
 *     </ul>
 *   </li>
 *   <li><b>ctx:</b> Context string indicating operation type:
 *     <ul>
 *       <li><b>"Load"</b> - Reading element value: x = arr[0]</li>
 *       <li><b>"Store"</b> - Writing element value: arr[0] = x</li>
 *       <li><b>"Del"</b> - Deleting element: del arr[0]</li>
 *     </ul>
 *   </li>
 * </ul>
 * 
 * <h3>Context Usage</h3>
 * <table border="1">
 *   <tr><th>Context</th><th>Operation</th><th>Example</th></tr>
 *   <tr><td>Load</td><td>Reading element</td><td>x = arr[0], if arr[i] > 5</td></tr>
 *   <tr><td>Store</td><td>Writing element</td><td>arr[0] = 10, arr[i] = value</td></tr>
 *   <tr><td>Store</td><td>Augmented assignment</td><td>arr[0] += 5 (target has Store)</td></tr>
 *   <tr><td>Del</td><td>Deleting element</td><td>del arr[0]</td></tr>
 * </table>
 * 
 * <h3>Common Patterns</h3>
 * <pre>
 * # Constant index
 * first = arr[0]          # Access first element
 * arr[0] = value          # Set first element
 * 
 * # Variable index  
 * element = arr[i]        # Access at variable position
 * arr[index] = data       # Set at variable position
 * 
 * # Expression index
 * next = arr[i + 1]       # Offset access
 * arr[count * 2] = x      # Calculated position
 * 
 * # Negative index
 * last = arr[-1]          # Last element
 * second_last = arr[-2]   # Second to last
 * 
 * # Nested subscripting
 * value = matrix[i][j]    # 2D array access
 * </pre>
 * 
 * <h3>Usage Example</h3>
 * <pre>
 * SubscriptNode subscript = (SubscriptNode) node;
 * AstNode array = subscript.getValue();
 * AstNode index = subscript.getSlice();
 * String context = subscript.getCtx();
 * 
 * // Get array name
 * if (array instanceof NameNode) {
 *     String arrayName = ((NameNode) array).getId();
 *     System.out.println("Array: " + arrayName);
 * }
 * 
 * // Get index value
 * if (index instanceof ConstantNode) {
 *     Object indexValue = ((ConstantNode) index).getValue();
 *     System.out.println("Constant index: " + indexValue);
 * } else if (index instanceof NameNode) {
 *     String indexVar = ((NameNode) index).getId();
 *     System.out.println("Variable index: " + indexVar);
 * }
 * 
 * // Determine operation
 * if ("Load".equals(context)) {
 *     System.out.println("Reading from array");
 * } else if ("Store".equals(context)) {
 *     System.out.println("Writing to array");
 * }
 * </pre>
 * 
 * @see AstNode
 * @see NameNode
 * @see ConstantNode
 * @see BinOpNode
 * @see AssignNode
 */
public class SubscriptNode extends AstNode {
    private AstNode value;
    private AstNode slice;
    private String ctx;
    
    public AstNode getValue() { 
        return value; 
    }
    
    public void setValue(AstNode value) { 
        this.value = value; 
    }
    
    public AstNode getSlice() { 
        return slice; 
    }
    
    public void setSlice(AstNode slice) { 
        this.slice = slice; 
    }
    
    public String getCtx() { 
        return ctx; 
    }
    
    public void setCtx(String ctx) { 
        this.ctx = ctx; 
    }
    
    @Override
    public String toString(int indent) {
        StringBuilder sb = new StringBuilder();
        sb.append(getIndent(indent)).append("Subscript(\n");
        sb.append(getIndent(indent + 1)).append("value=\n");
        if (value != null) {
            sb.append(value.toString(indent + 2));
        } else {
            sb.append(getIndent(indent + 2)).append("null");
        }
        sb.append(",\n");
        sb.append(getIndent(indent + 1)).append("slice=\n");
        if (slice != null) {
            sb.append(slice.toString(indent + 2));
        } else {
            sb.append(getIndent(indent + 2)).append("null");
        }
        sb.append(",\n");
        sb.append(getIndent(indent + 1)).append("ctx=").append(ctx).append("()\n");
        sb.append(getIndent(indent)).append(")");
        return sb.toString();
    }
}
