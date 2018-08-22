package graphql.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;


public class DateTimeHelper {

    public static final CopyOnWriteArrayList<DateTimeFormatter> DATE_FORMATTERS = new CopyOnWriteArrayList<>();

    static {
        DATE_FORMATTERS.add(DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC));
        DATE_FORMATTERS.add(DateTimeFormatter.ISO_LOCAL_DATE_TIME.withZone(ZoneOffset.UTC));
        DATE_FORMATTERS.add(DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC));
    }

    // ISO_8601
    public static String toISOString(LocalDateTime dateTime) {
        Objects.requireNonNull(dateTime, "dateTime");

        return DateTimeFormatter.ISO_INSTANT.format(ZonedDateTime.of(dateTime, ZoneOffset.UTC));
    }

    public static String toISOString(LocalDate date) {
        Objects.requireNonNull(date, "date");

        return DateTimeFormatter.ISO_LOCAL_DATE.format(date);
    }

    public static String toISOString(LocalTime time) {
        Objects.requireNonNull(time, "time");

        return DateTimeFormatter.ISO_LOCAL_TIME.format(time);
    }

    public static String toISOString(Date date) {
        Objects.requireNonNull(date, "date");

        return toISOString(toLocalDateTime(date));
    }

    public static LocalDateTime toLocalDateTime(Date date) {
        Objects.requireNonNull(date, "date");

        return date.toInstant().atZone(ZoneOffset.UTC).toLocalDateTime();
    }

    public static Date toDate(LocalDate date) {
        Objects.requireNonNull(date, "date");

        return toDate(date.atStartOfDay());
    }

    public static Date toDate(LocalDateTime dateTime) {
        Objects.requireNonNull(dateTime, "dateTime");

        return Date.from(dateTime.atZone(ZoneOffset.UTC).toInstant());
    }

    public static LocalDateTime parseDate(String date) {
        Objects.requireNonNull(date, "date");

        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                // equals ISO_LOCAL_DATE
                if (formatter.equals(DATE_FORMATTERS.get(2))) {
                    LocalDate localDate = LocalDate.parse(date, formatter);

                    return localDate.atStartOfDay();
                } else {
                    return LocalDateTime.parse(date, formatter);
                }
            } catch (java.time.format.DateTimeParseException ignored) {
            }
        }

        return null;
    }

    public static Date createDate(int year, int month, int day) {
        return createDate(year, month, day, 0, 0, 0, 0);
    }

    public static Date createDate(int year, int month, int day, int hours, int min, int sec) {
        return createDate(year, month, day, hours, min, sec, 0);
    }

    public static Date createDate(int year, int month, int day, int hours, int min, int sec, int millis) {
        long nanos = TimeUnit.MILLISECONDS.toNanos(millis);
        LocalDateTime localDateTime = LocalDateTime.of(year, month, day, hours, min, sec, (int) nanos);
        return DateTimeHelper.toDate(localDateTime);
    }

    public static Date convertImpl(Object input) {
        if (input instanceof String) {
            LocalDateTime localDateTime = DateTimeHelper.parseDate((String) input);

            if (localDateTime != null) {
                return DateTimeHelper.toDate(localDateTime);
            }
        }
        return null;
    }

}

