package in.robinhood.ramanujan.middleware.base.pojo;

import lombok.Data;

@Data
public class StringWrapper {
    private String str;

    public StringWrapper(String str) {
        this.str = str;
    }

    public void concat(String concatStr) {
        this.str = this.str + concatStr;
    }
}
