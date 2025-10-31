package uk.gov.moj.cpp.businessprocesses.listener;

import static java.lang.Boolean.TRUE;
import static java.util.UUID.randomUUID;
import static org.camunda.bpm.engine.test.mock.Mocks.register;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static uk.gov.justice.services.test.utils.core.reflection.ReflectionUtil.setField;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.LAST_UPDATED_BY_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.LAST_UPDATED_BY_NAME;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.TASK_TYPE_ID;

import uk.gov.justice.services.common.converter.ObjectToJsonObjectConverter;
import uk.gov.justice.services.common.converter.jackson.ObjectMapperProducer;
import uk.gov.justice.services.core.dispatcher.SystemUserProvider;
import uk.gov.justice.services.core.sender.Sender;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.moj.cpp.businessprocesses.pojo.WorkflowOpaTaskType;
import uk.gov.moj.cpp.businessprocesses.service.CourtCentreService;
import uk.gov.moj.cpp.businessprocesses.shared.TaskAuditService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.delegate.DelegateTask;
import org.camunda.bpm.engine.impl.TaskServiceImpl;
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
public class OpaTaskCompletedListenerTest {

    @RegisterExtension
    ProcessEngineExtension extension = ProcessEngineExtension.builder()
            .configurationResource("camunda.cfg.xml")
            .build();

    @Mock
    protected RuntimeService runtimeService;

    @Mock
    protected TaskService taskService;

    @Mock
    protected CourtCentreService courtCentreService;

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

    @Spy
    @InjectMocks
    protected OpaTaskCreatedListener opaTaskCreatedListener;

    @Mock
    protected TasksUpdatedListener tasksUpdatedListener;

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

    protected TaskServiceImpl taskServiceImpl;

    protected ProcessInstance processInstance;

    private static final String TASK_TYPE_ID_VALUE = randomUUID().toString();
    private static final String CHANGE_AUTHOR_ID = randomUUID().toString();
    private static final String CHANGE_AUTHOR = "John Smith";
    private static final String WORK_QUEUE = randomUUID().toString();
    private static final String DEEP_LINK = "https://dummyUrl/prosecution-casefile/case-at-a-glance/";
    private static final String DUE_DATE = "2022-08-24T01:46:36.068Z";
    private static final String OPA_CUSTOM_WORK_FLOW = "opa_custom_task_process";
    private static final String OPA_CUSTOM_TASK_PROCESS_BPMN = "opa_custom_task_process.bpmn";
    private static final String OPA_TASK_VARIABLE_JSON_STRING = "opaTaskVariablesJsonString";
    public static final String DEEP_LINK_LABEL = "deepLink";
    public static final String WORK_QUEUE_LABEL = "workQueue";
    public static final String CANDIDATE_GROUPS_LABEL = "candidateGroups";
    public static final String TASK_NAME = "OPA custom user activity";
    public static final String TASK_NAME_LABEL = "taskName";
    public static final String DUE_DATE_LABEL = "dueDate";
    private final ObjectMapper objectMapper = new ObjectMapperProducer().objectMapper();
    private final ObjectToJsonObjectConverter objectToJsonObjectConverter = new ObjectToJsonObjectConverter(objectMapper);

    @BeforeEach
    public void setUp() throws Exception {
        runtimeService = extension.getProcessEngine().getRuntimeService();
        taskService = extension.getProcessEngine().getTaskService();
        taskServiceImpl = (TaskServiceImpl) taskService;
        register("tasksCreatedListener", tasksCreatedListener);
        register("tasksAssignedListener", tasksAssignedListener);
        register("tasksCompletedListener", tasksCompletedListener);
        register("tasksUpdatedListener", tasksUpdatedListener);
        register("opaTaskCreatedListener", opaTaskCreatedListener);
        setField(opaTaskCreatedListener, "tService", taskServiceImpl);
    }

    private static WorkflowOpaTaskType buildWorkflowOpaTaskType() {
        return WorkflowOpaTaskType.WorkflowOpaTaskTypeBuilder.aWorkflowOpaTaskType()
                .withId(UUID.randomUUID())
                .withOpaId(UUID.randomUUID().toString())
                .withTaskName("TaskName")
                .withDisplayName("TaskName")
                .withCourtCodes("OU")
                .withComment("Comments")
                .withTaskCreationDate(LocalDate.now())
                .withDue(LocalDateTime.now())
                .withHearingDate(LocalDate.now())
                .withCandidateGroups("Listing Officer,Operational Delivery Admin (Crown),CTSC admin (Mags)") //
                .build();
    }

    @Test
    @Deployment(resources = {OPA_CUSTOM_TASK_PROCESS_BPMN})
    public void shouldRaiseTaskDeletedPublicEventAfterCompletionWhenDeletionReasonPresent() {
        processInstance = runtimeService.createProcessInstanceByKey(OPA_CUSTOM_WORK_FLOW)
                .businessKey(OPA_CUSTOM_WORK_FLOW)
                .setVariable(LAST_UPDATED_BY_ID, CHANGE_AUTHOR_ID)
                .setVariable(LAST_UPDATED_BY_NAME, CHANGE_AUTHOR)
                .setVariable(TASK_TYPE_ID, TASK_TYPE_ID_VALUE)
                .setVariable(DEEP_LINK_LABEL, DEEP_LINK)
                .setVariable(WORK_QUEUE_LABEL, WORK_QUEUE)
                .setVariable(CANDIDATE_GROUPS_LABEL, "")
                .setVariable(TASK_NAME_LABEL, TASK_NAME)
                .setVariable(DUE_DATE_LABEL, DUE_DATE)
                .setVariable(OPA_TASK_VARIABLE_JSON_STRING, objectToJsonObjectConverter.convert(buildWorkflowOpaTaskType()).toString())
                .execute();
        doAssertion(true);
    }

    @Test
    @Deployment(resources = {OPA_CUSTOM_TASK_PROCESS_BPMN})
    public void shouldRaiseTaskCompletedPublicEventAfterCompletionWhenDeletionReasonNotPresent() {
        processInstance = runtimeService.createProcessInstanceByKey(OPA_CUSTOM_WORK_FLOW)
                .businessKey(OPA_CUSTOM_WORK_FLOW)
                .setVariable(LAST_UPDATED_BY_ID, CHANGE_AUTHOR_ID)
                .setVariable(LAST_UPDATED_BY_NAME, CHANGE_AUTHOR)
                .setVariable(TASK_TYPE_ID, TASK_TYPE_ID_VALUE)
                .setVariable(DEEP_LINK_LABEL, DEEP_LINK)
                .setVariable(WORK_QUEUE_LABEL, WORK_QUEUE)
                .setVariable(CANDIDATE_GROUPS_LABEL, "")
                .setVariable(TASK_NAME_LABEL, TASK_NAME)
                .setVariable(DUE_DATE_LABEL, DUE_DATE)
                .setVariable(OPA_TASK_VARIABLE_JSON_STRING, objectToJsonObjectConverter.convert(buildWorkflowOpaTaskType()).toString())
                .execute();
        doAssertion(false);
    }

    private void doAssertion(boolean isDeleted) {
        final Optional<Task> optionalTask = taskService.createTaskQuery().processInstanceId(processInstance.getId()).active().list().stream().filter(task -> task.getTaskDefinitionKey().equals("opa_custom_activity")).findFirst();
        assertThat(optionalTask.isPresent(), is(TRUE));

        final Task activeTask = optionalTask.get();
        final String taskId = activeTask.getId();

        if (isDeleted) {
            taskService.setVariableLocal(taskId, "deletionReason", "random value");
        }
        taskService.setVariableLocal(taskId, LAST_UPDATED_BY_NAME, CHANGE_AUTHOR);
        taskService.setVariableLocal(taskId, LAST_UPDATED_BY_ID, CHANGE_AUTHOR_ID);

        taskService.complete(taskId);
        verify(tasksCompletedListener).notify(delegateTaskArgumentCaptor.capture());
        final DelegateTask delegateTask = delegateTaskArgumentCaptor.getValue();
        assertThat("opa_custom_activity", is(delegateTask.getTaskDefinitionKey()));
        assertThat(OPA_CUSTOM_WORK_FLOW, is(delegateTask.getExecution().getProcessInstance().getBusinessKey()));

        // audit service and sender service invoked 1 times for task create
        verify(taskAuditService, times(1)).audit(jsonEnvelopeArgument.capture(), eq(CHANGE_AUTHOR_ID));
        verify(sender, times(2)).sendAsAdmin(jsonEnvelopeArgumentCaptor.capture());

        final List<JsonEnvelope> jsonEnvelopeList = jsonEnvelopeArgumentCaptor.getAllValues();
        assertThat(jsonEnvelopeList.get(0).metadata().name() , is("businessprocesses.command.record-task-created"));
        assertThat(jsonEnvelopeList.get(1).metadata().name(), is(isDeleted ? "businessprocesses.command.record-task-deleted" : "businessprocesses.command.record-task-completed"));
    }

    @AfterEach
    public void tearDown() throws Exception {
        Mocks.reset();
    }
}