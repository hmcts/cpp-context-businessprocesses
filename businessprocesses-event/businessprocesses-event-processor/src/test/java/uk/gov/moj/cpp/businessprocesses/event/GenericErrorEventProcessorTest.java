package uk.gov.moj.cpp.businessprocesses.event;

import static java.lang.String.format;
import static java.util.Optional.of;
import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.justice.services.messaging.JsonEnvelope.envelopeFrom;
import static uk.gov.justice.services.test.utils.core.messaging.MetadataBuilderFactory.metadataWithRandomUUID;
import static uk.gov.justice.services.test.utils.core.reflection.ReflectionUtil.setField;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.CASE_DETAILS;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.CASE_URN;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.CORRESPONDENCE_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.COURT_CODES;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.DEEP_LINK;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.DOCUMENT_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.ERROR_CODE;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.HEARING_DATE;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.LAST_UPDATED_BY_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.LAST_UPDATED_BY_NAME;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.NOTE;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.PROSECUTION;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.TASK_TYPE_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.WORK_QUEUE;
import static uk.gov.moj.cpp.businessprocesses.util.FileUtil.getFileContentAsJson;
import static uk.gov.moj.cpp.businessprocesses.util.FileUtil.getPayload;
import static uk.gov.moj.cpp.businessprocesses.util.JsonHelper.getJsonObject;
import static uk.gov.moj.cpp.businessprocesses.util.TestDataProvider.getWorkflowTaskType;

import uk.gov.justice.services.core.dispatcher.SystemUserProvider;
import uk.gov.justice.services.core.featurecontrol.FeatureControlGuard;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.moj.cpp.businessprocesses.service.DueDateCalculationService;
import uk.gov.moj.cpp.businessprocesses.service.ProgressionService;
import uk.gov.moj.cpp.businessprocesses.service.ReferenceDataService;
import uk.gov.moj.cpp.businessprocesses.service.TaskTypeService;
import uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants;
import uk.gov.moj.cpp.businessprocesses.util.TestDataProvider;

import java.io.IOException;
import java.util.Map;
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
public class GenericErrorEventProcessorTest {
    private static final String CORRESPONDENCE_FAILURE_IDENTIFIED_JSON = "public.correspondence.event.outbound-correspondence-notification-failed.json";
    private static final String CASE_VALIDATION_ERROR_IDENTIFIED_JSON = "public.prosecutioncasefile.events.case-validation-failed.json";
    private static final String DEFENDANT_VALIDATION_ERROR_IDENTIFIED_JSON = "public.prosecutioncasefile.events.defendant-validation-failed.json";
    private static final String DEFENDANT_VALIDATION_ERROR_IDENTIFIED__ASS_CODE_JSON = "public.prosecutioncasefile.events.sjp-validation-failed-acccode.json";
    private static final String SJP_VALIDATION_ERROR_IDENTIFIED_JSON = "public.prosecutioncasefile.events.sjp-validation-failed.json";
    private static final String SPI_ERROR_IDENTIFIED_BPMN_PROCESS_NAME = "spi_error_identified";
    private static final String CORRESPONDENCE_FAILURE_BPMN_PROCESS_NAME = "correspondence_failure_identified";
    private static final String CASE_ID_TAG = "caseId";
    private static final String DRAFT_CORRESPONDENCE_FAILURE_ID_TAG = "draftCorrespondenceId";
    private static final String CASE_URN_VALUE = "Qf6RAf7rqp";
    private static final UUID CASE_ID_VALUE = UUID.fromString("b32bb895-9b31-4661-a2a5-89f91e007ca3");
    private static final UUID SYSTEM_USER_ID = randomUUID();
    private static final UUID TASK_TYPE_ID_SPI_ERROR = UUID.fromString("d8165bf4-7c54-49e4-9575-4c055b7e9b5d");
    private static final UUID TASK_TYPE_ID_CORRESPONDENCE_FAILURE = UUID.fromString("ae937624-56be-4be8-a5c0-e45a0fac5072");
    private static final String CANDIDATE_GROUPS = "candidate-groups";
    private static final String TASK_NAME_SPI_ERROR = "SPI_Error";
    private static final String TASK_NAME_CORRESPONDENCE_FAILURE = "Correspondence_failure";
    private static final String CASE_INITIATION_CODE_INVALID = "CASE_INITIATION_CODE_INVALID,";
    private static final String WORK_QUEUE_ID_VALUE = "66567e81-931e-337e-a5b3-47e0b221bcc7";
    private static final String DUE_DATE = "2020-03-05";
    private static final String DEFENDANT_NATIONALITY_INVALID = "DEFENDANT_NATIONALITY_INVALID,";
    private static final String SYSTEM_USER = "SYSTEM";
    private static final String TASK_TYPE_SERVICE = "taskTypeService";
    private static final String DEEP_LINK_SPI_ERROR = "prosecution-casefile/spi-errors/%s?pageNumber=1&urn=%s&pageSize=10";
    private static final String DEEP_LINK_DOCUMENTS = "prosecution-casefile/documents/";
    private static final String COURT_CODE = "B62IZ01";
    private static final String COURT_CENTRE_OUCODE = "B62IZ00";
    private static final String REGION_VALUE = "Wales";
    private static final String COURT_ID_VAL = "9cb28565-d38d-35c1-9044-1f6ac802a669";

    @InjectMocks
    private ErrorHandlerEventProcessor errorHandlerEventProcessor;

    @Mock
    private RuntimeService runtimeService;

    @Captor
    private ArgumentCaptor<Map> variablesMapCaptor;

    @InjectMocks
    private TaskTypeService taskTypeService;

    @Mock
    private SystemUserProvider systemUserProvider;
    @Mock
    private ProgressionService progressionService;

    @Mock
    protected ReferenceDataService referenceDataService;

    @Mock
    private DueDateCalculationService dueDateCalculationService;

    @Mock
    private FeatureControlGuard featureControlGuard;

    @BeforeEach
    public void setup() {
        setField(errorHandlerEventProcessor, TASK_TYPE_SERVICE, taskTypeService);
        when(systemUserProvider.getContextSystemUserId()).thenReturn(of(SYSTEM_USER_ID));
        when(featureControlGuard.isFeatureEnabled(any())).thenReturn(true);
    }

    @Test
    void shouldNotCallHandleCaseValidationFailedWhenFeatureIsDisabled() {
        when(featureControlGuard.isFeatureEnabled("camunda-prosecutioncasefile-events-case-validation-failed")).thenReturn(false);
        JsonEnvelope jsonEnvelope = mock(JsonEnvelope.class);

        errorHandlerEventProcessor.handleCaseValidationFailedProcessor(jsonEnvelope);

        verify(featureControlGuard, times(1)).isFeatureEnabled("camunda-prosecutioncasefile-events-case-validation-failed");
        verifyNoInteractions(jsonEnvelope);
    }

    @Test
    void shouldNotCallHandleDefendantValidationFailedWhenFeatureIsDisabled() {
        when(featureControlGuard.isFeatureEnabled("camunda-prosecutioncasefile-events-defendant-validation-failed")).thenReturn(false);
        JsonEnvelope jsonEnvelope = mock(JsonEnvelope.class);

        errorHandlerEventProcessor.handleDefendantValidationFailedProcessor(jsonEnvelope);

        verify(featureControlGuard, times(1)).isFeatureEnabled("camunda-prosecutioncasefile-events-defendant-validation-failed");
        verifyNoInteractions(jsonEnvelope);
    }

    @Test
    void shouldNotCallHandleSjpValidationFailedWhenFeatureIsDisabled() {
        when(featureControlGuard.isFeatureEnabled("camunda-prosecutioncasefile-events-sjp-validation-failed")).thenReturn(false);
        JsonEnvelope jsonEnvelope = mock(JsonEnvelope.class);

        errorHandlerEventProcessor.handleSjpValidationFailedProcessor(jsonEnvelope);

        verify(featureControlGuard, times(1)).isFeatureEnabled("camunda-prosecutioncasefile-events-sjp-validation-failed");
        verifyNoInteractions(jsonEnvelope);
    }

    @Test
    void shouldNotCallHandleOutboundCorrespondenceNotificationFailedWhenFeatureIsDisabled() {
        when(featureControlGuard.isFeatureEnabled("camunda-correspondence-event-outbound-correspondence-notification-failed")).thenReturn(false);
        JsonEnvelope jsonEnvelope = mock(JsonEnvelope.class);

        errorHandlerEventProcessor.handleOutboundCorrespondenceNotificationFailedProcessor(jsonEnvelope);

        verify(featureControlGuard, times(1)).isFeatureEnabled("camunda-correspondence-event-outbound-correspondence-notification-failed");
        verifyNoInteractions(jsonEnvelope);
    }

    @Test
    public void shouldStartNewSPIErrorIdentifiedProcessWhenSJPValidationFail() throws IOException {

        // Given
        final JsonObject eventPayload = getEventPayload(SJP_VALIDATION_ERROR_IDENTIFIED_JSON, CASE_ID_VALUE.toString(), CASE_ID_TAG);

        // when
        when(referenceDataService.getWorkflowTaskType(anyString())).thenReturn(of(getWorkflowTaskType(TASK_NAME_SPI_ERROR)));
        when(dueDateCalculationService.calculateDueDate(any())).thenReturn(DUE_DATE);
        when(referenceDataService.retrieveCourtCentreDetailsByCourtRoomCode(COURT_CODE)).thenReturn(TestDataProvider.getReferenceDataCourtRoomsPayload());
        when(referenceDataService.retrieveCourtCentreDetailsByCourtId(COURT_ID_VAL)).thenReturn(TestDataProvider.getReferenceDataCourtRoomsPayload());

        errorHandlerEventProcessor.handleSjpValidationFailedProcessor((envelopeFrom(metadataWithRandomUUID("public.prosecutioncasefile.events.sjp-validation-failed"), eventPayload)));

        // Then
        verify(runtimeService, times(1)).startProcessInstanceByKey(Mockito.eq(SPI_ERROR_IDENTIFIED_BPMN_PROCESS_NAME), any(), variablesMapCaptor.capture());

        Map variablesMap = variablesMapCaptor.getValue();
        assertThat(variablesMap.size(), is(16));

        assertThat(TASK_TYPE_ID_SPI_ERROR, is(variablesMap.get(TASK_TYPE_ID)));
        assertNotNull((variablesMap.get(DEEP_LINK)));
        assertThat(variablesMap.get(DEEP_LINK), is(format(DEEP_LINK_SPI_ERROR, CASE_ID_VALUE, CASE_URN_VALUE)));
        assertNull(variablesMap.get(CANDIDATE_GROUPS));
        assertThat(variablesMap.get(CASE_ID_TAG), is("b32bb895-9b31-4661-a2a5-89f91e007ca3"));
        assertThat(variablesMap.get(ProcessVariableConstants.TASK_NAME), is(TASK_NAME_SPI_ERROR));
        assertThat(variablesMap.get(ERROR_CODE), is(DEFENDANT_NATIONALITY_INVALID));
        assertThat(variablesMap.get(NOTE), is(DEFENDANT_NATIONALITY_INVALID));
        assertThat(variablesMap.get(WORK_QUEUE), is(WORK_QUEUE_ID_VALUE));
        assertThat(variablesMap.get(ProcessVariableConstants.DUE_DATE), is(DUE_DATE));
        assertNotNull(variablesMap.get(LAST_UPDATED_BY_ID));
        assertNotNull(variablesMap.get(LAST_UPDATED_BY_NAME));
        assertThat(variablesMap.get(HEARING_DATE), is("1978-02-04T09:05:01.001Z"));

        final JsonObject prosecution = eventPayload.getJsonObject(PROSECUTION);
        final JsonObject caseDetails = prosecution.getJsonObject(CASE_DETAILS);
        final String caseId = caseDetails.getString(CASE_ID_TAG);
        assertThat(variablesMap.get(CASE_ID_TAG), is(caseId));
        assertNotNull(variablesMap.get(ERROR_CODE));
        assertThat(variablesMap.get(COURT_CODES), is(COURT_CENTRE_OUCODE));
    }

    @Test
    public void shouldStartNewSPIErrorIdentifiedProcessWhenCaseValidationFail() throws IOException {

        // Given
        final JsonObject eventPayload = getFileContentAsJson(CASE_VALIDATION_ERROR_IDENTIFIED_JSON);

        // when
        when(referenceDataService.getWorkflowTaskType(anyString())).thenReturn(of(getWorkflowTaskType(TASK_NAME_SPI_ERROR)));
        when(dueDateCalculationService.calculateDueDate(any())).thenReturn(DUE_DATE);
        when(referenceDataService.retrieveCourtCentreDetailsByCourtRoomCode(COURT_CODE)).thenReturn(TestDataProvider.getReferenceDataCourtRoomsPayload());

        errorHandlerEventProcessor.handleCaseValidationFailedProcessor((envelopeFrom(metadataWithRandomUUID("public.prosecutioncasefile.events.case-validation-failed"), eventPayload)));

        // Then
        verify(runtimeService, times(1)).startProcessInstanceByKey(Mockito.eq(SPI_ERROR_IDENTIFIED_BPMN_PROCESS_NAME), any(), variablesMapCaptor.capture());

        Map variablesMap = variablesMapCaptor.getValue();
        assertThat(variablesMap.size(), is(16));

        assertThat(TASK_TYPE_ID_SPI_ERROR, is(variablesMap.get(TASK_TYPE_ID)));
        assertNotNull((variablesMap.get(DEEP_LINK)));
        assertThat(variablesMap.get(DEEP_LINK), is(format(DEEP_LINK_SPI_ERROR, CASE_ID_VALUE, CASE_URN_VALUE)));
        assertNull(variablesMap.get(CANDIDATE_GROUPS));
        assertThat(variablesMap.get(CASE_ID_TAG), is("b32bb895-9b31-4661-a2a5-89f91e007ca3"));
        assertThat(variablesMap.get(ProcessVariableConstants.TASK_NAME), is(TASK_NAME_SPI_ERROR));
        assertThat(variablesMap.get(ERROR_CODE), is(CASE_INITIATION_CODE_INVALID));
        assertThat(variablesMap.get(NOTE), is(CASE_INITIATION_CODE_INVALID));
        assertThat(variablesMap.get(WORK_QUEUE), is(WORK_QUEUE_ID_VALUE));
        assertThat(variablesMap.get(ProcessVariableConstants.DUE_DATE), is(DUE_DATE));
        assertNotNull(variablesMap.get(LAST_UPDATED_BY_ID));
        assertNotNull(variablesMap.get(LAST_UPDATED_BY_NAME));
        assertThat(variablesMap.get(COURT_CODES), is(COURT_CENTRE_OUCODE));
        assertThat(variablesMap.get(HEARING_DATE), is("1978-02-04T09:05:01.001Z"));

    }

    @Test
    public void shouldStartNewSPIErrorIdentifiedProcessWhenDefendantValidationFail() throws IOException {

        // Given
        final JsonObject eventPayload = getFileContentAsJson(DEFENDANT_VALIDATION_ERROR_IDENTIFIED_JSON);

        // when
        when(referenceDataService.getWorkflowTaskType(anyString())).thenReturn(of(getWorkflowTaskType(TASK_NAME_SPI_ERROR)));
        when(dueDateCalculationService.calculateDueDate(any())).thenReturn(DUE_DATE);
        when(referenceDataService.retrieveCourtCentreDetailsByCourtRoomCode(COURT_CODE)).thenReturn(TestDataProvider.getReferenceDataCourtRoomsPayload());

        errorHandlerEventProcessor.handleDefendantValidationFailedProcessor((envelopeFrom(metadataWithRandomUUID("public.prosecutioncasefile.events.defendant-validation-failed"), eventPayload)));

        // Then
        verify(runtimeService, times(1)).startProcessInstanceByKey(Mockito.eq(SPI_ERROR_IDENTIFIED_BPMN_PROCESS_NAME), any(), variablesMapCaptor.capture());

        Map variablesMap = variablesMapCaptor.getValue();
        assertThat(variablesMap.size(), is(16));

        assertThat(variablesMap.get(TASK_TYPE_ID), is(TASK_TYPE_ID_SPI_ERROR));
        assertNotNull((variablesMap.get(DEEP_LINK)));
        assertThat(variablesMap.get(DEEP_LINK), is(format(DEEP_LINK_SPI_ERROR, CASE_ID_VALUE, CASE_URN_VALUE)));
        assertNull(variablesMap.get(CANDIDATE_GROUPS));
        assertThat(variablesMap.get(CASE_ID_TAG), is(CASE_ID_VALUE.toString()));
        assertThat(variablesMap.get(ProcessVariableConstants.TASK_NAME), is(TASK_NAME_SPI_ERROR));
        assertThat(variablesMap.get(ERROR_CODE), is(DEFENDANT_NATIONALITY_INVALID));
        assertThat(variablesMap.get(NOTE), is(DEFENDANT_NATIONALITY_INVALID));
        assertThat(variablesMap.get(WORK_QUEUE), is(WORK_QUEUE_ID_VALUE));
        assertThat(variablesMap.get(ProcessVariableConstants.DUE_DATE), is(DUE_DATE));
        assertNotNull(variablesMap.get(LAST_UPDATED_BY_ID));
        assertNotNull(variablesMap.get(LAST_UPDATED_BY_NAME));
        assertThat(variablesMap.get(COURT_CODES), is(COURT_CENTRE_OUCODE));
        assertThat(variablesMap.get(HEARING_DATE), is("2050-02-04T09:05:01.001Z"));
    }

    @Test
    public void shouldStartNewCorrespondenceFailureIdentifiedProcessWhenCorrespondenceFailure() throws IOException {
        // Given
        final String payload = getPayload(CORRESPONDENCE_FAILURE_IDENTIFIED_JSON)
                .replaceAll("%CASE_ID%", CASE_ID_VALUE.toString());
        final JsonObject eventPayload = getJsonObject(payload);
        final String documentId = eventPayload.getString(DOCUMENT_ID);

        // when
        when(referenceDataService.getWorkflowTaskType(anyString())).thenReturn(of(getWorkflowTaskType(TASK_NAME_CORRESPONDENCE_FAILURE)));
        when(dueDateCalculationService.calculateDueDate(any())).thenReturn(DUE_DATE);
        when(progressionService.getProsecutionCase(any())).thenReturn(TestDataProvider.getJurisdictionType());
        errorHandlerEventProcessor.handleOutboundCorrespondenceNotificationFailedProcessor((envelopeFrom(metadataWithRandomUUID("public.correspondence.event.outbound-correspondence-notification-failed"), eventPayload)));

        // Then
        verify(runtimeService, times(1)).startProcessInstanceByKey(Mockito.eq(CORRESPONDENCE_FAILURE_BPMN_PROCESS_NAME), any(), variablesMapCaptor.capture());

        final Map variablesMap = variablesMapCaptor.getValue();
        assertThat(variablesMap.size(), is(13));
        final String correspondenceId = eventPayload.getString(DRAFT_CORRESPONDENCE_FAILURE_ID_TAG);

        assertThat(variablesMap.get(TASK_TYPE_ID), is(TASK_TYPE_ID_CORRESPONDENCE_FAILURE));
        assertThat(variablesMap.get(DEEP_LINK), is(DEEP_LINK_DOCUMENTS.concat(CASE_ID_VALUE.toString())));
        assertThat(variablesMap.get(CASE_ID_TAG), is(CASE_ID_VALUE.toString()));
        assertNull(variablesMap.get(CANDIDATE_GROUPS));
        assertThat(variablesMap.get(ProcessVariableConstants.TASK_NAME), is(TASK_NAME_CORRESPONDENCE_FAILURE));
        assertThat(variablesMap.get(WORK_QUEUE), is(WORK_QUEUE_ID_VALUE));
        assertThat(variablesMap.get(ProcessVariableConstants.DUE_DATE), is(DUE_DATE));
        assertThat(variablesMap.get(CORRESPONDENCE_ID), is(correspondenceId));
        assertNotNull(variablesMap.get(LAST_UPDATED_BY_ID));
        assertThat(variablesMap.get(LAST_UPDATED_BY_NAME), is(SYSTEM_USER));
        assertThat(variablesMap.get(CASE_URN), is("CLYTBISLEB"));
    }


    @Test
    public void shouldStartNewSPIErrorIdentifiedProcessWhenSJPValidationFailAssCode() throws IOException {

        // Given
        final JsonObject eventPayload = getEventPayload(DEFENDANT_VALIDATION_ERROR_IDENTIFIED__ASS_CODE_JSON, CASE_ID_VALUE.toString(), CASE_ID_TAG);

        // when
        when(referenceDataService.getWorkflowTaskType(anyString())).thenReturn(of(getWorkflowTaskType(TASK_NAME_SPI_ERROR)));
        when(dueDateCalculationService.calculateDueDate(any())).thenReturn(DUE_DATE);
        when(referenceDataService.retrieveCourtCentreDetailsByCourtRoomCode(COURT_CODE)).thenReturn(TestDataProvider.getReferenceDataCourtRoomsPayload());
        when(referenceDataService.retrieveCourtCentreDetailsByCourtId(COURT_ID_VAL)).thenReturn(TestDataProvider.getReferenceDataCourtRoomsPayload());

        errorHandlerEventProcessor.handleSjpValidationFailedProcessor((envelopeFrom(metadataWithRandomUUID("public.prosecutioncasefile.events.sjp-validation-failed"), eventPayload)));

        // Then
        verify(runtimeService, times(1)).startProcessInstanceByKey(Mockito.eq(SPI_ERROR_IDENTIFIED_BPMN_PROCESS_NAME), any(), variablesMapCaptor.capture());

        Map variablesMap = variablesMapCaptor.getValue();
        assertThat(variablesMap.size(), is(16));

        assertThat(TASK_TYPE_ID_SPI_ERROR, is(variablesMap.get(TASK_TYPE_ID)));
        assertNotNull((variablesMap.get(DEEP_LINK)));
        assertThat(variablesMap.get(DEEP_LINK), is(format(DEEP_LINK_SPI_ERROR, CASE_ID_VALUE, CASE_URN_VALUE)));
        assertNull(variablesMap.get(CANDIDATE_GROUPS));
        assertThat(variablesMap.get(CASE_ID_TAG), is("b32bb895-9b31-4661-a2a5-89f91e007ca3"));
        assertThat(variablesMap.get(ProcessVariableConstants.TASK_NAME), is(TASK_NAME_SPI_ERROR));
        assertThat(variablesMap.get(ERROR_CODE), is(DEFENDANT_NATIONALITY_INVALID));
        assertThat(variablesMap.get(NOTE), is(DEFENDANT_NATIONALITY_INVALID));
        assertThat(variablesMap.get(WORK_QUEUE), is(WORK_QUEUE_ID_VALUE));
        assertThat(variablesMap.get(ProcessVariableConstants.DUE_DATE), is(DUE_DATE));
        assertNotNull(variablesMap.get(LAST_UPDATED_BY_ID));
        assertNotNull(variablesMap.get(LAST_UPDATED_BY_NAME));
        assertThat(variablesMap.get(HEARING_DATE), is("1978-02-04T09:05:01.001Z"));

        final JsonObject prosecution = eventPayload.getJsonObject(PROSECUTION);
        final JsonObject caseDetails = prosecution.getJsonObject(CASE_DETAILS);

        final String caseId = caseDetails.getString(CASE_ID_TAG);
        assertThat(variablesMap.get(CASE_ID_TAG), is(caseId));
        assertNotNull(variablesMap.get(ERROR_CODE));
        assertThat(variablesMap.get(COURT_CODES), is(COURT_CENTRE_OUCODE));
    }

    private JsonObject getEventPayload(final String fileName, final String id, final String idType) {
        return getFileContentAsJson(fileName,
                ImmutableMap.<String, Object>builder()
                        .put(idType, id)
                        .build());
    }
}
