package uk.gov.moj.cpp.businessprocesses.it;

import static com.google.common.collect.ImmutableMap.of;
import static java.util.UUID.fromString;
import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static uk.gov.justice.services.messaging.JsonEnvelope.metadataBuilder;
import static uk.gov.moj.cpp.businessprocesses.helper.BpmRestApiHelper.getProcessInstanceList;
import static uk.gov.moj.cpp.businessprocesses.helper.FileUtil.getFileContentAsJson;
import static uk.gov.moj.cpp.businessprocesses.stub.ProgressionServiceStub.PROGRESSION_PROSECUTION_JSON;
import static uk.gov.moj.cpp.businessprocesses.stub.ReferenceDataServiceStub.stubPublicHolidays;
import static uk.gov.moj.cpp.businessprocesses.stub.ReferenceDataServiceStub.stubWorkQueueForQueueName;
import static uk.gov.moj.cpp.businessprocesses.stub.ReferenceDataServiceStub.stubWorkflowTaskTypes;
import static uk.gov.moj.cpp.businessprocesses.util.JsonUtil.getJsonStringFromResource;
import static uk.gov.moj.cpp.platform.test.feature.toggle.FeatureStubber.stubFeaturesFor;

import uk.gov.justice.services.messaging.Metadata;
import uk.gov.moj.cpp.businessprocesses.helper.CustomMessageProducerClient;
import uk.gov.moj.cpp.businessprocesses.stub.HearingStub;
import uk.gov.moj.cpp.businessprocesses.stub.ProgressionServiceStub;

import java.io.IOException;
import java.time.Duration;

import javax.json.JsonArray;
import javax.json.JsonObject;

import com.google.common.collect.ImmutableMap;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled
class SummonsApplicationIT {

    private static final String BUSINESS_PROCESSES_CONTEXT = "businessprocesses";
    private static final String PUBLIC_EVENT_HEARING_LISTED = "public.hearing.initiated";
    private static final String PUBLIC_EVENT_HEARING_RESULTED = "public.events.hearing.hearing-resulted";
    private static final String USER_ID = randomUUID().toString();

    private static final String HEARING_INITIATED_APPLICATION_DETAILS_JSON = "summon-application/public.hearing.initiated.json";
    private static final String HEARING_RESULTED_APPLICATION_DETAILS_JSON = "summon-application/public.events.hearing.hearing-resulted.json";
    private static final String PROCESS_NEW_SUMMONS_APPLICATION = "process_summons_application";

    private final CustomMessageProducerClient publicMessageClient = new CustomMessageProducerClient();

    @BeforeEach
    public void setUp() throws IOException {
        stubWorkflowTaskTypes();
        stubWorkQueueForQueueName(fromString("66567e81-931e-337e-a5b3-47e0b221bcc7"), "work_queue_name");
        stubPublicHolidays();
        publicMessageClient.startProducer("public.event");
    }

    @Test
    void shouldCreateProcessNewSummonsApplicationTask() throws Exception {
        final ImmutableMap<String, Boolean> features = of("camunda-hearing-initiated", true, "camunda-hearing-hearing-resulted", true);
        stubFeaturesFor(BUSINESS_PROCESSES_CONTEXT, features);
        final String payload = getJsonStringFromResource("summon-application/hearing.get-hearing.json");
        HearingStub.stubGetHearing("9c4894cb-0708-4f80-bee5-95236dfdd7e8", payload);
        ProgressionServiceStub.stubProsecutionCase("0c42449a-10da-444e-9bc6-8c3b8e524101", PROGRESSION_PROSECUTION_JSON);

        final Metadata metadata = metadataBuilder()
                .withName(PUBLIC_EVENT_HEARING_LISTED)
                .withId(randomUUID())
                .withUserId(USER_ID)
                .withClientCorrelationId(randomUUID().toString())
                .build();

        final JsonObject eventPayload = getFileContentAsJson(HEARING_INITIATED_APPLICATION_DETAILS_JSON);

        // before
        JsonArray processInstanceList = getProcessInstanceList(PROCESS_NEW_SUMMONS_APPLICATION, "CPJKJUAK8G");
        final int beforeSize = processInstanceList.size();

        publicMessageClient.sendMessage(PUBLIC_EVENT_HEARING_LISTED, eventPayload, metadata);

        waitUntilCondition(beforeSize+1);

        final JsonObject processInstances = getProcessInstances(getProcessInstanceList(PROCESS_NEW_SUMMONS_APPLICATION, "CPJKJUAK8G"), "CPJKJUAK8G");
        final String processInstanceId = processInstances.getString("id");

        assertThat(processInstances.getString("businessKey"), is("CPJKJUAK8G"));
        assertThat(processInstanceId, is(notNullValue()));

        // hearing resulted with summons approved
        final JsonObject hearingResultedPayload = getFileContentAsJson(HEARING_RESULTED_APPLICATION_DETAILS_JSON);

        final Metadata metadata1 = metadataBuilder()
                .withName(PUBLIC_EVENT_HEARING_RESULTED)
                .withId(randomUUID())
                .withUserId(USER_ID)
                .withClientCorrelationId(randomUUID().toString())
                .build();
        publicMessageClient.sendMessage(PUBLIC_EVENT_HEARING_RESULTED, hearingResultedPayload, metadata1);

        waitUntilCondition(0);
    }

    private JsonObject getProcessInstances(final JsonArray processInstances, final String businessKey) {
        return !processInstances.isEmpty() && processInstances.getJsonObject(0).getString("businessKey").equals(businessKey) ? processInstances.getJsonObject(0) : null;
    }

    private void waitUntilCondition(final int count) {
        Awaitility.await()
                .atMost(Duration.ofMillis(10000))
                .pollDelay(Duration.ofMillis(1000))
                .until(() -> getProcessInstanceList(PROCESS_NEW_SUMMONS_APPLICATION, "CPJKJUAK8G").size() == count);
    }

}
