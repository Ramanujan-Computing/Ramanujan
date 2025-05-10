package in.ramanujan.translation.codeConverter.pojo;

import lombok.Data;
import in.ramanujan.translation.codeConverter.CodeSnippetElement;
import in.ramanujan.translation.codeConverter.DagElement;


@Data
public class PairCodeSnippetElementWithParent {
    private CodeSnippetElement codeSnippetElement;
    private DagElement dagElement;

    public PairCodeSnippetElementWithParent(CodeSnippetElement codeSnippetElement, DagElement dagElement) {
        this.codeSnippetElement = codeSnippetElement;
        this.dagElement = dagElement;
    }
}
