package uk.gov.moj.cpp.businessprocesses.it;

import static com.github.tomakehurst.wiremock.client.WireMock.reset;
import static com.google.common.collect.ImmutableMap.of;
import static java.util.UUID.fromString;
import static java.util.UUID.randomUUID;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.ONE_MINUTE;
import static org.awaitility.Durations.ONE_SECOND;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static uk.gov.justice.services.messaging.JsonEnvelope.metadataBuilder;
import static uk.gov.moj.cpp.businessprocesses.helper.BpmRestApiHelper.getProcessInstanceList;
import static uk.gov.moj.cpp.businessprocesses.helper.BpmRestApiHelper.getTaskList;
import static uk.gov.moj.cpp.businessprocesses.helper.BpmRestApiHelper.waitForEvent;
import static uk.gov.moj.cpp.businessprocesses.helper.FileUtil.getFileContentAsJson;
import static uk.gov.moj.cpp.businessprocesses.stub.ReferenceDataServiceStub.stubPublicHolidays;
import static uk.gov.moj.cpp.businessprocesses.stub.ReferenceDataServiceStub.stubResultDefinition;
import static uk.gov.moj.cpp.businessprocesses.stub.ReferenceDataServiceStub.stubWorkQueueForQueueName;
import static uk.gov.moj.cpp.businessprocesses.stub.ReferenceDataServiceStub.stubWorkflowTaskTypes;
import static uk.gov.moj.cpp.platform.test.feature.toggle.FeatureStubber.stubFeaturesFor;

import uk.gov.justice.services.messaging.Metadata;
import uk.gov.moj.cpp.businessprocesses.helper.CustomMessageProducerClient;

import java.io.IOException;
import java.util.List;
import java.util.Random;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;

import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ListingHearingListedIT {

    private static final String PUBLIC_LISTING_HEARING_LISTED_EVENT = "public.listing.hearing-listed";
    private static final String PUBLIC_HEARING_RESULTED_EVENT = "public.events.hearing.hearing-resulted";
    private static final String LIST_BAIL_APPEAL_HEARING_PROCESS = "list_bail_appeal_hearing_process";
    private static final String PUBLIC_HEARING_HEARING_RESULTED = "hearing-hearing-resulted.json";
    private static final String USER_ID = randomUUID().toString();
    private static final String BUSINESS_PROCESSES_CONTEXT = "businessprocesses";

    private final CustomMessageProducerClient publicMessageClient = new CustomMessageProducerClient();
    private Metadata hearingResultedMetadata;
    private Metadata hearingListedMetadata;

    @BeforeEach
    void setUp() throws IOException {
        hearingResultedMetadata = metadataBuilder()
                .withName(PUBLIC_HEARING_RESULTED_EVENT)
                .withId(randomUUID())
                .withUserId(USER_ID)
                .withClientCorrelationId(randomUUID().toString())
                .build();
        hearingListedMetadata = metadataBuilder()
                .withName(PUBLIC_LISTING_HEARING_LISTED_EVENT)
                .withId(randomUUID())
                .withUserId(USER_ID)
                .withClientCorrelationId(randomUUID().toString())
                .build();
        publicMessageClient.startProducer("public.event");
        reset();
        stubWorkflowTaskTypes();
        stubWorkQueueForQueueName(fromString("66567e81-931e-337e-a5b3-47e0b221bcc7"), "work_queue_name");
        stubPublicHolidays();
    }

    @Test
    void shouldCompleteBailHearingTaskWhenPublicListingHearingListedEventReceived() throws IOException {
        final ImmutableMap<String, Boolean> features = of(
                "camunda-hearing-hearing-resulted", true,
                "list_bail_appeal_hearing", true,
                "camunda-listing-hearing-listed", true,
                "list_bail_appeal_hearing_process", true);
        stubFeaturesFor(BUSINESS_PROCESSES_CONTEXT, features);

        final String caseUrnToComplete = "CASE_URN_LISTING_" + new Random().nextInt(10000);
        final String caseUrn2 = "CASE_URN_OTHER_2_" + new Random().nextInt(10000);
        final String caseUrn3 = "CASE_URN_OTHER_3_" + new Random().nextInt(10000);
        final String hearingId = randomUUID().toString();

        final JsonObject hearingResultedPayload = getHearingResultedPayload(hearingId, caseUrnToComplete, caseUrn2, caseUrn3);
        stubResultDefinition("7a0932f5-8264-412e-a83c-2b6d1dce1506");
        publicMessageClient.sendMessage(PUBLIC_HEARING_RESULTED_EVENT, hearingResultedPayload, hearingResultedMetadata);

        final JsonArray processInstanceList = getProcessInstanceList(LIST_BAIL_APPEAL_HEARING_PROCESS, caseUrnToComplete);
        assertThat(processInstanceList.size(), is(1));

        final JsonObject processInstance = getProcessInstances(processInstanceList, caseUrnToComplete);
        final String processInstanceId = processInstance.getString("id");
        assertThat(processInstanceId, is(notNullValue()));
        assertThat(processInstance.getString("businessKey"), is(caseUrnToComplete));

        final List<String> taskListBefore = getTaskList(processInstanceId);
        assertThat(taskListBefore.size(), is(1));

        final JsonObject hearingListedPayload = createHearingListedPayload(hearingId, "Bail Application", caseUrnToComplete);
        publicMessageClient.sendMessage(PUBLIC_LISTING_HEARING_LISTED_EVENT, hearingListedPayload, hearingListedMetadata);

        waitForEvent();
        await().pollInterval(ONE_SECOND).atMost(ONE_MINUTE).until(() ->
                getProcessInstanceList(LIST_BAIL_APPEAL_HEARING_PROCESS, caseUrnToComplete).size(), is(0));

        final JsonArray processInstanceListAfter = getProcessInstanceList(LIST_BAIL_APPEAL_HEARING_PROCESS, caseUrnToComplete);
        assertThat(processInstanceListAfter.size(), is(0));
    }

    private JsonObject createHearingListedPayload(final String hearingId, final String hearingType, final String caseUrn) {
        return Json.createObjectBuilder()
                .add("hearingId", hearingId)
                .add("hearingType", hearingType)
                .add("caseUrns", Json.createArrayBuilder()
                        .add(Json.createObjectBuilder().add("caseURN", caseUrn).build())
                        .build())
                .build();
    }

    private JsonObject getHearingResultedPayload(final String hearingId, final String caseUrn1, final String caseUrn2, final String caseUrn3) {
        return getFileContentAsJson(PUBLIC_HEARING_HEARING_RESULTED,
                ImmutableMap.<String, Object>builder()
                        .put("CASE_URN_1", caseUrn1)
                        .put("CASE_URN_2", caseUrn2)
                        .put("CASE_URN_3", caseUrn3)
                        .put("HEARING_ID", hearingId)
                        .put("CASE_ID1", randomUUID().toString())
                        .put("CASE_ID2", randomUUID().toString())
                        .put("CASE_ID3", randomUUID().toString())
                        .put("CASE_ID1_STATUS", "INACTIVE")
                        .put("CASE_ID2_STATUS", "INACTIVE")
                        .put("CASE_ID3_STATUS", "ACTIVE")
                        .build());
    }

    private JsonObject getProcessInstances(final JsonArray processInstances, final String businessKey) {
        return !processInstances.isEmpty() && processInstances.getJsonObject(0).getString("businessKey").equals(businessKey)
                ? processInstances.getJsonObject(0)
                : null;
    }
}
