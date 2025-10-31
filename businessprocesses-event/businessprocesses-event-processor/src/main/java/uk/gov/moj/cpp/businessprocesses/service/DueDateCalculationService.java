package uk.gov.moj.cpp.businessprocesses.service;

import static java.sql.Date.valueOf;
import static java.time.LocalDateTime.now;

import uk.gov.justice.services.common.converter.ZonedDateTimes;
import uk.gov.moj.cpp.businessprocesses.shared.InvalidDueDateCalculationRuleException;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Date;

import javax.inject.Inject;


/**
 * Calculates a due date based on a due data calculation formula stored in reference data (against
 * each task).
 */
@SuppressWarnings({"squid:S2139", "squid:S2885"})
public class DueDateCalculationService {

    private static final String HEARING_DATE = "hearingDate";
    private static final String PLUS = "+";
    private static final String MINUS = "-";

    @Inject
    private CalendarWeekDayOperationsService calendarWeekDayOperationsService;

    /**
     * Calculate Due Date From Rules like now+1, ...
     */
    public String calculateDueDate(final String rule) {
        return calculateDueDate(rule, null);
    }

    public Date getCalculatedDate(final String rule, final String hearingDate) {
       return dueDateFormatterAsDate(calculateDueDateAsLocalDate(rule, hearingDate));
    }

    /**
     * Calculate Due Date From Rules like now+1, hearingDate+1...
     */
    public String calculateDueDate(final String rule, final String hearingDate) {
        final String formattedRule = getFormattedRuleFromRawRule(rule);
        LocalDate localDate;
        if (rule.contains(HEARING_DATE) && hearingDate == null) {
            throw new InvalidDueDateCalculationRuleException("Rule Expects HearingDate");
        } else if (rule.contains(HEARING_DATE)) {
            localDate = ZonedDateTimes.fromString(hearingDate).toLocalDate();
        } else {
            localDate = now().toLocalDate();
        }
        return dueDateFormatterAsString(getCalculateDueDateAsString(localDate, formattedRule));
    }

    /**
     * Calculate Due Date From Rules like now+1, hearingDate+1...
     */
    public LocalDate calculateDueDateAsLocalDate(final String rule, final String hearingDate) {
        final String formattedRule = getFormattedRuleFromRawRule(rule);
        LocalDate localDate;
        if (rule.contains(HEARING_DATE) && hearingDate == null) {
            throw new InvalidDueDateCalculationRuleException("Rule Expects HearingDate");
        } else if (rule.contains(HEARING_DATE)) {
            localDate = ZonedDateTimes.fromString(hearingDate).toLocalDate();
        } else {
            localDate = now().toLocalDate();
        }
        return getCalculateDueDate(localDate, formattedRule);
    }

    private String getCalculateDueDateAsString(final LocalDate dateTime, final String formattedRule) {
        if (formattedRule.contains(PLUS)) {
            final Long numberOfDays = getNumberOfDaysFromRule(formattedRule);
            return calendarWeekDayOperationsService.plusWorkingDays(dateTime, numberOfDays).toString();
        } else if (formattedRule.contains(MINUS)) {
            final Long numberOfDays = getNumberOfDaysFromRule(formattedRule);
            return calendarWeekDayOperationsService.minusWorkingDays(dateTime, numberOfDays).toString();
        } else {
            return dateTime.toString();
        }
    }

    private LocalDate getCalculateDueDate(final LocalDate dateTime, final String formattedRule) {
        if (formattedRule.contains(PLUS)) {
            final Long numberOfDays = getNumberOfDaysFromRule(formattedRule);
            return calendarWeekDayOperationsService.plusWorkingDays(dateTime, numberOfDays);
        } else if (formattedRule.contains(MINUS)) {
            final Long numberOfDays = getNumberOfDaysFromRule(formattedRule);
            return calendarWeekDayOperationsService.minusWorkingDays(dateTime, numberOfDays);
        } else {
            return dateTime;
        }
    }

    private String getFormattedRuleFromRawRule(final String rawRule) {
        return rawRule.replaceAll("[^a-zA-Z0-9+-]", "").replaceAll("\\s+", "").trim();
    }

    private Long getNumberOfDaysFromRule(final String rule) {
        final String days = rule.replaceAll("[^0-9]", "").replaceAll("\\s+", "").trim();
        if (!days.isEmpty()) {
            return Long.parseLong(days);
        }
        return 0L;
    }

    private String dueDateFormatterAsString(final String inputDate) {
        //formatted due date to support end of Day.
        LocalDate localDate = LocalDate.parse(inputDate);
        LocalDateTime localDateTime = localDate.atTime(23, 59, 59, 0);
        ZonedDateTime zonedDateTime = localDateTime.atZone(ZoneOffset.UTC);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        String formattedDateTime = zonedDateTime.format(formatter);
        return formattedDateTime;

    }

    private Date dueDateFormatterAsDate(final LocalDate localDate) {
        LocalDateTime localDateTimeStartOfDay = localDate.atStartOfDay();
        ZonedDateTime zonedDateTime = localDateTimeStartOfDay.atZone(ZoneOffset.UTC);  // You can choose other time zones too
        Date originalDate = Date.from(zonedDateTime.toInstant());
        LocalDateTime localDateTime = originalDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();

        // Add 23 hours and 59 minutes
        LocalDateTime newLocalDateTime = localDateTime.plusHours(23).plusMinutes(59).withSecond(59);
        //Ensure the date remains unchanged by setting the time to the end of the day.
        LocalDateTime endOfDay = localDateTime.toLocalDate().atTime(23, 59, 59);
        if (newLocalDateTime.isAfter(endOfDay)) {
            newLocalDateTime = endOfDay;
        }
        ZonedDateTime newZonedDateTime = newLocalDateTime.atZone(ZoneId.systemDefault());
        Date date = Date.from(newZonedDateTime.toInstant());

        return date;
    }
}
