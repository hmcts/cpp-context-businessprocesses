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
public class ApplicationReceivedBPMNTest {

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

    private static final String APPLICATION_RECEIVED_BPMN_PROCESS_NAME = "application-received";
    private static final String APPLICATION_RECEIVED_BPMN_TASK_NAME = "Process_Application";
    private static final String APPLICATION_RECEIVED_BPMN_FILE_NAME = "application-received.bpmn";

    private static final String CASE_ID_FIELD = "caseId";
    private static final String APPLICATION_ID_FIELD = "applicationId";
    private static final String CASE_URN_FIELD = "caseURN";
    private static final String DEEP_LINK_FIELD = "deepLink";
    private static final String DEFENDANT_ID_FIELD = "defendantId";
    private static final String DEFENDANT_NAME_FIELD = "defendantName";
    private static final String HEARING_DATE_FIELD = "hearingDate";
    private static final String HEARING_ID_FIELD = "hearingId";
    private static final String WORK_QUEUE_FIELD = "workQueue";
    private static final String APP_TYPE_FIELD = "appType";
    private static final String APP_TYPE = "Application for an order of reimbursement in relation to a closure order";
    private static final String IS_APPLICATION_PROSECUTOR_DEFENCE_FIELD = "isApplicationProsecutorOrDefence";
    private static final String DUE_DATE_FIELD = "dueDate";

    private static final String LAST_UPDATED_BY_ID = "lastUpdatedByID";
    private static final String LAST_UPDATED_BY_NAME = "lastUpdatedByName";
    private static final String JURISDICTION = "jurisdiction";
    private static final String TASK_TYPE_ID = "taskTypeId";
    private static final String IS_DELETABLE = "isDeletable";
    private static final String IS_DEFERRABLE = "isDeferrable";

    private static final String CASE_ID = randomUUID().toString();
    private static final String CASE_URN = "TFL12345678";
    private static final String DEEP_LINK = "https://dummyUrl/prosecution-casefile/application-at-a-glance/";
    private static final String DEFENDANT_ID = randomUUID().toString();
    private static final String DEFENDANT_NAME = "Roy Robbie";
    private static final String HEARING_DATE = "2022-08-24T01:46:36.068Z";
    private static final String HEARING_ID = randomUUID().toString();
    private static final String WORK_QUEUE = "ApplicationsWorkQueue";
    private static final String TASK_NAME = "Process ".concat(APP_TYPE).concat(" Application");
    private static final String DUE_DATE = "2021-08-24T01:46:36.068Z";
    private static final String SYSTEM_USER_ID = randomUUID().toString();
    private static final String SYSTEM_USER_NAME = "SYSTEM";
    private static final String CROWN_JURISDICTION_TYPE = "CROWN";
    private static final String TASK_TYPE_ID_VALUE = randomUUID().toString();


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
    @Deployment(resources = {APPLICATION_RECEIVED_BPMN_FILE_NAME})
    public void processorContainsActiveTasksIfIsApplicationProsecutorOrDefenceIsTrue() {
        final String applicationId = randomUUID().toString();

        final HashMap<String, Object> processVariables = new HashMap<>();
        processVariables.put(CASE_ID_FIELD, CASE_ID);
        processVariables.put(APPLICATION_ID_FIELD, applicationId);
        processVariables.put(CASE_URN_FIELD, CASE_URN);
        processVariables.put(DEEP_LINK_FIELD, DEEP_LINK.concat(applicationId));
        processVariables.put(DEFENDANT_ID_FIELD, DEFENDANT_ID);
        processVariables.put(DEFENDANT_NAME_FIELD, DEFENDANT_NAME);
        processVariables.put(HEARING_ID_FIELD, HEARING_ID);
        processVariables.put(HEARING_DATE_FIELD, HEARING_DATE);
        processVariables.put(WORK_QUEUE_FIELD, WORK_QUEUE);
        processVariables.put(IS_APPLICATION_PROSECUTOR_DEFENCE_FIELD, true);
        processVariables.put(APP_TYPE_FIELD, APP_TYPE);
        processVariables.put(DUE_DATE_FIELD, DUE_DATE);
        processVariables.put(LAST_UPDATED_BY_ID, SYSTEM_USER_ID);
        processVariables.put(LAST_UPDATED_BY_NAME, SYSTEM_USER_NAME);
        processVariables.put(JURISDICTION, CROWN_JURISDICTION_TYPE);
        processVariables.put(TASK_TYPE_ID, TASK_TYPE_ID_VALUE);
        processVariables.put(IS_DELETABLE, true);
        processVariables.put(IS_DEFERRABLE, true);


        final ProcessInstance processInstance = runtimeService
                .startProcessInstanceByKey(APPLICATION_RECEIVED_BPMN_PROCESS_NAME, applicationId, processVariables);

        assertThat(processInstance.isEnded(), is(false));
        assertThat(processInstance.isSuspended(), is(false));

        final List<Task> activeTaskList = taskService.createTaskQuery().processInstanceId(processInstance.getId()).active().list();

        assertThat(activeTaskList.size(), is(1));

        final Task task = activeTaskList.get(0);
        assertThat(task.getTaskDefinitionKey(), is(APPLICATION_RECEIVED_BPMN_TASK_NAME));
        assertThat(task.getName(), is(TASK_NAME));

        verify(sender, times(1)).sendAsAdmin(jsonEnvelopeArgumentCaptor.capture());

        final Metadata metadata = jsonEnvelopeArgumentCaptor.getValue().metadata();
        assertThat(metadata.name(), CoreMatchers.is("businessprocesses.command.record-task-created"));

        final String taskExecutionId = task.getExecutionId();

        assertThat(runtimeService.getVariable(taskExecutionId, CASE_ID_FIELD), is(CASE_ID));
        assertThat(runtimeService.getVariable(taskExecutionId, APPLICATION_ID_FIELD), is(applicationId));
        assertThat(runtimeService.getVariable(taskExecutionId, CASE_URN_FIELD), is(CASE_URN));
        assertThat(runtimeService.getVariable(taskExecutionId, DEEP_LINK_FIELD), is(DEEP_LINK.concat(applicationId)));
        assertThat(runtimeService.getVariable(taskExecutionId, DEFENDANT_ID_FIELD), is(DEFENDANT_ID));
        assertThat(runtimeService.getVariable(taskExecutionId, DEFENDANT_NAME_FIELD), is(DEFENDANT_NAME));
        assertThat(runtimeService.getVariable(taskExecutionId, HEARING_ID_FIELD), is(HEARING_ID));
        assertThat(runtimeService.getVariable(taskExecutionId, HEARING_DATE_FIELD), is(HEARING_DATE));
        assertThat(runtimeService.getVariable(taskExecutionId, LAST_UPDATED_BY_ID), is(SYSTEM_USER_ID));
        assertThat(runtimeService.getVariable(taskExecutionId, LAST_UPDATED_BY_NAME), is(SYSTEM_USER_NAME));
        assertThat(runtimeService.getVariable(taskExecutionId, JURISDICTION), is(CROWN_JURISDICTION_TYPE));
        assertThat(runtimeService.getVariable(taskExecutionId, TASK_TYPE_ID), is(TASK_TYPE_ID_VALUE));
        assertThat(runtimeService.getVariable(taskExecutionId, IS_DELETABLE), is(true));
        assertThat(runtimeService.getVariable(taskExecutionId, IS_DEFERRABLE), is(true));

        final List<Execution> executionList = runtimeService.createExecutionQuery()
                .processInstanceId(processInstance.getProcessInstanceId())
                .list();

        assertThat(executionList.size(), is(2));

        final String execution = executionList.get(0).getId();

        assertThat(runtimeService.getVariable(execution, CASE_ID_FIELD), is(CASE_ID));
        assertThat(runtimeService.getVariable(execution, APPLICATION_ID_FIELD), is(applicationId));
        assertThat(runtimeService.getVariable(execution, CASE_URN_FIELD), is(CASE_URN));
        assertThat(runtimeService.getVariable(execution, DEEP_LINK_FIELD), is(DEEP_LINK.concat(applicationId)));
        assertThat(runtimeService.getVariable(execution, DEFENDANT_ID_FIELD), is(DEFENDANT_ID));
        assertThat(runtimeService.getVariable(execution, DEFENDANT_NAME_FIELD), is(DEFENDANT_NAME));
        assertThat(runtimeService.getVariable(execution, HEARING_ID_FIELD), is(HEARING_ID));
        assertThat(runtimeService.getVariable(execution, HEARING_DATE_FIELD), is(HEARING_DATE));
        assertThat(runtimeService.getVariable(execution, LAST_UPDATED_BY_ID), is(SYSTEM_USER_ID));
        assertThat(runtimeService.getVariable(execution, LAST_UPDATED_BY_NAME), is(SYSTEM_USER_NAME));
        assertThat(runtimeService.getVariable(execution, JURISDICTION), is(CROWN_JURISDICTION_TYPE));
        assertThat(runtimeService.getVariable(execution, TASK_TYPE_ID), is(TASK_TYPE_ID_VALUE));
        assertThat(runtimeService.getVariable(execution, IS_DELETABLE), is(true));
        assertThat(runtimeService.getVariable(execution, IS_DEFERRABLE), is(true));
    }

    @Test
    @Deployment(resources = {APPLICATION_RECEIVED_BPMN_FILE_NAME})
    public void processorContainsActiveTasksIfIsApplicationProsecutorOrDefenceIsFalse() {
        final String applicationId = randomUUID().toString();
        final HashMap<String, Object> processVariables = new HashMap<>();
        processVariables.put(CASE_ID_FIELD, CASE_ID);
        processVariables.put(APPLICATION_ID_FIELD, applicationId);
        processVariables.put(CASE_URN_FIELD, CASE_URN);
        processVariables.put(DEEP_LINK_FIELD, DEEP_LINK.concat(applicationId));
        processVariables.put(DEFENDANT_ID_FIELD, DEFENDANT_ID);
        processVariables.put(DEFENDANT_NAME_FIELD, DEFENDANT_NAME);
        processVariables.put(HEARING_ID_FIELD, HEARING_ID);
        processVariables.put(HEARING_DATE_FIELD, HEARING_DATE);
        processVariables.put(WORK_QUEUE_FIELD, WORK_QUEUE);
        processVariables.put(IS_APPLICATION_PROSECUTOR_DEFENCE_FIELD, false);
        processVariables.put(APP_TYPE_FIELD, APP_TYPE);
        processVariables.put(DUE_DATE_FIELD, DUE_DATE);
        processVariables.put(LAST_UPDATED_BY_NAME, SYSTEM_USER_NAME);
        processVariables.put(JURISDICTION, CROWN_JURISDICTION_TYPE);
        processVariables.put(TASK_TYPE_ID, TASK_TYPE_ID_VALUE);

        final ProcessInstance processInstance = runtimeService
                .startProcessInstanceByKey(APPLICATION_RECEIVED_BPMN_PROCESS_NAME, applicationId, processVariables);

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