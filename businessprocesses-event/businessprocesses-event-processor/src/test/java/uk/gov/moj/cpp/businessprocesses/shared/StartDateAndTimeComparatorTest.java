package uk.gov.moj.cpp.businessprocesses.shared;

import static java.lang.Boolean.FALSE;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.slf4j.LoggerFactory.getLogger;

import uk.gov.justice.listing.events.Hearing;
import uk.gov.moj.cpp.businessprocesses.util.TestDataProvider;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;

@ExtendWith(MockitoExtension.class)
public class StartDateAndTimeComparatorTest {

    private static final Logger LOGGER = getLogger(StartDateAndTimeComparatorTest.class);

    @Test
    public void shouldGetHearings() {
        List<Hearing> hearingList = null;
        try {
            hearingList = TestDataProvider.getHearingList();
        } catch (IOException e) {
            assertThat("list creation failed", is(FALSE));
        }
        assertThat(hearingList, notNullValue());
        assertThat(hearingList.get(0).getHearingDays().get(0).getHearingDate(), is("2025-08-15"));
        assertThat(hearingList.get(1).getHearingDays().get(0).getHearingDate(), is("2025-08-01"));
        final StartDateAndTimeComparator comparator = new StartDateAndTimeComparator();
        assertThat(14, is(comparator.compare(hearingList.get(0), hearingList.get(1))));
        hearingList.sort(comparator);
        assertThat(hearingList.get(0).getHearingDays().get(0).getHearingDate(), is("2025-08-01"));
        assertThat(hearingList.get(1).getHearingDays().get(0).getHearingDate(), is("2025-08-15"));
    }

}
