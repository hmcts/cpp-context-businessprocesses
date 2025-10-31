package uk.gov.moj.cpp.businessprocesses.event;


import static java.util.Optional.of;
import static java.util.UUID.randomUUID;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.SPACE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.justice.services.messaging.JsonEnvelope.envelopeFrom;
import static uk.gov.justice.services.test.utils.core.messaging.MetadataBuilderFactory.metadataWithRandomUUID;
import static uk.gov.moj.cpp.businessprocesses.util.FileUtil.getFileContentAsJson;

import uk.gov.justice.services.core.dispatcher.SystemUserProvider;
import uk.gov.justice.services.core.featurecontrol.FeatureControlGuard;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.moj.cpp.businessprocesses.event.summonsapplication.SummonsApplicationHandler;
import uk.gov.moj.cpp.businessprocesses.service.TaskTypeService;
import uk.gov.moj.cpp.businessprocesses.shared.InterpreterForWelshActivityHandler;
import uk.gov.moj.cpp.businessprocesses.shared.NotesGenerator;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.json.JsonArray;
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
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class HearingInitiatedEventProcessorTest {

    private static final String HEARING_INIT_CASE_JSON = "hearingInitiated-caseDetails.json";
    private static final String HEARING_INIT_APPLICATION_JSON = "hearingInitiated-applicationDetails.json";

    private static final String HEARING_LISTED_BPMN_PROCESS_NAME = "hearing-listed";

    // Fields
    private static final String BOOK_INTERPRETER_FIELD = "hasInterpreter";
    private static final String HAS_CASE_ID_FIELD = "hasCaseId";
    private static final String APPLICATION_ID_FIELD = "applicationId";
    private static final String APPLICATION_REF_FIELD = "applicationReference";
    private static final String HEARING_DATE_TIME_FIELD = "hearingDateTime";

    private static final String BOOK_INTERPRETER_CASE_TASK_ID = "Book_interpreter_case";
    private static final String BOOK_INTERPRETER_APPLICATION_CASE_TASK_ID = "Book_interpreter_application";

    private static final String CASE_ID = "caseId";
    private static final String CASE_URN = "caseURN";
    private static final String DEEP_LINK = "deepLink";
    private static final String DEFENDANT_ID = "defendantId";
    private static final String DEFENDANT_NAME = "defendantName";
    private static final String JURISDICTION = "jurisdiction";
    private static final String WORK_QUEUE = "workQueue";
    private static final String HEARING_DATE = "hearingDate";
    private static final String HEARING_ID = "hearingId";
    private static final String NOTE = "note";

    private static final String LAST_UPDATED_BY_ID = "lastUpdatedByID";
    private static final String LAST_UPDATED_BY_NAME = "lastUpdatedByName";

    // Values
    private static final String CASE_ID1 = randomUUID().toString();
    private static final String CASE_ID2 = randomUUID().toString();
    private static final String APPLICATION_ID = randomUUID().toString();
    private static final String WORK_QUEUE_ID = "89058c86-ddc3-441c-94bd-c21c6a1e5d80";
    private static final String CASE_DEEP_LINK_URL = "https://dummyUrl/prosecution-casefile/case-at-a-glance/";
    private static final String APPLICATION_DEEP_LINK_URL = "https://dummyUrl/prosecution-casefile/application-at-a-glance/";
    private static final String JURISDICTION_TYPE_MAGISTRATES = "MAGISTRATES";
    private static final String CROWN_COURT_ADMIN_WORK_QUEUE_ID = "5cd9bd67-1f08-315b-9608-8eb7ebb7ea2f";
    private static final String JURISDICTION_TYPE_CROWN = "CROWN";
    private static final String CASE_URN1 = "TFL34652354";
    private static final String CASE_URN2 = "TFL12346523";
    private static final String APPLICATION_REF1 = "TFL34652354";
    private static final String APPLICATION_REF2 = "TFL12346523";
    private static final UUID SYSTEM_USER_ID = randomUUID();
    private static final String SYSTEM_USER_NAME = "SYSTEM";

    @InjectMocks
    private HearingInitiatedEventProcessor hearingInitiatedEventProcessor;

    @Mock
    private RuntimeService runtimeService;

    @Mock
    private TaskTypeService taskTypeService;

    @Captor
    private ArgumentCaptor<Map> processVariablesCaptor;

    @Mock
    private SystemUserProvider systemUserProvider;

    @Mock
    private FeatureControlGuard featureControlGuard;

    @Mock
    private InterpreterForWelshActivityHandler interpreterForWelshActivityHandler;

    @Mock
    private SummonsApplicationHandler summonsApplicationHandler;

    @Spy
    private NotesGenerator notesGenerator;

    private static String getPrefix(final String taskName) {
        return taskName + "_";
    }

    @BeforeEach
    public void setup() {
        when(featureControlGuard.isFeatureEnabled(any())).thenReturn(true);
    }

    @Test
    void shouldNotCallHandleHearingInitiatedWhenFeatureIsDisabled() {
        when(featureControlGuard.isFeatureEnabled("camunda-hearing-initiated")).thenReturn(false);
        JsonEnvelope jsonEnvelope = mock(JsonEnvelope.class);

        hearingInitiatedEventProcessor.handleHearingInitiatedProcessor(jsonEnvelope);

        verify(featureControlGuard, times(1)).isFeatureEnabled("camunda-hearing-initiated");
        verify(interpreterForWelshActivityHandler, never()).handleWelshInterpreterForCaseInitiated(any());
        verifyNoInteractions(jsonEnvelope);
    }

    @Test
    public void shouldStartHearingInitiatedProcessForMultipleCasesWithInterpretersInMagistratesCourt() {

        // Given
        final JsonObject eventPayload = getEventPayload(HEARING_INIT_CASE_JSON, CASE_ID1, "CASE_ID", JURISDICTION_TYPE_MAGISTRATES, "Welsh", "French", "", "");

        // when
        when(taskTypeService.getTaskVariablesFromRefDataWithPrefix(BOOK_INTERPRETER_CASE_TASK_ID, CASE_ID1, getPrefix(BOOK_INTERPRETER_CASE_TASK_ID), "2022-08-24T01:46:36.068Z")).
                thenReturn(getTaskTypes(CASE_ID1, CASE_DEEP_LINK_URL, BOOK_INTERPRETER_CASE_TASK_ID));
        when(systemUserProvider.getContextSystemUserId()).thenReturn(of(SYSTEM_USER_ID));
        hearingInitiatedEventProcessor.handleHearingInitiatedProcessor((envelopeFrom(metadataWithRandomUUID("public.hearing.trial-vacated"), eventPayload)));

        // Then
        verify(runtimeService, times(1)).startProcessInstanceByKey(Mockito.eq(HEARING_LISTED_BPMN_PROCESS_NAME), Mockito.eq(eventPayload.getString(HEARING_ID)), processVariablesCaptor.capture());

        verify(interpreterForWelshActivityHandler).handleWelshInterpreterForCaseInitiated(any());
        Map processVariables = processVariablesCaptor.getValue();
        assertThat(processVariables.size(), is(14));

        assertThat(processVariables.get(BOOK_INTERPRETER_FIELD), is(true));
        assertThat(processVariables.get(HAS_CASE_ID_FIELD), is(true));
        assertThat(processVariables.get(HEARING_ID), is(eventPayload.getString(HEARING_ID)));
        assertThat(processVariables.get(HEARING_DATE), is(eventPayload.getString(HEARING_DATE_TIME_FIELD)));
        assertThat(processVariables.get(CASE_ID), is(eventPayload.getJsonArray("caseDetails").getJsonObject(0).getString("caseId")));
        assertThat(processVariables.get(CASE_URN), is(eventPayload.getJsonArray("caseDetails").getJsonObject(0).getString("caseUrn")));
        final JsonArray defendants = eventPayload.getJsonArray("caseDetails").getJsonObject(0).getJsonArray("defendantDetails");
        assertThat(processVariables.get(DEFENDANT_ID), is(defendants.getJsonObject(0).getString(DEFENDANT_ID)));
        final String defendantFirstName = defendants.getJsonObject(0).getString("defendantFirstName");
        final String defendantLastName = defendants.getJsonObject(0).getString("defendantLastName");
        assertThat(processVariables.get(DEFENDANT_NAME), is(defendantFirstName + SPACE + defendantLastName));
        assertThat(processVariables.get(LAST_UPDATED_BY_ID), is(SYSTEM_USER_ID.toString()));
        assertThat(processVariables.get(LAST_UPDATED_BY_NAME), is(SYSTEM_USER_NAME));
        assertThat(processVariables.get(JURISDICTION), is(JURISDICTION_TYPE_MAGISTRATES));
        assertThat(processVariables.get(WORK_QUEUE), is(WORK_QUEUE_ID));
        assertThat(processVariables.get(getPrefix(BOOK_INTERPRETER_CASE_TASK_ID) + DEEP_LINK), is(CASE_DEEP_LINK_URL.concat(CASE_ID1)));
        assertThat(processVariables.get(NOTE), is("[ TFL34652354 = Abbie ARMSTRONG : Welsh,John Doe : French ][ TFL12346523 = John Doe : French ]"));

    }

    @Test
    public void shouldStartHearingInitiatedProcessForMultipleCasesWithOneInterpreterInMagistratesCourt() {

        // Given
        final JsonObject eventPayload = getEventPayload(HEARING_INIT_CASE_JSON, CASE_ID1, "CASE_ID", JURISDICTION_TYPE_MAGISTRATES, "Welsh", "", "", "");

        // when
        when(taskTypeService.getTaskVariablesFromRefDataWithPrefix(BOOK_INTERPRETER_CASE_TASK_ID, CASE_ID1, getPrefix(BOOK_INTERPRETER_CASE_TASK_ID), "2022-08-24T01:46:36.068Z")).
                thenReturn(getTaskTypes(CASE_ID1, CASE_DEEP_LINK_URL, BOOK_INTERPRETER_CASE_TASK_ID));
        when(systemUserProvider.getContextSystemUserId()).thenReturn(of(SYSTEM_USER_ID));
        hearingInitiatedEventProcessor.handleHearingInitiatedProcessor((envelopeFrom(metadataWithRandomUUID("public.hearing.trial-vacated"), eventPayload)));

        // Then
        verify(runtimeService, times(1)).startProcessInstanceByKey(Mockito.eq(HEARING_LISTED_BPMN_PROCESS_NAME), Mockito.eq(eventPayload.getString(HEARING_ID)), processVariablesCaptor.capture());
        verify(interpreterForWelshActivityHandler).handleWelshInterpreterForCaseInitiated(any());
        Map processVariables = processVariablesCaptor.getValue();
        assertThat(processVariables.size(), is(14));

        assertThat(processVariables.get(BOOK_INTERPRETER_FIELD), is(true));
        assertThat(processVariables.get(HAS_CASE_ID_FIELD), is(true));
        assertThat(processVariables.get(HEARING_ID), is(eventPayload.getString(HEARING_ID)));
        assertThat(processVariables.get(HEARING_DATE), is(eventPayload.getString(HEARING_DATE_TIME_FIELD)));
        assertThat(processVariables.get(CASE_ID), is(eventPayload.getJsonArray("caseDetails").getJsonObject(0).getString("caseId")));
        assertThat(processVariables.get(CASE_URN), is(eventPayload.getJsonArray("caseDetails").getJsonObject(0).getString("caseUrn")));
        final JsonArray defendants = eventPayload.getJsonArray("caseDetails").getJsonObject(0).getJsonArray("defendantDetails");
        assertThat(processVariables.get(DEFENDANT_ID), is(defendants.getJsonObject(0).getString(DEFENDANT_ID)));
        final String defendantFirstName = defendants.getJsonObject(0).getString("defendantFirstName");
        final String defendantLastName = defendants.getJsonObject(0).getString("defendantLastName");
        assertThat(processVariables.get(DEFENDANT_NAME), is(defendantFirstName + SPACE + defendantLastName));
        assertThat(processVariables.get(LAST_UPDATED_BY_ID), is(SYSTEM_USER_ID.toString()));
        assertThat(processVariables.get(LAST_UPDATED_BY_NAME), is(SYSTEM_USER_NAME));
        assertThat(processVariables.get(JURISDICTION), is(JURISDICTION_TYPE_MAGISTRATES));
        assertThat(processVariables.get(WORK_QUEUE), is(WORK_QUEUE_ID));
        assertThat(processVariables.get(getPrefix(BOOK_INTERPRETER_CASE_TASK_ID) + DEEP_LINK), is(CASE_DEEP_LINK_URL.concat(CASE_ID1)));
        assertThat(processVariables.get(NOTE), is("[ TFL34652354 = Abbie ARMSTRONG : Welsh ]"));
    }


    @Test
    public void shouldStartHearingInitiatedProcessForMultipleCasesWithOutInterpreterInMagistratesCourt() {
        // Given
        final JsonObject eventPayload = getEventPayload(HEARING_INIT_CASE_JSON, CASE_ID1, "CASE_ID", JURISDICTION_TYPE_MAGISTRATES, "", "", "", "");

        // when
        when(taskTypeService.getTaskVariablesFromRefDataWithPrefix(BOOK_INTERPRETER_CASE_TASK_ID, CASE_ID1, getPrefix(BOOK_INTERPRETER_CASE_TASK_ID), "2022-08-24T01:46:36.068Z")).
                thenReturn(getTaskTypes(CASE_ID1, CASE_DEEP_LINK_URL, BOOK_INTERPRETER_CASE_TASK_ID));
        when(systemUserProvider.getContextSystemUserId()).thenReturn(of(SYSTEM_USER_ID));
        hearingInitiatedEventProcessor.handleHearingInitiatedProcessor((envelopeFrom(metadataWithRandomUUID("public.hearing.trial-vacated"), eventPayload)));

        // Then
        verify(runtimeService, times(1)).startProcessInstanceByKey(Mockito.eq(HEARING_LISTED_BPMN_PROCESS_NAME), Mockito.eq(eventPayload.getString(HEARING_ID)), processVariablesCaptor.capture());
        verify(interpreterForWelshActivityHandler).handleWelshInterpreterForCaseInitiated(any());
        Map processVariables = processVariablesCaptor.getValue();
        assertThat(processVariables.size(), is(14));

        assertThat(processVariables.get(BOOK_INTERPRETER_FIELD), is(false));
        assertThat(processVariables.get(HAS_CASE_ID_FIELD), is(true));
        assertThat(processVariables.get(HEARING_ID), is(eventPayload.getString(HEARING_ID)));
        assertThat(processVariables.get(HEARING_DATE), is(eventPayload.getString(HEARING_DATE_TIME_FIELD)));
        assertThat(processVariables.get(CASE_ID), is(eventPayload.getJsonArray("caseDetails").getJsonObject(0).getString("caseId")));
        assertThat(processVariables.get(CASE_URN), is(eventPayload.getJsonArray("caseDetails").getJsonObject(0).getString("caseUrn")));
        final JsonArray defendants = eventPayload.getJsonArray("caseDetails").getJsonObject(0).getJsonArray("defendantDetails");
        assertThat(processVariables.get(DEFENDANT_ID), is(defendants.getJsonObject(0).getString(DEFENDANT_ID)));
        final String defendantFirstName = defendants.getJsonObject(0).getString("defendantFirstName");
        final String defendantLastName = defendants.getJsonObject(0).getString("defendantLastName");
        assertThat(processVariables.get(DEFENDANT_NAME), is(defendantFirstName + SPACE + defendantLastName));
        assertThat(processVariables.get(LAST_UPDATED_BY_ID), is(SYSTEM_USER_ID.toString()));
        assertThat(processVariables.get(LAST_UPDATED_BY_NAME), is(SYSTEM_USER_NAME));
        assertThat(processVariables.get(JURISDICTION), is(JURISDICTION_TYPE_MAGISTRATES));
        assertThat(processVariables.get(WORK_QUEUE), is(WORK_QUEUE_ID));
        assertThat(processVariables.get(getPrefix(BOOK_INTERPRETER_CASE_TASK_ID) + DEEP_LINK), is(CASE_DEEP_LINK_URL.concat(CASE_ID1)));
        assertThat(processVariables.get(NOTE), is(EMPTY));
    }

    @Test
    public void shouldStartHearingInitiatedProcessForMultipleCasesWithInterpretersInCrownCourt() {
        when(taskTypeService.getTaskVariablesFromRefDataWithPrefix(BOOK_INTERPRETER_CASE_TASK_ID, CASE_ID1, getPrefix(BOOK_INTERPRETER_CASE_TASK_ID), "2022-08-24T01:46:36.068Z")).
                thenReturn(getTaskTypes(CASE_ID1, CASE_DEEP_LINK_URL, BOOK_INTERPRETER_CASE_TASK_ID));
        when(systemUserProvider.getContextSystemUserId()).thenReturn(of(SYSTEM_USER_ID));
        // Given
        final JsonObject eventPayload = getEventPayload(HEARING_INIT_CASE_JSON, CASE_ID1, "CASE_ID", JURISDICTION_TYPE_CROWN, "Welsh", "French", "", "");

        // when
        hearingInitiatedEventProcessor.handleHearingInitiatedProcessor((envelopeFrom(metadataWithRandomUUID("public.hearing.trial-vacated"), eventPayload)));

        // Then
        verify(runtimeService, times(1)).startProcessInstanceByKey(Mockito.eq(HEARING_LISTED_BPMN_PROCESS_NAME), Mockito.eq(eventPayload.getString(HEARING_ID)), processVariablesCaptor.capture());
        verify(interpreterForWelshActivityHandler).handleWelshInterpreterForCaseInitiated(any());
        final Map processVariables = processVariablesCaptor.getValue();
        assertThat(processVariables.size(), is(14));

        assertThat(processVariables.get(BOOK_INTERPRETER_FIELD), is(true));
        assertThat(processVariables.get(HAS_CASE_ID_FIELD), is(true));
        assertThat(processVariables.get(HEARING_ID), is(eventPayload.getString(HEARING_ID)));
        assertThat(processVariables.get(HEARING_DATE), is(eventPayload.getString(HEARING_DATE_TIME_FIELD)));
        assertThat(processVariables.get(CASE_ID), is(eventPayload.getJsonArray("caseDetails").getJsonObject(0).getString("caseId")));
        assertThat(processVariables.get(CASE_URN), is(eventPayload.getJsonArray("caseDetails").getJsonObject(0).getString("caseUrn")));
        final JsonArray defendants = eventPayload.getJsonArray("caseDetails").getJsonObject(0).getJsonArray("defendantDetails");
        assertThat(processVariables.get(DEFENDANT_ID), is(defendants.getJsonObject(0).getString(DEFENDANT_ID)));
        final String defendantFirstName = defendants.getJsonObject(0).getString("defendantFirstName");
        final String defendantLastName = defendants.getJsonObject(0).getString("defendantLastName");
        assertThat(processVariables.get(DEFENDANT_NAME), is(defendantFirstName + SPACE + defendantLastName));
        assertThat(processVariables.get(LAST_UPDATED_BY_ID), is(SYSTEM_USER_ID.toString()));
        assertThat(processVariables.get(LAST_UPDATED_BY_NAME), is(SYSTEM_USER_NAME));
        assertThat(processVariables.get(JURISDICTION), is(JURISDICTION_TYPE_CROWN));
        assertThat(processVariables.get(WORK_QUEUE), is(CROWN_COURT_ADMIN_WORK_QUEUE_ID));
        assertThat(processVariables.get(getPrefix(BOOK_INTERPRETER_CASE_TASK_ID) + DEEP_LINK), is(CASE_DEEP_LINK_URL.concat(CASE_ID1)));
        assertThat(processVariables.get(NOTE), is("[ TFL34652354 = Abbie ARMSTRONG : Welsh,John Doe : French ][ TFL12346523 = John Doe : French ]"));
    }

    @Test
    public void shouldNotStartHearingInitiatedProcessForCaseWhenInterpreterFeatureFlagIsFalse() {
        // Given
        when(featureControlGuard.isFeatureEnabled("camunda-interpreter")).thenReturn(false);
        final JsonObject eventPayload = getEventPayload(HEARING_INIT_CASE_JSON, CASE_ID1, "CASE_ID", JURISDICTION_TYPE_MAGISTRATES, "Welsh", "French", "", "");

        // when
        hearingInitiatedEventProcessor.handleHearingInitiatedProcessor((envelopeFrom(metadataWithRandomUUID("public.hearing.trial-vacated"), eventPayload)));

        // Then
        verify(runtimeService, never()).startProcessInstanceByKey(Mockito.eq(HEARING_LISTED_BPMN_PROCESS_NAME), Mockito.eq(eventPayload.getString(HEARING_ID)), anyMap());
        verify(interpreterForWelshActivityHandler, never()).handleWelshInterpreterForCaseInitiated(any());
        verify(summonsApplicationHandler, never()).handleSummonsApplicationHearingInitiated(any());
    }

    @Test
    public void shouldStartHearingInitiatedProcessForMultipleApplicationWithInterpretersInMagistratesCourt() {
        when(taskTypeService.getTaskVariablesFromRefDataWithPrefix(BOOK_INTERPRETER_APPLICATION_CASE_TASK_ID, APPLICATION_ID, getPrefix(BOOK_INTERPRETER_APPLICATION_CASE_TASK_ID), "2022-08-24T01:46:36.068Z")).
                thenReturn(getTaskTypes(APPLICATION_ID, APPLICATION_DEEP_LINK_URL, BOOK_INTERPRETER_CASE_TASK_ID));
        when(systemUserProvider.getContextSystemUserId()).thenReturn(of(SYSTEM_USER_ID));

        // Givens
        final JsonObject eventPayload = getEventPayload(HEARING_INIT_APPLICATION_JSON, APPLICATION_ID, "APPLICATION_ID", JURISDICTION_TYPE_MAGISTRATES, "Welsh", "French", APPLICATION_REF1, APPLICATION_REF2);

        // when
        hearingInitiatedEventProcessor.handleHearingInitiatedProcessor((envelopeFrom(metadataWithRandomUUID("public.hearing.trial-vacated"), eventPayload)));

        // Then
        verify(runtimeService, times(1)).startProcessInstanceByKey(Mockito.eq(HEARING_LISTED_BPMN_PROCESS_NAME), Mockito.eq(eventPayload.getString(HEARING_ID)), processVariablesCaptor.capture());
        verify(interpreterForWelshActivityHandler, never()).handleWelshInterpreterForCaseInitiated(any());
        Map processVariables = processVariablesCaptor.getValue();
        assertThat(processVariables.size(), is(14));

        assertThat(processVariables.get(BOOK_INTERPRETER_FIELD), is(true));
        assertThat(processVariables.get(HAS_CASE_ID_FIELD), is(false));
        assertThat(processVariables.get(HEARING_ID), is(eventPayload.getString(HEARING_ID)));
        assertThat(processVariables.get(HEARING_DATE), is(eventPayload.getString(HEARING_DATE_TIME_FIELD)));
        assertThat(processVariables.get(APPLICATION_ID_FIELD), is(eventPayload.getJsonArray("applicationDetails").getJsonObject(0).getString("applicationId")));
        assertThat(processVariables.get(CASE_URN), is(eventPayload.getJsonArray("applicationDetails").getJsonObject(0).getString(APPLICATION_REF_FIELD)));
        final JsonObject defendantDetails = eventPayload.getJsonArray("applicationDetails").getJsonObject(0).getJsonObject("subject");
        assertThat(processVariables.get(DEFENDANT_ID), is(defendantDetails.getString(DEFENDANT_ID)));
        final String defendantFirstName = defendantDetails.getString("defendantFirstName");
        final String defendantLastName = defendantDetails.getString("defendantLastName");
        assertThat(processVariables.get(DEFENDANT_NAME), is(defendantFirstName + SPACE + defendantLastName));
        assertThat(processVariables.get(LAST_UPDATED_BY_ID), is(SYSTEM_USER_ID.toString()));
        assertThat(processVariables.get(LAST_UPDATED_BY_NAME), is(SYSTEM_USER_NAME));
        assertThat(processVariables.get(JURISDICTION), is(JURISDICTION_TYPE_MAGISTRATES));
        assertThat(processVariables.get(WORK_QUEUE), is(WORK_QUEUE_ID));
        assertThat(processVariables.get(getPrefix(BOOK_INTERPRETER_CASE_TASK_ID) + DEEP_LINK), is(APPLICATION_DEEP_LINK_URL.concat(APPLICATION_ID)));
        assertThat(processVariables.get(NOTE), is("[ TFL34652354 = Abbie ARMSTRONG : Welsh ][ TFL12346523 = John Doe : French ]"));
    }

    @Test
    public void shouldStartHearingInitiatedProcessForMultipleApplicationWithInterpretersInMagistratesCourtAndEmptyAppRef() {
        when(taskTypeService.getTaskVariablesFromRefDataWithPrefix(BOOK_INTERPRETER_APPLICATION_CASE_TASK_ID, APPLICATION_ID, getPrefix(BOOK_INTERPRETER_APPLICATION_CASE_TASK_ID), "2022-08-24T01:46:36.068Z")).
                thenReturn(getTaskTypes(APPLICATION_ID, APPLICATION_DEEP_LINK_URL, BOOK_INTERPRETER_CASE_TASK_ID));
        when(systemUserProvider.getContextSystemUserId()).thenReturn(of(SYSTEM_USER_ID));

        // Givens
        final JsonObject eventPayload = getEventPayload(HEARING_INIT_APPLICATION_JSON, APPLICATION_ID, "APPLICATION_ID", JURISDICTION_TYPE_MAGISTRATES, "Welsh", "French", "", "");

        // when
        hearingInitiatedEventProcessor.handleHearingInitiatedProcessor((envelopeFrom(metadataWithRandomUUID("public.hearing.trial-vacated"), eventPayload)));

        // Then
        verify(runtimeService, times(1)).startProcessInstanceByKey(Mockito.eq(HEARING_LISTED_BPMN_PROCESS_NAME), Mockito.eq(eventPayload.getString(HEARING_ID)), processVariablesCaptor.capture());
        verify(interpreterForWelshActivityHandler, never()).handleWelshInterpreterForCaseInitiated(any());
        Map processVariables = processVariablesCaptor.getValue();
        assertThat(processVariables.size(), is(14));

        assertThat(processVariables.get(BOOK_INTERPRETER_FIELD), is(true));
        assertThat(processVariables.get(HAS_CASE_ID_FIELD), is(false));
        assertThat(processVariables.get(HEARING_ID), is(eventPayload.getString(HEARING_ID)));
        assertThat(processVariables.get(HEARING_DATE), is(eventPayload.getString(HEARING_DATE_TIME_FIELD)));
        assertThat(processVariables.get(APPLICATION_ID_FIELD), is(eventPayload.getJsonArray("applicationDetails").getJsonObject(0).getString("applicationId")));
        assertThat(processVariables.get(CASE_URN), is(eventPayload.getJsonArray("applicationDetails").getJsonObject(0).getString(APPLICATION_REF_FIELD)));
        final JsonObject defendantDetails = eventPayload.getJsonArray("applicationDetails").getJsonObject(0).getJsonObject("subject");
        assertThat(processVariables.get(DEFENDANT_ID), is(defendantDetails.getString(DEFENDANT_ID)));
        final String defendantFirstName = defendantDetails.getString("defendantFirstName");
        final String defendantLastName = defendantDetails.getString("defendantLastName");
        assertThat(processVariables.get(DEFENDANT_NAME), is(defendantFirstName + SPACE + defendantLastName));
        assertThat(processVariables.get(LAST_UPDATED_BY_ID), is(SYSTEM_USER_ID.toString()));
        assertThat(processVariables.get(LAST_UPDATED_BY_NAME), is(SYSTEM_USER_NAME));
        assertThat(processVariables.get(JURISDICTION), is(JURISDICTION_TYPE_MAGISTRATES));
        assertThat(processVariables.get(WORK_QUEUE), is(WORK_QUEUE_ID));
        assertThat(processVariables.get(getPrefix(BOOK_INTERPRETER_CASE_TASK_ID) + DEEP_LINK), is(APPLICATION_DEEP_LINK_URL.concat(APPLICATION_ID)));
        assertThat(processVariables.get(NOTE), is("[ Abbie ARMSTRONG : Welsh,John Doe : French ]"));
    }

    @Test
    public void shouldStartHearingInitiatedProcessForMultipleApplicationWithOutInterpretersInMagistratesCourt() {

        // Givens
        final JsonObject eventPayload = getEventPayload(HEARING_INIT_APPLICATION_JSON, APPLICATION_ID, "APPLICATION_ID", JURISDICTION_TYPE_MAGISTRATES, "", "", "", "");

        // when
        when(taskTypeService.getTaskVariablesFromRefDataWithPrefix(BOOK_INTERPRETER_APPLICATION_CASE_TASK_ID, APPLICATION_ID, getPrefix(BOOK_INTERPRETER_APPLICATION_CASE_TASK_ID), "2022-08-24T01:46:36.068Z")).
                thenReturn(getTaskTypes(APPLICATION_ID, APPLICATION_DEEP_LINK_URL, BOOK_INTERPRETER_CASE_TASK_ID));
        when(systemUserProvider.getContextSystemUserId()).thenReturn(of(SYSTEM_USER_ID));
        hearingInitiatedEventProcessor.handleHearingInitiatedProcessor((envelopeFrom(metadataWithRandomUUID("public.hearing.trial-vacated"), eventPayload)));

        // Then
        verify(runtimeService, times(1)).startProcessInstanceByKey(Mockito.eq(HEARING_LISTED_BPMN_PROCESS_NAME), Mockito.eq(eventPayload.getString(HEARING_ID)), processVariablesCaptor.capture());
        verify(interpreterForWelshActivityHandler, never()).handleWelshInterpreterForCaseInitiated(any());

        Map processVariables = processVariablesCaptor.getValue();
        assertThat(processVariables.size(), is(14));

        assertThat(processVariables.get(BOOK_INTERPRETER_FIELD), is(false));
        assertThat(processVariables.get(HAS_CASE_ID_FIELD), is(false));
        assertThat(processVariables.get(HEARING_ID), is(eventPayload.getString(HEARING_ID)));
        assertThat(processVariables.get(HEARING_DATE), is(eventPayload.getString(HEARING_DATE_TIME_FIELD)));
        assertThat(processVariables.get(APPLICATION_ID_FIELD), is(eventPayload.getJsonArray("applicationDetails").getJsonObject(0).getString("applicationId")));
        final JsonObject defendantDetails = eventPayload.getJsonArray("applicationDetails").getJsonObject(0).getJsonObject("subject");
        assertThat(processVariables.get(DEFENDANT_ID), is(defendantDetails.getString(DEFENDANT_ID)));
        final String defendantFirstName = defendantDetails.getString("defendantFirstName");
        final String defendantLastName = defendantDetails.getString("defendantLastName");
        assertThat(processVariables.get(DEFENDANT_NAME), is(defendantFirstName + SPACE + defendantLastName));
        assertThat(processVariables.get(LAST_UPDATED_BY_ID), is(SYSTEM_USER_ID.toString()));
        assertThat(processVariables.get(LAST_UPDATED_BY_NAME), is(SYSTEM_USER_NAME));
        assertThat(processVariables.get(WORK_QUEUE), is(WORK_QUEUE_ID));
        assertThat(processVariables.get(JURISDICTION), is(JURISDICTION_TYPE_MAGISTRATES));
        assertThat(processVariables.get(getPrefix(BOOK_INTERPRETER_CASE_TASK_ID) + DEEP_LINK), is(APPLICATION_DEEP_LINK_URL.concat(APPLICATION_ID)));
        assertThat(processVariables.get(NOTE), is(EMPTY));
    }

    @Test
    public void shouldNotStartHearingInitiatedProcessForApplicationWhenInterpreterFeatureFlagIsFalse() {
        // Given
        when(featureControlGuard.isFeatureEnabled("camunda-interpreter")).thenReturn(false);
        final JsonObject eventPayload = getEventPayload(HEARING_INIT_APPLICATION_JSON, APPLICATION_ID, "APPLICATION_ID", JURISDICTION_TYPE_MAGISTRATES, "Welsh", "French", APPLICATION_REF1, APPLICATION_REF2);

        // when
        hearingInitiatedEventProcessor.handleHearingInitiatedProcessor((envelopeFrom(metadataWithRandomUUID("public.hearing.trial-vacated"), eventPayload)));

        // Then
        verify(runtimeService, never()).startProcessInstanceByKey(Mockito.eq(HEARING_LISTED_BPMN_PROCESS_NAME), Mockito.eq(eventPayload.getString(HEARING_ID)), anyMap());
        verify(interpreterForWelshActivityHandler, never()).handleWelshInterpreterForCaseInitiated(any());
        verify(summonsApplicationHandler).handleSummonsApplicationHearingInitiated(any());
    }

    private JsonObject getEventPayload(final String fileName, final String id, final String idName, final String jurisdictionType, final String Lang1, final String Lang2, final String app_ref1, final String app_ref2) {
        return getFileContentAsJson(fileName,
                ImmutableMap.<String, Object>builder()
                        .put(idName, id)
                        .put("JURISDICTION_TYPE", jurisdictionType)
                        .put("CASE_ID2", CASE_ID2)
                        .put("LANG1", Lang1)
                        .put("LANG2", Lang2)
                        .put("CASE_URN1", CASE_URN1)
                        .put("CASE_URN2", CASE_URN2)
                        .put("APPLICATION_REF1", app_ref1)
                        .put("APPLICATION_REF2", app_ref2)
                        .build());
    }

    private Map<String, Object> getTaskTypes(final String referenceId, final String deepLinkUrl, final String taskName) {
        final Map<String, Object> referenceData = new HashMap<>();
        referenceData.put(getPrefix(taskName) + DEEP_LINK, deepLinkUrl.concat(referenceId));
        referenceData.put(WORK_QUEUE, WORK_QUEUE_ID);
        return referenceData;
    }

}