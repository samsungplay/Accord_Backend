package com.infiniteplay.accord.models;

import com.infiniteplay.accord.entities.User;
import com.infiniteplay.accord.utils.TimeUtils;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.springframework.lang.Nullable;

import java.time.*;
import java.util.*;

@AllArgsConstructor
@Getter
@Setter
public class ChatRecordSearchFilter<T> {

    String query;
    @Nullable
    Map<String, T> queryParameters;

    static Random random = new Random();

    private static int randomParameterID() {
        return random.nextInt(100000000);
    }

    public static ChatRecordSearchFilter<String> createContentFilter(String content) {
        String parameterName = "content" + randomParameterID();
        content = content.trim();
        return new ChatRecordSearchFilter<>("(c.message LIKE CONCAT('%',:" + parameterName + ",'%'))", Map.of(parameterName, content));
    }

    public static ChatRecordSearchFilter<User> createFromUserFilter(User user) {
        int id = randomParameterID();
        String parameterName = "user" + id;

        return new ChatRecordSearchFilter<>("(c.sender = :" + parameterName + ")" , Map.of(parameterName, user));
    }


    public static ChatRecordSearchFilter<String> createMentionsUserFilter(User user) {
        int id = randomParameterID();
        String parameterName = "mentionsuser" + id;
        return new ChatRecordSearchFilter<>("(c.message LIKE CONCAT('%',:" + parameterName + ",'%'))", Map.of(parameterName, "[m]@"+user.getId()+"[m]"));
    }

    public static ChatRecordSearchFilter<Void> createHasLinkFilter() {
        //pretty basic but should do the job in most cases
        return new ChatRecordSearchFilter<>("(c.message LIKE '%http://%' OR c.message LIKE '%https://%' OR " +
                "c.message LIKE '%www.%')", null);
    }

    public static ChatRecordSearchFilter<Void> createHasEmbedFilter() {
        return new ChatRecordSearchFilter<>("((c.message LIKE '%http://%' OR c.message LIKE '%https://%' OR " +
                "c.message LIKE '%www.%') AND (c.hideEmbed=false OR c.hideEmbed IS NULL))", null);
    }

    public static ChatRecordSearchFilter<Void> createHasFileFilter() {
        return new ChatRecordSearchFilter<>("(c.attachments IS NOT NULL)", null);
    }

    public static ChatRecordSearchFilter<Void> createHasImageFilter() {
        return new ChatRecordSearchFilter<>("(c.attachments IS NOT NULL AND c.attachments LIKE '%.jpg%' OR c.attachments LIKE '%.jpeg%' OR c.attachments LIKE '%.png% OR c.attachments LIKE %.gif% OR c.attachments LIKE %.webp%')", null);
    }

    public static ChatRecordSearchFilter<Void> createHasPollFilter() {
        return new ChatRecordSearchFilter<>("(c.poll IS NOT NULL)", null);
    }

    public static ChatRecordSearchFilter<Void> createHasVideoFilter() {
        return new ChatRecordSearchFilter<>("(c.attachments IS NOT NULL AND c.attachments LIKE '%.mp4%' OR c.attachments LIKE '%.webm%')", null);
    }

    public static ChatRecordSearchFilter<Void> createHasSoundFilter() {
        return new ChatRecordSearchFilter<>("(c.attachments IS NOT NULL AND c.attachments LIKE '%.mp3%' OR c.attachments LIKE '%.ogg%' OR c.attachments LIKE '%.wav%')", null);
    }

    public static ChatRecordSearchFilter<Void> createHasReplyFilter() {
        return new ChatRecordSearchFilter<>("(c.replyTargetId IS NOT NULL)", null);
    }

    public static ChatRecordSearchFilter<ZonedDateTime> createBeforeDateFilter(LocalDate localDate, String localTimeZone) {
        //need to convert date to the system time, KST
        Date date = Date.from(localDate.atStartOfDay().atZone(ZoneId.of(localTimeZone)).toInstant());


        ZonedDateTime kstDate = TimeUtils.convertToKST(date, ZoneId.of(localTimeZone));

        String parameterName = "date" + randomParameterID();
        return new ChatRecordSearchFilter<>("(c.date < :" + parameterName + ")", Map.of(parameterName, kstDate));
    }

    public static ChatRecordSearchFilter<ZonedDateTime> createAfterDateFilter(LocalDate localDate, String localTimeZone) {

        ZonedDateTime kstDate = TimeUtils.convertToKST(Date.from(localDate.plusDays(1).atStartOfDay().atZone(ZoneId.of(localTimeZone)).toInstant()), ZoneId.of(localTimeZone));

        String parameterName = "date" + randomParameterID();
        return new ChatRecordSearchFilter<>("(c.date >= :" + parameterName + ")", Map.of(parameterName, kstDate));
    }

    public static ChatRecordSearchFilter<ZonedDateTime> createBeforeDateFilterInclusive(LocalDate localDate, String localTimeZone) {
        //need to convert date to the system time, KST
        Date date = Date.from(localDate.atStartOfDay().plusDays(1).atZone(ZoneId.of(localTimeZone)).toInstant());

        ZonedDateTime kstDate = TimeUtils.convertToKST(date, ZoneId.of(localTimeZone));
        String parameterName = "date" + randomParameterID();
        return new ChatRecordSearchFilter<>("(c.date < :" + parameterName + ")", Map.of(parameterName, kstDate));
    }

    public static ChatRecordSearchFilter<ZonedDateTime> createAfterDateFilterInclusive(LocalDate localDate, String localTimeZone) {

        ZonedDateTime kstDate = TimeUtils.convertToKST(Date.from(localDate.atStartOfDay().atZone(ZoneId.of(localTimeZone)).toInstant()), ZoneId.of(localTimeZone));

        String parameterName = "date" + randomParameterID();
        return new ChatRecordSearchFilter<>("(c.date >= :" + parameterName + ")", Map.of(parameterName, kstDate));
    }


    public static ChatRecordSearchFilter<Void> createPinnedFilter(boolean pinned) {
        return new ChatRecordSearchFilter<>("(c.pinned =" + pinned + ")", null);
    }


}
