package in.ramanujan.developer.console.server.html;

public class Cell {
    private final String text;

    public Cell(String text) {
        this.text = text;
    }

    public String html() {
        return "<td>" + text + "</td>";
    }
}
