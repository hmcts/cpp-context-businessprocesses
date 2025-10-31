package uk.gov.moj.cpp.businessprocesses.event;

import static java.util.Optional.of;
import static java.util.UUID.randomUUID;
import static org.mockito.Mockito.when;
import static uk.gov.justice.services.messaging.Envelope.metadataBuilder;

import uk.gov.justice.services.core.dispatcher.SystemUserProvider;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.messaging.Metadata;
import uk.gov.moj.cpp.businessprocesses.service.TaskTypeService;
import uk.gov.moj.cpp.progression.json.schemas.event.CotrTaskRequested;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.json.JsonObject;

import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.test.Deployment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class CotrPublicEventsProcessorTest {
    private static final UUID SYSTEM_USER_ID = randomUUID();
    private static final String COTR_CUSTOM_ACTIVITY_TASK_NAME = "cotr_custom_activity";
    @Captor
    private ArgumentCaptor<Envelope<JsonObject>> envelopeArgumentCaptor;
    @InjectMocks
    private CotrEventsProcessor processor;
    @Mock
    private RuntimeService runtimeService;
    @Mock
    private TaskTypeService taskTypeService;
    @Mock
    private SystemUserProvider systemUserProvider;

    @BeforeEach
    public void setup() {
        when(systemUserProvider.getContextSystemUserId()).thenReturn(of(SYSTEM_USER_ID));
    }

    @Test
    @Deployment(resources = {"cotr_custom_task_process.bpmn"})
    public void shouldRaiseReviewNotesUpdatedPublicEvent() {
        when(taskTypeService.getTaskVariablesFromRefData(COTR_CUSTOM_ACTIVITY_TASK_NAME, null)).thenReturn(getTaskReferenceData());
        final UUID cotrId = randomUUID();
        final String value = "Value 1";
        final Metadata eventEnvelopeMetadata = metadataBuilder()
                .withName("public.progression.cotr-task-requested")
                .withId(randomUUID())
                .build();
        final Envelope<CotrTaskRequested> eventEnvelope = Envelope.envelopeFrom(eventEnvelopeMetadata, CotrTaskRequested.cotrTaskRequested()
                .withCotrId(cotrId)
                .withHearingDate("2022-03-08")
                .withCourtCode("CRO001")
                .withOrganisationId(UUID.fromString("1371dfe8-8aa5-47f7-bb76-275b83fc312d"))
                .withComments(value)
                .withRoles("Listing Officer")
                .withTaskName("COTR-Listing Officer Task")
                .withComments("TODO")
                .withNumberOfDays(2)
                .build());
        processor.handleCotrTaskRequested(eventEnvelope);
    }

    private Map<String, Object> getTaskReferenceData() {
        final Map<String, Object> referenceData = new HashMap<>();
        referenceData.put("workQueue", "ApplicationCreatedWorkQueue");
        return referenceData;
    }
}