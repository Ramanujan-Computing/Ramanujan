package in.ramanujan.middleware.base.pojo;

import in.ramanujan.middleware.base.DagElement;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class TranslateResponse {
    private DagElement firstDagElement;
    private List<DagElement> dagElementList;
    private Map<String, String> codeAndDagElementMap;
    private String commonFunctionCode;
}
