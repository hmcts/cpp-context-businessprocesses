package uk.gov.moj.cpp.businessprocesses.listener;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasNoJsonPath;
import static java.util.UUID.randomUUID;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.camunda.bpm.engine.test.mock.Mocks.register;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import uk.gov.justice.services.core.dispatcher.SystemUserProvider;
import uk.gov.justice.services.core.sender.Sender;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.Metadata;
import uk.gov.moj.cpp.businessprocesses.service.UserGroupsService;
import uk.gov.moj.cpp.businessprocesses.shared.TaskAuditService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.json.JsonObject;

import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.delegate.DelegateTask;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.engine.test.Deployment;
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
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@SuppressWarnings({"squid:S2187"})
@ExtendWith({MockitoExtension.class, ProcessEngineExtension.class})
public class TasksAssignedListenerTest {

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

    @Mock
    private UserGroupsService userGroupsService;

    @Captor
    protected ArgumentCaptor<JsonEnvelope> jsonEnvelopeArgumentCaptor;

    @Captor
    protected ArgumentCaptor<DelegateTask> delegateTaskArgumentCaptor;

    @Captor
    protected ArgumentCaptor<JsonEnvelope> jsonEnvelopeArgument;

    protected ProcessInstance processInstance;

    private static final String SJP_CASE_HEARING_DECISION_BPMN_TASK_NAME = "Refer_SJS_case_for_hearing";
    private static final String SJP_CASE_HEARING_DECISION_BPMN_PROCESS_NAME = "sjp-case-hearing-decision-saved";
    private static final String CASE_ID_FIELD = "caseId";
    private static final String CASE_URN_FIELD = "caseURN";
    private static final String DEFENDANT_ID_FIELD = "defendantId";
    private static final String DEFENDANT_NAME_FIELD = "defendantName";
    private static final String REFER_COURT_HEARING_FIELD = "referCourtHearing";
    private static final String DUE_DATE_FIELD = "dueDate";
    private static final String DEEP_LINK_FIELD = "deepLink";
    private static final String WORK_QUEUE_FIELD = "workQueue";
    private static final String LAST_UPDATED_BY_NAME = "lastUpdatedByName";
    private static final String LAST_UPDATED_BY_ID = "lastUpdatedByID";
    private static final String TASK_TYPE_ID = "taskTypeId";
    private static final String IS_DELETABLE_FIELD = "isDeletable";
    private static final String IS_DEFERRABLE_FIELD = "isDeferrable";
    private static final String HEARING_DATE = "hearingDate";

    private static final String CASE_ID = randomUUID().toString();
    private static final String TASK_TYPE_ID_VALUE = randomUUID().toString();
    private static final String DEFENDANT_ID = randomUUID().toString();
    private static final String DEFENDANT_NAME = "Roy Robbie";
    private static final String DEEP_LINK = "https://dummyUrl/prosecution-casefile/case-at-a-glance/".concat(CASE_ID);
    private static final String DUE_DATE = "2022-08-24";
    private static final String HEARING_DATE_VALUE = "2024-09-24T01:46:36.068Z";
    private static final String WORK_QUEUE = "SJPWorkQueue";
    private static final String CASE_URN = "TFL12345678";
    private static final String CHANGE_AUTHOR = "John Smith";
    private static final String CHANGE_AUTHOR_ID = randomUUID().toString();
    private static final String USER_ID = randomUUID().toString();
    private static final String ASSIGN_TO = "Emma Cleaner";
    public static final String TASK_NAME_FIELD = "taskName";
    public static final String TASK_NAME = "Refer SJS case for hearing";
    private static final String SJP_CASE_HEARING_DECISION_SAVED_BPMN = "sjp-case-hearing-decision-saved.bpmn";

    @BeforeEach
    public void setUp() throws Exception {
        runtimeService = extension.getProcessEngine().getRuntimeService();
        taskService = extension.getProcessEngine().getTaskService();
        register("tasksCreatedListener", tasksCreatedListener);
        register("tasksAssignedListener", tasksAssignedListener);
        register("tasksCompletedListener", tasksCompletedListener);
        register("tasksUpdatedListener", tasksUpdatedListener);
    }

    @Test
    @Deployment(resources = {SJP_CASE_HEARING_DECISION_SAVED_BPMN})
    public void shouldTriggerTasksAssignedListener() {

        when(systemUserProvider.getContextSystemUserId()).thenReturn(Optional.of(randomUUID()));
        when(userGroupsService.getUserDetails(any())).thenReturn(ASSIGN_TO);

        startProcess(SJP_CASE_HEARING_DECISION_BPMN_PROCESS_NAME, CASE_ID);

        final List<Task> activeTaskList = taskService.createTaskQuery().processInstanceId(processInstance.getId()).active().list();
        final Task task = activeTaskList.stream().filter(taskDetails -> (SJP_CASE_HEARING_DECISION_BPMN_TASK_NAME).equals(taskDetails.getTaskDefinitionKey()))
                .findFirst()
                .orElse(null);

        assertThat(activeTaskList.stream().anyMatch(taskDetails -> (SJP_CASE_HEARING_DECISION_BPMN_TASK_NAME).equals(taskDetails.getTaskDefinitionKey())), is(true));
        taskService.setVariableLocal(task.getId(), LAST_UPDATED_BY_NAME, CHANGE_AUTHOR);
        taskService.setVariableLocal(task.getId(), LAST_UPDATED_BY_ID, CHANGE_AUTHOR_ID);
        taskService.setAssignee(task.getId(), USER_ID);
        verify(tasksAssignedListener).notify(delegateTaskArgumentCaptor.capture());

        final DelegateTask delegateTask = delegateTaskArgumentCaptor.getValue();
        assertThat(delegateTask.getName(), is(TASK_NAME));
        assertThat(delegateTask.getExecution().getProcessInstance().getBusinessKey(), is(CASE_ID));

        verify(taskAuditService, times(2)).audit(jsonEnvelopeArgument.capture(), eq(CHANGE_AUTHOR_ID));

        verify(sender, times(2)).sendAsAdmin(jsonEnvelopeArgumentCaptor.capture());


        final Metadata metadata = jsonEnvelopeArgumentCaptor.getValue().metadata();
        assertThat(metadata.name(), is("businessprocesses.command.record-task-assigned"));

        final JsonObject jsonObject = jsonEnvelopeArgumentCaptor.getValue().asJsonObject();

        assertThat(jsonObject.getString("id"), notNullValue());
        assertThat(jsonObject, hasJsonPath("$.assignToId"));
        assertThat(jsonObject, hasJsonPath("$.assignTo"));
        assertThat(jsonObject.containsKey("assignToId"), is(true));
        assertThat(jsonObject.getString("assignToId"), is(USER_ID));
        assertThat(jsonObject.getString("assignTo"), is(ASSIGN_TO));
        assertThat(jsonObject.getString("changeAuthorId"), is(CHANGE_AUTHOR_ID));
        assertThat(jsonObject.getString("changeAuthorId"), is(CHANGE_AUTHOR_ID));
    }


    @Test
    @Deployment(resources = {SJP_CASE_HEARING_DECISION_SAVED_BPMN})
    public void shouldTriggerTasksAssignedListenerWhenAssignedToIdEmptyForUnAssigned() {

        when(systemUserProvider.getContextSystemUserId()).thenReturn(Optional.of(randomUUID()));

        startProcess(SJP_CASE_HEARING_DECISION_BPMN_PROCESS_NAME, CASE_ID);

        final List<Task> activeTaskList = taskService.createTaskQuery().processInstanceId(processInstance.getId()).active().list();
        final Task task = activeTaskList.stream().filter(taskDetails -> (SJP_CASE_HEARING_DECISION_BPMN_TASK_NAME).equals(taskDetails.getTaskDefinitionKey()))
                .findFirst()
                .orElse(null);

        assertThat(activeTaskList.stream().anyMatch(taskDetails -> (SJP_CASE_HEARING_DECISION_BPMN_TASK_NAME).equals(taskDetails.getTaskDefinitionKey())), is(true));
        taskService.setVariableLocal(task.getId(), LAST_UPDATED_BY_NAME, CHANGE_AUTHOR);
        taskService.setVariableLocal(task.getId(), LAST_UPDATED_BY_ID, CHANGE_AUTHOR_ID);
        taskService.setAssignee(task.getId(), EMPTY);
        verify(tasksAssignedListener).notify(delegateTaskArgumentCaptor.capture());

        final DelegateTask delegateTask = delegateTaskArgumentCaptor.getValue();
        assertThat(delegateTask.getName(), is(TASK_NAME));
        assertThat(delegateTask.getExecution().getProcessInstance().getBusinessKey(), is(CASE_ID));

        verify(taskAuditService, times(2)).audit(jsonEnvelopeArgument.capture(), eq(CHANGE_AUTHOR_ID));

        verify(sender, times(2)).sendAsAdmin(jsonEnvelopeArgumentCaptor.capture());


        final Metadata metadata = jsonEnvelopeArgumentCaptor.getValue().metadata();
        assertThat(metadata.name(), is("businessprocesses.command.record-task-assigned"));

        final JsonObject jsonObject = jsonEnvelopeArgumentCaptor.getValue().asJsonObject();

        assertThat(jsonObject.getString("id"), notNullValue());
        assertThat(jsonObject, hasNoJsonPath("$.assignToId"));
        assertThat(jsonObject.containsKey("assignToId"), is(false));
        assertThat(jsonObject.getString("changeAuthorId"), is(CHANGE_AUTHOR_ID));
        assertThat(jsonObject.getString("changeAuthor"), is(CHANGE_AUTHOR));
    }

    private void startProcess(final String processDefinitionKey, final String businessKey) {
        final Map<String, Object> processVariables = new HashMap<>();
        processVariables.put(TASK_NAME_FIELD, TASK_NAME);
        processVariables.put(CASE_ID_FIELD, CASE_ID);
        processVariables.put(CASE_URN_FIELD, CASE_URN);
        processVariables.put(DEFENDANT_ID_FIELD, DEFENDANT_ID);
        processVariables.put(DEFENDANT_NAME_FIELD, DEFENDANT_NAME);
        processVariables.put(DEEP_LINK_FIELD, DEEP_LINK);
        processVariables.put(DUE_DATE_FIELD, DUE_DATE);
        processVariables.put(WORK_QUEUE_FIELD, WORK_QUEUE);
        processVariables.put(LAST_UPDATED_BY_NAME, CHANGE_AUTHOR);
        processVariables.put(LAST_UPDATED_BY_ID, CHANGE_AUTHOR_ID);
        processVariables.put(REFER_COURT_HEARING_FIELD, true);
        processVariables.put(TASK_TYPE_ID, TASK_TYPE_ID_VALUE);
        processVariables.put(IS_DELETABLE_FIELD, true);
        processVariables.put(IS_DEFERRABLE_FIELD, false);
        processVariables.put(HEARING_DATE, HEARING_DATE_VALUE);
        processInstance = runtimeService.startProcessInstanceByKey(processDefinitionKey, businessKey, processVariables);
    }

    @AfterEach
    public void tearDown() throws Exception {
        Mocks.reset();
    }
}