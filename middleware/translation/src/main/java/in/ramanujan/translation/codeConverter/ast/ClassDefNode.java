package in.ramanujan.translation.codeConverter.ast;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a Python class definition in the AST.
 *
 * <p>ClassDefNode is produced when the parser encounters a {@code ClassDef(…)}
 * node in the Python AST dump.  It stores the class name, optional base classes
 * (for inheritance), and the class body (fields, methods, etc.).</p>
 *
 * <h3>Python Example</h3>
 * <pre>
 * class Person:
 *     def __init__(self, name, age):
 *         self.name = name
 *         self.age  = age
 *
 *     def greet(self):
 *         return self.name
 * </pre>
 *
 * <h3>AST Dump Format</h3>
 * <pre>
 * ClassDef(
 *   name='Person',
 *   bases=[],
 *   keywords=[],
 *   body=[
 *     FunctionDef(
 *       name='__init__',
 *       args=arguments(
 *         posonlyargs=[],
 *         args=[arg(arg='self'), arg(arg='name'), arg(arg='age')],
 *         …),
 *       body=[
 *         Assign(
 *           targets=[Attribute(value=Name(id='self', ctx=Load()), attr='name', ctx=Store())],
 *           value=Name(id='name', ctx=Load())),
 *         Assign(
 *           targets=[Attribute(value=Name(id='self', ctx=Load()), attr='age',  ctx=Store())],
 *           value=Name(id='age',  ctx=Load()))],
 *       decorator_list=[]),
 *     FunctionDef(
 *       name='greet',
 *       args=arguments(args=[arg(arg='self')], …),
 *       body=[Return(value=Attribute(value=Name(id='self'), attr='name', …))],
 *       decorator_list=[])],
 *   decorator_list=[])
 * </pre>
 *
 * @see AstNode
 * @see FunctionDefNode
 * @see AttributeNode
 */
public class ClassDefNode extends AstNode {

    /** Unqualified class name, e.g. "Person". */
    private String name;

    /**
     * Base class name nodes.  Usually empty for simple single-inheritance Python
     * code that does not extend anything other than {@code object}.
     */
    private List<AstNode> bases = new ArrayList<>();

    /**
     * Statements in the class body.  Typically a mix of {@link FunctionDefNode}
     * (methods) and {@link AssignNode} (class-level attributes).
     */
    private List<AstNode> body = new ArrayList<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<AstNode> getBases() {
        return bases;
    }

    public void setBases(List<AstNode> bases) {
        this.bases = bases;
    }

    public List<AstNode> getBody() {
        return body;
    }

    public void setBody(List<AstNode> body) {
        this.body = body;
    }

    @Override
    public String toString(int indent) {
        StringBuilder sb = new StringBuilder();
        sb.append(getIndent(indent)).append("ClassDef(\n");
        sb.append(getIndent(indent + 1)).append("name='").append(name).append("',\n");
        sb.append(getIndent(indent + 1)).append("bases=[\n");
        for (AstNode base : bases) {
            sb.append(base != null ? base.toString(indent + 2) : getIndent(indent + 2) + "null");
            sb.append(",\n");
        }
        sb.append(getIndent(indent + 1)).append("],\n");
        sb.append(getIndent(indent + 1)).append("body=[\n");
        for (int i = 0; i < body.size(); i++) {
            AstNode node = body.get(i);
            sb.append(node != null ? node.toString(indent + 2) : getIndent(indent + 2) + "null");
            if (i < body.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append(getIndent(indent + 1)).append("]\n");
        sb.append(getIndent(indent)).append(")");
        return sb.toString();
    }
}
