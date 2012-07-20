package com.rails.mbta.commuterrail.util;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class SharedPreferencesLibrary {

    /**
     * Turn a CSV string into a Set of Strings. This is to support Android SDK <
     * 11, since later versions allow you to store a Set<String>.
     */
    public static Set<String> getCsvAsSet(String csv) {
        if (csv.isEmpty()) {
            return new HashSet<String>();
        }

        Set<String> valueSet = new HashSet<String>();
        String[] values = csv.split(",");
        for (int i = 0; i < values.length; ++i) {
            valueSet.add(values[i]);
        }

        return valueSet;
    }

    /**
     * Turn a Set<String> into a single CSV string. This is to support Android
     * SDK < 11, since later versions allow you to store a Set<String>.
     */
    public static String getSetAsCsv(Set<String> valueSet) {
        if (valueSet.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("");

        for (Iterator<String> iter = valueSet.iterator(); iter.hasNext();) {
            sb.append(iter.next()).append(",");
        }
        sb.deleteCharAt(sb.length() - 1);

        return sb.toString();
    }
}
