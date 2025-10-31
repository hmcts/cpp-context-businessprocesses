package uk.gov.moj.cpp.businessprocesses.shared;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import uk.gov.justice.listing.events.Hearing;
import uk.gov.moj.cpp.businessprocesses.util.TestDataProvider;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;

public class HearingHelperTest {

    @Test
    void shouldGetLatestHearing() throws IOException {
        final List<Hearing> hearingList = TestDataProvider.getHearingList();
        final Hearing hearing = HearingHelper.getLatestHearing(hearingList);
        assertThat(hearing.getId().toString(), is("1cc912fe-f5c0-4de1-b422-5714286d2608"));
    }

    @Test
    void shouldGetHearingDate() throws IOException {
        final List<Hearing> hearingList = TestDataProvider.getHearingList();
        final Hearing hearing = HearingHelper.getLatestHearing(hearingList);
        final String hearingDate = HearingHelper.getHearingDate(hearing);
        assertThat(hearingDate, is("2025-08-01T09:00:00.000Z"));
    }

    @Test
    void shouldGetCustodyTimeLimitForHearingList() throws IOException {
        final List<Hearing> hearingList = TestDataProvider.getHearingList();
        final String custodyTimeLimit = HearingHelper.getCustodyTimeLimit(hearingList);
        assertThat(custodyTimeLimit, is("2020-11-19"));
    }
}
