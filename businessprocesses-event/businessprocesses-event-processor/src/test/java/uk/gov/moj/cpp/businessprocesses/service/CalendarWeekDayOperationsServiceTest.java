package uk.gov.moj.cpp.businessprocesses.service;


import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import uk.gov.moj.cpp.businessprocesses.pojo.PublicHoliday;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class CalendarWeekDayOperationsServiceTest {

    @Mock
    private ReferenceDataService referenceDataService;

    @InjectMocks
    private CalendarWeekDayOperationsService calendarWeekDayOperationsService;

    @BeforeEach
    public void setUp() {
        // Given
        when(referenceDataService.getPublicHolidays(anyString(), any(), any())).thenReturn(getPublicHolidaysForJune2022());
    }

    /*
     * To Understand this Test It is Good to know Working Days of june 2022.
     * 30 May  Mon.
     * 31 May  Tue.
     * 01 June Wen.
     * 02 June Tru. (Public Holiday in England)
     * 03 June Fri. (Public Holiday in England)
     * 04 June Sat.
     * 05 June Sun.
     * 06 June Mon .....(No Public holidays ) ...
     */

    @Test
    public void shouldPlusTwoDaysFromJune1Of2022WhenWeHavePublicHolidaysAndWeekEnd() {
        // When
        final LocalDate result = calendarWeekDayOperationsService.plusWorkingDays(LocalDate.parse("2022-06-01"), 2L);

        // Then
        assertThat(result, is(LocalDate.parse("2022-06-07")));
    }

    @Test
    public void shouldPlusTwoDaysFromJune6Of2022WithOutAnyHolidays() {
        // When
        final LocalDate result = calendarWeekDayOperationsService.plusWorkingDays(LocalDate.parse("2022-06-06"), 2L);

        // Then
        assertThat(result, is(LocalDate.parse("2022-06-08")));
    }

    @Test
    public void shouldMinusTwoDaysFromJune6Of2022WhenWeHavePublicHolidaysAndWeekEnd() {
        // When
        final LocalDate result = calendarWeekDayOperationsService.minusWorkingDays(LocalDate.parse("2022-06-06"), 2L);

        // Then
        assertThat(result, is(LocalDate.parse("2022-05-31")));
    }

    @Test
    public void shouldMinusTwoDaysFromJune8Of2022WithOutHolidays() {
        // When
        final LocalDate result = calendarWeekDayOperationsService.minusWorkingDays(LocalDate.parse("2022-06-08"), 2L);

        // Then
        assertThat(result, is(LocalDate.parse("2022-06-06")));
    }

    private List<PublicHoliday> getPublicHolidaysForJune2022() {
        final List<PublicHoliday> publicHolidays = new ArrayList();
        publicHolidays.add(publicHoliday(LocalDate.parse("2022-06-02")));
        publicHolidays.add(publicHoliday(LocalDate.parse("2022-06-03")));
        return publicHolidays;
    }

    private PublicHoliday publicHoliday(final LocalDate localDate) {
        return new PublicHoliday(UUID.randomUUID(), "division", "title", localDate);
    }
}