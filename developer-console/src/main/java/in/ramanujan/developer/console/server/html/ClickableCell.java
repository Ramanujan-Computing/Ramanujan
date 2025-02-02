package in.ramanujan.developer.console.server.html;

import java.util.List;

public abstract class ClickableCell extends Cell {
    protected final List<String> args;
    private final String text;

    public ClickableCell(List<String> args, String text) {
        super(text);
        this.args = args;
        this.text = text;
    }

    protected abstract String callScript();

    @Override
    public final String html() {
        StringBuilder stringBuilder = new StringBuilder("<td>");
        stringBuilder.append("<button onclick = \"" + callScript() + "\">" + text + "</button>");
        stringBuilder.append("</td>");
        return stringBuilder.toString();
    }
}
