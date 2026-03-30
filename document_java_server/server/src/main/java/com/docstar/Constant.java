package com.docstar;

import java.math.BigInteger;

public class Constant {
    private static String padding = "99";
    private static final int minRandomBound = 2;
    private static final int maxRandomBound = 20;
    private static final long modParameter = 2147483647L;
    private static final BigInteger modBigParameter = new BigInteger(
            "1000000000000000000000000000000000000000000000007");
    private static final int hashBlockCount = 8;
    private static final int hashBlockSize = 6;
    private static final int hashBlockSize1 = 48;
    private static final String delimiter = ".;:!?#@$&() =";
    private static final int maxKeywordCount = 1000;
    private static final int paddedFakes = 5;
    private static final String sampleFile = "Hello world";
    private static final BigInteger maxLimitNoAccess = new BigInteger("38373737373737373737373737373737373737");
    private static final BigInteger minLimitNoAccess = new BigInteger("37373737373737373737373737373737373737");
    private static final String accessConstant = "0";
    private static final int maxKeyOccurrence = 10000;
    private static final int minKeywordLength = 4;
    private static final int maxKeywordLength = 19;
    private static final int minDataDistribution = 10;
    private static final int maxDataDistribution = 30;
    private static final int binCount = 3;
    private static final int longLength = 18;
    private static final int stringBlock = 4;
    private static final int checkpoint1 = 2000;
    private static final int checkpoint2 = 200;

    public static String getPadding() {
        return padding;
    }

    public static void setPadding(String padding) {
        Constant.padding = padding;
    }

    public static int getMinRandomBound() {
        return minRandomBound;
    }

    public static int getMaxRandomBound() {
        return maxRandomBound;
    }

    public static long getModParameter() {
        return modParameter;
    }

    public static BigInteger getModBigParameter() {
        return modBigParameter;
    }

    public static int getHashBlockCount() {
        return hashBlockCount;
    }

    public static int getHashBlockSize() {
        return hashBlockSize;
    }

    public static int getHashBlockSize1() {
        return hashBlockSize1;
    }

    public static String getDelimiter() {
        return delimiter;
    }

    public static int getMaxKeywordCount() {
        return maxKeywordCount;
    }

    public static int getPaddedFakes() {
        return paddedFakes;
    }

    public static String getSampleFile() {
        return sampleFile;
    }

    public static BigInteger getMaxLimitNoAccess() {
        return maxLimitNoAccess;
    }

    public static BigInteger getMinLimitNoAccess() {
        return minLimitNoAccess;
    }

    public static String getAccessConstant() {
        return accessConstant;
    }

    public static int getMaxKeyOccurrence() {
        return maxKeyOccurrence;
    }

    public static int getMinKeywordLength() {
        return minKeywordLength;
    }

    public static int getMaxKeywordLength() {
        return maxKeywordLength;
    }

    public static int getMinDataDistribution() {
        return minDataDistribution;
    }

    public static int getMaxDataDistribution() {
        return maxDataDistribution;
    }

    public static int getBinCount() {
        return binCount;
    }

    public static int getLongLength() {
        return longLength;
    }

    public static int getStringBlock() {
        return stringBlock;
    }

    public static int getCheckpoint1() {
        return checkpoint1;
    }

    public static int getCheckpoint2() {
        return checkpoint2;
    }
}
