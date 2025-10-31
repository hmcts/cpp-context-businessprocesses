package uk.gov.moj.cpp.businessprocesses.event;

import static com.google.common.io.Resources.getResource;
import static java.nio.charset.Charset.defaultCharset;
import static java.util.Optional.of;
import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.justice.services.messaging.JsonEnvelope.envelopeFrom;
import static uk.gov.justice.services.test.utils.core.messaging.MetadataBuilderFactory.metadataWithRandomUUID;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.BPMN_PROCESS_BCM_WELSH_LANGUAGE_TRANSLATION_REQUEST;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.SYSTEM_USER_NAME;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.TASK_NAME_REQUEST_WLU_TO_TRANSLATE_WELSH_DOCUMENT;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.CASE_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.CASE_URN;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.COURT_FORM_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.HEARING_DATE;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.LAST_UPDATED_BY_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.LAST_UPDATED_BY_NAME;

import uk.gov.justice.services.common.converter.StringToJsonObjectConverter;
import uk.gov.justice.services.core.dispatcher.SystemUserProvider;
import uk.gov.justice.services.core.featurecontrol.FeatureControlGuard;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.moj.cpp.businessprocesses.service.TaskTypeService;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.json.JsonObject;

import com.google.common.io.Resources;
import org.camunda.bpm.engine.ProcessEngineConfiguration;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.test.Deployment;
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
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith({MockitoExtension.class, ProcessEngineExtension.class})
@MockitoSettings(strictness = Strictness.LENIENT)
public class FormFinalisedPublicEventProcessorTest {

    @RegisterExtension
    ProcessEngineExtension extension = ProcessEngineExtension.builder()
            .configurationResource("camunda.cfg.xml")
            .build();

    private static final String WORK_QUEUE = "workQueue";
    private static final String DEEP_LINK = "deepLink";

    private static final UUID SYSTEM_USER_ID = randomUUID();

    @InjectMocks
    private FormFinalisedPublicEventProcessor formFinalisedPublicEventProcessor;

    @Spy
    private StringToJsonObjectConverter stringToJsonObjectConverter = new StringToJsonObjectConverter();

    private static final String PUBLIC_PROGRESSION_FORM_FINALISED = "public.progression.form-finalised";

    @Mock
    private RuntimeService runtimeService;
    @Mock
    private SystemUserProvider systemUserProvider;
    @Mock
    private TaskTypeService taskTypeService;

    @Captor
    private ArgumentCaptor<Map> processVariablesCaptor;

    @Mock
    private FeatureControlGuard featureControlGuard;

    @RequiredHistoryLevel(ProcessEngineConfiguration.HISTORY_ACTIVITY)
    @BeforeEach
    public void setup() {
        when(systemUserProvider.getContextSystemUserId()).thenReturn(of(SYSTEM_USER_ID));
        when(featureControlGuard.isFeatureEnabled(any())).thenReturn(true);
    }

    @Test
    void shouldNotCallHandleFormFinalisedWhenFeatureIsDisabled() {
        when(featureControlGuard.isFeatureEnabled("camunda-progression-form-finalised")).thenReturn(false);
        JsonEnvelope jsonEnvelope = mock(JsonEnvelope.class);

        formFinalisedPublicEventProcessor.handleFormFinalisedProcessor(jsonEnvelope);

        verify(featureControlGuard, times(1)).isFeatureEnabled("camunda-progression-form-finalised");
        verifyNoInteractions(jsonEnvelope);
    }

    @Test
    @Deployment(resources = {"bcm_welsh_language_translation_request.bpmn"})
    public void testHandleFormFinalisedEventForWorkFlow() throws IOException {
        final UUID courtFormId = randomUUID();
        final UUID userId = randomUUID();
        final UUID caseId = randomUUID();
        when(taskTypeService.getTaskVariablesFromRefData(TASK_NAME_REQUEST_WLU_TO_TRANSLATE_WELSH_DOCUMENT, caseId.toString())).thenReturn(getTaskReferenceData(caseId.toString()));
        when(runtimeService.startProcessInstanceByKey(any(), any(), anyMap())).thenReturn(any());
        final String inputEvent = Resources.toString(getResource("finalised-form-event-data.json"), defaultCharset())
                .replaceAll("%caseId%", caseId.toString())
                .replaceAll("%courtFormId%", courtFormId.toString())
                .replaceAll("%userId%", userId.toString());
        final JsonObject payload = stringToJsonObjectConverter.convert(inputEvent);

        final JsonEnvelope requestEnvelope = envelopeFrom(
                metadataWithRandomUUID(PUBLIC_PROGRESSION_FORM_FINALISED)
                        .withUserId(userId.toString())
                , payload);
        formFinalisedPublicEventProcessor.handleFormFinalisedProcessor(requestEnvelope);

        // Then
        verify(runtimeService, times(1)).startProcessInstanceByKey(Mockito.eq(BPMN_PROCESS_BCM_WELSH_LANGUAGE_TRANSLATION_REQUEST), Mockito.eq(courtFormId.toString()), processVariablesCaptor.capture());

        Map processVariables = processVariablesCaptor.getValue();
        assertThat(processVariables.size(), is(8));

        assertThat(processVariables.get(COURT_FORM_ID), is(courtFormId.toString()));
        assertThat(processVariables.get(CASE_ID), is(caseId.toString()));
        assertThat(processVariables.get(DEEP_LINK), is(getDeepLink(caseId.toString())));
        assertThat(processVariables.get(WORK_QUEUE), is("ApplicationCreatedWorkQueue"));
        assertThat(processVariables.get(CASE_URN), is("TFL4359536"));
        assertThat(processVariables.get(LAST_UPDATED_BY_ID), is(SYSTEM_USER_ID.toString()));
        assertThat(processVariables.get(LAST_UPDATED_BY_NAME), is(SYSTEM_USER_NAME));
        assertThat(processVariables.get(HEARING_DATE), is("2025-12-22T10:45:00Z"));
    }

    @AfterEach
    public void tearDown() {
        Mocks.reset();
    }

    private Map<String, Object> getTaskReferenceData(final String referenceId) {
        final Map<String, Object> referenceData = new HashMap<>();
        referenceData.put(DEEP_LINK, getDeepLink(referenceId));
        referenceData.put(WORK_QUEUE, "ApplicationCreatedWorkQueue");
        return referenceData;
    }

    private String getDeepLink(final String referenceId) {
        return "https://dummyUrl/prosecution-casefile/case-at-a-glance/".concat(referenceId);
    }
}