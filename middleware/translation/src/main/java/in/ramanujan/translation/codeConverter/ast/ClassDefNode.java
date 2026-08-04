package in.ramanujan.translation.codeConverter.ast;

import java.util.ArrayList;
import java.util.List;

public class ClassDefNode extends AstNode {
    private String name;
    private List<AstNode> bases = new ArrayList<>();
    private List<AstNode> body = new ArrayList<>();

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public List<AstNode> getBases() { return bases; }
    public void setBases(List<AstNode> bases) { this.bases = bases; }

    public List<AstNode> getBody() { return body; }
    public void setBody(List<AstNode> body) { this.body = body; }

    @Override
    public String toString(int indent) {
        StringBuilder sb = new StringBuilder();
        sb.append(getIndent(indent)).append("ClassDef(\n");
        sb.append(getIndent(indent + 1)).append("name='").append(name).append("',\n");
        sb.append(getIndent(indent + 1)).append("body=[\n");
        for (int i = 0; i < body.size(); i++) {
            AstNode node = body.get(i);
            sb.append(node != null ? node.toString(indent + 2) : getIndent(indent + 2) + "null");
            if (i < body.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append(getIndent(indent + 1)).append("]\n");
        sb.append(getIndent(indent)).append(")");
        return sb.toString();
    }
}
