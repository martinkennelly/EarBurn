package org.earburn.client.library.util;

public class StringUtil {
    public static String combine(String[] split,int beginning,int length,String delimiter) {
        StringBuilder builder = new StringBuilder();
        for (int x = beginning; (x < split.length) && (x < (beginning + length)); x++) {
            builder.append(split[x]).append(delimiter);
        }
        if (builder.length() > 0) {
            builder.setLength(builder.length() - delimiter.length());
        }
        return builder.toString();
    }

    public static String combine(String[] split,int beginning) {
        return StringUtil.combine(split,beginning,split.length," ");
    }
}
