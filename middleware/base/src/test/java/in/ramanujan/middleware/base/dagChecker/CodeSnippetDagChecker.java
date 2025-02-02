package in.ramanujan.middleware.base.dagChecker;

import in.ramanujan.middleware.base.CodeSnippetElement;

public class CodeSnippetDagChecker{
    public Boolean checkDag(CodeSnippetElement codeSnippetElementDag1, CodeSnippetElement codeSnippetElementDag2) {
        if(codeSnippetElementDag1 == null && codeSnippetElementDag2 != null) {
            return  false;
        }

        if(codeSnippetElementDag1 != null && codeSnippetElementDag2 == null) {
            return  false;
        }

        if(codeSnippetElementDag1 == null && codeSnippetElementDag2 == null) {
            return  true;
        }
        if(codeSnippetElementDag1.getCode() != null && codeSnippetElementDag1.getCode().equalsIgnoreCase(
                codeSnippetElementDag2.getCode()
        )) {
            if(codeSnippetElementDag1.getNext().size() > 0 && codeSnippetElementDag2.getNext().size() == 0) {
                return false;
            }
            if(codeSnippetElementDag1.getNext().size() == 0 && codeSnippetElementDag2.getNext().size() > 0) {
                return  false;
            }
            for(CodeSnippetElement codeSnippetElementNextForDag1 : codeSnippetElementDag1.getNext()) {
                boolean flag = false;
                for(CodeSnippetElement codeSnippetElementNextForDag2 : codeSnippetElementDag2.getNext()) {
                    if(checkDag(codeSnippetElementNextForDag1, codeSnippetElementNextForDag2)) {
                        flag = true;
                        break;
                    }
                }
                if(!flag) {
                    return false;
                }
            }
            return true;
        } else {
            return  false;
        }
    }
}
