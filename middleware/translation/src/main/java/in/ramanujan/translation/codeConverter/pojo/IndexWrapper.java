package in.ramanujan.translation.codeConverter.pojo;

import lombok.Data;

@Data
public class IndexWrapper {
    private Integer index;

    public IndexWrapper(Integer index) {
        this.index = index;
    }
}
