package uk.gov.moj.cpp.businessprocesses.helper;

import static java.lang.Integer.parseInt;
import static java.util.Objects.isNull;
import static java.util.regex.Pattern.compile;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DurationHelper {

    private static final String DURATION_REGEX = "^P((\\d*)Y)?((\\d*)W)?((\\d*)D)?([Tt]((\\d*)H)?((\\d*)M)?((\\d*)S)?)?";
    private static Pattern durationPatternCompiled = compile(DURATION_REGEX);

    public static long parseDuration(final String value, final long defaultValue) {

        if (isNull(value)) {
            return defaultValue;
        }

        long seconds = 0;

        final Matcher matcher = durationPatternCompiled.matcher(value);

        if (matcher.matches() && matcher.groupCount() > 0) {

            long days = 0;

            if (matcher.group(1) != null) {
                days += 365 * parseInt(matcher.group(2));
            }
            if (matcher.group(3) != null) {
                days += 7 * parseInt(matcher.group(4));
            }
            if (matcher.group(5) != null) {
                days += parseInt(matcher.group(6));
            }

            seconds = days * 24 * 60 * 60;

            if (matcher.group(7) != null) {

                if (matcher.group(8) != null) {
                    seconds += 60 * 60 * parseInt(matcher.group(9));
                }
                if (matcher.group(10) != null) {
                    seconds += 60 * parseInt(matcher.group(11));
                }
                if (matcher.group(12) != null) {
                    seconds += parseInt(matcher.group(13));
                }

            }
        }
        return seconds;

    }
}
