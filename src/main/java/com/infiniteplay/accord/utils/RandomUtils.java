package com.infiniteplay.accord.utils;

import java.util.Random;

public class RandomUtils {

    public static String getRandomHEXString() {
        Random random = new Random();
        int randomInt = random.nextInt(0xffffff + 1);

        return String.format("#%06x", randomInt);
    }

}
