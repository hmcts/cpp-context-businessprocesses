package uk.gov.moj.cpp.businessprocesses.shared;

import static java.time.LocalDateTime.parse;
import static java.time.ZoneOffset.UTC;
import static java.time.ZonedDateTime.of;
import static java.util.Date.from;
import static java.util.Objects.nonNull;
import static java.util.TimeZone.getTimeZone;
import static org.joda.time.DateTimeZone.forTimeZone;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

public class DateConverter {

    private static final DateTimeZone UTC_DATE_TIME_ZONE = forTimeZone(getTimeZone(UTC));

    private DateConverter() {
    }

    public static String getUTCZonedDateTimeString(final Date date) {
        return new DateTime(date, UTC_DATE_TIME_ZONE).toString();
    }

    public static String getUTCZonedDateTimeString(final LocalDateTime localDateTime) {
        return new DateTime(localDateTime, UTC_DATE_TIME_ZONE).toString();
    }

    public static String getUTCZonedDateTimeString(final String date) {
        return new DateTime(parse(date), UTC_DATE_TIME_ZONE).toString();
    }

    public static ZonedDateTime getUTCZonedDateTime(final LocalDateTime localDateTime) {
        return of(localDateTime, UTC);
    }

    public static Date getDate(final LocalDateTime localDateTime) {
        return from(getUTCZonedDateTime(localDateTime).toInstant());
    }

    public static boolean isFutureDate(String startDate) {
        return nonNull(startDate) && LocalDate.parse(startDate).isAfter(LocalDate.now());
    }

    public static LocalDate addDaysSkippingWeekends(LocalDate date, int days) {
        LocalDate result = date;
        int addedDays = 0;
        while (addedDays < days) {
            result = result.plusDays(1);
            if (!(result.getDayOfWeek() == DayOfWeek.SATURDAY || result.getDayOfWeek() == DayOfWeek.SUNDAY)) {
                ++addedDays;
            }
        }
        return result;
    }

    public static String formatHearingDate(String dateStr, String timeStr){
        try{
            if (StringUtils.isEmpty(dateStr)) {
                return "";
            }

            if(StringUtils.isEmpty(timeStr)){
                timeStr = "00:00:00.000";
            }
            LocalDate date = LocalDate.parse(dateStr);
            LocalTime time = LocalTime.parse(timeStr);

            ZonedDateTime zonedDateTime = ZonedDateTime.of(date, time, ZoneId.of("UTC"));

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
            return formatter.format(zonedDateTime);
        } catch (Exception e) {
            return "";
        }
    }
}
