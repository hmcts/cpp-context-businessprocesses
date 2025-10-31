package uk.gov.moj.cpp.businessprocesses.event;

import static java.util.Optional.of;
import static java.util.UUID.randomUUID;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.SPACE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

import javax.json.JsonArray;
import javax.json.JsonObject;

import com.google.common.collect.ImmutableMap;
import org.camunda.bpm.engine.ProcessEngineConfiguration;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.test.RequiredHistoryLevel;
import org.camunda.bpm.engine.test.mock.Mocks;
import org.camunda.bpm.extension.junit5.test.ProcessEngineExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith({MockitoExtension.class, ProcessEngineExtension.class})
@MockitoSettings(strictness = Strictness.LENIENT)
public class ApplicationCreatedEventProcessorTest {

    private static final String APPLICATION_CREATED_JSON = "court-application-created.json";
    private static final String APPLICATION_CREATED_WITHOUT_EXTERNAL_CREATOR_JSON = "court-application-created-without-ApplicationExternalCreatorType.json";
    private static final String APPLICATION_CREATED_WITHOUT_CASEID_URN_JSON = "court-application-created-without-caseid-urn.json";

    private static final String APP_ID = randomUUID().toString();
    private static final UUID SYSTEM_USER_ID = randomUUID();
    private static final String SYSTEM_USER_NAME = "SYSTEM";

    private static final String APPLICATION_CREATED_BPMN_PROCESS_NAME = "application-received";
    private static final String APPLICATION_ID_FIELD = "applicationId";
    private static final String APP_TYPE_FIELD = "appType";
    private static final String IS_APPLICATION_PROSECUTOR_DEFENCE_FIELD = "isApplicationProsecutorOrDefence";

    private static final String LAST_UPDATED_BY_ID = "lastUpdatedByID";
    private static final String LAST_UPDATED_BY_NAME = "lastUpdatedByName";
    private static final String TYPE = "type";
    private static final String PROSECUTOR = "PROSECUTOR";
    private static final String DEFENCE = "DEFENCE";

    private static final String CASE_ID = "caseId";
    private static final String CASE_URN = "caseURN";
    private static final String DEEP_LINK = "deepLink";
    private static final String DEFENDANT_ID = "defendantId";
    private static final String DEFENDANT_NAME = "defendantName";
    private static final String JURISDICTION = "jurisdiction";
    private static final String WORK_QUEUE = "workQueue";

    private static final String CROWN_COURT_ADMIN_WORK_QUEUE_ID = "5cd9bd67-1f08-315b-9608-8eb7ebb7ea2f";
    private static final String CROWN_JURISDICTION_TYPE = "CROWN";
    private static final String MAGISTRATES_JURISDICTION_TYPE = "MAGISTRATES";


    private static final String APPLICATION_TASK_NAME = "Process_Application";

    @InjectMocks
    private ApplicationCreatedEventProcessor applicationCreatedEventProcessor;

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

    @RegisterExtension
    ProcessEngineExtension extension = ProcessEngineExtension.builder()
            .configurationResource("camunda.cfg.xml")
            .build();

    @RequiredHistoryLevel(ProcessEngineConfiguration.HISTORY_ACTIVITY)
    @BeforeEach
    public void setup() {
        when(systemUserProvider.getContextSystemUserId()).thenReturn(of(SYSTEM_USER_ID));
        when(featureControlGuard.isFeatureEnabled(any())).thenReturn(true);
    }

    @Test
    void shouldNotCallCourtApplicationCreatedWhenFeatureIsDisabled() {
        when(featureControlGuard.isFeatureEnabled("camunda-progression-court-application-created")).thenReturn(false);
        JsonEnvelope jsonEnvelope = mock(JsonEnvelope.class);

        applicationCreatedEventProcessor.handleCourtApplicationCreatedProcessor(jsonEnvelope);

        verify(featureControlGuard, times(1)).isFeatureEnabled("camunda-progression-court-application-created");
        verifyNoInteractions(jsonEnvelope);
    }

    @Test
    public void shouldStartCourtApplicationCreatedWithApplicationByProsecutorWithAllFieldsInJson() {

        // Given
        when(taskTypeService.getTaskVariablesFromRefData(APPLICATION_TASK_NAME, APP_ID)).thenReturn(getTaskReferenceData(APP_ID));
        final JsonObject eventPayload = getEventPayload(APPLICATION_CREATED_JSON, PROSECUTOR, APP_ID, MAGISTRATES_JURISDICTION_TYPE);

        // when
        applicationCreatedEventProcessor.handleCourtApplicationCreatedProcessor((envelopeFrom(metadataWithRandomUUID("public.progression.court-application-created"), eventPayload)));

        // Then
        verify(runtimeService, times(1)).startProcessInstanceByKey(eq(APPLICATION_CREATED_BPMN_PROCESS_NAME), eq(APP_ID), processVariablesCaptor.capture());

        Map processVariables = processVariablesCaptor.getValue();
        assertThat(processVariables.size(), is(13));
        assertThat(processVariables.get(APPLICATION_ID_FIELD), is(APP_ID));
        final JsonObject courtApplication = eventPayload.getJsonObject("courtApplication");
        final JsonArray caseDetails = courtApplication.getJsonArray("courtApplicationCases");
        final JsonObject caseDetailsOne = caseDetails.getJsonObject(0);
        final String caseId = caseDetailsOne.getString("prosecutionCaseId");
        assertThat(processVariables.get(CASE_ID), is(caseId));
        final JsonObject prosecutionCaseIdentifier = caseDetailsOne.getJsonObject("prosecutionCaseIdentifier");
        assertThat(processVariables.get(CASE_URN), is(prosecutionCaseIdentifier.getString(CASE_URN)));
        final JsonObject subject = courtApplication.getJsonObject("subject");
        final String defendantId = subject.getString("id");
        final String defendantFirstName = subject.getJsonObject("personDetails").getString("firstName");
        final String defendantLastName = subject.getJsonObject("personDetails").getString("lastName");
        final String defendantName = defendantFirstName + SPACE + defendantLastName;
        assertThat(processVariables.get(DEFENDANT_ID), is(defendantId));
        assertThat(processVariables.get(DEFENDANT_NAME), is(defendantName));
        assertThat(processVariables.get(IS_APPLICATION_PROSECUTOR_DEFENCE_FIELD), is(true));
        assertThat(processVariables.get(APP_TYPE_FIELD), is(courtApplication.getJsonObject(TYPE).getString(TYPE)));

        assertThat(processVariables.get(LAST_UPDATED_BY_ID), is(SYSTEM_USER_ID.toString()));
        assertThat(processVariables.get(LAST_UPDATED_BY_NAME), is(SYSTEM_USER_NAME));
        assertThat(processVariables.get(JURISDICTION), is(MAGISTRATES_JURISDICTION_TYPE));
        assertThat(processVariables.get(WORK_QUEUE), is("ApplicationCreatedWorkQueue"));
        assertThat(processVariables.get(DEEP_LINK), is("https://dummyUrl/prosecution-casefile/application-at-a-glance/".concat(APP_ID)));
    }

    @Test
    public void shouldStartCourtApplicationCreatedWithApplicationByDefenceWithOutAllFieldsInJson() {

        // Given
        when(taskTypeService.getTaskVariablesFromRefData(APPLICATION_TASK_NAME, APP_ID)).thenReturn(getTaskReferenceData(APP_ID));
        final JsonObject eventPayload = getEventPayload(APPLICATION_CREATED_WITHOUT_CASEID_URN_JSON, DEFENCE, APP_ID, MAGISTRATES_JURISDICTION_TYPE);

        // when
        applicationCreatedEventProcessor.handleCourtApplicationCreatedProcessor((envelopeFrom(metadataWithRandomUUID("public.progression.court-application-created"), eventPayload)));

        // Then
        verify(runtimeService, times(1)).startProcessInstanceByKey(eq(APPLICATION_CREATED_BPMN_PROCESS_NAME), eq(APP_ID), processVariablesCaptor.capture());

        Map processVariables = processVariablesCaptor.getValue();
        assertThat(processVariables.size(), is(13));
        assertThat(processVariables.get(APPLICATION_ID_FIELD), is(APP_ID));
        assertThat(processVariables.get(CASE_ID), is(EMPTY));
        final JsonObject courtApplication = eventPayload.getJsonObject("courtApplication");
        final JsonArray caseDetails = courtApplication.getJsonArray("courtApplicationCases");
        final JsonObject caseDetailsOne = caseDetails.getJsonObject(0);
        final JsonObject prosecutionCaseIdentifier = caseDetailsOne.getJsonObject("prosecutionCaseIdentifier");
        assertThat(processVariables.get(CASE_URN), is(EMPTY));
        final JsonObject subject = courtApplication.getJsonObject("subject");
        final String defendantId = subject.getString("id");
        final String defendantFirstName = subject.getJsonObject("personDetails").getString("firstName");
        final String defendantLastName = subject.getJsonObject("personDetails").getString("lastName");
        final String defendantName = defendantFirstName + SPACE + defendantLastName;
        assertThat(processVariables.get(DEFENDANT_ID), is(defendantId));
        assertThat(processVariables.get(DEFENDANT_NAME), is(defendantName));
        assertThat(processVariables.get(IS_APPLICATION_PROSECUTOR_DEFENCE_FIELD), is(true));
        assertThat(processVariables.get(APP_TYPE_FIELD), is(courtApplication.getJsonObject(TYPE).getString(TYPE)));

        assertThat(processVariables.get(LAST_UPDATED_BY_ID), is(SYSTEM_USER_ID.toString()));
        assertThat(processVariables.get(LAST_UPDATED_BY_NAME), is(SYSTEM_USER_NAME));
        assertThat(processVariables.get(JURISDICTION), is(MAGISTRATES_JURISDICTION_TYPE));
        assertThat(processVariables.get(WORK_QUEUE), is("ApplicationCreatedWorkQueue"));
        assertThat(processVariables.get(DEEP_LINK), is("https://dummyUrl/prosecution-casefile/application-at-a-glance/".concat(APP_ID)));
    }


    @Test
    public void shouldStartCourtApplicationCreatedWithApplicationByOtherWithAllFieldsInJsonAsCrownCourtType() {

        // Given
        when(taskTypeService.getTaskVariablesFromRefData(APPLICATION_TASK_NAME, APP_ID)).thenReturn(getTaskReferenceData(APP_ID));
        final JsonObject eventPayload = getEventPayload(APPLICATION_CREATED_JSON, "OTHER", APP_ID, CROWN_JURISDICTION_TYPE);


        // when
        applicationCreatedEventProcessor.handleCourtApplicationCreatedProcessor((envelopeFrom(metadataWithRandomUUID("public.progression.court-application-created"), eventPayload)));

        // Then
        verify(runtimeService, times(1)).startProcessInstanceByKey(eq(APPLICATION_CREATED_BPMN_PROCESS_NAME), eq(APP_ID), processVariablesCaptor.capture());

        Map processVariables = processVariablesCaptor.getValue();
        assertThat(processVariables.size(), is(13));
        assertThat(processVariables.get(APPLICATION_ID_FIELD), is(APP_ID));
        final JsonObject courtApplication = eventPayload.getJsonObject("courtApplication");
        final JsonArray caseDetails = courtApplication.getJsonArray("courtApplicationCases");
        final JsonObject caseDetailsOne = caseDetails.getJsonObject(0);
        final String caseId = caseDetailsOne.getString("prosecutionCaseId");
        assertThat(processVariables.get(CASE_ID), is(caseId));
        final JsonObject prosecutionCaseIdentifier = caseDetailsOne.getJsonObject("prosecutionCaseIdentifier");
        assertThat(processVariables.get(CASE_URN), is(prosecutionCaseIdentifier.getString(CASE_URN)));
        final JsonObject subject = courtApplication.getJsonObject("subject");
        final String defendantId = subject.getString("id");
        final String defendantFirstName = subject.getJsonObject("personDetails").getString("firstName");
        final String defendantLastName = subject.getJsonObject("personDetails").getString("lastName");
        final String defendantName = defendantFirstName + SPACE + defendantLastName;
        assertThat(processVariables.get(DEFENDANT_ID), is(defendantId));
        assertThat(processVariables.get(DEFENDANT_NAME), is(defendantName));
        assertThat(processVariables.get(IS_APPLICATION_PROSECUTOR_DEFENCE_FIELD), is(false));
        assertThat(processVariables.get(APP_TYPE_FIELD), is(courtApplication.getJsonObject(TYPE).getString(TYPE)));

        assertThat(processVariables.get(LAST_UPDATED_BY_ID), is(SYSTEM_USER_ID.toString()));
        assertThat(processVariables.get(LAST_UPDATED_BY_NAME), is(SYSTEM_USER_NAME));
        assertThat(processVariables.get(JURISDICTION), is(CROWN_JURISDICTION_TYPE));
        assertThat(processVariables.get(WORK_QUEUE), is(CROWN_COURT_ADMIN_WORK_QUEUE_ID));
        assertThat(processVariables.get(DEEP_LINK), is("https://dummyUrl/prosecution-casefile/application-at-a-glance/".concat(APP_ID)));
    }


    @Test
    public void shouldStartCourtApplicationCreatedForInternalApplicationWithAllFieldsInJson() {

        // Given
        when(taskTypeService.getTaskVariablesFromRefData(APPLICATION_TASK_NAME, APP_ID)).thenReturn(getTaskReferenceData(APP_ID));
        final JsonObject eventPayload = getEventPayloadWithOutApplicationExternalCreator(APPLICATION_CREATED_WITHOUT_EXTERNAL_CREATOR_JSON, APP_ID, MAGISTRATES_JURISDICTION_TYPE);


        // when
        applicationCreatedEventProcessor.handleCourtApplicationCreatedProcessor((envelopeFrom(metadataWithRandomUUID("public.progression.court-application-created"), eventPayload)));

        // Then
        verify(runtimeService, times(1)).startProcessInstanceByKey(eq(APPLICATION_CREATED_BPMN_PROCESS_NAME), eq(APP_ID), processVariablesCaptor.capture());

        Map processVariables = processVariablesCaptor.getValue();
        assertThat(processVariables.size(), is(13));
        assertThat(processVariables.get(APPLICATION_ID_FIELD), is(APP_ID));
        final JsonObject courtApplication = eventPayload.getJsonObject("courtApplication");
        final JsonArray caseDetails = courtApplication.getJsonArray("courtApplicationCases");
        final JsonObject caseDetailsOne = caseDetails.getJsonObject(0);
        final String caseId = caseDetailsOne.getString("prosecutionCaseId");
        assertThat(processVariables.get(CASE_ID), is(caseId));
        final JsonObject prosecutionCaseIdentifier = caseDetailsOne.getJsonObject("prosecutionCaseIdentifier");
        assertThat(processVariables.get(CASE_URN), is(prosecutionCaseIdentifier.getString(CASE_URN)));
        final JsonObject subject = courtApplication.getJsonObject("subject");
        final String defendantId = subject.getString("id");
        final String defendantFirstName = subject.getJsonObject("personDetails").getString("firstName");
        final String defendantLastName = subject.getJsonObject("personDetails").getString("lastName");
        final String defendantName = defendantFirstName + SPACE + defendantLastName;
        assertThat(processVariables.get(DEFENDANT_ID), is(defendantId));
        assertThat(processVariables.get(DEFENDANT_NAME), is(defendantName));
        assertThat(processVariables.get(IS_APPLICATION_PROSECUTOR_DEFENCE_FIELD), is(false));
        assertThat(processVariables.get(APP_TYPE_FIELD), is(courtApplication.getJsonObject(TYPE).getString(TYPE)));

        assertThat(processVariables.get(LAST_UPDATED_BY_ID), is(SYSTEM_USER_ID.toString()));
        assertThat(processVariables.get(LAST_UPDATED_BY_NAME), is(SYSTEM_USER_NAME));
        assertThat(processVariables.get(JURISDICTION), is(MAGISTRATES_JURISDICTION_TYPE));
        assertThat(processVariables.get(WORK_QUEUE), is("ApplicationCreatedWorkQueue"));
        assertThat(processVariables.get(DEEP_LINK), is("https://dummyUrl/prosecution-casefile/application-at-a-glance/".concat(APP_ID)));
    }


    private JsonObject getEventPayload(final String fileName, final String creator, final String id, final String courtType) {
        return getFileContentAsJson(fileName,
                ImmutableMap.<String, Object>builder()
                        .put("CREATOR_TYPE", creator)
                        .put("ID", id)
                        .put("JURISDICTION", courtType)
                        .build());
    }

    private JsonObject getEventPayloadWithOutApplicationExternalCreator(final String fileName, final String id, final String courtType) {
        return getFileContentAsJson(fileName,
                ImmutableMap.<String, Object>builder()
                        .put("ID", id)
                        .put("JURISDICTION", courtType)
                        .build());
    }

    @AfterEach
    public void tearDown() {
        Mocks.reset();
    }

    private Map<String, Object> getTaskReferenceData(final String referenceId) {
        final Map<String, Object> referenceData = new HashMap<>();
        referenceData.put(DEEP_LINK, "https://dummyUrl/prosecution-casefile/application-at-a-glance/".concat(referenceId));
        referenceData.put(WORK_QUEUE, "ApplicationCreatedWorkQueue");
        return referenceData;
    }
}