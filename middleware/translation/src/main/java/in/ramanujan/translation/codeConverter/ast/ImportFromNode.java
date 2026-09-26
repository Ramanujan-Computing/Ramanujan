package in.ramanujan.translation.codeConverter.ast;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a Python 'from ... import ...' statement (e.g. 'from foo import bar', 'from foo import bar as b, *').
 */
public class ImportFromNode extends AstNode {
    private String module;
    private List<AliasNode> names = new ArrayList<>();
    private Integer level;

    public String getModule() {
        return module;
    }

    public void setModule(String module) {
        this.module = module;
    }

    public List<AliasNode> getNames() {
        return names;
    }

    public void setNames(List<AliasNode> names) {
        this.names = names;
    }

    public Integer getLevel() {
        return level;
    }

    public void setLevel(Integer level) {
        this.level = level;
    }

    @Override
    public String toString(int indent) {
        StringBuilder sb = new StringBuilder();
        sb.append(getIndent(indent)).append("ImportFrom(\n");
        sb.append(getIndent(indent + 1)).append("module='").append(module).append("',\n");
        sb.append(getIndent(indent + 1)).append("names=[\n");
        for (int i = 0; i < names.size(); i++) {
            sb.append(names.get(i).toString(indent + 2));
            if (i < names.size() - 1) sb.append(",\n");
            else sb.append("\n");
        }
        sb.append(getIndent(indent + 1)).append("],\n");
        sb.append(getIndent(indent + 1)).append("level=").append(level).append("\n");
        sb.append(getIndent(indent)).append(")");
        return sb.toString();
    }
}
