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
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.CASE_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.CASE_URN;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.CUSTODY_TIME_LIMIT;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.DEFENDANT_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.DEFENDANT_NAME;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.JURISDICTION;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.NOTE;
import static uk.gov.moj.cpp.businessprocesses.util.FileUtil.getFileContentAsJson;

import uk.gov.justice.services.common.converter.JsonObjectToObjectConverter;
import uk.gov.justice.services.common.converter.jackson.ObjectMapperProducer;
import uk.gov.justice.services.core.dispatcher.SystemUserProvider;
import uk.gov.justice.services.core.featurecontrol.FeatureControlGuard;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.moj.cpp.businessprocesses.service.ReferenceDataService;
import uk.gov.moj.cpp.businessprocesses.service.TaskTypeService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.json.JsonObject;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import org.camunda.bpm.engine.RuntimeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import uk.gov.moj.cpp.businessprocesses.util.TestDataProvider;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class CaseHearingResultedEventProcessorTest {

    private static final String CASE_HEARING_RESULTED_JSON = "caseHearing-resulted.json";
    private static final String CASE_HEARING_RESULTED_MULTIPLE_CASE_JSON = "caseHearing-resulted-multipleCases.json";
    private static final String CASE_HEARING_RESULTS_BPMN_PROCESS_NAME = "case-results";

    private static final String REMOVE_DDJ_FROM_HEARING_TASK_NAME = "Remove_DDJ_from_hearing";
    private static final String SEND_DOCUMENTS_TO_PRISON_TASK_NAME = "Send_documents_to_prison";

    private static final String DEEP_LINK = "deepLink";
    private static final String WORK_QUEUE_FIELD = "workQueue";
    private static final String LAST_UPDATED_BY_ID = "lastUpdatedByID";
    private static final String LAST_UPDATED_BY_NAME = "lastUpdatedByName";
    private static final String COURT_CODES = "courtCodes";

    private static final String SEND_DOCUMENTS_TO_PRISON_PREFIX = "sendDocumentsToPrison_";

    // Values
    private static final String HEARING_ID = randomUUID().toString();
    private static final String PROSECUTION_CASE_ID1 = randomUUID().toString();
    private static final String PROSECUTION_CASE_ID2 = randomUUID().toString();
    private static final String PROSECUTION_CASE_ID3 = randomUUID().toString();
    private static final String PROSECUTION_CASE_ID4 = randomUUID().toString();
    private static final String JURISDICTION_TYPE_MAGISTRATES = "MAGISTRATES";
    private static final UUID SYSTEM_USER_ID = randomUUID();
    private static final String SYSTEM_USER_NAME = "SYSTEM";
    private static final String WORK_QUEUE = randomUUID().toString();
    private static final String CASE_URN1 = "TFL34652354";
    private static final String CASE_URN2 = "TFL12345678";
    private static final String CASE_URN3 = "TFL98765432";
    private static final String CASE_URN4 = "TFL11111111";
    private static final String DEFENDANT_ID1 = randomUUID().toString();
    private static final String DEFENDANT_ID1_1 = randomUUID().toString();
    private static final String DEFENDANT_ID2_1 = randomUUID().toString();
    private static final String DEFENDANT_ID2_2 = randomUUID().toString();
    private static final String DEFENDANT_ID3_1 = randomUUID().toString();
    private static final String DEFENDANT_ID3_2 = randomUUID().toString();
    private static final String DEFENDANT_ID4_1 = randomUUID().toString();
    private static final String DEFENDANT_ID4_2 = randomUUID().toString();
    private static final String REMAND_STATUS = "Custody or remanded into custody";
    private static final String FIRST_NAME = "John";
    private static final String LAST_NAME = "Doe";
    private static final List<String> SEND_DOCUMENT_TO_PRISON_RESULT_CODES = Stream.of("1002", "1007", "1024", "1081", "1088", "1507", "3132", "4046", "4049", "4051")
            .collect(Collectors.toList());
    private static final String CJS_CODE_INVALID_FOR_PRISON_TRANSFER = "6789";
    private static final String CROWN_COURT_ADMIN_WORK_QUEUE_ID = "5cd9bd67-1f08-315b-9608-8eb7ebb7ea2f";
    private static final String CUSTODY_TIME_LIMIT_VAL = "2026-05-21";
    private static final String CUSTODY_TIME_LIMIT_VAL2 = "2027-05-21";

    private static final String CASE_HEARING_ID1 = UUID.nameUUIDFromBytes((HEARING_ID + PROSECUTION_CASE_ID1).getBytes()).toString();
    private static final String CASE_HEARING_ID2 = UUID.nameUUIDFromBytes((HEARING_ID + PROSECUTION_CASE_ID2).getBytes()).toString();
    private static final String CASE_HEARING_ID3 = UUID.nameUUIDFromBytes((HEARING_ID + PROSECUTION_CASE_ID3).getBytes()).toString();
    private static final String CASE_HEARING_ID4 = UUID.nameUUIDFromBytes((HEARING_ID + PROSECUTION_CASE_ID4).getBytes()).toString();
    public static final String PRISON_NAME = "prisonName";
    public static final String DEFENDANT_REMAND_STATUS = "defendantRemandStatus";
    public static final String HEARING_DATE = "hearingDate";
    public static final String HEARING_ID_LABEL = "hearingId";
    public static final String HAS_CUSTODIAL_RESULTS = "hasCustodialResults";
    private static final String EXECUTE_INSTANTLY = "executeInstantly";


    @InjectMocks
    private CaseHearingResultedEventProcessor caseHearingResultedEventProcessor;

    @Mock
    private RuntimeService runtimeService;

    @Mock
    private TaskTypeService taskTypeService;

    @Mock
    private ReferenceDataService referenceDataService;

    @Captor
    private ArgumentCaptor<Map> processVariablesCaptor1;

    @Captor
    private ArgumentCaptor<Map> processVariablesCaptor2;

    @Captor
    private ArgumentCaptor<Map> processVariablesCaptor3;

    @Captor
    private ArgumentCaptor<Map> processVariablesCaptor4;

    @Mock
    private SystemUserProvider systemUserProvider;

    @Mock
    private FeatureControlGuard featureControlGuard;

    @Spy
    private final ObjectMapper objectMapper = new ObjectMapperProducer().objectMapper();

    @Spy
    private final JsonObjectToObjectConverter jsonToObjectConverter = new JsonObjectToObjectConverter(objectMapper);

    @BeforeEach
    public void setup() throws IOException {
        when(taskTypeService.getTaskVariablesFromRefData(REMOVE_DDJ_FROM_HEARING_TASK_NAME, PROSECUTION_CASE_ID1, "2022-10-04T12:00:15.351Z", null)).thenReturn(getTaskReferenceData(PROSECUTION_CASE_ID1));
        when(taskTypeService.getTaskVariablesFromRefData(SEND_DOCUMENTS_TO_PRISON_TASK_NAME, PROSECUTION_CASE_ID1, "2022-10-04T12:00:15.351Z", null)).thenReturn(getTaskReferenceData(PROSECUTION_CASE_ID1));
        when(systemUserProvider.getContextSystemUserId()).thenReturn(of(SYSTEM_USER_ID));
        when(featureControlGuard.isFeatureEnabled(any())).thenReturn(true);
        when(referenceDataService.retrieveCourtCentreDetailsByCourtRoomName(any())).thenReturn(TestDataProvider.getReferenceDataCourtRoomsPayload());
    }

    @Test
    void shouldNotCallHandleHearingResultedWhenFeatureIsDisabled() {
        when(featureControlGuard.isFeatureEnabled("camunda-progression-hearing-resulted")).thenReturn(false);
        JsonEnvelope jsonEnvelope = mock(JsonEnvelope.class);

        caseHearingResultedEventProcessor.handleHearingResultedProcessor(jsonEnvelope);

        verify(featureControlGuard, times(1)).isFeatureEnabled("camunda-progression-hearing-resulted");
        verifyNoInteractions(jsonEnvelope);
    }

    @Test
    public void shouldStartCaseResultedProcessForMagistratesHearingWithAllResultsFinalForCase() {

        // Given
        final JsonObject eventPayload = getEventPayload(CASE_HEARING_RESULTED_JSON, HEARING_ID, JURISDICTION_TYPE_MAGISTRATES, PROSECUTION_CASE_ID1, "INACTIVE");
        when(taskTypeService.getTaskVariablesFromRefData(SEND_DOCUMENTS_TO_PRISON_TASK_NAME, PROSECUTION_CASE_ID1, "2022-10-04T12:00:15.351Z", null)).thenReturn(getTaskReferenceData(PROSECUTION_CASE_ID1));

        // when
        caseHearingResultedEventProcessor.handleHearingResultedProcessor((envelopeFrom(metadataWithRandomUUID("public.progression.hearing-resulted"), eventPayload)));

        // Then
        verify(runtimeService, times(1)).startProcessInstanceByKey(Mockito.eq(CASE_HEARING_RESULTS_BPMN_PROCESS_NAME), Mockito.eq(CASE_HEARING_ID1), processVariablesCaptor1.capture());

        final Map processVariables = processVariablesCaptor1.getValue();
        assertThat(processVariables.size(), is(12));

        assertThat(processVariables.get(EXECUTE_INSTANTLY), is(false));
        assertThat(processVariables.get(HAS_CUSTODIAL_RESULTS), is(false));
        assertThat(processVariables.get(CASE_ID), is(PROSECUTION_CASE_ID1));
        assertThat(processVariables.get(LAST_UPDATED_BY_ID), is(SYSTEM_USER_ID.toString()));
        assertThat(processVariables.get(LAST_UPDATED_BY_NAME), is(SYSTEM_USER_NAME));
        assertThat(processVariables.get(JURISDICTION), is(JURISDICTION_TYPE_MAGISTRATES));
        assertThat(processVariables.get(HEARING_ID_LABEL), is(HEARING_ID));
        assertThat(processVariables.get(HEARING_DATE), is("2022-10-04T12:00:15.351Z"));
        assertThat(processVariables.get(DEEP_LINK), is("https://dummyUrl/prosecution-casefile/case-at-a-glance/".concat(PROSECUTION_CASE_ID1)));
        assertThat(processVariables.get(WORK_QUEUE_FIELD), is(WORK_QUEUE));
        assertThat(processVariables.get(COURT_CODES), is("B01LY00"));
        assertThat(processVariables.get(CUSTODY_TIME_LIMIT), is(CUSTODY_TIME_LIMIT_VAL));
    }

    @Test
    public void shouldStartCaseResultedProcessForMagistratesHearingWithAllResultsFinalForCaseWIthMultipleCases() {

        // Given
        final JsonObject eventPayload = getEventPayload(CASE_HEARING_RESULTED_MULTIPLE_CASE_JSON, HEARING_ID, JURISDICTION_TYPE_MAGISTRATES, PROSECUTION_CASE_ID1, PROSECUTION_CASE_ID2, PROSECUTION_CASE_ID3, "INACTIVE");

        // when
        when(systemUserProvider.getContextSystemUserId()).thenReturn(of(SYSTEM_USER_ID));
        when(taskTypeService.getTaskVariablesFromRefData(SEND_DOCUMENTS_TO_PRISON_TASK_NAME, PROSECUTION_CASE_ID2, "2022-10-04T12:00:15.351Z", null)).thenReturn(getTaskReferenceData(PROSECUTION_CASE_ID2));
        when(taskTypeService.getTaskVariablesFromRefData(SEND_DOCUMENTS_TO_PRISON_TASK_NAME, PROSECUTION_CASE_ID3, "2022-10-04T12:00:15.351Z", null)).thenReturn(getTaskReferenceData(PROSECUTION_CASE_ID3));
        caseHearingResultedEventProcessor.handleHearingResultedProcessor((envelopeFrom(metadataWithRandomUUID("public.progression.hearing-resulted"), eventPayload)));

        // Then
        verify(runtimeService, times(1)).startProcessInstanceByKey(Mockito.eq(CASE_HEARING_RESULTS_BPMN_PROCESS_NAME), Mockito.eq(CASE_HEARING_ID1), processVariablesCaptor1.capture());
        verify(runtimeService, times(1)).startProcessInstanceByKey(Mockito.eq(CASE_HEARING_RESULTS_BPMN_PROCESS_NAME), Mockito.eq(CASE_HEARING_ID2), processVariablesCaptor2.capture());
        verify(runtimeService, times(1)).startProcessInstanceByKey(Mockito.eq(CASE_HEARING_RESULTS_BPMN_PROCESS_NAME), Mockito.eq(CASE_HEARING_ID3), processVariablesCaptor3.capture());

        final Map processVariables1 = processVariablesCaptor1.getValue();
        assertThat(processVariables1.size(), is(12));

        assertThat(processVariables1.get(EXECUTE_INSTANTLY), is(false));
        assertThat(processVariables1.get(HAS_CUSTODIAL_RESULTS), is(false));
        final String prosecutionCaseId1 = processVariables1.get(CASE_ID).toString();
        assertThat(prosecutionCasesList().contains(prosecutionCaseId1), is(true));
        assertThat(processVariables1.get(LAST_UPDATED_BY_ID), is(SYSTEM_USER_ID.toString()));
        assertThat(processVariables1.get(LAST_UPDATED_BY_NAME), is(SYSTEM_USER_NAME));
        assertThat(processVariables1.get(JURISDICTION), is(JURISDICTION_TYPE_MAGISTRATES));
        assertThat(processVariables1.get(HEARING_ID_LABEL), is(HEARING_ID));
        assertThat(processVariables1.get(HEARING_DATE), is("2022-10-04T12:00:15.351Z"));
        assertThat(processVariables1.get(DEEP_LINK), is("https://dummyUrl/prosecution-casefile/case-at-a-glance/".concat(prosecutionCaseId1)));
        assertThat(processVariables1.get(WORK_QUEUE_FIELD), is(WORK_QUEUE));
        assertThat(processVariables1.get(COURT_CODES), is("B01LY00"));
        assertThat(processVariables1.get(CUSTODY_TIME_LIMIT), is(CUSTODY_TIME_LIMIT_VAL2));

        final Map processVariables2 = processVariablesCaptor2.getValue();
        assertThat(processVariables2.size(), is(12));

        assertThat(processVariables2.get(EXECUTE_INSTANTLY), is(false));
        assertThat(processVariables2.get(HAS_CUSTODIAL_RESULTS), is(false));
        final String prosecutionCaseId2 = processVariables2.get(CASE_ID).toString();

        assertThat(prosecutionCasesList().contains(prosecutionCaseId2), is(true));
        assertThat(processVariables2.get(LAST_UPDATED_BY_ID), is(SYSTEM_USER_ID.toString()));
        assertThat(processVariables2.get(LAST_UPDATED_BY_NAME), is(SYSTEM_USER_NAME));
        assertThat(processVariables2.get(JURISDICTION), is(JURISDICTION_TYPE_MAGISTRATES));
        assertThat(processVariables2.get(HEARING_ID_LABEL), is(HEARING_ID));
        assertThat(processVariables2.get(HEARING_DATE), is("2022-10-04T12:00:15.351Z"));
        assertThat(processVariables2.get(DEEP_LINK), is("https://dummyUrl/prosecution-casefile/case-at-a-glance/".concat(prosecutionCaseId2)));
        assertThat(processVariables2.get(WORK_QUEUE_FIELD), is(WORK_QUEUE));
        assertThat(processVariables2.get(COURT_CODES), is("B01LY00"));
        assertThat(processVariables1.get(CUSTODY_TIME_LIMIT), is(CUSTODY_TIME_LIMIT_VAL2));


        final Map processVariables3 = processVariablesCaptor3.getValue();
        assertThat(processVariables3.size(), is(12));

        assertThat(processVariables3.get(EXECUTE_INSTANTLY), is(false));
        assertThat(processVariables3.get(HAS_CUSTODIAL_RESULTS), is(false));
        final String prosecutionCaseId3 = processVariables3.get(CASE_ID).toString();

        assertThat(prosecutionCasesList().contains(prosecutionCaseId3), is(true));
        assertThat(processVariables3.get(LAST_UPDATED_BY_ID), is(SYSTEM_USER_ID.toString()));
        assertThat(processVariables3.get(LAST_UPDATED_BY_NAME), is(SYSTEM_USER_NAME));
        assertThat(processVariables3.get(JURISDICTION), is(JURISDICTION_TYPE_MAGISTRATES));
        assertThat(processVariables3.get(HEARING_ID_LABEL), is(HEARING_ID));
        assertThat(processVariables3.get(HEARING_DATE), is("2022-10-04T12:00:15.351Z"));
        assertThat(processVariables3.get(DEEP_LINK), is("https://dummyUrl/prosecution-casefile/case-at-a-glance/".concat(prosecutionCaseId3)));
        assertThat(processVariables3.get(WORK_QUEUE_FIELD), is(WORK_QUEUE));
        assertThat(processVariables3.get(COURT_CODES), is("B01LY00"));
        assertThat(processVariables1.get(CUSTODY_TIME_LIMIT), is(CUSTODY_TIME_LIMIT_VAL2));
    }

    @Test
    public void shouldStartCaseResultedProcessForMagistratesHearingWithAllResultsFinalForCaseIsFalse() {

        when(systemUserProvider.getContextSystemUserId()).thenReturn(of(SYSTEM_USER_ID));
        // Given
        final JsonObject eventPayload = getEventPayload(CASE_HEARING_RESULTED_JSON, HEARING_ID, JURISDICTION_TYPE_MAGISTRATES, PROSECUTION_CASE_ID1, "ACTIVE");

        // when
        caseHearingResultedEventProcessor.handleHearingResultedProcessor((envelopeFrom(metadataWithRandomUUID("public.progression.hearing-resulted"), eventPayload)));

        // Then
        verify(runtimeService, times(1)).startProcessInstanceByKey(Mockito.eq(CASE_HEARING_RESULTS_BPMN_PROCESS_NAME), Mockito.eq(CASE_HEARING_ID1), processVariablesCaptor1.capture());

        final Map processVariables = processVariablesCaptor1.getValue();
        assertThat(processVariables.size(), is(12));

        assertThat(processVariables.get(EXECUTE_INSTANTLY), is(false));
        assertThat(processVariables.get(HAS_CUSTODIAL_RESULTS), is(false));
        assertThat(processVariables.get(CASE_ID), is(PROSECUTION_CASE_ID1));
        assertThat(processVariables.get(LAST_UPDATED_BY_ID), is(SYSTEM_USER_ID.toString()));
        assertThat(processVariables.get(LAST_UPDATED_BY_NAME), is(SYSTEM_USER_NAME));
        assertThat(processVariables.get(JURISDICTION), is(JURISDICTION_TYPE_MAGISTRATES));
        assertThat(processVariables.get(HEARING_ID_LABEL), is(HEARING_ID));
        assertThat(processVariables.get(HEARING_DATE), is("2022-10-04T12:00:15.351Z"));
        assertThat(processVariables.get(DEEP_LINK), is("https://dummyUrl/prosecution-casefile/case-at-a-glance/".concat(PROSECUTION_CASE_ID1)));
        assertThat(processVariables.get(WORK_QUEUE_FIELD), is(WORK_QUEUE));
        assertThat(processVariables.get(COURT_CODES), is("B01LY00"));
        assertThat(processVariables.get(CUSTODY_TIME_LIMIT), is(CUSTODY_TIME_LIMIT_VAL));
    }

    @ParameterizedTest
    @MethodSource("sendDocumentToPrisonResultCodesProvider")
    void shouldStartCaseResultedProcessForCrownHearingWithAllResultsFinalForCase(String cjsCode) {

        // Given
        when(systemUserProvider.getContextSystemUserId()).thenReturn(of(SYSTEM_USER_ID));
        when(taskTypeService.getTaskVariablesFromRefDataWithPrefix(SEND_DOCUMENTS_TO_PRISON_TASK_NAME, PROSECUTION_CASE_ID1, SEND_DOCUMENTS_TO_PRISON_PREFIX, "2022-10-04T12:00:15.351Z")).thenReturn(getTaskReferenceDataPrefix(PROSECUTION_CASE_ID1));

        final JsonObject eventPayload = getEventPayloadForSendDocumentsToPrison(CASE_HEARING_RESULTED_JSON, HEARING_ID, "CROWN", PROSECUTION_CASE_ID1, PROSECUTION_CASE_ID2, PROSECUTION_CASE_ID3, "INACTIVE", cjsCode);

        // when
        caseHearingResultedEventProcessor.handleHearingResultedProcessor((envelopeFrom(metadataWithRandomUUID("public.progression.hearing-resulted"), eventPayload)));

        // Then
        verify(runtimeService, times(1)).startProcessInstanceByKey(Mockito.eq(CASE_HEARING_RESULTS_BPMN_PROCESS_NAME), Mockito.eq(CASE_HEARING_ID1), processVariablesCaptor1.capture());

        final Map processVariables = processVariablesCaptor1.getValue();
        assertThat(processVariables.size(), is(18));

        assertThat(processVariables.get(EXECUTE_INSTANTLY), is(false));
        assertThat(processVariables.get(HAS_CUSTODIAL_RESULTS), is(true));
        assertThat(processVariables.get(CASE_ID), is(PROSECUTION_CASE_ID1));
        assertThat(processVariables.get(LAST_UPDATED_BY_ID), is(SYSTEM_USER_ID.toString()));
        assertThat(processVariables.get(LAST_UPDATED_BY_NAME), is(SYSTEM_USER_NAME));
        assertThat(processVariables.get(JURISDICTION), is("CROWN"));
        assertThat(processVariables.get(HEARING_ID_LABEL), is(HEARING_ID));
        assertThat(processVariables.get(HEARING_DATE), is("2022-10-04T12:00:15.351Z"));
        assertThat(processVariables.get(DEEP_LINK), is("https://dummyUrl/prosecution-casefile/case-at-a-glance/".concat(PROSECUTION_CASE_ID1)));
        assertThat(processVariables.get(WORK_QUEUE_FIELD), is(CROWN_COURT_ADMIN_WORK_QUEUE_ID));
        assertThat(processVariables.get(NOTE), is("[John1 Doe1]"));
        assertThat(processVariables.get(CASE_URN), is(CASE_URN1));
        assertThat(processVariables.get(DEFENDANT_ID), is(DEFENDANT_ID1));
        assertThat(processVariables.get(DEFENDANT_NAME), is("John1 Doe1"));
        assertThat(processVariables.get(DEFENDANT_REMAND_STATUS), is(REMAND_STATUS));
        assertThat(processVariables.get(PRISON_NAME), is("HMP/YOI Low Newton"));
        assertThat(processVariables.get(COURT_CODES), is("B62IZ00"));
        assertThat(processVariables.get(CUSTODY_TIME_LIMIT), is(CUSTODY_TIME_LIMIT_VAL));

    }

    @ParameterizedTest
    @MethodSource("sendDocumentToPrisonResultCodesProvider")
    void shouldStartCaseResultedProcessForMagistratesHearingAndSendDocumentsToPrisonWithAllResultsFinalForCaseWIthMultipleCases(String cjsCode) {

        // Given
        when(taskTypeService.getTaskVariablesFromRefData(SEND_DOCUMENTS_TO_PRISON_TASK_NAME, PROSECUTION_CASE_ID2, "2022-10-04T12:00:15.351Z", null)).thenReturn(getTaskReferenceData(PROSECUTION_CASE_ID2));
        when(taskTypeService.getTaskVariablesFromRefData(SEND_DOCUMENTS_TO_PRISON_TASK_NAME, PROSECUTION_CASE_ID3, "2022-10-04T12:00:15.351Z", null)).thenReturn(getTaskReferenceData(PROSECUTION_CASE_ID3));
        when(taskTypeService.getTaskVariablesFromRefData(SEND_DOCUMENTS_TO_PRISON_TASK_NAME, PROSECUTION_CASE_ID4, "2022-10-04T12:00:15.351Z", null)).thenReturn(getTaskReferenceData(PROSECUTION_CASE_ID4));
        final JsonObject eventPayload = getEventPayloadForSendDocumentsToPrison(CASE_HEARING_RESULTED_MULTIPLE_CASE_JSON, HEARING_ID, JURISDICTION_TYPE_MAGISTRATES, PROSECUTION_CASE_ID1, PROSECUTION_CASE_ID2, PROSECUTION_CASE_ID3, "INACTIVE", cjsCode);

        // when
        caseHearingResultedEventProcessor.handleHearingResultedProcessor((envelopeFrom(metadataWithRandomUUID("public.progression.hearing-resulted"), eventPayload)));

        // Then
        verify(runtimeService, times(1)).startProcessInstanceByKey(Mockito.eq(CASE_HEARING_RESULTS_BPMN_PROCESS_NAME), Mockito.eq(CASE_HEARING_ID1), processVariablesCaptor1.capture());
        verify(runtimeService, times(1)).startProcessInstanceByKey(Mockito.eq(CASE_HEARING_RESULTS_BPMN_PROCESS_NAME), Mockito.eq(CASE_HEARING_ID2), processVariablesCaptor2.capture());
        verify(runtimeService, times(1)).startProcessInstanceByKey(Mockito.eq(CASE_HEARING_RESULTS_BPMN_PROCESS_NAME), Mockito.eq(CASE_HEARING_ID3), processVariablesCaptor3.capture());
        verify(runtimeService, times(1)).startProcessInstanceByKey(Mockito.eq(CASE_HEARING_RESULTS_BPMN_PROCESS_NAME), Mockito.eq(CASE_HEARING_ID4), processVariablesCaptor4.capture());

        final Map processVariables1 = processVariablesCaptor1.getValue();
        assertThat(processVariables1.size(), is(18));

        assertThat(processVariables1.get(EXECUTE_INSTANTLY), is(false));
        assertThat(processVariables1.get(HAS_CUSTODIAL_RESULTS), is(true));
        final String prosecutionCaseId1 = processVariables1.get(CASE_ID).toString();
        assertThat(prosecutionCasesList().contains(prosecutionCaseId1), is(true));
        assertThat(processVariables1.get(LAST_UPDATED_BY_ID), is(SYSTEM_USER_ID.toString()));
        assertThat(processVariables1.get(LAST_UPDATED_BY_NAME), is(SYSTEM_USER_NAME));
        assertThat(processVariables1.get(JURISDICTION), is(JURISDICTION_TYPE_MAGISTRATES));
        assertThat(processVariables1.get(HEARING_ID_LABEL), is(HEARING_ID));
        assertThat(processVariables1.get(HEARING_DATE), is("2022-10-04T12:00:15.351Z"));
        assertThat(processVariables1.get(DEEP_LINK), is("https://dummyUrl/prosecution-casefile/case-at-a-glance/".concat(prosecutionCaseId1)));
        assertThat(processVariables1.get(WORK_QUEUE_FIELD), is(WORK_QUEUE));
        assertThat(processVariables1.get(NOTE), is("[John1 Doe1]"));
        assertThat(processVariables1.get(CASE_URN), is(CASE_URN1));
        assertThat(processVariables1.get(DEFENDANT_ID), is(DEFENDANT_ID1));
        assertThat(processVariables1.get(DEFENDANT_NAME), is("John1 Doe1"));
        assertThat(processVariables1.get(DEFENDANT_REMAND_STATUS), is(REMAND_STATUS));
        assertThat(processVariables1.get(PRISON_NAME), is(""));
        assertThat(processVariables1.get(COURT_CODES), is("B01LY00"));
        assertThat(processVariables1.get(CUSTODY_TIME_LIMIT), is(CUSTODY_TIME_LIMIT_VAL2));

        final Map processVariables2 = processVariablesCaptor2.getValue();
        assertThat(processVariables2.size(), is(18));

        assertThat(processVariables2.get(EXECUTE_INSTANTLY), is(false));
        assertThat(processVariables2.get(HAS_CUSTODIAL_RESULTS), is(true));
        final String prosecutionCaseId2 = processVariables2.get(CASE_ID).toString();

        assertThat(prosecutionCasesList().contains(prosecutionCaseId2), is(true));
        assertThat(processVariables2.get(LAST_UPDATED_BY_ID), is(SYSTEM_USER_ID.toString()));
        assertThat(processVariables2.get(LAST_UPDATED_BY_NAME), is(SYSTEM_USER_NAME));
        assertThat(processVariables2.get(JURISDICTION), is(JURISDICTION_TYPE_MAGISTRATES));
        assertThat(processVariables2.get(HEARING_ID_LABEL), is(HEARING_ID));
        assertThat(processVariables2.get(HEARING_DATE), is("2022-10-04T12:00:15.351Z"));
        assertThat(processVariables2.get(DEEP_LINK), is("https://dummyUrl/prosecution-casefile/case-at-a-glance/".concat(prosecutionCaseId2)));
        assertThat(processVariables2.get(WORK_QUEUE_FIELD), is(WORK_QUEUE));
        assertThat(processVariables2.get(DEFENDANT_REMAND_STATUS), is(REMAND_STATUS));
        assertThat(processVariables2.get(CASE_URN), is(CASE_URN2));
        assertThat(processVariables2.get(DEFENDANT_ID), is(DEFENDANT_ID2_2));
        assertThat(processVariables2.get(DEFENDANT_NAME), is("John22 Doe22"));
        assertThat(processVariables2.get(NOTE), is("[John22 Doe22]"));
        assertThat(processVariables2.get(PRISON_NAME), is(""));
        assertThat(processVariables2.get(COURT_CODES), is("B01LY00"));
        assertThat(processVariables2.get(CUSTODY_TIME_LIMIT), is(CUSTODY_TIME_LIMIT_VAL2));


        final Map processVariables3 = processVariablesCaptor3.getValue();
        assertThat(processVariables3.size(), is(18));
        assertThat(processVariables3.get(EXECUTE_INSTANTLY), is(false));
        assertThat(processVariables3.get(HAS_CUSTODIAL_RESULTS), is(true));
        final String prosecutionCaseId3 = processVariables3.get(CASE_ID).toString();

        assertThat(prosecutionCasesList().contains(prosecutionCaseId3), is(true));
        assertThat(processVariables3.get(LAST_UPDATED_BY_ID), is(SYSTEM_USER_ID.toString()));
        assertThat(processVariables3.get(LAST_UPDATED_BY_NAME), is(SYSTEM_USER_NAME));
        assertThat(processVariables3.get(JURISDICTION), is(JURISDICTION_TYPE_MAGISTRATES));
        assertThat(processVariables3.get(HEARING_ID_LABEL), is(HEARING_ID));
        assertThat(processVariables3.get(HEARING_DATE), is("2022-10-04T12:00:15.351Z"));
        assertThat(processVariables3.get(DEEP_LINK), is("https://dummyUrl/prosecution-casefile/case-at-a-glance/".concat(prosecutionCaseId3)));
        assertThat(processVariables3.get(WORK_QUEUE_FIELD), is(WORK_QUEUE));
        assertThat(processVariables3.get(DEFENDANT_REMAND_STATUS), is(REMAND_STATUS));
        assertThat(processVariables3.get(CASE_URN), is(CASE_URN3));
        assertThat(processVariables3.get(DEFENDANT_ID), is(DEFENDANT_ID3_1));
        assertThat(processVariables3.get(DEFENDANT_NAME), is("John31 Doe31"));
        assertThat(processVariables3.get(NOTE), is("[John31 Doe31,John32 Doe32]"));
        assertThat(processVariables3.get(PRISON_NAME), is("HMP/YOI Low Newton"));
        assertThat(processVariables3.get(COURT_CODES), is("B62IZ00"));
        assertThat(processVariables3.get(CUSTODY_TIME_LIMIT), is(CUSTODY_TIME_LIMIT_VAL2));

        final Map processVariables4 = processVariablesCaptor4.getValue();
        assertThat(processVariables4.size(), is(12));

        assertThat(processVariables4.get(EXECUTE_INSTANTLY), is(false));
        assertThat(processVariables4.get(HAS_CUSTODIAL_RESULTS), is(false));
        final String prosecutionCaseId4 = processVariables4.get(CASE_ID).toString();
        assertThat(prosecutionCasesList().contains(prosecutionCaseId4), is(true));
        assertThat(processVariables4.get(LAST_UPDATED_BY_ID), is(SYSTEM_USER_ID.toString()));
        assertThat(processVariables4.get(LAST_UPDATED_BY_NAME), is(SYSTEM_USER_NAME));
        assertThat(processVariables4.get(JURISDICTION), is(JURISDICTION_TYPE_MAGISTRATES));
        assertThat(processVariables4.get(HEARING_ID_LABEL), is(HEARING_ID));
        assertThat(processVariables4.get(HEARING_DATE), is("2022-10-04T12:00:15.351Z"));
        assertThat(processVariables4.get(DEEP_LINK), is("https://dummyUrl/prosecution-casefile/case-at-a-glance/".concat(prosecutionCaseId4)));
        assertThat(processVariables4.get(WORK_QUEUE_FIELD), is(WORK_QUEUE));
        assertThat(processVariables4.get(COURT_CODES), is("B01LY00"));
        assertThat(processVariables4.get(CUSTODY_TIME_LIMIT), is(CUSTODY_TIME_LIMIT_VAL2));
    }

    static Stream<String> sendDocumentToPrisonResultCodesProvider() {
        return SEND_DOCUMENT_TO_PRISON_RESULT_CODES.stream();
    }

    private JsonObject getEventPayload(final String fileName, final String id, final String jurisdictionType, final String prosecutionCaseId, final String caseStatus) {
        return getFileContentAsJson(fileName,
                ImmutableMap.<String, Object>builder()
                        .put("ID", id)
                        .put("JURISDICTION_TYPE", jurisdictionType)
                        .put("PROSECUTION_CASE1", prosecutionCaseId)
                        .put("CASE_STATUS", caseStatus)
                        .put("DEFENDANT_ID1", DEFENDANT_ID1)
                        .build());
    }

    private JsonObject getEventPayload(final String fileName, final String id, final String jurisdictionType, final String prosecutionCaseId1, final String prosecutionCaseId2, final String prosecutionCaseId3, final String caseStatus) {
        return getFileContentAsJson(fileName,
                ImmutableMap.<String, Object>builder()
                        .put("ID", id)
                        .put("JURISDICTION_TYPE", jurisdictionType)
                        .put("PROSECUTION_CASE1", prosecutionCaseId1)
                        .put("PROSECUTION_CASE2", prosecutionCaseId2)
                        .put("PROSECUTION_CASE3", prosecutionCaseId3)
                        .put("PROSECUTION_CASE4", PROSECUTION_CASE_ID4)
                        .put("CASE_STATUS", caseStatus)
                        .put("DEFENDANT_ID1", DEFENDANT_ID1)
                        .put("DEFENDANT_ID1_1", DEFENDANT_ID1_1)
                        .put("DEFENDANT_ID2_1", DEFENDANT_ID2_1)
                        .put("DEFENDANT_ID2_2", DEFENDANT_ID2_2)
                        .put("DEFENDANT_ID3_1", DEFENDANT_ID3_1)
                        .put("DEFENDANT_ID3_2", DEFENDANT_ID3_2)
                        .put("DEFENDANT_ID4_1", DEFENDANT_ID4_1)
                        .put("DEFENDANT_ID4_2", DEFENDANT_ID4_2)
                        .build());
    }

    private JsonObject getEventPayloadForSendDocumentsToPrison(final String fileName, final String id, final String jurisdictionType, final String prosecutionCaseId1, final String prosecutionCaseId2, final String prosecutionCaseId3, final String caseStatus, final String cjsCode) {
        return getFileContentAsJson(fileName,
                ImmutableMap.<String, Object>builder()
                        .put("ID", id)
                        .put("JURISDICTION_TYPE", jurisdictionType)
                        .put("PROSECUTION_CASE1", prosecutionCaseId1)
                        .put("PROSECUTION_CASE2", prosecutionCaseId2)
                        .put("PROSECUTION_CASE3", prosecutionCaseId3)
                        .put("PROSECUTION_CASE4", PROSECUTION_CASE_ID4)
                        .put("CASE_STATUS", caseStatus)
                        .put("DEFENDANT_ID1", DEFENDANT_ID1)
                        .put("REMAND_STATUS", REMAND_STATUS)
                        .put("CASE_URN1", CASE_URN1)
                        .put("CASE_URN2", CASE_URN2)
                        .put("CASE_URN3", CASE_URN3)
                        .put("CASE_URN4", CASE_URN4)
                        .put("DEFENDANT_ID1_1", DEFENDANT_ID1_1)
                        .put("DEFENDANT_ID2_1", DEFENDANT_ID2_1)
                        .put("DEFENDANT_ID2_2", DEFENDANT_ID2_2)
                        .put("DEFENDANT_ID3_1", DEFENDANT_ID3_1)
                        .put("DEFENDANT_ID3_2", DEFENDANT_ID3_2)
                        .put("DEFENDANT_ID4_1", DEFENDANT_ID4_1)
                        .put("DEFENDANT_ID4_2", DEFENDANT_ID4_2)
                        .put("FIRST_NAME_1", FIRST_NAME + "1")
                        .put("FIRST_NAME2_1", FIRST_NAME + "21")
                        .put("FIRST_NAME2_2", FIRST_NAME + "22")
                        .put("FIRST_NAME3_1", FIRST_NAME + "31")
                        .put("FIRST_NAME3_2", FIRST_NAME + "32")
                        .put("LAST_NAME_1", LAST_NAME + "1")
                        .put("LAST_NAME2_1", LAST_NAME + "21")
                        .put("LAST_NAME2_2", LAST_NAME + "22")
                        .put("LAST_NAME3_1", LAST_NAME + "31")
                        .put("LAST_NAME3_2", LAST_NAME + "32")
                        .put("CJS_CODE1", cjsCode)
                        .put("CJS_CODE2_1", CJS_CODE_INVALID_FOR_PRISON_TRANSFER)
                        .put("CJS_CODE2_2", cjsCode)
                        .put("CJS_CODE3_1", cjsCode)
                        .put("CJS_CODE3_2_1", CJS_CODE_INVALID_FOR_PRISON_TRANSFER)
                        .put("CJS_CODE3_2_2", cjsCode)
                        .build());
    }

    private Map<String, Object> getTaskReferenceData(final String referenceId) {
        final Map<String, Object> referenceData = new HashMap<>();
        referenceData.put(DEEP_LINK, "https://dummyUrl/prosecution-casefile/case-at-a-glance/".concat(referenceId));
        referenceData.put(WORK_QUEUE_FIELD, WORK_QUEUE);
        return referenceData;
    }

    private Map<String, Object> getTaskReferenceDataPrefix(final String referenceId) {
        final Map<String, Object> referenceData = new HashMap<>();
        referenceData.put(SEND_DOCUMENTS_TO_PRISON_PREFIX + DEEP_LINK, "https://dummyUrl/prosecution-casefile/case-at-a-glance/".concat(referenceId));
        referenceData.put(WORK_QUEUE_FIELD, WORK_QUEUE);
        return referenceData;
    }

    private List<String> prosecutionCasesList() {
        List<String> list = new ArrayList<>();
        list.add(PROSECUTION_CASE_ID1);
        list.add(PROSECUTION_CASE_ID2);
        list.add(PROSECUTION_CASE_ID3);
        list.add(PROSECUTION_CASE_ID4);
        return list;
    }
}
