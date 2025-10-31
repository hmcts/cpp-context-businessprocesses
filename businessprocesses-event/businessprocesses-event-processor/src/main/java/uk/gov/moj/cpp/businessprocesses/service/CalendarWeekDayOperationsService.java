package uk.gov.moj.cpp.businessprocesses.service;

import static java.time.DayOfWeek.SATURDAY;
import static java.time.DayOfWeek.SUNDAY;
import static java.util.stream.Collectors.toList;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.ENGLAND_AND_WALES_DIVISION;

import uk.gov.moj.cpp.businessprocesses.pojo.PublicHoliday;

import java.time.LocalDate;
import java.util.List;

import javax.inject.Inject;

/**
 * Calculates future or past dates, but only using working days (not weekends or public holidays).
 * e.g. if you want to know 2 working days after 24th Dec, rather than just adding 2 days it checks
 * for weekends and also public holidays (Christmas day/boxing day) to derive the correct date.
 */
public class CalendarWeekDayOperationsService {

    @Inject
    private ReferenceDataService referenceDataService;

    /**
     * Returns working day after adding number of Working days to Date send.
     */
    public LocalDate plusWorkingDays(final LocalDate date, final Long numberOfDays) {
        return workingDayOperations(date, numberOfDays, true);
    }


    /**
     * Returns working day after subtracting number of Working days to Date send.
     */
    public LocalDate minusWorkingDays(final LocalDate date, final Long numberOfDays) {
        return workingDayOperations(date, numberOfDays, false);
    }


    private LocalDate workingDayOperations(final LocalDate date, final Long numberOfDays, final boolean isAddingOperation) {

        List<PublicHoliday> publicHolidays;
        if (isAddingOperation) {
            publicHolidays = referenceDataService.getPublicHolidays(ENGLAND_AND_WALES_DIVISION, date, date.plusDays(30));
        } else {
            publicHolidays = referenceDataService.getPublicHolidays(ENGLAND_AND_WALES_DIVISION, date.minusDays(30), date);
        }

        final List<LocalDate> localDatePublicHolidaysList = publicHolidays.stream().map(PublicHoliday::getDate).collect(toList());

        LocalDate adjustedLocalDate = date;
        // Check if date is holiday
        for (int count = 0; count < numberOfDays; count++) {
            if (isAddingOperation) {
                adjustedLocalDate = getAdjustedWorkingDate(adjustedLocalDate.plusDays(1), localDatePublicHolidaysList, true);
            } else {
                adjustedLocalDate = getAdjustedWorkingDate(adjustedLocalDate.minusDays(1), localDatePublicHolidaysList, false);
            }
        }
        return adjustedLocalDate;
    }

    private LocalDate getAdjustedWorkingDate(final LocalDate localDate, final List<LocalDate> publicHolidays, final boolean isAddingOperation) {
        LocalDate adjustedLocalDate = localDate;
        while (isDateOnAWeekend(adjustedLocalDate) || publicHolidays.contains(adjustedLocalDate)) {
            if (isAddingOperation) {
                adjustedLocalDate = adjustedLocalDate.plusDays(1);
            } else {
                adjustedLocalDate = adjustedLocalDate.minusDays(1);
            }
        }
        return adjustedLocalDate;
    }

    private boolean isDateOnAWeekend(final LocalDate localDate) {
        return localDate.getDayOfWeek() == SUNDAY || localDate.getDayOfWeek() == SATURDAY;
    }
}
