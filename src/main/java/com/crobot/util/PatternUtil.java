package com.crobot.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PatternUtil {

    private static final Pattern patternForEsas = Pattern.compile("[12]\\d{3}[/][0-9]*[-]*[0-9]*[\\s+][E]");
    //private static final Pattern patternForEsas2 = Pattern.compile("[12]\\d{3}[/][0-9]*[\\s+][E]");
    private static final Pattern patternForKarar = Pattern.compile("[12]\\d{3}[/][0-9]*[\\s+][K]");

    /**
     * Return esasYıl and esasNo
     *
     * @param str
     * @return
     */
    public static Integer[] getEsas(String str) {
        if (str == null)
            return null;
        Matcher matcher = patternForEsas.matcher(str);
        if (matcher.find()) {
            try {
                String str1 = matcher.group(0).replaceAll("[A-Z]", "");
                String[] resultStr = str1.trim().split("[/]");
                Integer[] result = new Integer[2];
                result[0] = Integer.parseInt(resultStr[0]);

                if (resultStr[1].contains("-")) {
                    String[] s = resultStr[1].split("\\-");
                    result[1] = Integer.parseInt(s[1]);
                } else {
                    result[1] = Integer.parseInt(resultStr[1]);
                }
                return result;
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Returns kararYıl and kararNo
     *
     * @param str
     * @return
     */
    public static Integer[] getKarar(String str) {
        if (str == null)
            return null;
        Matcher matcher = patternForKarar.matcher(str);
        if (matcher.find()) {
            try {
                String str1 = matcher.group(0).replaceAll("[A-Z]", "");
                String[] resultStr = str1.trim().split("[/]");
                Integer[] result = new Integer[2];
                result[0] = Integer.parseInt(resultStr[0]);
                result[1] = Integer.parseInt(resultStr[1]);
                return result;
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }


}