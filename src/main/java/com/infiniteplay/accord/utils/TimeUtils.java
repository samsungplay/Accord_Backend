package com.infiniteplay.accord.utils;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.ResolverStyle;
import java.time.format.SignStyle;
import java.time.temporal.ChronoField;
import java.util.Date;

public class TimeUtils {
    public static ZonedDateTime getCurrentKST() {
        return ZonedDateTime.now(KST_ZONE);
    }
    public static ZonedDateTime convertToKST(Date date, ZoneId zoneId) {
        ZonedDateTime zonedDate = date.toInstant().atZone(zoneId);
        return zonedDate.withZoneSameInstant(KST_ZONE);
    }
    public static DateTimeFormatter LOCAL_FORMAT =  new DateTimeFormatterBuilder()
            .appendValue(ChronoField.YEAR, 4)
            .appendLiteral('-')
            .appendValue(ChronoField.MONTH_OF_YEAR, 1,2,SignStyle.NORMAL)
            .appendLiteral('-')
            .appendValue(ChronoField.DAY_OF_MONTH, 1, 2, SignStyle.NORMAL)
            .toFormatter()
            .withResolverStyle(ResolverStyle.STRICT);

    public static ZoneId KST_ZONE = ZoneId.of("Asia/Seoul");

}
