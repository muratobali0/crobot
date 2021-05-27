package com.crobot.util;

public final class RandomUtil {

    /**
     * returns random integer between 0 and given number.
     *
     * @param max
     * @return
     */
    public static int getRandom(int max) {
        return (int) (Math.random() * max);
    }

    /**
     * returns random integer between minimum and maximum range
     *
     * @param maximum
     * @param minimum
     * @return
     */
    public static int getRandomInteger(int minimum, int maximum) {
        return ((int) (Math.random() * (maximum - minimum))) + minimum;
    }

}
