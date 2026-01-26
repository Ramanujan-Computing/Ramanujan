package in.ramanujan.translation.codeConverter.ast;

/**
 * Represents a binary operation in Python (operation with two operands).
 * 
 * <p>BinOpNode handles all arithmetic, bitwise, and other binary operations in Python.
 * Operations follow Python's operator precedence rules and can be nested to form
 * complex expressions.</p>
 * 
 * <h3>Python Examples</h3>
 * <pre>
 * # Arithmetic operations
 * x + y              # Addition: BinOp(left=Name('x'), op=Add(), right=Name('y'))
 * a - b              # Subtraction: BinOp(left=Name('a'), op=Sub(), right=Name('b'))
 * 2 * 3              # Multiplication
 * 10 / 5             # Division
 * 10 // 3            # Floor division (result: 3)
 * 10 % 3             # Modulo (result: 1)
 * 2 ** 8             # Power (result: 256)
 * 
 * # Bitwise operations
 * x & y              # Bitwise AND
 * x | y              # Bitwise OR
 * x ^ y              # Bitwise XOR
 * x << 2             # Left shift
 * x >> 2             # Right shift
 * 
 * # Complex nested expressions
 * result = x + y * z # Nested: BinOp(Add(x, BinOp(Mult(y, z))))
 * a = (b + c) / d    # Nested: BinOp(Div(BinOp(Add(b, c)), d))
 * 
 * # With constants and variables
 * total = count * 10 + offset
 * area = length * width
 * </pre>
 * 
 * <h3>AST Structure</h3>
 * <pre>
 * # Simple: x + y
 * BinOp(
 *   left=Name(id='x', ctx=Load()),
 *   op=Add(),
 *   right=Name(id='y', ctx=Load()))
 * 
 * # Nested: x + y * z  (respects precedence: * before +)
 * BinOp(
 *   left=Name(id='x', ctx=Load()),
 *   op=Add(),
 *   right=BinOp(
 *     left=Name(id='y', ctx=Load()),
 *     op=Mult(),
 *     right=Name(id='z', ctx=Load())))
 * 
 * # With constants: 5 * 3
 * BinOp(
 *   left=Constant(value=5),
 *   op=Mult(),
 *   right=Constant(value=3))
 * </pre>
 * 
 * <h3>Field Details</h3>
 * <ul>
 *   <li><b>left:</b> Left operand (can be any expression: Name, Constant, BinOp, Call, etc.)</li>
 *   <li><b>op:</b> Operator as string:
 *     <ul>
 *       <li><b>Arithmetic:</b> "Add" (+), "Sub" (-), "Mult" (*), "Div" (/), "FloorDiv" (//), "Mod" (%), "Pow" (**)</li>
 *       <li><b>Bitwise:</b> "LShift" (<<), "RShift" (>>), "BitOr" (|), "BitXor" (^), "BitAnd" (&)</li>
 *       <li><b>Matrix:</b> "MatMult" (@)</li>
 *     </ul>
 *   </li>
 *   <li><b>right:</b> Right operand (can be any expression)</li>
 * </ul>
 * 
 * <h3>Operator Reference</h3>
 * <table border="1">
 *   <tr><th>Python</th><th>Op String</th><th>Description</th><th>Example</th></tr>
 *   <tr><td>+</td><td>Add</td><td>Addition</td><td>5 + 3 = 8</td></tr>
 *   <tr><td>-</td><td>Sub</td><td>Subtraction</td><td>5 - 3 = 2</td></tr>
 *   <tr><td>*</td><td>Mult</td><td>Multiplication</td><td>5 * 3 = 15</td></tr>
 *   <tr><td>/</td><td>Div</td><td>Division</td><td>10 / 4 = 2.5</td></tr>
 *   <tr><td>//</td><td>FloorDiv</td><td>Floor Division</td><td>10 // 4 = 2</td></tr>
 *   <tr><td>%</td><td>Mod</td><td>Modulo</td><td>10 % 3 = 1</td></tr>
 *   <tr><td>**</td><td>Pow</td><td>Power</td><td>2 ** 3 = 8</td></tr>
 * </table>
 * 
 * <h3>Usage Example</h3>
 * <pre>
 * BinOpNode binOp = (BinOpNode) expression;
 * String operator = binOp.getOp();
 * AstNode left = binOp.getLeft();
 * AstNode right = binOp.getRight();
 * 
 * System.out.println("Operation: " + operator);
 * 
 * // Process left and right operands recursively
 * if (left instanceof ConstantNode) {
 *     System.out.println("Left constant: " + ((ConstantNode) left).getValue());
 * } else if (left instanceof BinOpNode) {
 *     // Handle nested operation
 * }
 * </pre>
 * 
 * @see AstNode
 * @see ConstantNode
 * @see NameNode
 * @see AugAssignNode
 */
public class BinOpNode extends AstNode {
    private AstNode left;
    private String op;  // Add, Sub, Mult, Div, Mod, Pow
    private AstNode right;
    
    public AstNode getLeft() { 
        return left; 
    }
    
    public void setLeft(AstNode left) { 
        this.left = left; 
    }
    
    public String getOp() { 
        return op; 
    }
    
    public void setOp(String op) { 
        this.op = op; 
    }
    
    public AstNode getRight() { 
        return right; 
    }
    
    public void setRight(AstNode right) { 
        this.right = right; 
    }
    
    @Override
    public String toString(int indent) {
        StringBuilder sb = new StringBuilder();
        sb.append(getIndent(indent)).append("BinOp(\n");
        sb.append(getIndent(indent + 1)).append("left=\n");
        if (left != null) {
            sb.append(left.toString(indent + 2));
        } else {
            sb.append(getIndent(indent + 2)).append("null");
        }
        sb.append(",\n");
        sb.append(getIndent(indent + 1)).append("op=").append(op).append("(),\n");
        sb.append(getIndent(indent + 1)).append("right=\n");
        if (right != null) {
            sb.append(right.toString(indent + 2));
        } else {
            sb.append(getIndent(indent + 2)).append("null");
        }
        sb.append("\n").append(getIndent(indent)).append(")");
        return sb.toString();
    }
}
