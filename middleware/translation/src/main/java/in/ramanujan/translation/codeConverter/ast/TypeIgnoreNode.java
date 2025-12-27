package in.ramanujan.translation.codeConverter.ast;

/**
 * Represents a type: ignore comment in Python source.
 *
 * <p>TypeIgnore nodes appear in Module.type_ignores and capture the line number and
 * optional tag of a "# type: ignore[...]" directive.</p>
 */
public class TypeIgnoreNode extends AstNode {
    private String tag; // may be null

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    @Override
    public String toString(int indent) {
        StringBuilder sb = new StringBuilder();
        sb.append(getIndent(indent)).append("TypeIgnore(");
        sb.append("lineno=").append(getLineno());
        if (tag != null) {
            sb.append(", tag='").append(tag).append("'");
        }
        sb.append(")");
        return sb.toString();
    }
}
