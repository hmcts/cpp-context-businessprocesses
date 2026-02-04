package uk.gov.moj.cpp.businessprocesses.it;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.reset;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static java.lang.String.format;
import static java.util.UUID.randomUUID;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;
import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static javax.ws.rs.core.Response.Status.ACCEPTED;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static uk.gov.justice.service.wiremock.testutil.InternalEndpointMockUtils.stubPingFor;
import static uk.gov.justice.services.messaging.JsonEnvelope.metadataBuilder;
import static uk.gov.moj.cpp.businessprocesses.helper.DurationHelper.parseDuration;
import static uk.gov.moj.cpp.businessprocesses.helper.WiremockHelper.verifyCommandIssued;

import uk.gov.justice.services.messaging.Metadata;
import uk.gov.justice.services.test.utils.core.messaging.MessageConsumerClient;
import uk.gov.moj.cpp.businessprocesses.helper.CustomMessageProducerClient;

import javax.json.JsonObject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SJPTriggersDeleteDocumentsTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(SJPTriggersDeleteDocumentsTest.class);

    private static final String PUBLIC_SJP_ALL_OFFENCES_DISMISSED_OR_WITHDRAWN = "public.sjp.all-offences-for-defendant-dismissed-or-withdrawn";
    private static final String CASE_ID_FIELD = "caseId";
    private static final String DEFENDANT_ID_FIELD = "defendantId";
    private static final String CASE_ID = randomUUID().toString();
    private static final String DEFENDANT_ID = randomUUID().toString();
    private static final String USER_ID = randomUUID().toString();
    private static final String SJP_BUSINESS_CONTEXT_SERVICE = "sjp-service";
    private static final String SJP_BUSINESS_CONTEXT_COMMAND_API =
            ".*/command/api/rest/sjp/cases/%s/defendant/%s/financial-means";
    private static final String SJP_BUSINESS_CONTEXT_URL = format(SJP_BUSINESS_CONTEXT_COMMAND_API, CASE_ID, DEFENDANT_ID);
    private static final String SJP_BUSINESS_CONTEXT_COMMAND_CONTENT = "application/vnd.sjp.delete-financial-means+json";


    private static final String PROGRESSION_BUSINESS_CONTEXT_SERVICE = "progression-service";
    private static final String PROGRESSION_BUSINESS_CONTEXT_COMMAND_API =
            ".*command/api/rest/progression/cases/%s/defendants/%s/financial-means";
    private static final String PROGRESSION_BUSINESS_CONTEXT_URL = format(PROGRESSION_BUSINESS_CONTEXT_COMMAND_API, CASE_ID, DEFENDANT_ID);
    private static final String PROGRESSION_BUSINESS_CONTEXT_COMMAND_CONTENT = "application/vnd.sjp.delete-financial-means+json";

    private final CustomMessageProducerClient publicMessageClient = new CustomMessageProducerClient();
    private final MessageConsumerClient processCompletedConsumer = new MessageConsumerClient();

    @BeforeEach
    public void setUp() {
        publicMessageClient.startProducer("public.event");
        processCompletedConsumer.startConsumer("public.bpm.financial-means-information-deleted", "jms.topic.public.event");
        LOGGER.info("Setting up test");
        reset();
        stubPingFor(SJP_BUSINESS_CONTEXT_SERVICE);
        stubFor(post(urlMatching(SJP_BUSINESS_CONTEXT_URL))
                .willReturn(aResponse().withStatus(ACCEPTED.getStatusCode())));
        stubPingFor(PROGRESSION_BUSINESS_CONTEXT_SERVICE);
        stubFor(post(urlMatching(PROGRESSION_BUSINESS_CONTEXT_URL))
                .willReturn(aResponse().withStatus(ACCEPTED.getStatusCode())));
    }

    @Test
    public void shouldStartProcessAndSendCompletedEvent() {
        final long delayBeforeFinancialMeansDeletion = parseDuration(System.getProperty("delayBeforeFinancialMeansDeletion"), 60);

        final Metadata metadata = metadataBuilder()
                .withName(PUBLIC_SJP_ALL_OFFENCES_DISMISSED_OR_WITHDRAWN)
                .withId(randomUUID())
                .withUserId(USER_ID)
                .withClientCorrelationId(randomUUID().toString())
                .build();

        final JsonObject processStartRequestedMessageAsJson = createObjectBuilder()
                .add(CASE_ID_FIELD, CASE_ID)
                .add(DEFENDANT_ID_FIELD, DEFENDANT_ID)
                .build();

        publicMessageClient.sendMessage(PUBLIC_SJP_ALL_OFFENCES_DISMISSED_OR_WITHDRAWN, processStartRequestedMessageAsJson, metadata);

        LOGGER.info(format("Will wait for %d seconds for the command to be issued", delayBeforeFinancialMeansDeletion));

        verifyCommandIssued(SJP_BUSINESS_CONTEXT_URL, CONTENT_TYPE, SJP_BUSINESS_CONTEXT_COMMAND_CONTENT, delayBeforeFinancialMeansDeletion);

        verifyCommandIssued(PROGRESSION_BUSINESS_CONTEXT_URL, CONTENT_TYPE, PROGRESSION_BUSINESS_CONTEXT_COMMAND_CONTENT, delayBeforeFinancialMeansDeletion);

        final String processFinishedEvent = processCompletedConsumer.retrieveMessage(10000).orElse(null);

        assertThat(processFinishedEvent, is(notNullValue()));
    }

}
