package uk.gov.moj.cpp.businessprocesses.it.opa;

import static com.github.tomakehurst.wiremock.client.WireMock.reset;
import static com.google.common.collect.ImmutableMap.of;
import static java.util.UUID.fromString;
import static java.util.UUID.randomUUID;
import static uk.gov.justice.services.messaging.JsonEnvelope.metadataBuilder;
import static uk.gov.moj.cpp.businessprocesses.helper.BpmRestApiHelper.getProcessInstanceList;
import static uk.gov.moj.cpp.businessprocesses.helper.BpmRestApiHelper.getTaskList;
import static uk.gov.moj.cpp.businessprocesses.it.QueueUtil.publicEvents;
import static uk.gov.moj.cpp.businessprocesses.it.QueueUtil.sendMessage;
import static uk.gov.moj.cpp.businessprocesses.stub.ReferenceDataServiceStub.stubPublicHolidays;
import static uk.gov.moj.cpp.businessprocesses.stub.ReferenceDataServiceStub.stubWorkQueueForQueueName;
import static uk.gov.moj.cpp.businessprocesses.stub.ReferenceDataServiceStub.stubWorkflowTaskTypes;
import static uk.gov.moj.cpp.platform.test.feature.toggle.FeatureStubber.stubFeaturesFor;

import uk.gov.justice.services.common.converter.ObjectToJsonValueConverter;
import uk.gov.justice.services.common.converter.jackson.ObjectMapperProducer;
import uk.gov.justice.services.messaging.Metadata;
import uk.gov.moj.cpp.businessprocesses.helper.BpmRestApiHelper;
import uk.gov.moj.cpp.progression.json.schemas.event.OpaTaskRequested;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.json.JsonObject;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class OpaWorkFlowIT {
    private static final String OPA_CUSTOM_ACTIVITY_TASK_NAME = "opa_custom_activity";
    private static final String OPA_CUSTOM_TASK_PROCESS_PROCESS_NAME = "opa_custom_task_process";
    private static final String PUBLIC_DEFENCE_OPA_TASK_REQUESTED = "public.defence.opa-task-requested";
    private final MessageProducer publicMessageClient = publicEvents.createProducer();
    private static final String USER_ID = randomUUID().toString();
    private final ObjectMapper objectMapper = new ObjectMapperProducer().objectMapper();
    private final ObjectToJsonValueConverter objectToJsonValueConverter = new ObjectToJsonValueConverter(objectMapper);
    public static final String TASK_NAME = "List case for Further Case Management Task";

    @BeforeEach
    public void setUp() throws IOException {
        reset();
        stubWorkflowTaskTypes();
        stubWorkQueueForQueueName(fromString("66567e81-931e-337e-a5b3-47e0b221bcc7"), "work_queue_name");
        stubPublicHolidays();
    }

    @AfterEach
    public void tearDown() throws JMSException {
        publicMessageClient.close();
    }

    @Test
    public void shouldTestReviewNoteUpdateWorkFlow() {
        final UUID opaId = randomUUID();
        final Metadata metadata = metadataBuilder()
                .withName(PUBLIC_DEFENCE_OPA_TASK_REQUESTED)
                .withId(randomUUID())
                .withUserId(USER_ID)
                .build();
        final OpaTaskRequested opaReviewNotesUpdated = OpaTaskRequested.opaTaskRequested()
                .withOpaId(opaId)
                .withCourtCode("ODCODE")
                .withTaskName(TASK_NAME)
                .withOrganisationId(UUID.fromString("1371dfe8-8aa5-47f7-bb76-275b83fc312d"))
                .withRoles("Listing Officer, Admin")
                .withNumberOfDays(4)
                .build();
        final JsonObject payload = (JsonObject) objectToJsonValueConverter.convert(opaReviewNotesUpdated);
        sendMessage(publicMessageClient, PUBLIC_DEFENCE_OPA_TASK_REQUESTED, payload, metadata);
        final List<String> processInstanceList = getProcessInstanceList(OPA_CUSTOM_TASK_PROCESS_PROCESS_NAME);
        final String processInstanceId = processInstanceList.get(processInstanceList.size() - 1);
        MatcherAssert.assertThat(processInstanceId, CoreMatchers.is(CoreMatchers.notNullValue()));
        final JsonObject taskDetails = getTaskList(processInstanceId).stream()
                .findFirst()
                .map(BpmRestApiHelper::getTaskDetails)
                .orElse(null);
        MatcherAssert.assertThat(taskDetails.getBoolean("suspended"), CoreMatchers.is(false));
        MatcherAssert.assertThat(taskDetails.getString("processDefinitionId"), CoreMatchers.is(CoreMatchers.notNullValue()));
        MatcherAssert.assertThat(taskDetails.getString("name"), CoreMatchers.is(TASK_NAME));
        MatcherAssert.assertThat(taskDetails.getString("id"), CoreMatchers.is(CoreMatchers.notNullValue()));
        MatcherAssert.assertThat(taskDetails.getString("taskDefinitionKey"), CoreMatchers.is(OPA_CUSTOM_ACTIVITY_TASK_NAME));
    }

}