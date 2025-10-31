package uk.gov.moj.cpp.businessprocesses.bpmn;

import static java.util.UUID.randomUUID;
import static org.camunda.bpm.engine.test.mock.Mocks.register;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import uk.gov.justice.services.core.dispatcher.SystemUserProvider;
import uk.gov.justice.services.core.sender.Sender;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.Metadata;
import uk.gov.moj.cpp.businessprocesses.listener.TasksAssignedListener;
import uk.gov.moj.cpp.businessprocesses.listener.TasksCompletedListener;
import uk.gov.moj.cpp.businessprocesses.listener.TasksCreatedListener;
import uk.gov.moj.cpp.businessprocesses.service.CourtCentreService;
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
public class SPIErrorIdentififedBPMNTest {

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

    @Mock
    protected SystemUserProvider systemUserProvider;

    @Mock
    protected TaskAuditService taskAuditService;

    @Captor
    protected ArgumentCaptor<JsonEnvelope> jsonEnvelopeArgumentCaptor;

    private static final String SPI_ERROR_IDENTIFIED_BPMN_PROCESS_NAME = "spi_error_identified";
    private static final String SPI_ERROR_IDENTIFIED_BPMN_FILE_NAME = "spi_error_identified.bpmn";

    private static final String CASE_ID_FIELD = "caseId";
    private static final String ERROR_CODE_FIELD = "errorCode";
    private static final String DEEP_LINK_FIELD = "deepLink";
    private static final String WORK_QUEUE_FIELD = "workQueue";

    private static final String LAST_UPDATED_BY_ID = "lastUpdatedByID";
    private static final String LAST_UPDATED_BY_NAME = "lastUpdatedByName";
    private static final String TASK_TYPE_ID = "taskTypeId";
    private static final String DUE_DATE_FIELD = "dueDate";
    private static final String DUE_DATE = "2022-08-24T01:46:36.068Z";

    // Data
    private static final String CASE_ID = randomUUID().toString();
    private static final String ERROR_CODE = "testErrorCode";
    private static final String CASE_URN_FIELD = "caseURN";

    private static final String CASE_DEEP_LINK = "https://dummyUrl/prosecution-casefile/case-at-a-glance/".concat(CASE_ID);
    private static final String WORK_QUEUE = "89058c86-ddc3-441c-94bd-c21c6a1e5d80";
    private static final String SYSTEM_USER_ID = randomUUID().toString();
    private static final String SYSTEM_USER_NAME = "SYSTEM";
    private static final String CASE_URN = "TFL12345678";

    private static final String COURT_CODES_VAL = "B01LY00";
    private static final String TASK_TYPE_ID_VALUE = randomUUID().toString();
    public static final String NOTE_FIELD = "note";
    public static final String NOTE = "notenotenote";
    public static final String TASK_NAME_FIELD = "taskName";
    public static final String TASK_NAME = "SPI_error";
    public static final String IS_DELETABLE = "isDeletable";
    public static final String IS_DEFERRABLE = "isDeferrable";
    public static final String COURT_CODES = "courtCodes";

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
    @Deployment(resources = {SPI_ERROR_IDENTIFIED_BPMN_FILE_NAME})
    public void shouldStartSPIErrorProcess() {

        final HashMap<String, Object> processVariables = new HashMap<>();

        processVariables.put(CASE_ID_FIELD, CASE_ID);
        processVariables.put(ERROR_CODE_FIELD, ERROR_CODE);
        processVariables.put(DEEP_LINK_FIELD, CASE_DEEP_LINK);
        processVariables.put(DUE_DATE_FIELD, DUE_DATE);
        processVariables.put(WORK_QUEUE_FIELD, WORK_QUEUE);
        processVariables.put(LAST_UPDATED_BY_ID, SYSTEM_USER_ID);
        processVariables.put(LAST_UPDATED_BY_NAME, SYSTEM_USER_NAME);
        processVariables.put(TASK_TYPE_ID, TASK_TYPE_ID_VALUE);
        processVariables.put(CASE_URN_FIELD, CASE_URN);
        processVariables.put(NOTE_FIELD, NOTE);
        processVariables.put(TASK_NAME_FIELD, TASK_NAME);
        processVariables.put(IS_DELETABLE, true);
        processVariables.put(IS_DEFERRABLE, true);
        processVariables.put(COURT_CODES, "B01LY00");

        final ProcessInstance processInstance = runtimeService
                .startProcessInstanceByKey(SPI_ERROR_IDENTIFIED_BPMN_PROCESS_NAME, CASE_ID, processVariables);

        assertThat(processInstance.isEnded(), is(false));
        assertThat(processInstance.isSuspended(), is(false));

        verify(sender, times(1)).sendAsAdmin(jsonEnvelopeArgumentCaptor.capture());

        final Metadata metadata = jsonEnvelopeArgumentCaptor.getValue().metadata();
        assertThat(metadata.name(), CoreMatchers.is("businessprocesses.command.record-task-created"));

        final List<Task> activeTaskList = taskService.createTaskQuery().processInstanceId(processInstance.getId()).active().list();
        assertThat(activeTaskList.size(), is(1));

        final Task spiErrorTask = activeTaskList.get(0);
        assertThat(spiErrorTask.getTaskDefinitionKey(), is(TASK_NAME));

        final String spiErrorTaskExecutionId = spiErrorTask.getExecutionId();

        assertThat(runtimeService.getVariable(spiErrorTaskExecutionId, CASE_ID_FIELD), is(CASE_ID));
        assertThat(runtimeService.getVariable(spiErrorTaskExecutionId, DEEP_LINK_FIELD), is(CASE_DEEP_LINK));
        assertThat(runtimeService.getVariable(spiErrorTaskExecutionId, DUE_DATE_FIELD), is(DUE_DATE));
        assertThat(runtimeService.getVariable(spiErrorTaskExecutionId, ERROR_CODE_FIELD), is(ERROR_CODE));
        assertThat(runtimeService.getVariable(spiErrorTaskExecutionId, LAST_UPDATED_BY_ID), is(SYSTEM_USER_ID));
        assertThat(runtimeService.getVariable(spiErrorTaskExecutionId, LAST_UPDATED_BY_NAME), is(SYSTEM_USER_NAME));
        assertThat(runtimeService.getVariable(spiErrorTaskExecutionId, TASK_TYPE_ID), is(TASK_TYPE_ID_VALUE));
        assertThat(runtimeService.getVariable(spiErrorTaskExecutionId, IS_DELETABLE), is(true));
        assertThat(runtimeService.getVariable(spiErrorTaskExecutionId, IS_DEFERRABLE), is(true));
        assertThat(runtimeService.getVariable(spiErrorTaskExecutionId, COURT_CODES), is(COURT_CODES_VAL));
    }

    @AfterEach
    public void tearDown() throws Exception {
        Mocks.reset();
    }

}