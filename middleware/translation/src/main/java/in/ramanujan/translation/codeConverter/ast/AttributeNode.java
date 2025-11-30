package in.ramanujan.translation.codeConverter.ast;

/**
 * Represents attribute access in Python (dot notation).
 * 
 * <p>AttributeNode represents accessing an attribute or method of an object using dot
 * notation (obj.attr). This is used for object properties, methods, module members,
 * and class attributes. The context determines whether this is reading (Load),
 * writing (Store), or deleting (Del) the attribute.</p>
 * 
 * <h3>Python Examples</h3>
 * <pre>
 * # Attribute access (Load context)
 * value = obj.property       # Read object property
 * length = text.length       # Read attribute
 * result = math.pi           # Module constant
 * 
 * # Attribute assignment (Store context)
 * obj.value = 10             # Set object property
 * instance.count = 5         # Set attribute
 * 
 * # Method call (Load context on AttributeNode, then Call)
 * result = obj.method()      # Call object method
 * text = message.upper()     # Call string method
 * list.append(item)          # Call list method
 * 
 * # Chained attribute access
 * value = obj.child.property
 * result = data.nested.value
 * 
 * # Module attributes
 * import math
 * pi = math.pi               # Module constant
 * root = math.sqrt(16)       # Module function
 * 
 * # In expressions
 * total = obj.value + other.value
 * if obj.status == "active":
 *     obj.count += 1
 * 
 * # In conditionals
 * if user.is_admin:
 *     show_admin_panel()
 * </pre>
 * 
 * <h3>AST Structure</h3>
 * <pre>
 * # Read: value = obj.property
 * Assign(
 *   targets=[Name(id='value', ctx=Store())],
 *   value=Attribute(
 *     value=Name(id='obj', ctx=Load()),
 *     attr='property',
 *     ctx=Load()))
 * 
 * # Write: obj.value = 10
 * Assign(
 *   targets=[
 *     Attribute(
 *       value=Name(id='obj', ctx=Load()),
 *       attr='value',
 *       ctx=Store())],
 *   value=Constant(value=10))
 * 
 * # Method call: obj.method()
 * Call(
 *   func=Attribute(
 *     value=Name(id='obj', ctx=Load()),
 *     attr='method',
 *     ctx=Load()),
 *   args=[],
 *   keywords=[])
 * 
 * # Chained: obj.child.property
 * Attribute(
 *   value=Attribute(
 *     value=Name(id='obj', ctx=Load()),
 *     attr='child',
 *     ctx=Load()),
 *   attr='property',
 *   ctx=Load())
 * 
 * # Module: math.pi
 * Attribute(
 *   value=Name(id='math', ctx=Load()),
 *   attr='pi',
 *   ctx=Load())
 * </pre>
 * 
 * <h3>Field Details</h3>
 * <ul>
 *   <li><b>value:</b> The object whose attribute is being accessed:
 *     <ul>
 *       <li>NameNode - Variable: obj, instance, module</li>
 *       <li>AttributeNode - Chained access: obj.child (for obj.child.property)</li>
 *       <li>CallNode - Method result: get_obj().property</li>
 *       <li>SubscriptNode - Array element: arr[i].property</li>
 *       <li>Any expression that yields an object</li>
 *     </ul>
 *   </li>
 *   <li><b>attr:</b> The attribute name as a string:
 *     <ul>
 *       <li>Property names: value, count, status, length</li>
 *       <li>Method names: append, upper, close, process</li>
 *       <li>Module members: pi, sqrt, random</li>
 *       <li>Class attributes: class_var, MAX_SIZE</li>
 *     </ul>
 *   </li>
 *   <li><b>ctx:</b> Context string indicating operation type:
 *     <ul>
 *       <li><b>"Load"</b> - Reading attribute: x = obj.value</li>
 *       <li><b>"Store"</b> - Writing attribute: obj.value = x</li>
 *       <li><b>"Del"</b> - Deleting attribute: del obj.value</li>
 *     </ul>
 *   </li>
 * </ul>
 * 
 * <h3>Context Usage</h3>
 * <table border="1">
 *   <tr><th>Context</th><th>Operation</th><th>Example</th></tr>
 *   <tr><td>Load</td><td>Reading attribute</td><td>x = obj.value, obj.method()</td></tr>
 *   <tr><td>Store</td><td>Writing attribute</td><td>obj.value = 10</td></tr>
 *   <tr><td>Store</td><td>Augmented assignment</td><td>obj.count += 1</td></tr>
 *   <tr><td>Del</td><td>Deleting attribute</td><td>del obj.value</td></tr>
 * </table>
 * 
 * <h3>Common Patterns</h3>
 * <pre>
 * # Property access
 * value = obj.property       # Read property
 * obj.property = value       # Set property
 * 
 * # Method call
 * result = obj.method(args)  # Call method
 * 
 * # Module usage
 * math.sqrt(x)               # Module function
 * math.pi                    # Module constant
 * 
 * # Chained access
 * obj.child.grandchild       # Nested objects
 * 
 * # List methods
 * list.append(item)
 * list.sort()
 * list.clear()
 * 
 * # String methods
 * text.upper()
 * text.lower()
 * text.strip()
 * 
 * # Dictionary methods
 * dict.keys()
 * dict.values()
 * dict.get(key)
 * </pre>
 * 
 * <h3>Usage Example</h3>
 * <pre>
 * AttributeNode attribute = (AttributeNode) node;
 * AstNode object = attribute.getValue();
 * String attrName = attribute.getAttr();
 * String context = attribute.getCtx();
 * 
 * // Get object name
 * if (object instanceof NameNode) {
 *     String objectName = ((NameNode) object).getId();
 *     System.out.println("Object: " + objectName);
 *     System.out.println("Attribute: " + attrName);
 *     System.out.println("Full: " + objectName + "." + attrName);
 * }
 * 
 * // Check for chained access
 * else if (object instanceof AttributeNode) {
 *     System.out.println("Chained attribute access");
 *     // Recursively process parent attribute
 * }
 * 
 * // Determine operation
 * if ("Load".equals(context)) {
 *     System.out.println("Reading attribute: " + attrName);
 * } else if ("Store".equals(context)) {
 *     System.out.println("Writing attribute: " + attrName);
 * }
 * 
 * // Check for common method names
 * if ("append".equals(attrName) || "sort".equals(attrName) || 
 *     "clear".equals(attrName)) {
 *     System.out.println("List method: " + attrName);
 * }
 * </pre>
 * 
 * @see AstNode
 * @see NameNode
 * @see CallNode
 * @see AssignNode
 */
public class AttributeNode extends AstNode {
    private AstNode value;
    private String attr;
    private String ctx;
    
    public AstNode getValue() { 
        return value; 
    }
    
    public void setValue(AstNode value) { 
        this.value = value; 
    }
    
    public String getAttr() { 
        return attr; 
    }
    
    public void setAttr(String attr) { 
        this.attr = attr; 
    }
    
    public String getCtx() { 
        return ctx; 
    }
    
    public void setCtx(String ctx) { 
        this.ctx = ctx; 
    }
}
