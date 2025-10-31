package uk.gov.moj.cpp.businessprocesses.event;


import static java.util.Optional.of;
import static java.util.UUID.randomUUID;
import static org.apache.commons.lang3.StringUtils.SPACE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.justice.services.messaging.JsonEnvelope.envelopeFrom;
import static uk.gov.justice.services.test.utils.core.messaging.MetadataBuilderFactory.metadataWithRandomUUID;
import static uk.gov.moj.cpp.businessprocesses.util.FileUtil.getFileContentAsJson;

import uk.gov.justice.services.core.dispatcher.SystemUserProvider;
import uk.gov.justice.services.core.featurecontrol.FeatureControlGuard;
import uk.gov.moj.cpp.businessprocesses.service.TaskTypeService;

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
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class HearingTrialVacatedEventProcessorTest {

    private static final String HEARING_TRAIL_CASE_VACATED_JSON = "hearingTrialVacated-caseDetails.json";
    private static final String HEARING_TRAIL_APPLICATION_VACATED_JSON = "hearingTrialVacated-applicationDetails.json";

    private static final String HEARING_EFFECTIVE_TRIAL_JSON = "hearingTrialVacatedForEffectiveTrial.json";

    private static final String HEARING_VACATED_BPMN_PROCESS_NAME = "hearing-vacated";

    // Fields
    private static final String CANCEL_INTERPRETER_CASE_TASK_NAME = "Cancel_interpreter_case";
    private static final String CANCEL_INTERPRETER_APPLICATION_TASK_NAME = "Cancel_interpreter_application";

    private static final String CASE_ID_FIELD = "caseId";
    private static final String APPLICATION_ID_FIELD = "applicationId";
    private static final String APPLICATION_REF_FIELD = "applicationReference";
    private static final String DEFENDANT_ID_FIELD = "defendantId";
    private static final String DEFENDANT_NAME_FIELD = "defendantName";
    private static final String DEFENDANT_REMAND_STATUS_FIELD = "defendantRemandStatus";
    private static final String HEARING_ID_FIELD = "hearingId";
    private static final String HEARING_DATE_FIELD = "hearingDate";
    private static final String HEARING_DATE_TIME_FIELD = "hearingDateTime";
    private static final String CANCEL_INTERPRETER_FIELD = "hasInterpreter";
    private static final String WORK_QUEUE_FIELD = "workQueue";
    private static final String URN_FIELD = "caseURN";

    private static final String CASE_URN = "caseURN";
    private static final String CASEURN = "caseUrn";
    private static final String DEEP_LINK = "deepLink";
    private static final String JURISDICTION = "jurisdiction";
    private static final String WORK_QUEUE = "workQueue";


    private static final String LAST_UPDATED_BY_ID = "lastUpdatedByID";
    private static final String LAST_UPDATED_BY_NAME = "lastUpdatedByName";

    // Values
    private static final String CASE_ID = randomUUID().toString();
    private static final String APPLICATION_ID = randomUUID().toString();
    private static final String WORK_QUEUE_ID = "89058c86-ddc3-441c-94bd-c21c6a1e5d80";
    private static final String CASE_DEEP_LINK_URL = "https://dummyUrl/prosecution-casefile/case-at-a-glance/";
    private static final String APPLICATION_DEEP_LINK_URL = "https://dummyUrl/prosecution-casefile/application-at-a-glance/";
    private static final String CASE_JURISDICTION_TYPE_MAGISTRATES = "MAGISTRATES";
    private static final String CASE_JURISDICTION_TYPE_CROWN = "CROWN";
    private static final String CROWN_COURT_ADMIN_WORK_QUEUE_ID = "5cd9bd67-1f08-315b-9608-8eb7ebb7ea2f";
    private static final UUID SYSTEM_USER_ID = randomUUID();
    private static final String SYSTEM_USER_NAME = "SYSTEM";

    @InjectMocks
    private HearingTrialVacatedEventProcessor hearingTrialVacatedEventProcessor;

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

    @BeforeEach
    public void setup() {
        when(featureControlGuard.isFeatureEnabled(any())).thenReturn(true);
    }

    @Test
    public void shouldStartHearingTrialVacatedCaseProcessWithCancelInterpreterFalse() {

        // Given
        final JsonObject eventPayload = getEventPayload(HEARING_TRAIL_CASE_VACATED_JSON, false, CASE_ID, "CASE_ID", CASE_JURISDICTION_TYPE_MAGISTRATES);

        // when
        when(taskTypeService.getTaskVariablesFromRefDataWithPrefix(CANCEL_INTERPRETER_CASE_TASK_NAME, CASE_ID, getPrefix(CANCEL_INTERPRETER_CASE_TASK_NAME), "2022-08-24T01:46:36.068Z")).
                thenReturn(getTaskTypes(CASE_ID, CASE_DEEP_LINK_URL, CANCEL_INTERPRETER_CASE_TASK_NAME));
        when(systemUserProvider.getContextSystemUserId()).thenReturn(of(SYSTEM_USER_ID));
        hearingTrialVacatedEventProcessor.handleHearingVacatedProcessor((envelopeFrom(metadataWithRandomUUID("public.hearing.trial-vacated"), eventPayload)));

        // Then
        verify(runtimeService, times(1)).startProcessInstanceByKey(Mockito.eq(HEARING_VACATED_BPMN_PROCESS_NAME), Mockito.eq(eventPayload.getString(HEARING_ID_FIELD)), processVariablesCaptor.capture());

        Map processVariables = processVariablesCaptor.getValue();
        assertThat(processVariables.size(), is(14));

        assertThat(processVariables.get(CANCEL_INTERPRETER_FIELD), is(false));
        assertThat(processVariables.get(HEARING_ID_FIELD), is(eventPayload.getString(HEARING_ID_FIELD)));
        assertThat(processVariables.get(HEARING_DATE_FIELD), is(eventPayload.getString(HEARING_DATE_TIME_FIELD)));
        assertThat(processVariables.get(CASE_ID_FIELD), is(eventPayload.getJsonArray("caseDetails").getJsonObject(0).getString("caseId")));
        assertThat(processVariables.get(URN_FIELD), is(eventPayload.getJsonArray("caseDetails").getJsonObject(0).getString(CASEURN)));
        final JsonArray defendants = eventPayload.getJsonArray("caseDetails").getJsonObject(0).getJsonArray("defendantDetails");
        assertThat(processVariables.get(DEFENDANT_ID_FIELD), is(defendants.getJsonObject(0).getString(DEFENDANT_ID_FIELD)));
        assertThat(processVariables.get(DEFENDANT_REMAND_STATUS_FIELD), is(defendants.getJsonObject(0).getString(DEFENDANT_REMAND_STATUS_FIELD)));
        final String defendantFirstName = defendants.getJsonObject(0).getString("defendantFirstName");
        final String defendantLastName = defendants.getJsonObject(0).getString("defendantLastName");
        assertThat(processVariables.get(DEFENDANT_NAME_FIELD), is(defendantFirstName + SPACE + defendantLastName));
        assertThat(processVariables.get(LAST_UPDATED_BY_ID), is(SYSTEM_USER_ID.toString()));
        assertThat(processVariables.get(LAST_UPDATED_BY_NAME), is(SYSTEM_USER_NAME));
        assertThat(processVariables.get(JURISDICTION), is(CASE_JURISDICTION_TYPE_MAGISTRATES));
        assertThat(processVariables.get(WORK_QUEUE_FIELD), is(WORK_QUEUE_ID));
        assertThat(processVariables.get(getPrefix(CANCEL_INTERPRETER_CASE_TASK_NAME) + DEEP_LINK), is(CASE_DEEP_LINK_URL.concat(CASE_ID)));
    }

    @Test
    public void shouldStartHearingTrialVacatedCaseProcessWithCancelInterpreterTrueMagistratesWorkQueue() {

        // Given
        final JsonObject eventPayload = getEventPayload(HEARING_TRAIL_CASE_VACATED_JSON, true, CASE_ID, "CASE_ID", CASE_JURISDICTION_TYPE_MAGISTRATES);

        // when
        when(taskTypeService.getTaskVariablesFromRefDataWithPrefix(CANCEL_INTERPRETER_CASE_TASK_NAME, CASE_ID, getPrefix(CANCEL_INTERPRETER_CASE_TASK_NAME), "2022-08-24T01:46:36.068Z")).
                thenReturn(getTaskTypes(CASE_ID, CASE_DEEP_LINK_URL, CANCEL_INTERPRETER_CASE_TASK_NAME));
        when(systemUserProvider.getContextSystemUserId()).thenReturn(of(SYSTEM_USER_ID));
        hearingTrialVacatedEventProcessor.handleHearingVacatedProcessor((envelopeFrom(metadataWithRandomUUID("public.hearing.trial-vacated"), eventPayload)));

        // Then
        verify(runtimeService, times(1)).startProcessInstanceByKey(Mockito.eq(HEARING_VACATED_BPMN_PROCESS_NAME), Mockito.eq(eventPayload.getString(HEARING_ID_FIELD)), processVariablesCaptor.capture());

        Map processVariables = processVariablesCaptor.getValue();
        assertThat(processVariables.size(), is(14));

        assertThat(processVariables.get(CANCEL_INTERPRETER_FIELD), is(true));
        assertThat(processVariables.get(HEARING_ID_FIELD), is(eventPayload.getString(HEARING_ID_FIELD)));
        assertThat(processVariables.get(HEARING_DATE_FIELD), is(eventPayload.getString(HEARING_DATE_TIME_FIELD)));
        assertThat(processVariables.get(CASE_ID_FIELD), is(eventPayload.getJsonArray("caseDetails").getJsonObject(0).getString("caseId")));
        assertThat(processVariables.get(URN_FIELD), is(eventPayload.getJsonArray("caseDetails").getJsonObject(0).getString(CASEURN)));
        final JsonArray defendants = eventPayload.getJsonArray("caseDetails").getJsonObject(0).getJsonArray("defendantDetails");
        assertThat(processVariables.get(DEFENDANT_ID_FIELD), is(defendants.getJsonObject(0).getString(DEFENDANT_ID_FIELD)));
        assertThat(processVariables.get(DEFENDANT_REMAND_STATUS_FIELD), is(defendants.getJsonObject(0).getString(DEFENDANT_REMAND_STATUS_FIELD)));
        final String defendantFirstName = defendants.getJsonObject(0).getString("defendantFirstName");
        final String defendantLastName = defendants.getJsonObject(0).getString("defendantLastName");
        assertThat(processVariables.get(DEFENDANT_NAME_FIELD), is(defendantFirstName + SPACE + defendantLastName));
        assertThat(processVariables.get(LAST_UPDATED_BY_ID), is(SYSTEM_USER_ID.toString()));
        assertThat(processVariables.get(LAST_UPDATED_BY_NAME), is(SYSTEM_USER_NAME));
        assertThat(processVariables.get(JURISDICTION), is(CASE_JURISDICTION_TYPE_MAGISTRATES));
        assertThat(processVariables.get(WORK_QUEUE_FIELD), is(WORK_QUEUE_ID));
        assertThat(processVariables.get(getPrefix(CANCEL_INTERPRETER_CASE_TASK_NAME) + DEEP_LINK), is(CASE_DEEP_LINK_URL.concat(CASE_ID)));
    }

    @Test
    public void shouldStartHearingTrialVacatedCaseProcessWithCancelInterpreterTrueCrownCourtWorkQueue() {

        // Given
        final JsonObject eventPayload = getEventPayload(HEARING_TRAIL_CASE_VACATED_JSON, true, CASE_ID, "CASE_ID", CASE_JURISDICTION_TYPE_CROWN);

        // when
        when(taskTypeService.getTaskVariablesFromRefDataWithPrefix(CANCEL_INTERPRETER_CASE_TASK_NAME, CASE_ID, getPrefix(CANCEL_INTERPRETER_CASE_TASK_NAME), "2022-08-24T01:46:36.068Z")).
                thenReturn(getTaskTypes(CASE_ID, CASE_DEEP_LINK_URL, CANCEL_INTERPRETER_CASE_TASK_NAME));
        when(systemUserProvider.getContextSystemUserId()).thenReturn(of(SYSTEM_USER_ID));
        hearingTrialVacatedEventProcessor.handleHearingVacatedProcessor((envelopeFrom(metadataWithRandomUUID("public.hearing.trial-vacated"), eventPayload)));

        // Then
        verify(runtimeService, times(1)).startProcessInstanceByKey(Mockito.eq(HEARING_VACATED_BPMN_PROCESS_NAME), Mockito.eq(eventPayload.getString(HEARING_ID_FIELD)), processVariablesCaptor.capture());

        Map processVariables = processVariablesCaptor.getValue();
        assertThat(processVariables.size(), is(14));

        assertThat(processVariables.get(CANCEL_INTERPRETER_FIELD), is(true));
        assertThat(processVariables.get(HEARING_ID_FIELD), is(eventPayload.getString(HEARING_ID_FIELD)));
        assertThat(processVariables.get(HEARING_DATE_FIELD), is(eventPayload.getString(HEARING_DATE_TIME_FIELD)));
        assertThat(processVariables.get(CASE_ID_FIELD), is(eventPayload.getJsonArray("caseDetails").getJsonObject(0).getString("caseId")));
        assertThat(processVariables.get(URN_FIELD), is(eventPayload.getJsonArray("caseDetails").getJsonObject(0).getString(CASEURN)));
        final JsonArray defendants = eventPayload.getJsonArray("caseDetails").getJsonObject(0).getJsonArray("defendantDetails");
        assertThat(processVariables.get(DEFENDANT_ID_FIELD), is(defendants.getJsonObject(0).getString(DEFENDANT_ID_FIELD)));
        assertThat(processVariables.get(DEFENDANT_REMAND_STATUS_FIELD), is(defendants.getJsonObject(0).getString(DEFENDANT_REMAND_STATUS_FIELD)));
        final String defendantFirstName = defendants.getJsonObject(0).getString("defendantFirstName");
        final String defendantLastName = defendants.getJsonObject(0).getString("defendantLastName");
        assertThat(processVariables.get(DEFENDANT_NAME_FIELD), is(defendantFirstName + SPACE + defendantLastName));
        assertThat(processVariables.get(LAST_UPDATED_BY_ID), is(SYSTEM_USER_ID.toString()));
        assertThat(processVariables.get(LAST_UPDATED_BY_NAME), is(SYSTEM_USER_NAME));
        assertThat(processVariables.get(WORK_QUEUE_FIELD), is(CROWN_COURT_ADMIN_WORK_QUEUE_ID));
        assertThat(processVariables.get(getPrefix(CANCEL_INTERPRETER_CASE_TASK_NAME) + DEEP_LINK), is(CASE_DEEP_LINK_URL.concat(CASE_ID)));
    }

    @Test
    public void shouldStartHearingTrialVacatedApplicationProcessWithCancelInterpreterTrueMagistratesWorkQueue() {

        // Given
        final JsonObject eventPayload = getEventPayload(HEARING_TRAIL_APPLICATION_VACATED_JSON, true, APPLICATION_ID, "APPLICATION_ID", CASE_JURISDICTION_TYPE_MAGISTRATES);

        // when
        when(taskTypeService.getTaskVariablesFromRefDataWithPrefix(CANCEL_INTERPRETER_APPLICATION_TASK_NAME, APPLICATION_ID, getPrefix(CANCEL_INTERPRETER_APPLICATION_TASK_NAME), "2022-08-24T01:46:36.068Z")).
                thenReturn(getTaskTypes(APPLICATION_ID, APPLICATION_DEEP_LINK_URL, CANCEL_INTERPRETER_APPLICATION_TASK_NAME));
        when(systemUserProvider.getContextSystemUserId()).thenReturn(of(SYSTEM_USER_ID));
        hearingTrialVacatedEventProcessor.handleHearingVacatedProcessor((envelopeFrom(metadataWithRandomUUID("public.hearing.trial-vacated"), eventPayload)));

        // Then
        verify(runtimeService, times(1)).startProcessInstanceByKey(Mockito.eq(HEARING_VACATED_BPMN_PROCESS_NAME), Mockito.eq(eventPayload.getString(HEARING_ID_FIELD)), processVariablesCaptor.capture());

        Map processVariables = processVariablesCaptor.getValue();
        assertThat(processVariables.size(), is(14));

        assertThat(processVariables.get(CANCEL_INTERPRETER_FIELD), is(true));
        assertThat(processVariables.get(HEARING_ID_FIELD), is(eventPayload.getString(HEARING_ID_FIELD)));
        assertThat(processVariables.get(HEARING_DATE_FIELD), is(eventPayload.getString(HEARING_DATE_TIME_FIELD)));
        assertThat(processVariables.get(APPLICATION_ID_FIELD), is(eventPayload.getJsonArray("applicationDetails").getJsonObject(0).getString("applicationId")));
        assertThat(processVariables.get(CASE_URN), is(eventPayload.getJsonArray("applicationDetails").getJsonObject(0).getString(APPLICATION_REF_FIELD)));
        final JsonObject defendantDetails = eventPayload.getJsonArray("applicationDetails").getJsonObject(0).getJsonObject("subject");
        assertThat(processVariables.get(DEFENDANT_ID_FIELD), is(defendantDetails.getString(DEFENDANT_ID_FIELD)));
        assertThat(processVariables.get(DEFENDANT_REMAND_STATUS_FIELD), is(defendantDetails.getString(DEFENDANT_REMAND_STATUS_FIELD)));
        final String defendantFirstName = defendantDetails.getString("defendantFirstName");
        final String defendantLastName = defendantDetails.getString("defendantLastName");
        assertThat(processVariables.get(DEFENDANT_NAME_FIELD), is(defendantFirstName + SPACE + defendantLastName));
        assertThat(processVariables.get(LAST_UPDATED_BY_ID), is(SYSTEM_USER_ID.toString()));
        assertThat(processVariables.get(LAST_UPDATED_BY_NAME), is(SYSTEM_USER_NAME));
        assertThat(processVariables.get(WORK_QUEUE_FIELD), is(WORK_QUEUE_ID));
        assertThat(processVariables.get(getPrefix(CANCEL_INTERPRETER_APPLICATION_TASK_NAME) + DEEP_LINK), is(APPLICATION_DEEP_LINK_URL.concat(APPLICATION_ID)));
    }

    @Test
    /**
     * The public.hearing.trail-vacated event is also created/sent when the hearing is marked effective/ineffective/cracked etc (not only vacated)
     * We only want to start to the process when vacated.
     */
    public void shouldNotStartHearingTrialVacatedProcessWhenHearingNotVacated() {

        // Given
        final JsonObject eventPayload = getEventPayload(HEARING_EFFECTIVE_TRIAL_JSON, false, CASE_ID, "CASE_ID", CASE_JURISDICTION_TYPE_MAGISTRATES);

        // when
        hearingTrialVacatedEventProcessor.handleHearingVacatedProcessor((envelopeFrom(metadataWithRandomUUID("public.hearing.trial-vacated"), eventPayload)));

        // Then
        verify(runtimeService, never()).startProcessInstanceByKey(anyString(), anyString(), anyMap());
    }

    private JsonObject getEventPayload(final String fileName, final boolean hasInterpreter, final String id, final String idName, final String jurisdictionType) {
        return getFileContentAsJson(fileName,
                ImmutableMap.<String, Object>builder()
                        .put("HAS_INTERPRETER", hasInterpreter)
                        .put(idName, id)
                        .put("JURISDICTION_TYPE", jurisdictionType)
                        .build());
    }

    private Map<String, Object> getTaskTypes(final String referenceId, final String deepLinkUrl, final String taskName) {
        final Map<String, Object> referenceData = new HashMap<>();
        referenceData.put(getPrefix(taskName) + DEEP_LINK, deepLinkUrl.concat(referenceId));
        referenceData.put(WORK_QUEUE, WORK_QUEUE_ID);
        return referenceData;
    }

    private static String getPrefix(final String taskName) {
        return taskName + "_";
    }

}