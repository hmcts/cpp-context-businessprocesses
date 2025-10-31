package uk.gov.moj.cpp.businessprocesses.service;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static uk.gov.moj.cpp.businessprocesses.util.TestDataProvider.getResponseEnvelopeFromProgressionCaag;
import static uk.gov.moj.cpp.businessprocesses.util.TestDataProvider.getResponseEnvelopeFromProgressionProsecutionCaseExist;

import uk.gov.justice.courts.progression.query.Caag;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.moj.cpp.businessprocesses.util.TestDataProvider;

import java.io.IOException;

import javax.json.JsonObject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ProgressionServiceTest {

    private static final String USER_ID = randomUUID().toString();
    private static final String CASE_ID = randomUUID().toString();
    private static final String CASE_URN = "CASE_URN";

    @Mock(answer = RETURNS_DEEP_STUBS)
    private Requester requester;

    @InjectMocks
    private ProgressionService target;


    @Test
    public void shouldGetProsecutionCaseCaag() {
        when(requester.request(any(Envelope.class), eq(JsonObject.class))).thenReturn(getResponseEnvelopeFromProgressionCaag());

        final Caag caag = target.getProsecutionCaseCaag(USER_ID, CASE_ID);
        assertThat(caag, notNullValue());
        assertThat(caag.getCaseId(), is("1082bd2f-63ef-42e7-86c7-6cfaaf3ba10a"));
        assertThat(caag.getDefendants(), hasSize(1));
        assertThat(caag.getDefendants().get(0).getCtlExpiryDate(), is("2025-03-31"));
    }

    @Test
    public void shouldGetProsecutionCase() throws IOException {
        final JsonObject responseFromProgression = TestDataProvider.getJurisdictionType();
        when(requester.requestAsAdmin(any(JsonEnvelope.class), eq(JsonObject.class)).payload()).thenReturn(responseFromProgression);

        final JsonObject jsonObject = target.getProsecutionCase(CASE_ID);
        assertThat(jsonObject, notNullValue());
        assertThat(jsonObject.getJsonObject("hearingsAtAGlance").getString("latestHearingJurisdictionType"),is("CROWN"));
    }

    @Test
    public void shouldGetProsecutionCaseExistByCaseUrn() throws IOException {
        when(requester.requestAsAdmin(any(JsonEnvelope.class), eq(JsonObject.class)).payload()).thenReturn(getResponseEnvelopeFromProgressionProsecutionCaseExist());

        final String caseId = target.getProsecutionCaseExistByCaseUrn(CASE_URN);
        assertThat(caseId, notNullValue());
        assertThat(caseId, is("8e4b2be0-f92a-4291-9b99-17af7e645472"));
    }
}