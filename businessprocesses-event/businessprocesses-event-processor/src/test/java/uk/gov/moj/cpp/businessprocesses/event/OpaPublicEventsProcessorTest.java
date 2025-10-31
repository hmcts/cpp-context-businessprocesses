package uk.gov.moj.cpp.businessprocesses.event;

import static java.util.Optional.of;
import static java.util.UUID.randomUUID;
import static org.mockito.Mockito.when;
import static uk.gov.justice.services.messaging.Envelope.metadataBuilder;

import uk.gov.justice.services.core.dispatcher.SystemUserProvider;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.messaging.Metadata;
import uk.gov.moj.cpp.businessprocesses.service.TaskTypeService;
import uk.gov.moj.cpp.progression.json.schemas.event.OpaTaskRequested;

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
public class OpaPublicEventsProcessorTest {
    private static final UUID SYSTEM_USER_ID = randomUUID();
    private static final String OPA_CUSTOM_ACTIVITY_TASK_NAME = "opa_custom_activity";
    @Captor
    private ArgumentCaptor<Envelope<JsonObject>> envelopeArgumentCaptor;
    @InjectMocks
    private OpaEventsProcessor processor;
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
    @Deployment(resources = {"opa_custom_task_process.bpmn"})
    public void shouldRaiseReviewNotesUpdatedPublicEvent() {
        when(taskTypeService.getTaskVariablesFromRefData(OPA_CUSTOM_ACTIVITY_TASK_NAME, null)).thenReturn(getTaskReferenceData());
        final UUID opaId = randomUUID();
        final Metadata eventEnvelopeMetadata = metadataBuilder()
                .withName("public.defence.opa-task-requested")
                .withId(randomUUID())
                .build();
        final Envelope<OpaTaskRequested> eventEnvelope = Envelope.envelopeFrom(eventEnvelopeMetadata, OpaTaskRequested.opaTaskRequested()
                .withOpaId(opaId)
                .withCourtCode("CRO001")
                .withOrganisationId(UUID.fromString("1371dfe8-8aa5-47f7-bb76-275b83fc312d"))
                .withRoles("Listing Officer")
                .withTaskName("OPA-Listing Officer Task")
                .withNumberOfDays(2)
                .build());
        processor.handleOpaTaskRequestEvent(eventEnvelope);
    }

    private Map<String, Object> getTaskReferenceData() {
        final Map<String, Object> referenceData = new HashMap<>();
        referenceData.put("workQueue", "ApplicationCreatedWorkQueue");
        return referenceData;
    }
}