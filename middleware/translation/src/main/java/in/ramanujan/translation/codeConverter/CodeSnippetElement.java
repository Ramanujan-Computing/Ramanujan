package in.ramanujan.translation.codeConverter;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
public class CodeSnippetElement {
    private String code;
    private String uuid;
    private List<CodeSnippetElement> next;

    public CodeSnippetElement() {
        next = new ArrayList<>();
        uuid = UUID.randomUUID().toString();
    }

    public CodeSnippetElement clone() {
        CodeSnippetElement codeSnippetElement = new CodeSnippetElement();
        codeSnippetElement.setCode(this.code);
        codeSnippetElement.setNext(new ArrayList<>(this.next));
        return codeSnippetElement;
    }
}
