package uk.gov.moj.cpp.businessprocesses.bpmn;

import static java.util.UUID.randomUUID;
import static org.camunda.bpm.engine.test.mock.Mocks.register;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.justice.services.messaging.Envelope.envelopeFrom;
import static uk.gov.justice.services.test.utils.core.messaging.MetadataBuilderFactory.metadataWithRandomUUID;
import static uk.gov.justice.services.test.utils.core.reflection.ReflectionUtil.setField;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.CUSTODY_TIME_LIMIT;

import uk.gov.justice.services.common.converter.ListToJsonArrayConverter;
import uk.gov.justice.services.common.converter.StringToJsonObjectConverter;
import uk.gov.justice.services.core.dispatcher.SystemUserProvider;
import uk.gov.justice.services.core.featurecontrol.FeatureControlGuard;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.justice.services.core.sender.Sender;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.Metadata;
import uk.gov.justice.services.messaging.MetadataBuilder;
import uk.gov.moj.cpp.businessprocesses.delegate.RemoveDDJAccessServiceTask;
import uk.gov.moj.cpp.businessprocesses.listener.TasksAssignedListener;
import uk.gov.moj.cpp.businessprocesses.listener.TasksCompletedListener;
import uk.gov.moj.cpp.businessprocesses.listener.TasksCreatedListener;
import uk.gov.moj.cpp.businessprocesses.shared.TaskAuditService;

import java.util.HashMap;
import java.util.List;

import javax.json.JsonObject;

import org.camunda.bpm.engine.HistoryService;
import org.camunda.bpm.engine.ProcessEngineConfiguration;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.history.HistoricActivityInstance;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.engine.test.Deployment;
import org.camunda.bpm.engine.test.RequiredHistoryLevel;
import org.camunda.bpm.engine.test.mock.Mocks;
import org.camunda.bpm.extension.junit5.test.ProcessEngineExtension;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.AfterEach;
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

@SuppressWarnings({"squid:S2187"})
@ExtendWith({MockitoExtension.class, ProcessEngineExtension.class})
public class CaseResultedBPMNTest {

    @RegisterExtension
    ProcessEngineExtension extension = ProcessEngineExtension.builder()
            .configurationResource("camunda.cfg.xml")
            .build();

    @Mock
    protected RuntimeService runtimeService;

    @Mock
    protected TaskService taskService;

    @Mock
    protected HistoryService historyService;

    @Mock
    protected Sender sender;

    @Spy
    @InjectMocks
    protected TasksAssignedListener tasksAssignedListener;

    @Spy
    @InjectMocks
    protected TasksCreatedListener tasksCreatedListener;

    @Spy
    @InjectMocks
    protected TasksCompletedListener tasksCompletedListener;

    @Mock
    protected SystemUserProvider systemUserProvider;

    @Mock
    protected TaskAuditService taskAuditService;

    @Captor
    protected ArgumentCaptor<JsonEnvelope> jsonEnvelopeArgumentCaptor;

    @Mock
    protected Requester requester;

    @Mock
    private DelegateExecution execution;

    @InjectMocks
    private RemoveDDJAccessServiceTask removeDDJAccessServiceTask;

    @Spy
    private StringToJsonObjectConverter stringToJsonObjectConverter;

    @Spy
    private ListToJsonArrayConverter listToJsonArrayConverter;

    @Mock
    private FeatureControlGuard featureControlGuard;

    private static final String REMOVE_DDJ_ACCESS_TASK_ID = "Remove_DDJ_from_hearing";
    private static final String SEND_DOCUMENTS_TO_PRISON_TASK_ID = "Send_documents_to_prison";
    private static final String CASE_RESULTED_BPMN_PROCESS_NAME = "case-results";
    private static final String CASE_RESULTED_BPMN_FILE_NAME = "case-results-v2.bpmn";
    private static final String PERMISSIONS_QUERY_API = "usersgroups.permissions";
    private static final String DDJ_ACCESS_DESCRIPTION = "Permission for DEPUTY_DISTRICT_JUDGE:Ketan Bond CaseId:";
    private static final String CASE_ID_FIELD = "caseId";
    private static final String HEARING_DATE_FIELD = "hearingDate";
    private static final String HEARING_ID_FIELD = "hearingId";
    private static final String LAST_UPDATED_BY_ID_FIELD = "lastUpdatedByID";
    private static final String LAST_UPDATED_BY_NAME_FIELD = "lastUpdatedByName";
    private static final String CASE_URN_FIELD = "caseURN";
    private static final String DEFENDANT_ID_FIELD = "defendantId";
    private static final String DEFENDANT_NAME_FIELD = "defendantName";
    private static final String DEFENDANT_REMAND_STATUS_FIELD = "defendantRemandStatus";
    private static final String WORK_QUEUE_FIELD = "workQueue";
    private static final String DEEP_LINK_FIELD = "deepLink";
    private static final String TASK_TYPE_ID_FIELD = "taskTypeId";
    private static final String DUE_DATE_FIELD = "dueDate";
    private static final String NOTE_FIELD = "note";
    private static final String EXECUTE_INSTANTLY = "executeInstantly";
    private static final String HAS_CUSTODIAL_RESULTS = "hasCustodialResults";
    private static final String JURISDICTION = "jurisdiction";
    private static final String MAGISTRATES_JURISDICTION_TYPE = "MAGISTRATES";
    private static final String CROWN_JURISDICTION_TYPE = "CROWN";
    private static final String CASE_ID = randomUUID().toString();
    private static final String HEARING_DATE = "2022-08-24T01:46:36.068Z";
    private static final String HEARING_ID = randomUUID().toString();
    private static final String SYSTEM_USER_ID = randomUUID().toString();
    private static final String SYSTEM_USER_NAME = "SYSTEM";
    private static final String CASE_URN = "TFL12345678";
    private static final String DEFENDANT_NAME = "John Smith";
    private static final String DEFENDANT_ID = randomUUID().toString();
    private static final String DEFENDANT_REMAND_STATUS = "ACTIVE";
    private static final String WORK_QUEUE = randomUUID().toString();
    private static final String CASE_DEEP_LINK = "https://dummyUrl/prosecution-casefile/case-at-a-glance/".concat(CASE_ID);
    private static final String TASK_TYPE_ID = randomUUID().toString();
    private static final String DUE_DATE = "2021-08-24T01:46:36.068Z";
    private static final String NOTES = "[John Smith,Fred Blogs]";
    private static final String CUSTODY_TIME_LIMIT_VAL = "2026-05-21";
    private static final String SEND_DOCUMENTS_TO_PRISON_PREFIX = "sendDocumentsToPrison_";
    public static final String TASK_NAME_FIELD = "taskName";
    public static final String TASK_NAME = "Send documents to Prison";
    public static final String PRISON_NAME_FIELD = "prisonName";
    public static final String PRISON_NAME = "HMP/YOI Low Newton";
    public static final String IS_DELETABLE_FIELD = "isDeletable";
    public static final String IS_DEFERRABLE_FIELD = "isDeferrable";

    @RequiredHistoryLevel(ProcessEngineConfiguration.HISTORY_ACTIVITY)
    @BeforeEach
    public void setUp() throws Exception {
        runtimeService = extension.getProcessEngine().getRuntimeService();
        taskService = extension.getProcessEngine().getTaskService();
        historyService = extension.getProcessEngine().getHistoryService();
        register("tasksCreatedListener", tasksCreatedListener);
        register("tasksAssignedListener", tasksAssignedListener);
        register("tasksCompletedListener", tasksCompletedListener);
        register("removeDDJAccessServiceTask", removeDDJAccessServiceTask);
        setField(this.listToJsonArrayConverter, "stringToJsonObjectConverter", stringToJsonObjectConverter);
    }

    @Test
    @Deployment(resources = {CASE_RESULTED_BPMN_FILE_NAME})
    void shouldStartCaseResultedProcessAndRemoveDDJAccessForCaseResultedInMagistratesCourtWithAllResultsFinal() {

        when(featureControlGuard.isFeatureEnabled(any())).thenReturn(true);

        final String permissionId = randomUUID().toString();
        final MetadataBuilder metadataBuilder = metadataWithRandomUUID(PERMISSIONS_QUERY_API);

        final HashMap<String, Object> processVariables = new HashMap<>();
        processVariables.put(CASE_ID_FIELD, CASE_ID);
        processVariables.put(HEARING_DATE_FIELD, HEARING_DATE);
        processVariables.put(HEARING_ID_FIELD, HEARING_ID);
        processVariables.put(LAST_UPDATED_BY_ID_FIELD, SYSTEM_USER_ID);
        processVariables.put(LAST_UPDATED_BY_NAME_FIELD, SYSTEM_USER_NAME);
        processVariables.put(JURISDICTION, MAGISTRATES_JURISDICTION_TYPE);
        processVariables.put(EXECUTE_INSTANTLY, true);
        processVariables.put(HAS_CUSTODIAL_RESULTS, false);

        final Envelope<JsonObject> jsonObjectEnvelope = envelopeFrom(metadataBuilder, getPermissionsList(permissionId, DDJ_ACCESS_DESCRIPTION));

        when(requester.requestAsAdmin(any(), eq(JsonObject.class))).thenReturn(jsonObjectEnvelope);

        final ProcessInstance processInstance = runtimeService
                .startProcessInstanceByKey(CASE_RESULTED_BPMN_PROCESS_NAME, CASE_ID_FIELD, processVariables);

        verify(requester, times(1)).requestAsAdmin(any(JsonEnvelope.class), eq(JsonObject.class));
        verify(sender, times(1)).sendAsAdmin(any(JsonEnvelope.class));

        assertThat(processInstance.isEnded(), is(true));
        assertThat(processInstance.isSuspended(), is(false));

        final List<HistoricActivityInstance> completedTasksList = historyService
                .createHistoricActivityInstanceQuery()
                .processInstanceId(processInstance.getId())
                .activityId(REMOVE_DDJ_ACCESS_TASK_ID)
                .list();

        assertThat(completedTasksList.size(), is(1));
        final HistoricActivityInstance completedDDJTask = completedTasksList.get(0);
        assertThat(completedDDJTask.getActivityType(), is("serviceTask"));
        assertThat(completedDDJTask.getActivityName(), is("Remove DDJ from hearing"));
    }

    @Test
    @Deployment(resources = {CASE_RESULTED_BPMN_FILE_NAME})
    void shouldStartCaseResultedProcessAndDoNothingForCaseResultedInCrownCourtWithAllResultsFinal() {
        final HashMap<String, Object> processVariables = new HashMap<>();
        processVariables.put(CASE_ID_FIELD, CASE_ID);
        processVariables.put(HEARING_DATE_FIELD, HEARING_DATE);
        processVariables.put(HEARING_ID_FIELD, HEARING_ID);
        processVariables.put(LAST_UPDATED_BY_ID_FIELD, SYSTEM_USER_ID);
        processVariables.put(LAST_UPDATED_BY_NAME_FIELD, SYSTEM_USER_NAME);
        processVariables.put(JURISDICTION, CROWN_JURISDICTION_TYPE);
        processVariables.put(EXECUTE_INSTANTLY, true);
        processVariables.put(HAS_CUSTODIAL_RESULTS, false);

        final ProcessInstance processInstance = runtimeService
                .startProcessInstanceByKey(CASE_RESULTED_BPMN_PROCESS_NAME, processVariables);


        verify(requester, never()).requestAsAdmin(any(JsonEnvelope.class), eq(JsonObject.class));
        verify(sender, never()).sendAsAdmin(any(JsonEnvelope.class));

        final List<HistoricActivityInstance> completedTasksList = historyService
                .createHistoricActivityInstanceQuery()
                .processInstanceId(processInstance.getId())
                .activityId(REMOVE_DDJ_ACCESS_TASK_ID)
                .list();

        assertThat(completedTasksList.size(), is(0));
    }

    @Test
    @Deployment(resources = {CASE_RESULTED_BPMN_FILE_NAME})
    void shouldStartCaseResultedProcessAndDoNothingForCaseResultedInMagistratesCourtWithNonResultsFinal() {
        final HashMap<String, Object> processVariables = new HashMap<>();
        processVariables.put(CASE_ID_FIELD, CASE_ID);
        processVariables.put(HEARING_DATE_FIELD, HEARING_DATE);
        processVariables.put(HEARING_ID_FIELD, HEARING_ID);
        processVariables.put(LAST_UPDATED_BY_ID_FIELD, SYSTEM_USER_ID);
        processVariables.put(LAST_UPDATED_BY_NAME_FIELD, SYSTEM_USER_NAME);
        processVariables.put(JURISDICTION, MAGISTRATES_JURISDICTION_TYPE);
        processVariables.put(EXECUTE_INSTANTLY, false);
        processVariables.put(HAS_CUSTODIAL_RESULTS, false);

        final ProcessInstance processInstance = runtimeService
                .startProcessInstanceByKey(CASE_RESULTED_BPMN_PROCESS_NAME, processVariables);

        verify(requester, never()).requestAsAdmin(any(JsonEnvelope.class), eq(JsonObject.class));
        verify(sender, never()).sendAsAdmin(any(JsonEnvelope.class));

        final List<HistoricActivityInstance> completedTasksList = historyService
                .createHistoricActivityInstanceQuery()
                .processInstanceId(processInstance.getId())
                .activityId(REMOVE_DDJ_ACCESS_TASK_ID)
                .list();

        assertThat(completedTasksList.size(), is(0));
    }

    @Test
    @Deployment(resources = {CASE_RESULTED_BPMN_FILE_NAME})
    void shouldStartCaseResultedProcessAndSendDocumentsToPrisonIfCustodialResultsArePresented() {

        final HashMap<String, Object> processVariables = new HashMap<>();
        processVariables.put(CASE_ID_FIELD, CASE_ID);
        processVariables.put(HEARING_DATE_FIELD, HEARING_DATE);
        processVariables.put(HEARING_ID_FIELD, HEARING_ID);
        processVariables.put(CASE_URN_FIELD, CASE_URN);
        processVariables.put(DEFENDANT_ID_FIELD, DEFENDANT_ID);
        processVariables.put(DEFENDANT_NAME_FIELD, DEFENDANT_NAME);
        processVariables.put(DEFENDANT_REMAND_STATUS_FIELD, DEFENDANT_REMAND_STATUS);
        processVariables.put(WORK_QUEUE_FIELD, WORK_QUEUE);
        processVariables.put(LAST_UPDATED_BY_ID_FIELD, SYSTEM_USER_ID);
        processVariables.put(LAST_UPDATED_BY_NAME_FIELD, SYSTEM_USER_NAME);
        processVariables.put(TASK_TYPE_ID_FIELD, TASK_TYPE_ID);
        processVariables.put(DEEP_LINK_FIELD, CASE_DEEP_LINK);
        processVariables.put(DUE_DATE_FIELD, DUE_DATE);
        processVariables.put(JURISDICTION, CROWN_JURISDICTION_TYPE);
        processVariables.put(NOTE_FIELD, NOTES);
        processVariables.put(TASK_NAME_FIELD, TASK_NAME);
        processVariables.put(PRISON_NAME_FIELD, PRISON_NAME);
        processVariables.put(IS_DELETABLE_FIELD, true);
        processVariables.put(IS_DEFERRABLE_FIELD, true);
        processVariables.put(CUSTODY_TIME_LIMIT, CUSTODY_TIME_LIMIT_VAL);
        processVariables.put(HAS_CUSTODIAL_RESULTS, true);
        processVariables.put(EXECUTE_INSTANTLY, false);

        final ProcessInstance processInstance = runtimeService
                .startProcessInstanceByKey(CASE_RESULTED_BPMN_PROCESS_NAME, CASE_ID_FIELD, processVariables);

        assertThat(processInstance.isEnded(), is(false));
        assertThat(processInstance.isSuspended(), is(false));

        final List<Task> activeTaskList = taskService.createTaskQuery().processInstanceId(processInstance.getId()).active().list();
        assertThat(activeTaskList.size(), is(1));

        final Task actualCancelInterpretersTask = activeTaskList.get(0);
        assertThat(actualCancelInterpretersTask.getTaskDefinitionKey(), is(SEND_DOCUMENTS_TO_PRISON_TASK_ID));

        final String actualCancelInterpretersTaskExecutionId = actualCancelInterpretersTask.getExecutionId();

        verify(sender, times(1)).sendAsAdmin(jsonEnvelopeArgumentCaptor.capture());

        final Metadata metadata = jsonEnvelopeArgumentCaptor.getValue().metadata();
        assertThat(metadata.name(), CoreMatchers.is("businessprocesses.command.record-task-created"));

        assertThat(runtimeService.getVariable(actualCancelInterpretersTaskExecutionId, CASE_ID_FIELD), is(CASE_ID));
        assertThat(runtimeService.getVariable(actualCancelInterpretersTaskExecutionId, HEARING_ID_FIELD), is(HEARING_ID));
        assertThat(runtimeService.getVariable(actualCancelInterpretersTaskExecutionId, HEARING_DATE_FIELD), is(HEARING_DATE));
        assertThat(runtimeService.getVariable(actualCancelInterpretersTaskExecutionId, DEFENDANT_ID_FIELD), is(DEFENDANT_ID));
        assertThat(runtimeService.getVariable(actualCancelInterpretersTaskExecutionId, DEFENDANT_NAME_FIELD), is(DEFENDANT_NAME));
//        assertThat(runtimeService.getVariable(actualCancelInterpretersTaskExecutionId, WORK_QUEUE_FIELD), is(WORK_QUEUE));
        assertThat(runtimeService.getVariable(actualCancelInterpretersTaskExecutionId, CASE_URN_FIELD), is(CASE_URN));
        assertThat(runtimeService.getVariable(actualCancelInterpretersTaskExecutionId, DEEP_LINK_FIELD), is(CASE_DEEP_LINK));
        assertThat(runtimeService.getVariable(actualCancelInterpretersTaskExecutionId, LAST_UPDATED_BY_ID_FIELD), is(SYSTEM_USER_ID));
        assertThat(runtimeService.getVariable(actualCancelInterpretersTaskExecutionId, LAST_UPDATED_BY_NAME_FIELD), is(SYSTEM_USER_NAME));
        assertThat(runtimeService.getVariable(actualCancelInterpretersTaskExecutionId, TASK_TYPE_ID_FIELD), is(TASK_TYPE_ID));
        assertThat(runtimeService.getVariable(actualCancelInterpretersTaskExecutionId, DEFENDANT_REMAND_STATUS_FIELD), is(DEFENDANT_REMAND_STATUS));
        assertThat(runtimeService.getVariable(actualCancelInterpretersTaskExecutionId, DUE_DATE_FIELD), is(DUE_DATE));
        assertThat(runtimeService.getVariable(actualCancelInterpretersTaskExecutionId, JURISDICTION), is(CROWN_JURISDICTION_TYPE));
        assertThat(runtimeService.getVariable(actualCancelInterpretersTaskExecutionId, NOTE_FIELD), is(NOTES));
        assertThat(runtimeService.getVariable(actualCancelInterpretersTaskExecutionId, DUE_DATE_FIELD), is(DUE_DATE));
//        assertThat(runtimeService.getVariable(actualCancelInterpretersTaskExecutionId, WORK_QUEUE_FIELD), is(WORK_QUEUE));
        assertThat(runtimeService.getVariable(actualCancelInterpretersTaskExecutionId, TASK_TYPE_ID_FIELD), is(TASK_TYPE_ID));
        assertThat(runtimeService.getVariable(actualCancelInterpretersTaskExecutionId, DEEP_LINK_FIELD), is(CASE_DEEP_LINK));
        assertThat(runtimeService.getVariable(actualCancelInterpretersTaskExecutionId, CUSTODY_TIME_LIMIT), is(CUSTODY_TIME_LIMIT_VAL));
    }

    @Test
    @Deployment(resources = {CASE_RESULTED_BPMN_FILE_NAME})
    void shouldStartCaseResultedProcessButTaskWontCreateWhenNoCustodialResults() {

        final HashMap<String, Object> processVariables = new HashMap<>();
        processVariables.put(CASE_ID_FIELD, CASE_ID);
        processVariables.put(HEARING_DATE_FIELD, HEARING_DATE);
        processVariables.put(HEARING_ID_FIELD, HEARING_ID);
        processVariables.put(CASE_URN_FIELD, CASE_URN);
        processVariables.put(DEFENDANT_ID_FIELD, DEFENDANT_ID);
        processVariables.put(DEFENDANT_NAME_FIELD, DEFENDANT_NAME);
        processVariables.put(DEFENDANT_REMAND_STATUS_FIELD, DEFENDANT_REMAND_STATUS);
        processVariables.put(WORK_QUEUE_FIELD, WORK_QUEUE);
        processVariables.put(LAST_UPDATED_BY_ID_FIELD, SYSTEM_USER_ID);
        processVariables.put(LAST_UPDATED_BY_NAME_FIELD, SYSTEM_USER_NAME);
        processVariables.put(TASK_TYPE_ID_FIELD, TASK_TYPE_ID);
        processVariables.put(DEEP_LINK_FIELD, CASE_DEEP_LINK);
        processVariables.put(DUE_DATE_FIELD, DUE_DATE);
        processVariables.put(JURISDICTION, CROWN_JURISDICTION_TYPE);
        processVariables.put(NOTE_FIELD, NOTES);

        processVariables.put(HAS_CUSTODIAL_RESULTS, false);
        processVariables.put(EXECUTE_INSTANTLY, false);

        final ProcessInstance processInstance = runtimeService
                .startProcessInstanceByKey(CASE_RESULTED_BPMN_PROCESS_NAME, CASE_ID_FIELD, processVariables);

        verify(sender, never()).sendAsAdmin(jsonEnvelopeArgumentCaptor.capture());

        assertThat(processInstance.isEnded(), is(true));

        final List<Task> activeTaskList = taskService.createTaskQuery().processInstanceId(processInstance.getId()).active().list();
        assertThat(activeTaskList.size(), is(0));
    }

    @AfterEach
    public void tearDown() throws Exception {
        Mocks.reset();
    }

    private JsonObject getPermissionsList(final String permissionId, final String description) {
        return stringToJsonObjectConverter.convert("{\n" +
                "  \"permissions\": [\n" +
                "    {\n" +
                "      \"permissionId\": \"" + permissionId + "\",\n" +
                "      \"description\": \"" + description + "\",\n" +
                "      \"object\" :\"CaseList\",\n" +
                "      \"action\" : \"View\",\n" +
                "      \"active\": \"true\"\n" +
                "    }\n" +
                "  ]\n" +
                "}\n");
    }

}