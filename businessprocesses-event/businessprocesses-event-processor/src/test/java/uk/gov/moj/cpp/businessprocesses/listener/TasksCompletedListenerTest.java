package uk.gov.moj.cpp.businessprocesses.listener;

import static java.lang.Boolean.TRUE;
import static java.util.UUID.randomUUID;
import static org.camunda.bpm.engine.test.mock.Mocks.register;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.DELETION_REASON;

import uk.gov.justice.services.common.util.UtcClock;
import uk.gov.justice.services.core.dispatcher.SystemUserProvider;
import uk.gov.justice.services.core.sender.Sender;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.Metadata;
import uk.gov.moj.cpp.businessprocesses.shared.TaskAuditService;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
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
public class TasksCompletedListenerTest {

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

    @Spy
    @InjectMocks
    protected CotrTaskCreatedListener cotrTaskCreatedListener;

    @Mock
    protected SystemUserProvider systemUserProvider;

    @Mock
    protected TaskAuditService taskAuditService;

    @Captor
    protected ArgumentCaptor<JsonEnvelope> jsonEnvelopeArgumentCaptor;

    @Captor
    protected ArgumentCaptor<DelegateTask> delegateTaskArgumentCaptor;

    @Captor
    protected ArgumentCaptor<JsonEnvelope> jsonEnvelopeArgument;

    protected ProcessInstance processInstance;

    private static final String SJP_CASE_HEARING_DECISION_BPMN_TASK_NAME = "Refer_SJS_case_for_hearing";
    public static final String TASK_NAME = "Refer SJS case for hearing";
    private static final String SJP_CASE_HEARING_DECISION_BPMN_PROCESS_NAME = "sjp-case-hearing-decision-saved";
    public static final String TASK_NAME_FIELD = "taskName";
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
    private static final String COMPLETED_DATE_FIELD = "completedDate";
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
    private static final String COMPLETED_DATE = new UtcClock().now().format(DateTimeFormatter.ISO_ZONED_DATE_TIME);
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
    public void shouldTriggerTasksCompletedListenerWhenTaskIsCompleted() {
        startProcess(SJP_CASE_HEARING_DECISION_BPMN_PROCESS_NAME, CASE_ID);
        final Optional<Task> optionalTask = taskService.createTaskQuery().processInstanceId(processInstance.getId()).active().list().stream().filter(task -> task.getTaskDefinitionKey().equals(SJP_CASE_HEARING_DECISION_BPMN_TASK_NAME)).findFirst();
        assertThat(optionalTask.isPresent(), is(TRUE));

        final Task activeTask = optionalTask.get();
        final String taskId = activeTask.getId();
        taskService.setVariableLocal(taskId, LAST_UPDATED_BY_NAME, CHANGE_AUTHOR);
        taskService.setVariableLocal(taskId, LAST_UPDATED_BY_ID, CHANGE_AUTHOR_ID);
        taskService.complete(taskId);
        verify(tasksCompletedListener).notify(delegateTaskArgumentCaptor.capture());
        final DelegateTask delegateTask = delegateTaskArgumentCaptor.getValue();
        assertThat(SJP_CASE_HEARING_DECISION_BPMN_TASK_NAME, is(delegateTask.getTaskDefinitionKey()));
        assertThat(CASE_ID, is(delegateTask.getExecution().getProcessInstance().getBusinessKey()));

        verify(taskAuditService, times(2)).audit(jsonEnvelopeArgument.capture(), eq(CHANGE_AUTHOR_ID));
        verify(sender, times(2)).sendAsAdmin(jsonEnvelopeArgumentCaptor.capture());

        final Metadata metadata = jsonEnvelopeArgumentCaptor.getValue().metadata();
        assertThat(metadata.name(), is("businessprocesses.command.record-task-completed"));

        final JsonObject jsonObject = jsonEnvelopeArgumentCaptor.getValue().asJsonObject();

        assertThat(jsonObject.getString("id"), notNullValue());
        assertThat(jsonObject.getString("changeAuthorId"), is(CHANGE_AUTHOR_ID));
        assertThat(jsonObject.getString("changeAuthor"), is(CHANGE_AUTHOR));
        assertThat(getDate(jsonObject.getString("completedDate")), is(getDate(COMPLETED_DATE)));
        assertThat(jsonObject.containsKey("deletionReason"), is(false));
    }

    @Test
    @Deployment(resources = {SJP_CASE_HEARING_DECISION_SAVED_BPMN})
    public void shouldTriggerTasksCompletedListenerWhenTaskIsDeleted() {
        startProcess(SJP_CASE_HEARING_DECISION_BPMN_PROCESS_NAME, CASE_ID);
        final Optional<Task> optionalTask = taskService.createTaskQuery().processInstanceId(processInstance.getId()).active().list().stream().filter(task -> task.getTaskDefinitionKey().equals(SJP_CASE_HEARING_DECISION_BPMN_TASK_NAME)).findFirst();
        assertThat(optionalTask.isPresent(), is(TRUE));

        final Task activeTask = optionalTask.get();
        final String taskId = activeTask.getId();

        taskService.setVariableLocal(taskId, LAST_UPDATED_BY_NAME, CHANGE_AUTHOR);
        taskService.setVariableLocal(taskId, LAST_UPDATED_BY_ID, CHANGE_AUTHOR_ID);
        taskService.setVariableLocal(taskId, DELETION_REASON, "Task Created In Error");
        taskService.complete(taskId);

        verify(tasksCompletedListener).notify(delegateTaskArgumentCaptor.capture());
        final DelegateTask delegateTask = delegateTaskArgumentCaptor.getValue();
        assertThat(SJP_CASE_HEARING_DECISION_BPMN_TASK_NAME, is(delegateTask.getTaskDefinitionKey()));
        assertThat(CASE_ID, is(delegateTask.getExecution().getProcessInstance().getBusinessKey()));

        verify(taskAuditService, times(2)).audit(jsonEnvelopeArgument.capture(), eq(CHANGE_AUTHOR_ID));

        verify(sender, times(2)).sendAsAdmin(jsonEnvelopeArgumentCaptor.capture());

        final Metadata metadata = jsonEnvelopeArgumentCaptor.getValue().metadata();
        assertThat(metadata.name(), is("businessprocesses.command.record-task-deleted"));

        final JsonObject jsonObject = jsonEnvelopeArgumentCaptor.getValue().asJsonObject();

        assertThat(jsonObject.getString("id"), notNullValue());
        assertThat(jsonObject.getString("changeAuthorId"), is(CHANGE_AUTHOR_ID));
        assertThat(jsonObject.getString("changeAuthor"), is(CHANGE_AUTHOR));
        assertThat(jsonObject.getString("deletionReason"), is("Task Created In Error"));
        assertThat(getDate(jsonObject.getString("deletedDate")), is(getDate(COMPLETED_DATE)));
    }

    private String getDate(final String datetime) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
        return formatter.format(ZonedDateTime.parse(datetime));
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
        processVariables.put(COMPLETED_DATE_FIELD, COMPLETED_DATE);
        processVariables.put(TASK_TYPE_ID, TASK_TYPE_ID_VALUE);
        processVariables.put(IS_DELETABLE_FIELD, false);
        processVariables.put(IS_DEFERRABLE_FIELD, true);
        processVariables.put(HEARING_DATE, HEARING_DATE_VALUE);
        processInstance = runtimeService.startProcessInstanceByKey(processDefinitionKey, businessKey, processVariables);
    }

    @AfterEach
    public void tearDown() throws Exception {
        Mocks.reset();
    }
}