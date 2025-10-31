package uk.gov.moj.cpp.businessprocesses.it;

import static com.github.tomakehurst.wiremock.client.WireMock.reset;
import static com.google.common.collect.ImmutableMap.of;
import static java.util.UUID.fromString;
import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static uk.gov.justice.services.messaging.JsonEnvelope.metadataBuilder;
import static uk.gov.moj.cpp.businessprocesses.helper.BpmRestApiHelper.getProcessInstanceHistoryList;
import static uk.gov.moj.cpp.businessprocesses.helper.BpmRestApiHelper.getProcessInstanceList;
import static uk.gov.moj.cpp.businessprocesses.helper.FileUtil.getFileContentAsJson;
import static uk.gov.moj.cpp.businessprocesses.stub.ReferenceDataServiceStub.stubPublicHolidays;
import static uk.gov.moj.cpp.businessprocesses.stub.ReferenceDataServiceStub.stubWorkQueueForQueueName;
import static uk.gov.moj.cpp.businessprocesses.stub.ReferenceDataServiceStub.stubWorkflowTaskTypes;
import static uk.gov.moj.cpp.businessprocesses.util.WireMockStubUtils.setupAsDeleteBulkPermission;
import static uk.gov.moj.cpp.businessprocesses.util.WireMockStubUtils.setupUsergoupsPermissions;
import static uk.gov.moj.cpp.platform.test.feature.toggle.FeatureStubber.stubFeaturesFor;

import uk.gov.justice.services.messaging.Metadata;
import uk.gov.moj.cpp.businessprocesses.helper.CustomMessageProducerClient;

import java.io.IOException;
import java.util.UUID;

import javax.json.JsonArray;
import javax.json.JsonObject;

import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class CaseResultedIT {

    private static final String PUBLIC_EVENT_HEARING_RESULTED = "public.progression.hearing-resulted";
    private static final String CASE_RESULTED_BPMN_PROCESS_NAME = "case-results";

    private static final String HEARING_RESULTED_ACTIVE_JSON = "hearing-resulted.json";
    private static final String REMOVE_DDJ_ACCESS_TASK_NAME = "Remove_DDJ_from_hearing";

    private static final String USER_ID = randomUUID().toString();
    private static final String CASE_ID1 = randomUUID().toString();
    private static final String CASE_ID2 = randomUUID().toString();
    private static final String CASE_ID3 = randomUUID().toString();
    private static final String PERMISSION_ID1 = randomUUID().toString();
    private static final String PERMISSION_ID2 = randomUUID().toString();
    private final CustomMessageProducerClient publicMessageClient = new CustomMessageProducerClient();

    @BeforeEach
    public void setUp() throws IOException {
        reset();
        stubWorkflowTaskTypes();
        stubWorkQueueForQueueName(fromString("66567e81-931e-337e-a5b3-47e0b221bcc7"), "work_queue_name");
        stubPublicHolidays();
        setupUsergoupsPermissions(PERMISSION_ID1, CASE_ID1);
        setupUsergoupsPermissions(PERMISSION_ID2, CASE_ID2);
        setupAsDeleteBulkPermission();
        publicMessageClient.startProducer("public.event");
    }

    @Test
    public void shouldStartCaseResultedProcessForHearingContaining3CasesAndAutoCompleteRemoveDDJAccessTaskForThe2CasesWithAllFinalResults() {
        final ImmutableMap<String, Boolean> features = of("camunda-progression-hearing-resulted", true);
        stubFeaturesFor("businessprocesses", features);

        final String hearingId = randomUUID().toString();

        final Metadata metadata = metadataBuilder()
                .withName(PUBLIC_EVENT_HEARING_RESULTED)
                .withId(randomUUID())
                .withUserId(USER_ID)
                .withClientCorrelationId(randomUUID().toString())
                .build();

        final JsonObject eventPayload = getEventPayload(HEARING_RESULTED_ACTIVE_JSON, hearingId, CASE_ID1, "INACTIVE", CASE_ID2, "INACTIVE", CASE_ID3, "ACTIVE");

        publicMessageClient.sendMessage(PUBLIC_EVENT_HEARING_RESULTED, eventPayload, metadata);

        // CASE 1 BPMN Process
        final String caseIdHearingIdUUID1 = UUID.nameUUIDFromBytes((hearingId + CASE_ID1).getBytes()).toString();
        final JsonArray processInstanceHistoryList = getProcessInstanceHistoryList(CASE_RESULTED_BPMN_PROCESS_NAME, caseIdHearingIdUUID1);
        assertThat(processInstanceHistoryList.size(), is(1));

        final JsonObject caseResultedProcess1 = processInstanceHistoryList.getJsonObject(0);
        assertThat(caseResultedProcess1.getString("state"), is("ACTIVE"));

        final JsonArray processInstanceList1 = getProcessInstanceList(CASE_RESULTED_BPMN_PROCESS_NAME, caseIdHearingIdUUID1);
        assertThat(processInstanceList1.size(), is(1));
        assertThat(processInstanceList1.getJsonObject(0).getBoolean("ended"), is(false));

        // CASE 2 BPMN Process
        final String caseIdHearingIdUUID2 = UUID.nameUUIDFromBytes((hearingId + CASE_ID2).getBytes()).toString();
        final JsonArray processInstanceHistoryList2 = getProcessInstanceHistoryList(CASE_RESULTED_BPMN_PROCESS_NAME, caseIdHearingIdUUID2);
        assertThat(processInstanceHistoryList2.size(), is(1));

        final JsonObject caseResultedProcess2 = processInstanceHistoryList2.getJsonObject(0);
        assertThat(caseResultedProcess2.getString("state"), is("ACTIVE"));

        final JsonArray processInstanceList2 = getProcessInstanceList(CASE_RESULTED_BPMN_PROCESS_NAME, caseIdHearingIdUUID2);
        assertThat(processInstanceList2.size(), is(1));
        assertThat(processInstanceList2.getJsonObject(0).getBoolean("ended"), is(false));

        // CASE 3 BPMN Process
        final String caseIdHearingIdUUID3 = UUID.nameUUIDFromBytes((hearingId + CASE_ID3).getBytes()).toString();
        final JsonArray processInstanceHistoryList3 = getProcessInstanceHistoryList(CASE_RESULTED_BPMN_PROCESS_NAME, caseIdHearingIdUUID3);
        assertThat(processInstanceHistoryList3.size(), is(1));

        final JsonObject caseResultedProcess3 = processInstanceHistoryList3.getJsonObject(0);
        assertThat(caseResultedProcess3.getString("state"), is("ACTIVE"));

        final JsonArray processInstanceList3 = getProcessInstanceList(CASE_RESULTED_BPMN_PROCESS_NAME, caseIdHearingIdUUID3);
        assertThat(processInstanceList3.size(), is(1));
        assertThat(processInstanceList3.getJsonObject(0).getBoolean("ended"), is(false));

    }

    private JsonObject getEventPayload(final String fileName, final String hearingId, final String caseId1, final String caseId1Status, final String caseId2, final String caseId2Status, final String caseId3, final String caseId3Status) {
        return getFileContentAsJson(fileName,
                ImmutableMap.<String, Object>builder()
                        .put("HEARING_ID", hearingId)
                        .put("CASE_ID1", caseId1)
                        .put("CASE_ID2", caseId2)
                        .put("CASE_ID3", caseId3)
                        .put("CASE_ID1_STATUS", caseId1Status)
                        .put("CASE_ID2_STATUS", caseId2Status)
                        .put("CASE_ID3_STATUS", caseId3Status)
                        .build());
    }
}
