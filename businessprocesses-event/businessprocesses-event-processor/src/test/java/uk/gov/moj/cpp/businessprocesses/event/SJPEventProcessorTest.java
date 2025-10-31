package uk.gov.moj.cpp.businessprocesses.event;

import static java.util.Optional.of;
import static java.util.UUID.randomUUID;
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
import static uk.gov.moj.cpp.businessprocesses.util.FileUtil.getFileContentAsJson;

import uk.gov.justice.services.core.dispatcher.SystemUserProvider;
import uk.gov.justice.services.core.featurecontrol.FeatureControlGuard;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.moj.cpp.businessprocesses.service.TaskTypeService;

import java.util.HashMap;
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
public class SJPEventProcessorTest {

    private static final String SJP_CASE_DECISION_SAVED_JSON = "sjpCaseDecisionSaved.json";
    private static final String SJP_CASE_HEARING_DECISION_BPMN_PROCESS_NAME = "sjp-case-hearing-decision-saved";
    private static final String CASE_ID = randomUUID().toString();
    private static final String SJP_TASK_NAME = "Refer_SJS_case_for_hearing";
    private static final UUID SYSTEM_USER_ID = randomUUID();
    private static final String SYSTEM_USER_NAME = "SYSTEM";
    private static final String LAST_UPDATED_BY_ID = "lastUpdatedByID";
    private static final String LAST_UPDATED_BY_NAME = "lastUpdatedByName";
    private static final String DEEP_LINK = "deepLink";
    private static final String WORK_QUEUE = "workQueue";
    private static final String HEARING_DATE = "hearingDate";

    @InjectMocks
    private SJPEventProcessor sjpEventProcessor;

    @Mock
    private RuntimeService runtimeService;

    @Captor
    private ArgumentCaptor<Map> processVariablesCaptor;

    @Mock
    private TaskTypeService taskTypeService;

    @Mock
    private SystemUserProvider systemUserProvider;

    @Mock
    private FeatureControlGuard featureControlGuard;

    @BeforeEach
    public void setup() {
        when(taskTypeService.getTaskVariablesFromRefData(SJP_TASK_NAME, CASE_ID)).thenReturn(getTaskReferenceData(CASE_ID));
        when(systemUserProvider.getContextSystemUserId()).thenReturn(of(SYSTEM_USER_ID));
        when(featureControlGuard.isFeatureEnabled(any())).thenReturn(true);
    }

    @Test
    void shouldNotCallSjpCaseReferredToCourtWhenFeatureIsDisabled() {
        when(featureControlGuard.isFeatureEnabled("camunda-sjp-case-referred-to-court")).thenReturn(false);
        JsonEnvelope jsonEnvelope = mock(JsonEnvelope.class);

        sjpEventProcessor.handleSjpCaseReferredToCourtProcessor(jsonEnvelope);

        verify(featureControlGuard, times(1)).isFeatureEnabled("camunda-sjp-case-referred-to-court");
        verifyNoInteractions(jsonEnvelope);
    }

    @Test
    public void shouldStartNewSJPCaseForHearingProcessWithReferCourtHearingFalse() {

        // Given
        final JsonObject eventPayload = getEventPayload(SJP_CASE_DECISION_SAVED_JSON, "WITHDRAW", CASE_ID);

        // when
        sjpEventProcessor.handleSjpCaseReferredToCourtProcessor((envelopeFrom(metadataWithRandomUUID("public.events.sjp.case-referred-to-court"), eventPayload)));

        // Then
        verify(runtimeService, times(1)).startProcessInstanceByKey(Mockito.eq(SJP_CASE_HEARING_DECISION_BPMN_PROCESS_NAME), Mockito.eq(CASE_ID), processVariablesCaptor.capture());

        Map processVariables = processVariablesCaptor.getValue();
        assertThat(processVariables.size(), is(10));
        assertThat(processVariables.get("referCourtHearing"), is(false));
        assertThat(processVariables.get("caseId"), is(eventPayload.getString("caseId")));
        assertThat(processVariables.get("caseURN"), is(eventPayload.getString("urn")));
        assertThat(processVariables.get("defendantId"), is(eventPayload.getString("defendantId")));
        assertThat(processVariables.get("defendantName"), is(eventPayload.getString("defendantName")));
        assertThat(processVariables.get(LAST_UPDATED_BY_ID), is(SYSTEM_USER_ID.toString()));
        assertThat(processVariables.get(LAST_UPDATED_BY_NAME), is(SYSTEM_USER_NAME));
        assertThat(processVariables.get(WORK_QUEUE), is("SJPWorkQueue"));
        assertThat(processVariables.get(DEEP_LINK), is("https://dummyUrl/prosecution-casefile/case-at-a-glance/".concat(CASE_ID)));
    }

    @Test
    public void shouldStartNewSJPCaseForHearingProcessWithReferCourtHearingTrue() {

        // Given
        final JsonObject eventPayload = getEventPayload(SJP_CASE_DECISION_SAVED_JSON, "REFER_FOR_COURT_HEARING", CASE_ID);

        // when
        sjpEventProcessor.handleSjpCaseReferredToCourtProcessor((envelopeFrom(metadataWithRandomUUID("public.events.sjp.case-referred-to-court"), eventPayload)));

        // Then
        verify(runtimeService, times(1)).startProcessInstanceByKey(Mockito.eq(SJP_CASE_HEARING_DECISION_BPMN_PROCESS_NAME), Mockito.eq(CASE_ID), processVariablesCaptor.capture());

        Map processVariables = processVariablesCaptor.getValue();
        assertThat(processVariables.size(), is(10));
        assertThat(processVariables.get("referCourtHearing"), is(true));
        assertThat(processVariables.get("caseId"), is(eventPayload.getString("caseId")));
        assertThat(processVariables.get("caseURN"), is(eventPayload.getString("urn")));
        assertThat(processVariables.get("defendantId"), is(eventPayload.getString("defendantId")));
        assertThat(processVariables.get("defendantName"), is(eventPayload.getString("defendantName")));
        assertThat(processVariables.get(LAST_UPDATED_BY_ID), is(SYSTEM_USER_ID.toString()));
        assertThat(processVariables.get(LAST_UPDATED_BY_NAME), is(SYSTEM_USER_NAME));
        assertThat(processVariables.get(WORK_QUEUE), is("SJPWorkQueue"));
        assertThat(processVariables.get(DEEP_LINK), is("https://dummyUrl/prosecution-casefile/case-at-a-glance/".concat(CASE_ID)));
        assertThat(processVariables.get(HEARING_DATE), is("2023-10-12T10:10:09.123Z"));
    }

    private JsonObject getEventPayload(final String fileName, final String type, final String caseId) {
        return getFileContentAsJson(fileName,
                ImmutableMap.<String, Object>builder()
                        .put("TYPE", type)
                        .put("CASE_ID", caseId)
                        .build());
    }

    private Map<String, Object> getTaskReferenceData(final String referenceId) {
        final Map<String, Object> referenceData = new HashMap<>();
        referenceData.put(DEEP_LINK, "https://dummyUrl/prosecution-casefile/case-at-a-glance/".concat(referenceId));
        referenceData.put(WORK_QUEUE, "SJPWorkQueue");
        return referenceData;
    }
}