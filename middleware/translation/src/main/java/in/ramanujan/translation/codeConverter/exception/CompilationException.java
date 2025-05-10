package in.ramanujan.translation.codeConverter.exception;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CompilationException extends Exception {
    private Integer line;
    private Integer character;
    private List<String> messageString;
    public CompilationException(Integer line, Integer character, String message) {
        this.line = line;
        this.character = character;
        this.messageString = new ArrayList<String>() {{add(message);}};
    }
}
