package in.ramanujan.pojo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


@Data
public class ContextStack {
    private List<Map<String, String>> functionVariableMapping;
    private int size;

    public ContextStack() {
        size = 0;
        functionVariableMapping = new ArrayList<>();
    }

    public void push(Map<String, String> variableMapToBePushed) {
        functionVariableMapping.add(variableMapToBePushed);
        size++;
    }

    public Map<String, String> getFunctionMapping() {
        if(size == 0) {
            return null;
        }
        return functionVariableMapping.get(size-1);
    }

    public void pop() {
        if(size == 0) {
            return;
        }
        functionVariableMapping.remove(size - 1);
        size--;
    }
}

