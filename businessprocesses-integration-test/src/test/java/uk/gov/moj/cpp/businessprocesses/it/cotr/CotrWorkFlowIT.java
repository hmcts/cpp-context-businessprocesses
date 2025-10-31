package uk.gov.moj.cpp.businessprocesses.it.cotr;

import static com.github.tomakehurst.wiremock.client.WireMock.reset;
import static com.google.common.collect.ImmutableMap.of;
import static java.util.UUID.fromString;
import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import uk.gov.moj.cpp.progression.json.schemas.event.CotrTaskRequested;

import java.io.IOException;
import java.time.LocalDate;
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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled
public class CotrWorkFlowIT {
    private static final String PUBLIC_PROGRESSION_COTR_TASK_REQUESTED = "public.progression.cotr-task-requested";
    private static final String COTR_CUSTOM_ACTIVITY_TASK_NAME = "cotr_custom_activity";
    private static final String COTR_CUSTOM_TASK_PROCESS_PROCESS_NAME = "cotr_custom_task_process";
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
        final UUID cotrId = randomUUID();
        final String value1 = "Value 1";
        final Metadata metadata = metadataBuilder()
                .withName(PUBLIC_PROGRESSION_COTR_TASK_REQUESTED)
                .withId(randomUUID())
                .withUserId(USER_ID)
                .build();
        final CotrTaskRequested cotrReviewNotesUpdated = CotrTaskRequested.cotrTaskRequested()
                .withCotrId(cotrId)
                .withHearingDate(LocalDate.now().toString())
                .withCourtCode("ODCODE")
                .withTaskName(TASK_NAME)
                .withOrganisationId(UUID.fromString("1371dfe8-8aa5-47f7-bb76-275b83fc312d"))
                .withRoles("Listing Officer, Admin")
                .withComments(value1)
                .withNumberOfDays(4)
                .build();
        final JsonObject payload = (JsonObject) objectToJsonValueConverter.convert(cotrReviewNotesUpdated);
        sendMessage(publicMessageClient, PUBLIC_PROGRESSION_COTR_TASK_REQUESTED, payload, metadata);
        final List<String> processInstanceList = getProcessInstanceList(COTR_CUSTOM_TASK_PROCESS_PROCESS_NAME);
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
        MatcherAssert.assertThat(taskDetails.getString("taskDefinitionKey"), CoreMatchers.is(COTR_CUSTOM_ACTIVITY_TASK_NAME));
    }

}