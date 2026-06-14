package in.ramanujan.translation.codeConverter.ast;

import java.util.ArrayList;
import java.util.List;

public class DeleteNode extends AstNode {
    private List<AstNode> targets = new ArrayList<>();

    public List<AstNode> getTargets() { return targets; }
    public void setTargets(List<AstNode> targets) { this.targets = targets; }

    @Override
    public String toString(int indent) {
        StringBuilder sb = new StringBuilder();
        sb.append(getIndent(indent)).append("Delete(\n");
        sb.append(getIndent(indent + 1)).append("targets=[\n");
        for (int i = 0; i < targets.size(); i++) {
            AstNode node = targets.get(i);
            sb.append(node != null ? node.toString(indent + 2) : getIndent(indent + 2) + "null");
            if (i < targets.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append(getIndent(indent + 1)).append("]\n");
        sb.append(getIndent(indent)).append(")");
        return sb.toString();
    }
}
