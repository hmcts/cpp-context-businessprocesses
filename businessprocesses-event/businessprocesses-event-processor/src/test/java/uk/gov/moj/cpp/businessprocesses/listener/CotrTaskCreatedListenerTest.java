package uk.gov.moj.cpp.businessprocesses.listener;

import static java.util.UUID.randomUUID;
import static org.camunda.bpm.engine.test.mock.Mocks.register;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
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
import uk.gov.moj.cpp.businessprocesses.pojo.WorkflowCOTRTaskType;
import uk.gov.moj.cpp.businessprocesses.service.CourtCentreService;
import uk.gov.moj.cpp.businessprocesses.shared.TaskAuditService;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@SuppressWarnings({"squid:S2187"})
@ExtendWith({MockitoExtension.class, ProcessEngineExtension.class})
public class CotrTaskCreatedListenerTest {

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
    protected CotrTaskCreatedListener cotrTaskCreatedListener;

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

    private static final String COTR_CUSTOM_WORK_FLOW = "cotr_custom_task_process";
    private static final String COTR_CUSTOM_TASK_PROCESS_BPMN = "cotr_custom_task_process.bpmn";
    private static final String COTR_TASK_VARIABLE_JSON_STRING = "cotrTaskVariablesJsonString";
    private static final String WORK_QUEUE = randomUUID().toString();
    private static final String DEEP_LINK = "https://dummyUrl/prosecution-casefile/case-at-a-glance/";
    private static final String DUE_DATE = "2022-08-24T01:46:36.068Z";
    public static final String DEEP_LINK_LABEL = "deepLink";
    public static final String WORK_QUEUE_LABEL = "workQueue";
    public static final String CANDIDATE_GROUPS_LABEL = "candidateGroups";
    public static final String TASK_NAME = "COTR custom user activity";
    public static final String TASK_NAME_LABEL = "taskName";
    public static final String DUE_DATE_LABEL = "dueDate";
    private static final String TASK_TYPE_ID_VALUE = randomUUID().toString();
    private static final String CHANGE_AUTHOR_ID = randomUUID().toString();
    private static final String CHANGE_AUTHOR = "John Smith";
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
        register("cotrTaskCreatedListener", cotrTaskCreatedListener);
        setField(cotrTaskCreatedListener, "tService", taskServiceImpl);
    }

    @Test
    @Deployment(resources = {COTR_CUSTOM_TASK_PROCESS_BPMN})
    public void shouldRaisePublicEventAfterCreation() {
        final LocalDateTime localDateTime = LocalDateTime.now();
        WorkflowCOTRTaskType workflowCOTRTaskType = WorkflowCOTRTaskType.WorkflowCOTRTaskTypeBuilder.aWorkflowCOTRTaskType()
                .withId(UUID.randomUUID())
                .withCotrId(UUID.randomUUID().toString())
                .withTaskName("TaskName")
                .withDisplayName("TaskName")
                .withCourtCodes("OU")
                .withComment("Comments")
                .withTaskCreationDate(LocalDate.now())
                .withDue(localDateTime)
                .withHearingDate(LocalDate.now())
                .withCandidateGroups("Listing Officer,Operational Delivery Admin (Crown),CTSC admin (Mags)") //
                .build();
        processInstance = runtimeService.createProcessInstanceByKey(COTR_CUSTOM_WORK_FLOW)
                .businessKey(COTR_CUSTOM_WORK_FLOW)
                .setVariable(LAST_UPDATED_BY_ID, CHANGE_AUTHOR_ID)
                .setVariable(LAST_UPDATED_BY_NAME, CHANGE_AUTHOR)
                .setVariable(TASK_TYPE_ID, TASK_TYPE_ID_VALUE)
                .setVariable(DEEP_LINK_LABEL, DEEP_LINK)
                .setVariable(WORK_QUEUE_LABEL, WORK_QUEUE)
                .setVariable(CANDIDATE_GROUPS_LABEL, "")
                .setVariable(TASK_NAME_LABEL, TASK_NAME)
                .setVariable(DUE_DATE_LABEL, DUE_DATE)
                .setVariable(COTR_TASK_VARIABLE_JSON_STRING, objectToJsonObjectConverter.convert(workflowCOTRTaskType).toString())
                .execute();
        final Task activeTask = taskService.createTaskQuery().processInstanceId(processInstance.getId()).active().list().get(0);
        assertThat("cotr_custom_activity", is(activeTask.getTaskDefinitionKey()));
        verify(cotrTaskCreatedListener).notify(delegateTaskArgumentCaptor.capture());

        final DelegateTask delegateTask = delegateTaskArgumentCaptor.getValue();
        assertThat(COTR_CUSTOM_WORK_FLOW, is(delegateTask.getExecution().getProcessInstance().getBusinessKey()));
        assertThat(delegateTask.getCreateTime().toString(), notNullValue());
        assertThat(delegateTask.getDueDate(), notNullValue());

        assertThat(taskServiceImpl.getTaskComments(activeTask.getId()).size(), is(1));

        verify(sender, times(1)).sendAsAdmin(jsonEnvelopeArgumentCaptor.capture());

        final JsonEnvelope jsonEnvelope = jsonEnvelopeArgumentCaptor.getValue();
        assertThat(jsonEnvelope.metadata().name() , is("businessprocesses.command.record-task-created"));
    }

    @AfterEach
    public void tearDown() throws Exception {
        Mocks.reset();
    }
}