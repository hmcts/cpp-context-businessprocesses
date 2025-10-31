package uk.gov.moj.cpp.businessprocesses.service;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static uk.gov.justice.services.messaging.Envelope.envelopeFrom;
import static uk.gov.justice.services.test.utils.core.messaging.MetadataBuilderFactory.metadataWithRandomUUID;
import static uk.gov.moj.cpp.businessprocesses.util.FileUtil.getFileContentAsJson;

import uk.gov.justice.core.courts.Hearing;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.justice.services.messaging.Envelope;

import java.util.UUID;

import javax.json.JsonObject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HearingServiceTest {

    public static final String HEARING_GET_HEARING = "hearing.get.hearing";
    @Mock
    private Requester requester;

    @InjectMocks
    private HearingService hearingService;

    @Test
    void shouldReturnHearing() {
        // given
        final String hearingId = UUID.randomUUID().toString();
        final JsonObject payload = getFileContentAsJson("json/hearing.get.hearing.json");
        final Envelope<JsonObject> envelope = envelopeFrom(
                metadataWithRandomUUID(HEARING_GET_HEARING),
                payload);

        // when
        when(requester.requestAsAdmin(any(Envelope.class), eq(JsonObject.class))).thenReturn(envelope);
        final Hearing hearing = hearingService.getHearing(hearingId);

        //then
        assertThat(hearing, notNullValue());
        assertThat(hearing.getId().toString(), is("9c4894cb-0708-4f80-bee5-95236dfdd7e8"));
    }
}