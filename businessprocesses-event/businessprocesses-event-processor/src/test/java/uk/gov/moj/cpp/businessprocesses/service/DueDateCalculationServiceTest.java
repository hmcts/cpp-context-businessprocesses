package uk.gov.moj.cpp.businessprocesses.service;


import static java.time.LocalDateTime.now;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import uk.gov.justice.services.common.util.UtcClock;
import uk.gov.moj.cpp.businessprocesses.shared.InvalidDueDateCalculationRuleException;
import uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class DueDateCalculationServiceTest {

    @Mock
    private CalendarWeekDayOperationsService calendarWeekDayOperationsService;
    @InjectMocks
    private DueDateCalculationService dueDateCalculationService;
    @InjectMocks
    private UtcClock utcClock;

    @Test
    public void shouldGetDueDateForRuleNow() {

        // Given
        final LocalDate localDate = now().toLocalDate();
        final String rule = "${now()}";
        ZonedDateTime utcDateTime = utcClock.now().with(LocalTime.of(23, 59, 59, 0)); // Set time to 23:59:00.000

        String expected = utcDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"));
        // When
        final String result = dueDateCalculationService.calculateDueDate(rule);

        // Then
        assertThat(result, is(expected));
        verify(calendarWeekDayOperationsService, never()).plusWorkingDays(any(), any());
        verify(calendarWeekDayOperationsService, never()).minusWorkingDays(any(), any());
    }

    @Test
    public void shouldGetCalculateDueDateForRuleNow() throws ParseException {

        // Given
        final LocalDate localDate = now().toLocalDate();
        final String rule = "${now()}";

        // When
        final Date result = dueDateCalculationService.getCalculatedDate(rule, null);

        // Then
        assertThat(new SimpleDateFormat(ProcessVariableConstants.DUE_DATE_FORMAT).format(result), is(localDate.toString()));
        verify(calendarWeekDayOperationsService, never()).plusWorkingDays(any(), any());
        verify(calendarWeekDayOperationsService, never()).minusWorkingDays(any(), any());
    }

    @Test
    public void shouldGetCalculateDueDateForRulePlusOne() throws ParseException {

        // Given
        final LocalDate localDate = now().toLocalDate();
        final String rule = "${now()+1}";
        when(calendarWeekDayOperationsService.plusWorkingDays(localDate, 1L)).thenReturn(localDate.plusDays(1));

        // When
        final Date result = dueDateCalculationService.getCalculatedDate(rule, null);

        // Then
        assertThat(new SimpleDateFormat(ProcessVariableConstants.DUE_DATE_FORMAT).format(result), is(localDate.plusDays(1).toString()));
    }

    @Test
    public void shouldGetCalculateDueDateForRulePlusTwo() throws ParseException {

        // Given
        final LocalDate localDate = now().toLocalDate();
        final String rule = "${now()+2}";
        when(calendarWeekDayOperationsService.plusWorkingDays(localDate, 2L)).thenReturn(localDate.plusDays(2));

        // When
        final Date result = dueDateCalculationService.getCalculatedDate(rule, null);
        // Then
        assertThat(new SimpleDateFormat(ProcessVariableConstants.DUE_DATE_FORMAT).format(result), is(localDate.plusDays(2L).toString()));
    }

    @Test
    public void shouldGetDueDateForRuleNowPlusOne() {

        // Given
        final LocalDate localDate = now().toLocalDate();
        ZonedDateTime utcDateTime = utcClock.now().plusDays(1).with(LocalTime.of(23, 59, 59, 0)); // Set time to 23:59:00.000

        String expected = utcDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"));

        final String rule = "${now()+1}";
        when(calendarWeekDayOperationsService.plusWorkingDays(localDate, 1L)).thenReturn(localDate.plusDays(1));

        // When
        final String result = dueDateCalculationService.calculateDueDate(rule);

        // Then
        assertThat(result, is(expected));
    }

    @Test
    public void shouldGetDueDateForRuleNowPlusTwo() {

        // Given
        final LocalDate localDate = now().toLocalDate();
        final String rule = "${now()+2}";

        ZonedDateTime utcDateTime = utcClock.now().plusDays(2).with(LocalTime.of(23, 59, 59, 0)); // Set time to 23:59:00.000

        String expected = utcDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"));
        when(calendarWeekDayOperationsService.plusWorkingDays(localDate, 2L)).thenReturn(localDate.plusDays(2));

        // When
        final String result = dueDateCalculationService.calculateDueDate(rule);

        // Then
        assertThat(result, is(expected));
    }

    @Test
    public void shouldGetDueDateForRuleNowMinusOne() {

        // Given
        final LocalDate localDate = now().toLocalDate();
        final String rule = "${now()-1d}";
        ZonedDateTime utcDateTime = utcClock.now().minusDays(1).with(LocalTime.of(23, 59, 59, 0)); // Set time to 23:59:00.000

        String expected = utcDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"));
        when(calendarWeekDayOperationsService.minusWorkingDays(localDate, 1L)).thenReturn(localDate.minusDays(1));

        // When
        final String result = dueDateCalculationService.calculateDueDate(rule);

        // Then
        assertThat(result, is(expected));
    }

    @Test
    public void shouldGetDueDateForRuleHearingDate() {

        // Given
       // final ZonedDateTime hearingDate = utcClock.now();
        ZonedDateTime utcDateTime = utcClock.now()
                .with(LocalTime.of(23, 59, 59, 0)); // Set time to 23:59:00.000

        String hearingDate = utcDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"));
        final String rule = "${hearingDate}";

        // When
        final String result = dueDateCalculationService.calculateDueDate(rule, hearingDate);

        // Then

        assertThat(result, is(hearingDate));
        verify(calendarWeekDayOperationsService, never()).plusWorkingDays(any(), any());
        verify(calendarWeekDayOperationsService, never()).minusWorkingDays(any(), any());
    }


    @Test
    public void shouldGetDueDateForRuleHearingDatePlusOne() {

        // Givens
        final ZonedDateTime hearingDate = utcClock.now();
        final String rule = "${hearingDate+1d}";
        ZonedDateTime utcDateTime = utcClock.now().plusDays(1).with(LocalTime.of(23, 59, 59, 0)); // Set time to 23:59:00.000

        String expected = utcDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"));
        when(calendarWeekDayOperationsService.plusWorkingDays(hearingDate.toLocalDate(), 1L)).thenReturn(hearingDate.toLocalDate().plusDays(1));

        // When
        final String result = dueDateCalculationService.calculateDueDate(rule, hearingDate.toString());

        // Then
        assertThat(result, is(expected));
    }


    @Test
    public void shouldGetDueDateForRuleHearingDateMinusOne() {

        // Given
        final ZonedDateTime hearingDate = utcClock.now();
        final String rule = "${hearingDate-1d}";
        ZonedDateTime utcDateTime = utcClock.now().minusDays(1).with(LocalTime.of(23, 59, 59, 0)); // Set time to 23:59:00.000

        String expected = utcDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"));
        when(calendarWeekDayOperationsService.minusWorkingDays(hearingDate.toLocalDate(), 1L)).thenReturn(hearingDate.toLocalDate().minusDays(1));

        // When
        final String result = dueDateCalculationService.calculateDueDate(rule, hearingDate.toString());

        // Then
        assertThat(result, is(expected));
    }

    @Test
    public void shouldGetDueDateForRuleHearingDateWithNullHearingDateExpectingException() {

        // Given
        final String rule = "${hearingDate+1d}";

        // Then
        assertThrows(InvalidDueDateCalculationRuleException.class, () -> dueDateCalculationService.calculateDueDate(rule));
    }
}