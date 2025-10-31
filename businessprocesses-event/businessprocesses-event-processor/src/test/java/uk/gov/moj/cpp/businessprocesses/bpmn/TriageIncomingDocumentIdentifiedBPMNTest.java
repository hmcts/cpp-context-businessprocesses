package uk.gov.moj.cpp.businessprocesses.bpmn;

import static java.util.UUID.randomUUID;
import static org.camunda.bpm.engine.test.mock.Mocks.register;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import uk.gov.justice.services.core.dispatcher.SystemUserProvider;
import uk.gov.justice.services.core.sender.Sender;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.moj.cpp.businessprocesses.listener.TasksAssignedListener;
import uk.gov.moj.cpp.businessprocesses.listener.TasksCompletedListener;
import uk.gov.moj.cpp.businessprocesses.listener.TasksCreatedListener;
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

@ExtendWith({MockitoExtension.class, ProcessEngineExtension.class})
public class TriageIncomingDocumentIdentifiedBPMNTest {

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

    private static final String TRIAGE_INCOMING_DOCUMENT_IDENTIFIED_BPMN_PROCESS_NAME = "triage_incoming_document_identified";
    private static final String TRIAGE_INCOMING_DOCUMENT_IDENTIFIED_BPMN_FILE_NAME = "triage_incoming_document_identified.bpmn";
    private static final String TRIAGE_INCOMING_DOCUMENT_TASK_ID = "Triage_incoming_document";

    private static final String CASE_ID_FIELD = "caseId";
    private static final String DEFENDANT_ID_FIELD = "defendantId";
    private static final String DEFENDANT_NAME_FIELD = "defendantName";
    private static final String CASE_URN_FIELD = "caseURN";
    private static final String DEEP_LINK_FIELD = "deepLink";
    private static final String WORK_QUEUE_FIELD = "workQueue";
    private static final String DUE_DATE_FIELD = "dueDate";
    private static final String LAST_UPDATED_BY_ID = "lastUpdatedByID";
    private static final String LAST_UPDATED_BY_NAME = "lastUpdatedByName";
    private static final String TASK_TYPE_ID = "taskTypeId";

    // Data
    private static final String CASE_ID = randomUUID().toString();
    private static final String DEFENDANT_ID = randomUUID().toString();
    private static final String DEFENDANT_NAME = "John Smith";
    private static final String CASE_URN = "TFL12345678";
    private static final String DUE_DATE = "2021-08-24T01:46:36.068Z";
    private static final String CASE_DEEP_LINK = "https://dummyUrl/prosecution-casefile/case-at-a-glance/".concat(CASE_ID);
    private static final String WORK_QUEUE = "89058c86-ddc3-441c-94bd-c21c6a1e5d80";
    private static final String SYSTEM_USER_ID = randomUUID().toString();
    private static final String SYSTEM_USER_NAME = "SYSTEM";
    private static final String TASK_TYPE_ID_VALUE = randomUUID().toString();
    public static final String TASK_NAME = "taskName";
    public static final String IS_DELETABLE = "isDeletable";
    public static final String IS_DEFERRABLE = "isDeferrable";

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
    @Deployment(resources = {TRIAGE_INCOMING_DOCUMENT_IDENTIFIED_BPMN_FILE_NAME})
    public void shouldStartListedTriageIncomingDocumentIdentifiedForCaseId() {

        final HashMap<String, Object> processVariables = new HashMap<>();

        processVariables.put(CASE_ID_FIELD, CASE_ID);
        processVariables.put(DEFENDANT_ID_FIELD, DEFENDANT_ID);
        processVariables.put(DEFENDANT_NAME_FIELD, DEFENDANT_NAME);
        processVariables.put(DEEP_LINK_FIELD, CASE_DEEP_LINK);
        processVariables.put(WORK_QUEUE_FIELD, WORK_QUEUE);
        processVariables.put(CASE_URN_FIELD, CASE_URN);
        processVariables.put(DUE_DATE_FIELD, DUE_DATE);
        processVariables.put(LAST_UPDATED_BY_ID, SYSTEM_USER_ID);
        processVariables.put(LAST_UPDATED_BY_NAME, SYSTEM_USER_NAME);
        processVariables.put(TASK_TYPE_ID, TASK_TYPE_ID_VALUE);
        processVariables.put(TASK_NAME, TRIAGE_INCOMING_DOCUMENT_TASK_ID);
        processVariables.put(IS_DELETABLE, true);
        processVariables.put(IS_DEFERRABLE, true);

        final ProcessInstance processInstance = runtimeService
                .startProcessInstanceByKey(TRIAGE_INCOMING_DOCUMENT_IDENTIFIED_BPMN_PROCESS_NAME, CASE_ID, processVariables);

        assertThat(processInstance.isEnded(), is(false));
        assertThat(processInstance.isSuspended(), is(false));

        final List<Task> activeTaskList = taskService.createTaskQuery().processInstanceId(processInstance.getId()).active().list();
        assertThat(activeTaskList.size(), is(1));

        final Task task = activeTaskList.get(0);

        final String actualTaskExecutionId = task.getExecutionId();
        assertThat(task.getTaskDefinitionKey(), is(TRIAGE_INCOMING_DOCUMENT_TASK_ID));

        assertThat(runtimeService.getVariable(actualTaskExecutionId, CASE_ID_FIELD), is(CASE_ID));
        assertThat(runtimeService.getVariable(actualTaskExecutionId, DEFENDANT_ID_FIELD), is(DEFENDANT_ID));
        assertThat(runtimeService.getVariable(actualTaskExecutionId, DEFENDANT_NAME_FIELD), is(DEFENDANT_NAME));
        assertThat(runtimeService.getVariable(actualTaskExecutionId, DEEP_LINK_FIELD), is(CASE_DEEP_LINK));
        assertThat(runtimeService.getVariable(actualTaskExecutionId, WORK_QUEUE_FIELD), is(WORK_QUEUE));
        assertThat(runtimeService.getVariable(actualTaskExecutionId, CASE_URN_FIELD), is(CASE_URN));
        assertThat(runtimeService.getVariable(actualTaskExecutionId, LAST_UPDATED_BY_ID), is(SYSTEM_USER_ID));
        assertThat(runtimeService.getVariable(actualTaskExecutionId, LAST_UPDATED_BY_NAME), is(SYSTEM_USER_NAME));
        assertThat(runtimeService.getVariable(actualTaskExecutionId, TASK_TYPE_ID), is(TASK_TYPE_ID_VALUE));
        assertThat(runtimeService.getVariable(actualTaskExecutionId, IS_DELETABLE), is(true));
        assertThat(runtimeService.getVariable(actualTaskExecutionId, IS_DEFERRABLE), is(true));
    }

    @AfterEach
    public void tearDown() throws Exception {
        Mocks.reset();
    }

}