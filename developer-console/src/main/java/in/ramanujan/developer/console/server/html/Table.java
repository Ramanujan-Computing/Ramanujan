package in.ramanujan.developer.console.server.html;

import java.util.List;

public class Table {
    final StringBuilder stringBuilder = new StringBuilder("<table>");

    public void addRow(List<Cell> cellList) {
        stringBuilder.append("<tr>");
        for(Cell cell : cellList) {
            stringBuilder.append(cell.html());
        }
        stringBuilder.append("</tr>");
    }


    public String get() {
        stringBuilder.append("</table>");
        return stringBuilder.toString();
    }
}
