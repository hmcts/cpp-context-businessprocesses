package uk.gov.moj.cpp.businessprocesses.event;

import static java.util.Optional.of;
import static java.util.UUID.randomUUID;
import static javax.json.Json.createObjectBuilder;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.justice.services.messaging.JsonEnvelope.envelopeFrom;
import static uk.gov.justice.services.test.utils.core.messaging.MetadataBuilderFactory.metadataWithRandomUUID;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.DEEP_LINK;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.DUE_DATE;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.WORK_QUEUE;
import static uk.gov.moj.cpp.businessprocesses.util.FileUtil.getFileContentAsJson;

import uk.gov.justice.services.core.dispatcher.SystemUserProvider;
import uk.gov.justice.services.core.featurecontrol.FeatureControlGuard;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.moj.cpp.businessprocesses.service.ProgressionService;
import uk.gov.moj.cpp.businessprocesses.service.ReferenceDataService;
import uk.gov.moj.cpp.businessprocesses.service.TaskTypeService;
import uk.gov.moj.cpp.businessprocesses.util.TestDataProvider;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.json.JsonObject;

import com.google.common.collect.ImmutableMap;
import org.camunda.bpm.engine.RuntimeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class DocumentEventProcessorTest {
    private static final String COURT_DOCUMENT_ADDED_JSON = "public.progression.document-added.json";
    private static final String TRIAGE_INCOMING_DOCUMENT_IDENTIFIED = "triage_incoming_document_identified";
    private static final String TRIAGE_INCOMING_DOCUMENT_TASK_NAME = "Triage_incoming_document";
    private static final String CASE_ID = "3aa6d35d-70c3-45fb-a05e-bfedbce16412";
    private static final String DOCUMENT_TYPE_ID = "0bb7b276-9dc0-4af2-83b9-f4acef0c7898";
    private static final String SECTION = "section";
    private static final String ID = "id";
    private static final String SECTION_PLEA = "Plea";
    private static final String SECTION_OTHER = "other";
    private static final UUID SYSTEM_USER_ID = randomUUID();
    private static final String LAST_UPDATED_BY_ID = "lastUpdatedByID";
    private static final String LAST_UPDATED_BY_NAME = "lastUpdatedByName";

    @InjectMocks
    private DocumentEventProcessor documentEventProcessor;

    @Mock
    private RuntimeService runtimeService;

    @Captor
    private ArgumentCaptor<Map> processVariablesCaptor;

    @Mock
    private TaskTypeService taskTypeService;

    @Mock
    private ReferenceDataService referenceDataService;

    @Mock
    private ProgressionService progressionService;

    @Mock
    private SystemUserProvider systemUserProvider;

    @Mock
    private FeatureControlGuard featureControlGuard;

    @BeforeEach
    public void setup() {
        when(featureControlGuard.isFeatureEnabled(any())).thenReturn(true);
    }

    @Test
    void shouldNotCallHandleDocumentAddedWhenFeatureIsDisabled() {
        when(featureControlGuard.isFeatureEnabled("camunda-progression-document-added")).thenReturn(false);
        JsonEnvelope jsonEnvelope = mock(JsonEnvelope.class);

        documentEventProcessor.handleDocumentAddedProcessor(jsonEnvelope);

        verify(featureControlGuard, times(1)).isFeatureEnabled("camunda-progression-document-added");
        verifyNoInteractions(jsonEnvelope);
    }

    @Test
    public void shouldStartTriageIncomingProcessWithPlea() throws IOException {
        when(taskTypeService.getTaskVariablesFromRefData(TRIAGE_INCOMING_DOCUMENT_TASK_NAME, CASE_ID)).thenReturn(getTaskReferenceData(CASE_ID));
        when(referenceDataService.getDocumentType(any(), any())).thenReturn(getDocumentTypeResponse(SECTION_PLEA));
        when(progressionService.getProsecutionCase(any())).thenReturn(TestDataProvider.getJurisdictionType());
        when(systemUserProvider.getContextSystemUserId()).thenReturn(of(SYSTEM_USER_ID));
        final JsonObject eventPayload = getEventPayload(COURT_DOCUMENT_ADDED_JSON, DOCUMENT_TYPE_ID, CASE_ID);

        documentEventProcessor.handleDocumentAddedProcessor((envelopeFrom(metadataWithRandomUUID("public.progression.court-document-added"), eventPayload)));

        verify(runtimeService, times(1)).startProcessInstanceByKey(Mockito.eq(TRIAGE_INCOMING_DOCUMENT_IDENTIFIED), Mockito.eq(CASE_ID), processVariablesCaptor.capture());

        Map processVariables = processVariablesCaptor.getValue();
        assertThat(processVariables.get("caseId"), is(eventPayload.getString("caseId")));
        assertThat(processVariables.get(DEEP_LINK), is("https://dummyUrl/prosecution-casefile/case-at-a-glance/".concat(CASE_ID)));
        assertThat(processVariables.get(LAST_UPDATED_BY_ID), is(SYSTEM_USER_ID.toString()));
        assertThat(processVariables.get(LAST_UPDATED_BY_NAME), is("SYSTEM"));
        assertThat(processVariables.get(WORK_QUEUE), is("5cd9bd67-1f08-315b-9608-8eb7ebb7ea2f"));

        verify(referenceDataService).getDocumentType(any(), any());
        verify(progressionService).getProsecutionCase(any());
    }

    @Test
    public void shouldStartTriageIncomingProcessWithOtherSection() {
        when(referenceDataService.getDocumentType(any(), any())).thenReturn(getDocumentTypeResponse(SECTION_OTHER));

        final JsonObject eventPayload = getEventPayload(COURT_DOCUMENT_ADDED_JSON, DOCUMENT_TYPE_ID, CASE_ID);

        documentEventProcessor.handleDocumentAddedProcessor((envelopeFrom(metadataWithRandomUUID("public.progression.court-document-added"), eventPayload)));

        verify(runtimeService, times(0)).startProcessInstanceByKey(Mockito.eq(TRIAGE_INCOMING_DOCUMENT_IDENTIFIED), Mockito.eq(CASE_ID), processVariablesCaptor.capture());

    }

    private JsonObject getEventPayload(final String fileName, final String documentTypeId, final String caseId) {
        return getFileContentAsJson(fileName,
                ImmutableMap.<String, Object>builder()
                        .put("CASE_ID", caseId)
                        .put("DOCUMENT_TYPE_ID", documentTypeId)
                        .build());
    }

    private Map<String, Object> getTaskReferenceData(final String referenceId) {
        final Map<String, Object> referenceData = new HashMap<>();
        referenceData.put(DEEP_LINK, "https://dummyUrl/prosecution-casefile/case-at-a-glance/".concat(referenceId));
        referenceData.put(WORK_QUEUE, "Crown Court Admin");
        referenceData.put(DUE_DATE, "${now()+2d}");
        return referenceData;
    }

    private Optional<JsonObject> getDocumentTypeResponse(final String section) {
        return Optional.of(createObjectBuilder()
                .add(ID, "e7be77a9-9ac5-431d-a23b-2d750e441b75")
                .add(SECTION, section)
                .build());
    }

}
