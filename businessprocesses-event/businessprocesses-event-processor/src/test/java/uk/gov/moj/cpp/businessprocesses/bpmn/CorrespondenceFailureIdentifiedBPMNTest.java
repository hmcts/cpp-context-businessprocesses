package uk.gov.moj.cpp.businessprocesses.bpmn;

import static java.util.UUID.randomUUID;
import static org.camunda.bpm.engine.test.mock.Mocks.register;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.SYSTEM_USER_NAME;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.CANDIDATE_GROUPS;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.CASE_URN;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.CORRESPONDENCE_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.DEEP_LINK;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.DUE_DATE;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.IS_DEFERRABLE;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.IS_DELETABLE;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.LAST_UPDATED_BY_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.LAST_UPDATED_BY_NAME;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.TASK_NAME;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.WORK_QUEUE;

import uk.gov.justice.services.core.dispatcher.SystemUserProvider;
import uk.gov.justice.services.core.sender.Sender;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.Metadata;
import uk.gov.moj.cpp.businessprocesses.listener.TasksAssignedListener;
import uk.gov.moj.cpp.businessprocesses.listener.TasksCompletedListener;
import uk.gov.moj.cpp.businessprocesses.listener.TasksCreatedListener;
import uk.gov.moj.cpp.businessprocesses.listener.TasksUpdatedListener;
import uk.gov.moj.cpp.businessprocesses.service.UserGroupsService;
import uk.gov.moj.cpp.businessprocesses.shared.TaskAuditService;

import java.util.HashMap;
import java.util.List;

import org.camunda.bpm.engine.ProcessEngineConfiguration;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.engine.test.Deployment;
import org.camunda.bpm.engine.test.RequiredHistoryLevel;
import org.camunda.bpm.engine.test.mock.Mocks;
import org.camunda.bpm.extension.junit5.test.ProcessEngineExtension;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
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
public class CorrespondenceFailureIdentifiedBPMNTest {

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
    protected TasksUpdatedListener tasksUpdatedListener;

    @Mock
    protected SystemUserProvider systemUserProvider;

    @Mock
    protected TaskAuditService taskAuditService;

    @Captor
    protected ArgumentCaptor<JsonEnvelope> jsonEnvelopeArgumentCaptor;

    private static final String CASE_ID = randomUUID().toString();
    private static final String CASE_URN_VALUE = "TFL12345678";
    private static final String SYSTEM_USER_ID = randomUUID().toString();
    private static final String CORRESPONDENCE_ID_VALUE = randomUUID().toString();
    private static final String TASK_TYPE_ID_VALUE = randomUUID().toString();
    private static final String WORK_QUEUE_FIELD = "workQueue";
    private static final String WORK_QUEUE_VALUE = "CorrespondenceWorkQueue";
    private static final String TASK_TYPE_ID = "taskTypeId";
    private static final String DEEP_LINK_VALUE = "https://dummyUrl/prosecution-casefile/doucments/";
    private static final String CASE_ID_FIELD = "caseId";
    private static final String CORRESPONDENCE_FAILURE_IDENTIFIED = "correspondence_failure_identified";
    private static final String CORRESPONDENCE_FAILURE_DEFINITION = "Correspondence_failure";
    private static final String TASK_NAME_VALUE = "Correspondence_failure";
    private static final List<String> USER_GROUPS = List.of("user-groups");
    private static final String DUEDATE_VALUE = "2021-08-24T01:46:36.068Z";
    private static final String DEEP_LINK_FIELD = "deepLink";
    private static final String DOCUMENT_ID = randomUUID().toString();
    private static final String CHANGE_AUTHOR = "John Smith";
    private static final String CHANGE_AUTHOR_ID = randomUUID().toString();
    private ProcessInstance processInstance = null;
    private static final String ASSIGN_TO = "Emma Cleaner";

    @Mock
    private UserGroupsService userGroupsService;

    @RequiredHistoryLevel(ProcessEngineConfiguration.HISTORY_ACTIVITY)
    @BeforeEach
    public void setUp() throws Exception {
        runtimeService = extension.getProcessEngine().getRuntimeService();
        taskService = extension.getProcessEngine().getTaskService();
        register("tasksCreatedListener", tasksCreatedListener);
        register("tasksAssignedListener", tasksAssignedListener);
        register("tasksCompletedListener", tasksCompletedListener);
        register("tasksUpdatedListener", tasksUpdatedListener);
    }

    @AfterEach
    public void tearDown() throws Exception {
        Mocks.reset();
    }

    @Test
    @Deployment(resources = {"correspondence_failure_identified.bpmn"})
    public void shouldProcessContainsActiveTaskWhenCorrespondenceFailureIdentified() {
        createTaskProcess();
        MatcherAssert.assertThat(processInstance.isEnded(), is(false));
        MatcherAssert.assertThat(processInstance.isSuspended(), is(false));

        final List<Task> activeTaskList = taskService.createTaskQuery().processInstanceId(processInstance.getId()).active().list();
        MatcherAssert.assertThat(activeTaskList.size(), is(1));

        final Task task = activeTaskList.get(0);
        MatcherAssert.assertThat(task.getTaskDefinitionKey(), is(CORRESPONDENCE_FAILURE_DEFINITION));
        MatcherAssert.assertThat(task.getName(), is(TASK_NAME_VALUE));

        verify(sender, times(1)).sendAsAdmin(jsonEnvelopeArgumentCaptor.capture());
        Metadata metadata = jsonEnvelopeArgumentCaptor.getValue().metadata();
        assertThat(metadata.name(), CoreMatchers.is("businessprocesses.command.record-task-created"));

        final String taskExecutionId = task.getExecutionId();
        MatcherAssert.assertThat(runtimeService.getVariable(taskExecutionId, CASE_ID_FIELD), is(CASE_ID));
        MatcherAssert.assertThat(runtimeService.getVariable(taskExecutionId, LAST_UPDATED_BY_ID), is(SYSTEM_USER_ID));
        MatcherAssert.assertThat(runtimeService.getVariable(taskExecutionId, LAST_UPDATED_BY_NAME), is(SYSTEM_USER_NAME));
        MatcherAssert.assertThat(runtimeService.getVariable(taskExecutionId, CORRESPONDENCE_ID), is(CORRESPONDENCE_ID_VALUE));
        MatcherAssert.assertThat(runtimeService.getVariable(taskExecutionId, TASK_TYPE_ID), is(TASK_TYPE_ID_VALUE));
        MatcherAssert.assertThat(runtimeService.getVariable(taskExecutionId, DEEP_LINK_FIELD), is(DEEP_LINK_VALUE.concat(DOCUMENT_ID)));
        MatcherAssert.assertThat(runtimeService.getVariable(taskExecutionId, CANDIDATE_GROUPS), is(USER_GROUPS));
        MatcherAssert.assertThat(runtimeService.getVariable(taskExecutionId, DUE_DATE), is(DUEDATE_VALUE));
        MatcherAssert.assertThat(runtimeService.getVariable(taskExecutionId, IS_DEFERRABLE), is(true));
        MatcherAssert.assertThat(runtimeService.getVariable(taskExecutionId, IS_DELETABLE), is(true));
    }

    @Test
    @Deployment(resources = {"correspondence_failure_identified.bpmn"})
    public void shouldTaskProcessStatusCompletedWhenCorrespondenceFailureIdentifiedProcessIsCompleted() {
        createTaskProcess();
        MatcherAssert.assertThat(processInstance.isEnded(), is(false));
        MatcherAssert.assertThat(processInstance.isSuspended(), is(false));

        final List<Task> activeTaskList = taskService.createTaskQuery().processInstanceId(processInstance.getId()).active().list();

        MatcherAssert.assertThat(activeTaskList.size(), is(1));

        final Task task = activeTaskList.get(0);
        MatcherAssert.assertThat(task.getTaskDefinitionKey(), is(CORRESPONDENCE_FAILURE_DEFINITION));
        MatcherAssert.assertThat(task.getName(), is(TASK_NAME_VALUE));

        taskService.setVariableLocal(task.getId(), LAST_UPDATED_BY_NAME, CHANGE_AUTHOR);
        taskService.setVariableLocal(task.getId(), LAST_UPDATED_BY_ID, CHANGE_AUTHOR_ID);
        taskService.complete(task.getId());
        verify(sender, times(2)).sendAsAdmin(jsonEnvelopeArgumentCaptor.capture());
        Metadata metadata = jsonEnvelopeArgumentCaptor.getValue().metadata();
        assertThat(metadata.name(), CoreMatchers.is("businessprocesses.command.record-task-completed"));

    }

    @Test
    @Deployment(resources = {"correspondence_failure_identified.bpmn"})
    public void shouldTaskProcessAssigneeChangedWhenCorrespondenceFailureIdentifiedProcessChangedAssignee() {
        createTaskProcess();
        final List<Task> activeTaskList = taskService.createTaskQuery().processInstanceId(processInstance.getId()).active().list();
        MatcherAssert.assertThat(activeTaskList.size(), is(1));
        final Task task = activeTaskList.get(0);
        MatcherAssert.assertThat(task.getTaskDefinitionKey(), is(CORRESPONDENCE_FAILURE_DEFINITION));
        MatcherAssert.assertThat(task.getName(), is(TASK_NAME_VALUE));

        when(userGroupsService.getUserDetails(any())).thenReturn(ASSIGN_TO);

        taskService.setVariableLocal(task.getId(), LAST_UPDATED_BY_NAME, CHANGE_AUTHOR);
        taskService.setVariableLocal(task.getId(), LAST_UPDATED_BY_ID, CHANGE_AUTHOR_ID);
        taskService.setAssignee(task.getId(), "ctsc");

        verify(sender, times(2)).sendAsAdmin(jsonEnvelopeArgumentCaptor.capture());
        verify(userGroupsService).getUserDetails(any());
        Metadata metadata = jsonEnvelopeArgumentCaptor.getValue().metadata();
        assertThat(metadata.name(), CoreMatchers.is("businessprocesses.command.record-task-assigned"));
    }


    private void createTaskProcess() {
        final HashMap<String, Object> processVariables = new HashMap<>();
        processVariables.put(CASE_ID_FIELD, CASE_ID);
        processVariables.put(LAST_UPDATED_BY_ID, SYSTEM_USER_ID);
        processVariables.put(LAST_UPDATED_BY_NAME, SYSTEM_USER_NAME);
        processVariables.put(CORRESPONDENCE_ID, CORRESPONDENCE_ID_VALUE);
        processVariables.put(TASK_TYPE_ID, TASK_TYPE_ID_VALUE);
        processVariables.put(DEEP_LINK, DEEP_LINK_VALUE.concat(DOCUMENT_ID));
        processVariables.put(WORK_QUEUE, WORK_QUEUE_VALUE);
        processVariables.put(TASK_NAME, CORRESPONDENCE_FAILURE_DEFINITION);
        processVariables.put(CANDIDATE_GROUPS, USER_GROUPS);
        processVariables.put(DUE_DATE, DUEDATE_VALUE);
        processVariables.put(CASE_URN, CASE_URN_VALUE);
        processVariables.put(IS_DELETABLE, true);
        processVariables.put(IS_DEFERRABLE, true);

        processInstance = runtimeService
                .startProcessInstanceByKey(CORRESPONDENCE_FAILURE_IDENTIFIED, DOCUMENT_ID, processVariables);
    }
}
