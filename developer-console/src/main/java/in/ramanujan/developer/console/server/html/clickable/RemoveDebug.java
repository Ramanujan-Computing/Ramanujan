package in.ramanujan.developer.console.server.html.clickable;

import in.ramanujan.developer.console.server.html.ClickableCell;

import java.util.List;

public class RemoveDebug extends ClickableCell {

    @Override
    protected String callScript() {
        return "call()";
    }

    public RemoveDebug(List<String> args, String text) {
        super(args, text);
    }

}
