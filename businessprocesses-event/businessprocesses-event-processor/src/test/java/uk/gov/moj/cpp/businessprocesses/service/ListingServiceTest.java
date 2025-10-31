package uk.gov.moj.cpp.businessprocesses.service;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static uk.gov.moj.cpp.businessprocesses.util.TestDataProvider.getResponseEnvelopeFromListing;

import uk.gov.justice.listing.events.Hearing;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.justice.services.messaging.Envelope;

import java.util.List;

import javax.json.JsonObject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ListingServiceTest {

    private static final String USER_ID = randomUUID().toString();
    private static final String CASE_ID = randomUUID().toString();

    @Mock
    private Requester requester;

    @InjectMocks
    private ListingService target;

    @BeforeEach
    public void setUp() {
        when(requester.request(any(Envelope.class), eq(JsonObject.class))).thenReturn(getResponseEnvelopeFromListing());
    }

    @Test
    public void shouldGetHearings() {
        final List<Hearing> hearingList = target.getHearings(USER_ID, CASE_ID);
        assertThat(hearingList, notNullValue());
        assertThat(hearingList, hasSize(2));
        assertThat(hearingList.get(0).getId().toString(), is("189a6bf4-fb19-4a7b-8896-93a11cdd6574"));
        assertThat(hearingList.get(0).getHearingDays().get(0).getHearingDate(), is("2025-08-15"));
        assertThat(hearingList.get(0).getType().getDescription(), is("Hearing"));
        assertThat(hearingList.get(1).getId().toString(), is("1cc912fe-f5c0-4de1-b422-5714286d2608"));
        assertThat(hearingList.get(1).getHearingDays().get(0).getHearingDate(), is("2025-08-01"));
        assertThat(hearingList.get(1).getType().getDescription(), is("Plea and Trial Preparation"));
    }

}