package in.robinhood.ramanujan.middleware.base.pojo;

import in.robinhood.ramanujan.middleware.base.CodeSnippetElement;
import in.robinhood.ramanujan.middleware.base.DagElement;
import lombok.Data;


@Data
public class PairCodeSnippetElementWithParent {
    private CodeSnippetElement codeSnippetElement;
    private DagElement dagElement;

    public PairCodeSnippetElementWithParent(CodeSnippetElement codeSnippetElement, DagElement dagElement) {
        this.codeSnippetElement = codeSnippetElement;
        this.dagElement = dagElement;
    }
}
