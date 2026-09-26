package in.ramanujan.translation.codeConverter.ast;

/**
 * Represents an imported alias in Python (e.g. 'import foo as f' or 'from bar import baz as b').
 */
public class AliasNode extends AstNode {
    private String name;
    private String asname;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAsname() {
        return asname;
    }

    public void setAsname(String asname) {
        this.asname = asname;
    }

    @Override
    public String toString(int indent) {
        return getIndent(indent) + "alias(name='" + name + "', asname=" + (asname != null ? "'" + asname + "'" : "null") + ")";
    }
}
