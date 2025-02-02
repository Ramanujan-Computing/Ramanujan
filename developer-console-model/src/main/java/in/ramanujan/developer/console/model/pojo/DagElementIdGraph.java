package in.ramanujan.developer.console.model.pojo;

import lombok.Data;

import java.util.List;

@Data
public class DagElementIdGraph {
    private String id;
    private List<DagElementIdGraph> nextId;
}
