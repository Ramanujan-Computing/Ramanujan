package in.ramanujan.middleware.base.utils;

import java.io.Serializable;
import java.util.Comparator;

public class IndexComparator implements Comparator<String>, Serializable {

    @Override
    public int compare(String s1, String s2) {
        if (s1 == null && s2 == null) return 0;
        if (s1 == null) return -1;
        if (s2 == null) return 1;

        String[] parts1 = s1.split("_");
        String[] parts2 = s2.split("_");
        int minLen = Math.min(parts1.length, parts2.length);

        for (int i = 0; i < minLen; i++) {
            try {
                long num1 = Long.parseLong(parts1[i]);
                long num2 = Long.parseLong(parts2[i]);
                if (num1 != num2) {
                    return Long.compare(num1, num2);
                }
            } catch (NumberFormatException e) {
                int cmp = parts1[i].compareTo(parts2[i]);
                if (cmp != 0) {
                    return cmp;
                }
            }
        }
        return Integer.compare(parts1.length, parts2.length);
    }
}
