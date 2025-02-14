package com.infiniteplay.accord.utils;

public class RegexConstants {
    public static final String EMAIL_REGEX = "^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,6}$";
    public static final String USER_REGEX = "^[\\p{L}\\p{N}_.]+$";

    public static final String PASSWORD_CONTAINS_SPECIAL_CHARACTER_REGEX = "[!@#$%^&*.?_]";

    public static final String PASSWORD_CONTAINS_LOWERCASE_REGEX = "[a-z]";

    public static final String PASSWORD_CONTAINS_DIGIT_REGEX = "[\\d]";

    public static final String PASSWORD_CONTAINS_UPPERCASE_REGEX = "[A-Z]";
    public static final String FILENAME_REGEX = "^[\\p{L}\\p{N}\\-. _]+$";
}
