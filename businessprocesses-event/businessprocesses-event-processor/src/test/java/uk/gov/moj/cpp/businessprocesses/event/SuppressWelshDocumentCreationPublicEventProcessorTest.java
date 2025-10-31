package uk.gov.moj.cpp.businessprocesses.event;

import static com.google.common.io.Resources.getResource;
import static java.nio.charset.Charset.defaultCharset;
import static java.util.Optional.of;
import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import org.hamcrest.core.Is;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Captor;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.justice.services.messaging.JsonEnvelope.envelopeFrom;
import static uk.gov.justice.services.test.utils.core.messaging.MetadataBuilderFactory.metadataWithRandomUUID;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.BPMN_PROCESS_WELSH_TRANSLATION_PROCESS;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.MATERIAL_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.CASE_URN;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.COURT_CODES;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.CUSTODY_TIME_LIMIT;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.DEFENDANT_NAME;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.HEARING_DATE;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.LAST_UPDATED_BY_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.LAST_UPDATED_BY_NAME;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.MASTER_DEFENDANT_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.TASK_NAME;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.TEMPLATE_NAME;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.WORK_QUEUE;

import org.camunda.bpm.extension.junit5.test.ProcessEngineExtension;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.justice.services.common.converter.StringToJsonObjectConverter;
import uk.gov.justice.services.core.dispatcher.SystemUserProvider;
import uk.gov.justice.services.core.featurecontrol.FeatureControlGuard;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.moj.cpp.businessprocesses.service.ListingService;
import uk.gov.moj.cpp.businessprocesses.service.ProgressionService;
import uk.gov.moj.cpp.businessprocesses.service.ReferenceDataService;
import uk.gov.moj.cpp.businessprocesses.service.TaskTypeService;
import uk.gov.moj.cpp.businessprocesses.util.TestDataProvider;

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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.TASK_NAME_TRANSLATE_TO_WELSH;

@ExtendWith({MockitoExtension.class, ProcessEngineExtension.class})
public class SuppressWelshDocumentCreationPublicEventProcessorTest {

    @RegisterExtension
    ProcessEngineExtension extension = ProcessEngineExtension.builder()
            .configurationResource("camunda.cfg.xml")
            .build();

    private static final UUID SYSTEM_USER_ID = randomUUID();

    @InjectMocks
    private SuppressWelshDocumentCreationPublicEventProcessor suppressWelshDocumentCreationPublicEventProcessor;

    @Spy
    private StringToJsonObjectConverter stringToJsonObjectConverter = new StringToJsonObjectConverter();

    private static final String PUBLIC_PROGRESSION_NOW_NOTIFICATION_SUPPRESSED = "public.progression.now-notification-suppressed";
    private static final String PUBLIC_HEARINGNOWS_NOW_NOTIFICATION_SUPPRESSED = "public.hearingnows.now-notification-suppressed";

    @Mock
    private RuntimeService runtimeService;

    @Mock
    private TaskTypeService taskTypeService;

    @Mock
    private ProgressionService progressionService;

    @Mock
    private ListingService listingService;

    @Mock
    private ReferenceDataService referenceDataService;

    @Mock
    private SystemUserProvider systemUserProvider;
    @Mock
    private FeatureControlGuard featureControlGuard;

    @Captor
    private ArgumentCaptor<Map> processVariablesCaptor;

    @RequiredHistoryLevel(ProcessEngineConfiguration.HISTORY_ACTIVITY)
    @BeforeEach
    public void setup() {
        extension.getProcessEngine().getRepositoryService().createDeployment().addClasspathResource("welsh-translation-process.bpmn");
        when(systemUserProvider.getContextSystemUserId()).thenReturn(of(SYSTEM_USER_ID));
    }

    @Test
    @Deployment(resources = {"welsh-translation-process.bpmn"})
    public void testHandleSupperWelshLanguageRequestEventForWorkFlow() throws IOException {
        final UUID masterDefendantId = randomUUID();
        final UUID materialId = randomUUID();
        when(taskTypeService.getTaskVariablesFromRefData(TASK_NAME_TRANSLATE_TO_WELSH, null)).thenReturn(getTaskReferenceData());
        when(progressionService.getProsecutionCaseExistByCaseUrn(any())).thenReturn("caseId");
        when(listingService.getHearings(any(), any())).thenReturn(TestDataProvider.getHearingList());
        when(referenceDataService.retrieveCourtCentreDetailsByCourtId(any())).thenReturn(TestDataProvider.getReferenceDataCourtRoomsPayload());

        final String inputEvent = Resources.toString(getResource("now_notification-suppressed.json"), defaultCharset())
                .replaceAll("%masterDefendantId%", masterDefendantId.toString())
                .replaceAll("%materialId%", materialId.toString());
        final JsonObject payload = stringToJsonObjectConverter.convert(inputEvent);

        final JsonEnvelope requestEnvelope = envelopeFrom(
                metadataWithRandomUUID(PUBLIC_PROGRESSION_NOW_NOTIFICATION_SUPPRESSED), payload);
        suppressWelshDocumentCreationPublicEventProcessor.handleNowNotificationSupressed(requestEnvelope);

        verify(runtimeService, times(1)).startProcessInstanceByKey(eq(BPMN_PROCESS_WELSH_TRANSLATION_PROCESS), eq(materialId.toString()), processVariablesCaptor.capture());

        Map processVariables = processVariablesCaptor.getValue();
        assertThat(processVariables.size(), Is.is(12));

        assertThat(processVariables.get(CASE_URN), Is.is("1123"));
        assertThat(processVariables.get(HEARING_DATE), Is.is("2025-08-01T09:00:00.000Z"));
        assertThat(processVariables.get(TASK_NAME), Is.is(TASK_NAME_TRANSLATE_TO_WELSH));
        assertThat(processVariables.get(WORK_QUEUE), Is.is("ApplicationCreatedWorkQueue"));
        assertThat(processVariables.get(MASTER_DEFENDANT_ID), Is.is(masterDefendantId.toString()));
        assertThat(processVariables.get(DEFENDANT_NAME), Is.is("testDefenddant"));
        assertThat(processVariables.get(COURT_CODES), Is.is("B62IZ00"));
        assertThat(processVariables.get(MATERIAL_ID), is(materialId.toString()));
        assertThat(processVariables.get(TEMPLATE_NAME), Is.is("test"));
        assertThat(processVariables.get(CUSTODY_TIME_LIMIT), Is.is("2020-11-19"));
        assertThat(processVariables.get(LAST_UPDATED_BY_ID), Is.is(SYSTEM_USER_ID.toString()));
        assertThat(processVariables.get(LAST_UPDATED_BY_NAME), Is.is("SYSTEM"));
    }

    @Test
    @Deployment(resources = {"welsh-translation-process.bpmn"})
    public void testHandleHearingNowsSuppressWelshLanguageRequestEventForWorkFlow() throws IOException {
        final UUID masterDefendantId = randomUUID();
        final UUID materialId = randomUUID();
        when(taskTypeService.getTaskVariablesFromRefData(TASK_NAME_TRANSLATE_TO_WELSH, null)).thenReturn(getTaskReferenceData());
        when(progressionService.getProsecutionCaseExistByCaseUrn(any())).thenReturn("caseId");
        when(listingService.getHearings(any(), any())).thenReturn(TestDataProvider.getHearingList());
        when(referenceDataService.retrieveCourtCentreDetailsByCourtId(any())).thenReturn(TestDataProvider.getReferenceDataCourtRoomsPayload());

        final String inputEvent = Resources.toString(getResource("now_notification-suppressed.json"), defaultCharset())
                .replaceAll("%masterDefendantId%", masterDefendantId.toString())
                .replaceAll("%materialId%", materialId.toString());
        final JsonObject payload = stringToJsonObjectConverter.convert(inputEvent);

        final JsonEnvelope requestEnvelope = envelopeFrom(
                metadataWithRandomUUID(PUBLIC_HEARINGNOWS_NOW_NOTIFICATION_SUPPRESSED), payload);
        suppressWelshDocumentCreationPublicEventProcessor.handleNowNotificationSupressed(requestEnvelope);

        verify(runtimeService, times(1)).startProcessInstanceByKey(eq(BPMN_PROCESS_WELSH_TRANSLATION_PROCESS), eq(materialId.toString()), processVariablesCaptor.capture());

        Map processVariables = processVariablesCaptor.getValue();
        assertThat(processVariables.size(), Is.is(12));

        assertThat(processVariables.get(CASE_URN), Is.is("1123"));
        assertThat(processVariables.get(HEARING_DATE), Is.is("2025-08-01T09:00:00.000Z"));
        assertThat(processVariables.get(TASK_NAME), Is.is(TASK_NAME_TRANSLATE_TO_WELSH));
        assertThat(processVariables.get(WORK_QUEUE), Is.is("ApplicationCreatedWorkQueue"));
        assertThat(processVariables.get(MASTER_DEFENDANT_ID), Is.is(masterDefendantId.toString()));
        assertThat(processVariables.get(DEFENDANT_NAME), Is.is("testDefenddant"));
        assertThat(processVariables.get(COURT_CODES), Is.is("B62IZ00"));
        assertThat(processVariables.get(MATERIAL_ID), is(materialId.toString()));
        assertThat(processVariables.get(TEMPLATE_NAME), Is.is("test"));
        assertThat(processVariables.get(CUSTODY_TIME_LIMIT), Is.is("2020-11-19"));
        assertThat(processVariables.get(LAST_UPDATED_BY_ID), Is.is(SYSTEM_USER_ID.toString()));
        assertThat(processVariables.get(LAST_UPDATED_BY_NAME), Is.is("SYSTEM"));
    }

    private Map<String, Object> getTaskReferenceData() {
        final Map<String, Object> referenceData = new HashMap<>();
        referenceData.put("taskName", TASK_NAME_TRANSLATE_TO_WELSH);
        referenceData.put("workQueue", "ApplicationCreatedWorkQueue");
        return referenceData;
    }

    @AfterEach
    public void tearDown() {
        Mocks.reset();
    }
}