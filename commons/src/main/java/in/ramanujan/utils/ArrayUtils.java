package in.ramanujan.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ArrayUtils {
    public static String createArrayIndexList(List<Integer> indexes) {
        StringBuilder indexList = new StringBuilder();
        if(indexes == null) {
            return indexList.toString();
        }
        Boolean first = true;
        for(Integer obj : indexes) {
            if(!first) {
                indexList.append("_");
            } else {
                first = false;
            }
            indexList.append(obj);
        }
        return indexList.toString();
    }

    public static List<String> getIndexes(String indexList) {
        if(indexList == null) {
            return null;
        }
        return new ArrayList<String>(Arrays.asList(indexList.split("_")));
    }

    public static Integer getIntegerIndex(Object index) {
        return ((Number)index).intValue();
    }
}
