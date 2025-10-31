package uk.gov.moj.cpp.businessprocesses.bpmn;

import static java.util.UUID.randomUUID;
import static org.camunda.bpm.engine.test.mock.Mocks.register;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import uk.gov.justice.services.core.dispatcher.SystemUserProvider;
import uk.gov.justice.services.core.sender.Sender;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.Metadata;
import uk.gov.moj.cpp.businessprocesses.listener.TasksAssignedListener;
import uk.gov.moj.cpp.businessprocesses.listener.TasksCompletedListener;
import uk.gov.moj.cpp.businessprocesses.listener.TasksCreatedListener;
import uk.gov.moj.cpp.businessprocesses.shared.TaskAuditService;

import java.util.HashMap;
import java.util.List;

import org.camunda.bpm.engine.ProcessEngineConfiguration;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.runtime.Execution;
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
public class SJPCaseHearingDecisionBPMNTest {

    @RegisterExtension
    ProcessEngineExtension extension = ProcessEngineExtension.builder()
            .configurationResource("camunda.cfg.xml")
            .build();

    @Mock
    protected RuntimeService runtimeService;

    @Mock
    protected TaskService taskService;

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

    private static final String SJP_CASE_HEARING_DECISION_BPMN_TASK_NAME = "Refer_SJS_case_for_hearing";
    private static final String SJP_CASE_HEARING_DECISION_BPMN_PROCESS_NAME = "sjp-case-hearing-decision-saved";
    private static final String SJP_BPMN_FILE_NAME = "sjp-case-hearing-decision-saved.bpmn";

    private static final String CASE_ID_FIELD = "caseId";
    private static final String CASE_URN_FIELD = "caseURN";
    private static final String DEFENDANT_ID_FIELD = "defendantId";
    private static final String DEFENDANT_NAME_FIELD = "defendantName";
    private static final String REFER_COURT_HEARING_FIELD = "referCourtHearing";
    private static final String DUE_DATE_FIELD = "dueDate";
    private static final String DEEP_LINK_FIELD = "deepLink";
    private static final String WORK_QUEUE_FIELD = "workQueue";
    private static final String LAST_UPDATED_BY_ID = "lastUpdatedByID";
    private static final String LAST_UPDATED_BY_NAME = "lastUpdatedByName";
    private static final String SYSTEM_USER_NAME = "SYSTEM";
    private static final String TASK_TYPE_ID = "taskTypeId";
    private static final String IS_DELETABLE = "isDeletable";
    private static final String IS_DEFERRABLE = "isDeferrable";
    private static final String HEARING_DATE = "hearingDate";
    private static final String CASE_ID = randomUUID().toString();
    private static final String DEFENDANT_ID = randomUUID().toString();
    private static final String DEFENDANT_NAME = "Roy Robbie";
    private static final String DEEP_LINK = "https://dummyUrl/prosecution-casefile/case-at-a-glance/".concat(CASE_ID);
    private static final String DUE_DATE = "2022-08-24T01:46:36.068Z";
    private static final String HEARING_DATE_VALUE = "2024-09-24T01:46:36.068Z";
    private static final String WORK_QUEUE = "SJPWorkQueue";
    private static final String CASE_URN = "TFL12345678";
    private static final String SYSTEM_USER_ID = randomUUID().toString();
    private static final String TASK_TYPE_ID_VALUE = randomUUID().toString();
    public static final String TASK_NAME_FIELD = "taskName";

    @RequiredHistoryLevel(ProcessEngineConfiguration.HISTORY_ACTIVITY)
    @BeforeEach
    public void setUp() throws Exception {
        runtimeService = extension.getProcessEngine().getRuntimeService();
        taskService = extension.getProcessEngine().getTaskService();
        register("tasksCreatedListener", tasksCreatedListener);
        register("tasksAssignedListener", tasksAssignedListener);
        register("tasksCompletedListener", tasksCompletedListener);
    }

    @Test
    @Deployment(resources = {SJP_BPMN_FILE_NAME})
    public void processorContainsActiveTasksIfReferCourtHearingIsTrue() {

        final HashMap<String, Object> processVariables = new HashMap<>();
        processVariables.put(TASK_NAME_FIELD, SJP_CASE_HEARING_DECISION_BPMN_TASK_NAME);
        processVariables.put(CASE_ID_FIELD, CASE_ID);
        processVariables.put(CASE_URN_FIELD, CASE_URN);
        processVariables.put(DEFENDANT_ID_FIELD, DEFENDANT_ID);
        processVariables.put(DEFENDANT_NAME_FIELD, DEFENDANT_NAME);
        processVariables.put(DEEP_LINK_FIELD, DEEP_LINK);
        processVariables.put(REFER_COURT_HEARING_FIELD, true);
        processVariables.put(DUE_DATE_FIELD, DUE_DATE);
        processVariables.put(WORK_QUEUE_FIELD, WORK_QUEUE);
        processVariables.put(LAST_UPDATED_BY_ID, SYSTEM_USER_ID);
        processVariables.put(LAST_UPDATED_BY_NAME, SYSTEM_USER_NAME);
        processVariables.put(TASK_TYPE_ID, TASK_TYPE_ID_VALUE);
        processVariables.put(IS_DEFERRABLE, true);
        processVariables.put(IS_DELETABLE, true);
        processVariables.put(HEARING_DATE, HEARING_DATE_VALUE);

        final ProcessInstance processInstance = runtimeService
                .startProcessInstanceByKey(SJP_CASE_HEARING_DECISION_BPMN_PROCESS_NAME, CASE_ID_FIELD, processVariables);

        assertThat(processInstance.isEnded(), is(false));
        assertThat(processInstance.isSuspended(), is(false));

        final List<Task> activeTaskList = taskService.createTaskQuery().processInstanceId(processInstance.getId()).active().list();

        assertThat(activeTaskList.size(), is(1));

        final Task task = activeTaskList.get(0);
        assertThat(task.getTaskDefinitionKey(), is(SJP_CASE_HEARING_DECISION_BPMN_TASK_NAME));

        final String taskExecutionId = task.getExecutionId();

        verify(sender, times(1)).sendAsAdmin(jsonEnvelopeArgumentCaptor.capture());

        final Metadata metadata = jsonEnvelopeArgumentCaptor.getValue().metadata();
        assertThat(metadata.name(), CoreMatchers.is("businessprocesses.command.record-task-created"));

        assertThat(runtimeService.getVariable(taskExecutionId, TASK_NAME_FIELD), is(SJP_CASE_HEARING_DECISION_BPMN_TASK_NAME));
        assertThat(runtimeService.getVariable(taskExecutionId, CASE_ID_FIELD), is(CASE_ID));
        assertThat(runtimeService.getVariable(taskExecutionId, CASE_URN_FIELD), is(CASE_URN));
        assertThat(runtimeService.getVariable(taskExecutionId, DEFENDANT_ID_FIELD), is(DEFENDANT_ID));
        assertThat(runtimeService.getVariable(taskExecutionId, DEFENDANT_NAME_FIELD), is(DEFENDANT_NAME));
        assertThat(runtimeService.getVariable(taskExecutionId, DEEP_LINK_FIELD), is(DEEP_LINK));
        assertThat(runtimeService.getVariable(taskExecutionId, DUE_DATE_FIELD), is(DUE_DATE));
        assertThat(runtimeService.getVariable(taskExecutionId, LAST_UPDATED_BY_ID), is(SYSTEM_USER_ID));
        assertThat(runtimeService.getVariable(taskExecutionId, LAST_UPDATED_BY_NAME), is(SYSTEM_USER_NAME));
        assertThat(runtimeService.getVariable(taskExecutionId, TASK_TYPE_ID), is(TASK_TYPE_ID_VALUE));
        assertThat(runtimeService.getVariable(taskExecutionId, IS_DELETABLE), is(true));
        assertThat(runtimeService.getVariable(taskExecutionId, IS_DEFERRABLE), is(true));
        assertThat(runtimeService.getVariable(taskExecutionId, HEARING_DATE), is(HEARING_DATE_VALUE));


        final List<Execution> executionList = runtimeService.createExecutionQuery()
                .processInstanceId(processInstance.getProcessInstanceId())
                .list();

        assertThat(executionList.size(), is(2));

        final String execution = executionList.get(0).getId();

        assertThat(runtimeService.getVariable(execution, CASE_ID_FIELD), is(CASE_ID));
        assertThat(runtimeService.getVariable(execution, CASE_URN_FIELD), is(CASE_URN));
        assertThat(runtimeService.getVariable(execution, DEFENDANT_ID_FIELD), is(DEFENDANT_ID));
        assertThat(runtimeService.getVariable(execution, DEFENDANT_NAME_FIELD), is(DEFENDANT_NAME));
        assertThat(runtimeService.getVariable(execution, DEEP_LINK_FIELD), is(DEEP_LINK));
        assertThat(runtimeService.getVariable(execution, DUE_DATE_FIELD), is(DUE_DATE));
        assertThat(runtimeService.getVariable(taskExecutionId, LAST_UPDATED_BY_ID), is(SYSTEM_USER_ID));
        assertThat(runtimeService.getVariable(taskExecutionId, LAST_UPDATED_BY_NAME), is(SYSTEM_USER_NAME));
        assertThat(runtimeService.getVariable(execution, TASK_TYPE_ID), is(TASK_TYPE_ID_VALUE));
    }

    @Test
    @Deployment(resources = {SJP_BPMN_FILE_NAME})
    public void processorContainsNoActiveTasksIfReferCourtHearingIsFalse() {
        final HashMap<String, Object> processVariables = new HashMap<>();
        processVariables.put(CASE_ID_FIELD, CASE_ID);
        processVariables.put(CASE_URN_FIELD, CASE_URN);
        processVariables.put(DEFENDANT_ID_FIELD, DEFENDANT_ID);
        processVariables.put(DEFENDANT_NAME_FIELD, DEFENDANT_NAME);
        processVariables.put(DEEP_LINK_FIELD, DEEP_LINK);
        processVariables.put(REFER_COURT_HEARING_FIELD, false);
        processVariables.put(DUE_DATE_FIELD, DUE_DATE);
        processVariables.put(WORK_QUEUE_FIELD, WORK_QUEUE);
        processVariables.put(LAST_UPDATED_BY_ID, SYSTEM_USER_ID);
        processVariables.put(LAST_UPDATED_BY_NAME, SYSTEM_USER_NAME);
        processVariables.put(TASK_TYPE_ID, TASK_TYPE_ID_VALUE);

        final ProcessInstance processInstance = runtimeService
                .startProcessInstanceByKey(SJP_CASE_HEARING_DECISION_BPMN_PROCESS_NAME, processVariables);

        verify(sender, never()).sendAsAdmin(jsonEnvelopeArgumentCaptor.capture());

        final List<Task> activeTaskList = taskService.createTaskQuery().processInstanceId(processInstance.getId()).active().list();

        assertThat(processInstance.isEnded(), is(true));
        assertThat(activeTaskList.size(), is(0));
    }

    @AfterEach
    public void tearDown() throws Exception {
        Mocks.reset();
    }

}