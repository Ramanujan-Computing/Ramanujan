package in.ramanujan.developer.console.server.html.clickable;

import in.ramanujan.developer.console.server.html.ClickableCell;

import java.util.List;

public class AddDebug extends ClickableCell {

    @Override
    protected String callScript() {
        return "call()";
    }

    public AddDebug(List<String> args, String text) {
        super(args, text);
    }

}
