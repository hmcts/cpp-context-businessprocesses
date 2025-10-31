package uk.gov.moj.cpp.businessprocesses.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class DateConverterTest {

    @Test
    void formatDateAndTime_shouldFormatGivenDateAndTime() {
        String dateStr = "2025-02-25";
        String timeStr = "22:30:00";
        String expected = "2025-02-25T22:30:00.000Z";

        String actual = DateConverter.formatHearingDate(dateStr, timeStr);
        assertEquals(expected, actual, "The formatted date-time does not match the expected one.");
    }

    @Test
    void formatHearingDate_shouldReturnDefaultTime_whenTimeStrIsEmpty() {
        String actual = DateConverter.formatHearingDate("2025-02-25", "");
        assertEquals("2025-02-25T00:00:00.000Z", actual, "The formatted date-time does not match the expected one.");
    }

    @Test
    void formatDateAndTime_shouldReturnEmptyString_whenDateStrIsEmpty() {
        String actual = DateConverter.formatHearingDate("", "22:30:00");
        assertEquals("", actual, "The formatted date-time does not match the expected one.");
    }
}
