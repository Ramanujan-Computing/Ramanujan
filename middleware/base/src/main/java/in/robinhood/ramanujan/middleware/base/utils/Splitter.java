package in.robinhood.ramanujan.middleware.base.utils;

import java.util.ArrayList;
import java.util.List;

public class Splitter {
    public static List<String> splitString(String mainString, int partSize) {
        List<String> list = new ArrayList<>();

        for (int start = 0; start < mainString.length(); start += partSize) {
            list.add(mainString.substring(start, Math.min(mainString.length(), start + partSize)));
        }

        return list;
    }
}
