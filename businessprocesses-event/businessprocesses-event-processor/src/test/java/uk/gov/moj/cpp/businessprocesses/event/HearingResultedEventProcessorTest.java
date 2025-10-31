package uk.gov.moj.cpp.businessprocesses.event;

import static com.google.common.io.Resources.getResource;
import static java.nio.charset.Charset.defaultCharset;
import static java.util.Optional.of;
import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.justice.services.messaging.JsonEnvelope.envelopeFrom;
import static uk.gov.justice.services.test.utils.core.messaging.MetadataBuilderFactory.metadataWithRandomUUID;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.CASE_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.CASE_URN;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.COURT_CODES;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.HEARING_DATE;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.HEARING_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.LAST_UPDATED_BY_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.LAST_UPDATED_BY_NAME;

import uk.gov.justice.services.common.converter.JsonObjectToObjectConverter;
import uk.gov.justice.services.common.converter.StringToJsonObjectConverter;
import uk.gov.justice.services.common.converter.jackson.ObjectMapperProducer;
import uk.gov.justice.services.core.dispatcher.SystemUserProvider;
import uk.gov.justice.services.core.featurecontrol.FeatureControlGuard;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.moj.cpp.businessprocesses.event.summonsapplication.SummonsApplicationHandler;
import uk.gov.moj.cpp.businessprocesses.refdata.query.api.Resultdefinition;
import uk.gov.moj.cpp.businessprocesses.service.ReferenceDataService;
import uk.gov.moj.cpp.businessprocesses.service.TaskTypeService;
import uk.gov.moj.cpp.businessprocesses.service.UserGroupsService;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

import javax.json.JsonObject;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.Resources;
import org.camunda.bpm.engine.ProcessEngineConfiguration;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.test.Deployment;
import org.camunda.bpm.engine.test.RequiredHistoryLevel;
import org.camunda.bpm.extension.junit5.test.ProcessEngineExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.moj.cpp.businessprocesses.shared.InterpreterForWelshActivityHandler;

@ExtendWith({MockitoExtension.class, ProcessEngineExtension.class})
public class HearingResultedEventProcessorTest {

    @RegisterExtension
    ProcessEngineExtension extension = ProcessEngineExtension.builder()
            .configurationResource("camunda.cfg.xml")
            .build();
    private static final UUID SYSTEM_USER_ID = randomUUID();
    public static final String LIST_BAIL_APPEAL_BPMN = "list_bail_appeal_hearing_process.bpmn";
    public static final String LIST_SERIOUS_CASE_BAIL_BPMN = "list_murder_case_for_bail_hearing_process.bpmn";
    public static final String HEARING_RESULTED_JSON = "json/public.events.hearing.hearing-resulted.json";

    public static final String HEARING_RESULTED_JSON_NEXT_CC = "json/public.events.hearing.hearing-resulted_next_hearing_cc.json";
    private static final String HEARING_RESULTED_EVENT = "public.events.hearing.hearing-resulted";

    public static final String SENSITIVE_CASE_TRANSFER = "update_access_to_sensitive_case_process.bpmn";

    public static final String TASK_NAME_UPDATE_ACCESS_TO_SENSITIVE_CASE = "update_access_to_sensitive_case";

    @InjectMocks
    private HearingResultedProcessor hearingResultedProcessor;

    @Spy
    private final StringToJsonObjectConverter stringToJsonObjectConverter = new StringToJsonObjectConverter();


    @Spy
    private final ObjectMapper objectMapper = new ObjectMapperProducer().objectMapper();

    @Spy
    private final JsonObjectToObjectConverter jsonToObjectConverter = new JsonObjectToObjectConverter(objectMapper);

    @Mock
    private RuntimeService runtimeService;

    @Mock
    private ReferenceDataService referenceDataService;

    @Mock
    private UserGroupsService userGroupsService;

    @Mock
    private TaskTypeService taskTypeService;

    @Mock
    private InterpreterForWelshActivityHandler interpreterForWelshActivityHandler;

    @Mock
    private SystemUserProvider systemUserProvider;

    @Mock
    private FeatureControlGuard featureControlGuard;

    @Mock
    private SummonsApplicationHandler summonsApplicationHandler;

    @Captor
    private ArgumentCaptor<Map> processVariablesCaptor;

    @RequiredHistoryLevel(ProcessEngineConfiguration.HISTORY_ACTIVITY)
    @BeforeEach
    public void setup() {
        extension.getProcessEngine().getRepositoryService().createDeployment().addClasspathResource(LIST_BAIL_APPEAL_BPMN);
        extension.getProcessEngine().getRepositoryService().createDeployment().addClasspathResource(LIST_SERIOUS_CASE_BAIL_BPMN);
        extension.getProcessEngine().getRepositoryService().createDeployment().addClasspathResource(SENSITIVE_CASE_TRANSFER);
    }

    @Test
    void shouldNotCallHandleHearingResultedWhenFeatureIsDisabled() {
        when(featureControlGuard.isFeatureEnabled("camunda-hearing-hearing-resulted")).thenReturn(false);
        JsonEnvelope jsonEnvelope = mock(JsonEnvelope.class);

        hearingResultedProcessor.handleHearingResultedProcessor(jsonEnvelope);

        verify(featureControlGuard, times(1)).isFeatureEnabled("camunda-hearing-hearing-resulted");
        verifyNoInteractions(jsonEnvelope);
    }

    @Test
    @Deployment(resources = {LIST_BAIL_APPEAL_BPMN})
    public void shouldCreateListBailAppealEventWorkFlow() throws IOException {
        final UUID userId = randomUUID();
        when(featureControlGuard.isFeatureEnabled(any())).thenReturn(true);
        when(systemUserProvider.getContextSystemUserId()).thenReturn(of(SYSTEM_USER_ID));
        when(referenceDataService.getResultDefinition(anyString())).thenReturn(Resultdefinition.resultdefinition().withShortCode("EXRIBA48").build());
        final String inputEvent = getInputEvent(HEARING_RESULTED_JSON).replace("HEARING_ID", randomUUID().toString());
        final JsonObject payload = stringToJsonObjectConverter.convert(inputEvent);
        final JsonEnvelope requestEnvelope = envelopeFrom(metadataWithRandomUUID(HEARING_RESULTED_EVENT).withUserId(userId.toString()), payload);


        hearingResultedProcessor.handleHearingResultedProcessor(requestEnvelope);
        verify(runtimeService, times(1)).startProcessInstanceByKey(anyString(), anyString(), anyMap());
//        verify(interpreterForWelshActivityHandler, times(1)).handleWelshInterpreterForCaseResulted(any(), any());
    }

    @Test
    @Deployment(resources = {LIST_BAIL_APPEAL_BPMN})
    public void shouldNotCreateListBailAppealEventWorkFlowWhenFeatureIsDisabled() throws IOException {
        final UUID userId = randomUUID();
        when(featureControlGuard.isFeatureEnabled(any())).thenReturn(false);
        final String inputEvent = getInputEvent(HEARING_RESULTED_JSON).replace("HEARING_ID", randomUUID().toString());
        final JsonObject payload = stringToJsonObjectConverter.convert(inputEvent);
        final JsonEnvelope requestEnvelope = envelopeFrom(metadataWithRandomUUID(HEARING_RESULTED_EVENT).withUserId(userId.toString()), payload);


        hearingResultedProcessor.handleHearingResultedProcessor(requestEnvelope);
        verify(runtimeService, times(0)).startProcessInstanceByKey(anyString(), anyString(), anyMap());
    }

    @Test
    @Deployment(resources = {LIST_SERIOUS_CASE_BAIL_BPMN})
    public void shouldCreateListSeriousBailAppealEventWorkFlow() throws IOException {
        final UUID userId = randomUUID();
        when(featureControlGuard.isFeatureEnabled(any())).thenReturn(true);
        when(systemUserProvider.getContextSystemUserId()).thenReturn(of(SYSTEM_USER_ID));
        when(referenceDataService.getResultDefinition(anyString())).thenReturn(Resultdefinition.resultdefinition().withShortCode("CCQB").build());
        final String inputEvent = getInputEvent(HEARING_RESULTED_JSON).replace("HEARING_ID", randomUUID().toString());
        final JsonObject payload = stringToJsonObjectConverter.convert(inputEvent);
        final JsonEnvelope requestEnvelope = envelopeFrom(metadataWithRandomUUID(HEARING_RESULTED_EVENT).withUserId(userId.toString()), payload);

        hearingResultedProcessor.handleHearingResultedProcessor(requestEnvelope);
        verify(runtimeService, times(1)).startProcessInstanceByKey(anyString(), anyString(), anyMap());
//        verify(interpreterForWelshActivityHandler, times(1)).handleWelshInterpreterForCaseResulted(any(), any());
    }

    @Test
    @Deployment(resources = {LIST_SERIOUS_CASE_BAIL_BPMN})
    public void shouldNotCreateListSeriousBailAppealEventWorkFlow() throws IOException {
        final UUID userId = randomUUID();
        when(featureControlGuard.isFeatureEnabled(any())).thenReturn(false);
        final String inputEvent = getInputEvent(HEARING_RESULTED_JSON).replace("HEARING_ID", randomUUID().toString());
        final JsonObject payload = stringToJsonObjectConverter.convert(inputEvent);
        final JsonEnvelope requestEnvelope = envelopeFrom(metadataWithRandomUUID(HEARING_RESULTED_EVENT).withUserId(userId.toString()), payload);

        hearingResultedProcessor.handleHearingResultedProcessor(requestEnvelope);
        verify(runtimeService, times(0)).startProcessInstanceByKey(anyString(), anyString(), anyMap());
    }

    @Test
    @Deployment(resources = {SENSITIVE_CASE_TRANSFER})
    public void shouldCreateSensitiveCaseTransferEventWorkFlow() throws IOException {
        final UUID userId = randomUUID();
        final String hearingId = randomUUID().toString();
        when(featureControlGuard.isFeatureEnabled(any())).thenReturn(true);
        when(userGroupsService.getCaseSensitive(anyString())).thenReturn(true);
        when(systemUserProvider.getContextSystemUserId()).thenReturn(of(SYSTEM_USER_ID));
        when(referenceDataService.getResultDefinition(anyString())).thenReturn(Resultdefinition.resultdefinition().withShortCode("NHCCS").build());

        final String inputEvent = getInputEvent(HEARING_RESULTED_JSON_NEXT_CC).replace("HEARING_ID", hearingId);
        final JsonObject payload = stringToJsonObjectConverter.convert(inputEvent);
        final JsonEnvelope requestEnvelope = envelopeFrom(metadataWithRandomUUID(HEARING_RESULTED_EVENT).withUserId(userId.toString()), payload);


        hearingResultedProcessor.handleHearingResultedProcessor(requestEnvelope);

        verify(runtimeService, times(1)).startProcessInstanceByKey(eq("update_access_to_sensitive_case_process"), eq("55df6649-8c4d-4614-a1db-a54b70cdc306"), processVariablesCaptor.capture());

        Map processVariables = processVariablesCaptor.getValue();
        assertThat(processVariables.size(), is(8));
        assertThat(processVariables.get(HEARING_DATE), is("2019-06-03T10:29:00.000Z"));
        assertThat(processVariables.get(CASE_URN), is("76GD6351219"));
        assertThat(processVariables.get(CASE_ID), is("55df6649-8c4d-4614-a1db-a54b70cdc306"));
        assertThat(processVariables.get(HEARING_ID), is(hearingId));
        assertThat(processVariables.get(LAST_UPDATED_BY_NAME), is("SYSTEM"));
        assertThat(processVariables.get(LAST_UPDATED_BY_ID), is(SYSTEM_USER_ID.toString()));
        assertThat(processVariables.get(COURT_CODES), is("1234"));
    }

    @Test
    @Deployment(resources = {SENSITIVE_CASE_TRANSFER})
    public void shouldNotCreateSensitiveCaseTransferEventWorkFlowWhenFeatureIsDisabled() throws IOException {
        final UUID userId = randomUUID();
        when(featureControlGuard.isFeatureEnabled(any())).thenReturn(false);

        final String inputEvent = getInputEvent(HEARING_RESULTED_JSON_NEXT_CC).replace("HEARING_ID", randomUUID().toString());
        final JsonObject payload = stringToJsonObjectConverter.convert(inputEvent);
        final JsonEnvelope requestEnvelope = envelopeFrom(metadataWithRandomUUID(HEARING_RESULTED_EVENT).withUserId(userId.toString()), payload);


        hearingResultedProcessor.handleHearingResultedProcessor(requestEnvelope);
        verify(runtimeService, times(0)).startProcessInstanceByKey(anyString(), anyString(), anyMap());
    }

    @Test
    @Deployment(resources = {SENSITIVE_CASE_TRANSFER})
    public void shouldNotCreateSensitiveCaseTransferEventWorkFlowWhenCaseSensitiveIsFalse() throws IOException {
        final UUID userId = randomUUID();
        when(featureControlGuard.isFeatureEnabled(any())).thenReturn(true);
        when(userGroupsService.getCaseSensitive(anyString())).thenReturn(false);
        when(referenceDataService.getResultDefinition(anyString())).thenReturn(Resultdefinition.resultdefinition().withShortCode("NHCCS").build());

        final String inputEvent = getInputEvent(HEARING_RESULTED_JSON_NEXT_CC).replace("HEARING_ID", randomUUID().toString());
        final JsonObject payload = stringToJsonObjectConverter.convert(inputEvent);
        final JsonEnvelope requestEnvelope = envelopeFrom(metadataWithRandomUUID(HEARING_RESULTED_EVENT).withUserId(userId.toString()), payload);


        hearingResultedProcessor.handleHearingResultedProcessor(requestEnvelope);
        verify(runtimeService, times(0)).startProcessInstanceByKey(anyString(), anyString(), anyMap());
    }


    private String getInputEvent(final String resourceName) throws IOException {
        return Resources.toString(getResource(resourceName), defaultCharset());
    }

}