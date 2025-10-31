package uk.gov.moj.cpp.businessprocesses.bpmn;

import static java.util.UUID.randomUUID;
import static org.camunda.bpm.engine.test.mock.Mocks.register;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import uk.gov.justice.services.common.util.UtcClock;
import uk.gov.justice.services.core.dispatcher.SystemUserProvider;
import uk.gov.justice.services.core.sender.Sender;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.Metadata;
import uk.gov.moj.cpp.businessprocesses.listener.TasksAssignedListener;
import uk.gov.moj.cpp.businessprocesses.listener.TasksCompletedListener;
import uk.gov.moj.cpp.businessprocesses.listener.TasksCreatedListener;
import uk.gov.moj.cpp.businessprocesses.shared.TaskAuditService;

import java.time.ZonedDateTime;
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
public class HearingListedBPMNTest {

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

    private static final String HEARING_LISTED_BPMN_PROCESS_NAME = "hearing-listed";
    private static final String HEARING_LISTED_BPMN_FILE_NAME = "hearing-listed.bpmn";
    private static final String BOOK_INTERPRETERS_CASE_TASK_ID = "Book_interpreter_case";
    private static final String BOOK_INTERPRETERS_CASE_PREFIX = "Book_interpreter_case_";
    private static final String BOOK_INTERPRETERS_APPLICATION_TASK_ID = "Book_interpreter_application";
    private static final String BOOK_INTERPRETERS_APPLICATION_PREFIX = "Book_interpreter_application_";

    // Fields
    private static final String BOOK_INTERPRETER_FIELD = "hasInterpreter";
    private static final String HAS_CASE_ID_FIELD = "hasCaseId";

    private static final String CASE_ID_FIELD = "caseId";
    private static final String APPLICATION_ID_FIELD = "applicationId";
    private static final String DEFENDANT_ID_FIELD = "defendantId";
    private static final String DEFENDANT_NAME_FIELD = "defendantName";
    private static final String CASE_URN_FIELD = "caseURN";
    private static final String HEARING_ID_FIELD = "hearingId";
    private static final String HEARING_DATE_FIELD = "hearingDate";
    private static final String NOTE_FIELD = "note";
    private static final String DEEP_LINK_FIELD = "deepLink";
    private static final String WORK_QUEUE_FIELD = "workQueue";
    private static final String DUE_DATE_FIELD = "dueDate";
    private static final String IS_DELETABLE_FIELD = "isDeletable";
    private static final String IS_DEFERRABLE_FIELD = "isDeferrable";

    private static final String LAST_UPDATED_BY_ID = "lastUpdatedByID";
    private static final String LAST_UPDATED_BY_NAME = "lastUpdatedByName";
    private static final String JURISDICTION = "jurisdiction";
    private static final String TASK_TYPE_ID = "taskTypeId";

    // Data
    private static final String CASE_ID = randomUUID().toString();
    private static final String APPLICATION_ID = randomUUID().toString();
    private static final String DEFENDANT_ID = randomUUID().toString();
    private static final String DEFENDANT_NAME = "John Smith";
    private static final String CASE_URN = "TFL12345678";
    private static final String HEARING_ID = randomUUID().toString();
    private static final ZonedDateTime HEARING_DATE = new UtcClock().now();
    private static final String NOTES = "[ TFL34652354 = Abbie ARMSTRONG : Welsh,John Doe : French ][ TFL12346523 = John Doe : French ]";
    private static final String DUE_DATE = "2021-08-24T01:46:36.068Z";

    private static final String CASE_DEEP_LINK = "https://dummyUrl/prosecution-casefile/case-at-a-glance/".concat(CASE_ID);
    private static final String APPLICATION_DEEP_LINK = "https://dummyUrl/prosecution-casefile/application-at-a-glance/".concat(APPLICATION_ID);
    private static final String WORK_QUEUE = "89058c86-ddc3-441c-94bd-c21c6a1e5d80";
    private static final String SYSTEM_USER_ID = randomUUID().toString();
    private static final String CROWN_JURISDICTION_TYPE = "CROWN";
    private static final String SYSTEM_USER_NAME = "SYSTEM";
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
    @Deployment(resources = {HEARING_LISTED_BPMN_FILE_NAME})
    public void shouldStartListedHearingProcessForCaseIdWithBookInterpretersTask() {

        final HashMap<String, Object> processVariables = new HashMap<>();
        processVariables.put(BOOK_INTERPRETER_FIELD, true);
        processVariables.put(HAS_CASE_ID_FIELD, true);
        processVariables.put(BOOK_INTERPRETERS_CASE_PREFIX+TASK_NAME_FIELD, BOOK_INTERPRETERS_CASE_TASK_ID);
        processVariables.put(CASE_ID_FIELD, CASE_ID);
        processVariables.put(DEFENDANT_ID_FIELD, DEFENDANT_ID);
        processVariables.put(HEARING_ID_FIELD, HEARING_ID);
        processVariables.put(HEARING_DATE_FIELD, HEARING_DATE);
        processVariables.put(DEFENDANT_NAME_FIELD, DEFENDANT_NAME);
        processVariables.put(BOOK_INTERPRETERS_CASE_PREFIX+DEEP_LINK_FIELD, CASE_DEEP_LINK);
        processVariables.put(BOOK_INTERPRETERS_CASE_PREFIX+WORK_QUEUE_FIELD, WORK_QUEUE);
        processVariables.put(CASE_URN_FIELD, CASE_URN);
        processVariables.put(NOTE_FIELD, NOTES);
        processVariables.put(BOOK_INTERPRETERS_CASE_PREFIX+DUE_DATE_FIELD, DUE_DATE);
        processVariables.put(LAST_UPDATED_BY_ID, SYSTEM_USER_ID);
        processVariables.put(LAST_UPDATED_BY_NAME, SYSTEM_USER_NAME);
        processVariables.put(JURISDICTION, CROWN_JURISDICTION_TYPE);
        processVariables.put(BOOK_INTERPRETERS_CASE_PREFIX+TASK_TYPE_ID, TASK_TYPE_ID_VALUE);
        processVariables.put(BOOK_INTERPRETERS_CASE_PREFIX+IS_DELETABLE_FIELD, true);
        processVariables.put(BOOK_INTERPRETERS_CASE_PREFIX+IS_DEFERRABLE_FIELD, true);


        final ProcessInstance processInstance = runtimeService
                .startProcessInstanceByKey(HEARING_LISTED_BPMN_PROCESS_NAME, HEARING_ID, processVariables);

        assertThat(processInstance.isEnded(), is(false));
        assertThat(processInstance.isSuspended(), is(false));

        verify(sender, times(1)).sendAsAdmin(jsonEnvelopeArgumentCaptor.capture());

        final Metadata metadata = jsonEnvelopeArgumentCaptor.getValue().metadata();
        assertThat(metadata.name(), CoreMatchers.is("businessprocesses.command.record-task-created"));

        final List<Task> activeTaskList = taskService.createTaskQuery().processInstanceId(processInstance.getId()).active().list();
        assertThat(activeTaskList.size(), is(1));

        final Task actualCancelInterpretersTask = activeTaskList.get(0);
        assertThat(actualCancelInterpretersTask.getTaskDefinitionKey(), is(BOOK_INTERPRETERS_CASE_TASK_ID));

        final String actualCancelInterpretersTaskExecutionId = actualCancelInterpretersTask.getExecutionId();

        assertThat(runtimeService.getVariable(actualCancelInterpretersTaskExecutionId, BOOK_INTERPRETERS_CASE_PREFIX+TASK_NAME_FIELD), is(BOOK_INTERPRETERS_CASE_TASK_ID));
        assertThat(runtimeService.getVariable(actualCancelInterpretersTaskExecutionId, CASE_ID_FIELD), is(CASE_ID));
        assertThat(runtimeService.getVariable(actualCancelInterpretersTaskExecutionId, HEARING_ID_FIELD), is(HEARING_ID));
        assertThat(runtimeService.getVariable(actualCancelInterpretersTaskExecutionId, HEARING_DATE_FIELD), is(HEARING_DATE));
        assertThat(runtimeService.getVariable(actualCancelInterpretersTaskExecutionId, DEFENDANT_ID_FIELD), is(DEFENDANT_ID));
        assertThat(runtimeService.getVariable(actualCancelInterpretersTaskExecutionId, DEFENDANT_NAME_FIELD), is(DEFENDANT_NAME));
        assertThat(runtimeService.getVariable(actualCancelInterpretersTaskExecutionId, NOTE_FIELD), is(NOTES));
        assertThat(runtimeService.getVariable(actualCancelInterpretersTaskExecutionId, BOOK_INTERPRETERS_CASE_PREFIX+DEEP_LINK_FIELD), is(CASE_DEEP_LINK));
        assertThat(runtimeService.getVariable(actualCancelInterpretersTaskExecutionId, BOOK_INTERPRETERS_CASE_PREFIX+WORK_QUEUE_FIELD), is(WORK_QUEUE));
        assertThat(runtimeService.getVariable(actualCancelInterpretersTaskExecutionId, CASE_URN_FIELD), is(CASE_URN));
        assertThat(runtimeService.getVariable(actualCancelInterpretersTaskExecutionId, LAST_UPDATED_BY_ID), is(SYSTEM_USER_ID));
        assertThat(runtimeService.getVariable(actualCancelInterpretersTaskExecutionId, LAST_UPDATED_BY_NAME), is(SYSTEM_USER_NAME));
        assertThat(runtimeService.getVariable(actualCancelInterpretersTaskExecutionId, JURISDICTION), is(CROWN_JURISDICTION_TYPE));
        assertThat(runtimeService.getVariable(actualCancelInterpretersTaskExecutionId, BOOK_INTERPRETERS_CASE_PREFIX+TASK_TYPE_ID), is(TASK_TYPE_ID_VALUE));
        assertThat(runtimeService.getVariable(actualCancelInterpretersTaskExecutionId, BOOK_INTERPRETERS_CASE_PREFIX+IS_DELETABLE_FIELD), is(true));
        assertThat(runtimeService.getVariable(actualCancelInterpretersTaskExecutionId, BOOK_INTERPRETERS_CASE_PREFIX+IS_DEFERRABLE_FIELD), is(true));
    }

    @Test
    @Deployment(resources = {HEARING_LISTED_BPMN_FILE_NAME})
    public void shouldStartListedHearingProcessForApplicationWithBookInterpretersTask() {

        final HashMap<String, Object> processVariables = new HashMap<>();
        processVariables.put(BOOK_INTERPRETER_FIELD, true);
        processVariables.put(HAS_CASE_ID_FIELD, false);
        processVariables.put(BOOK_INTERPRETERS_APPLICATION_PREFIX+TASK_NAME_FIELD, BOOK_INTERPRETERS_APPLICATION_TASK_ID);
        processVariables.put(APPLICATION_ID_FIELD, APPLICATION_ID);
        processVariables.put(DEFENDANT_ID_FIELD, DEFENDANT_ID);
        processVariables.put(HEARING_ID_FIELD, HEARING_ID);
        processVariables.put(HEARING_DATE_FIELD, HEARING_DATE);
        processVariables.put(DEFENDANT_NAME_FIELD, DEFENDANT_NAME);
        processVariables.put(BOOK_INTERPRETERS_APPLICATION_PREFIX+DEEP_LINK_FIELD, APPLICATION_DEEP_LINK);
        processVariables.put(NOTE_FIELD, NOTES);
        processVariables.put(BOOK_INTERPRETERS_APPLICATION_PREFIX+WORK_QUEUE_FIELD, WORK_QUEUE);
        processVariables.put(BOOK_INTERPRETERS_APPLICATION_PREFIX+DUE_DATE_FIELD, DUE_DATE);
        processVariables.put(LAST_UPDATED_BY_ID, SYSTEM_USER_ID);
        processVariables.put(LAST_UPDATED_BY_NAME, SYSTEM_USER_NAME);
        processVariables.put(JURISDICTION, CROWN_JURISDICTION_TYPE);
        processVariables.put(BOOK_INTERPRETERS_APPLICATION_PREFIX+TASK_TYPE_ID, TASK_TYPE_ID_VALUE);
        processVariables.put(CASE_URN_FIELD, CASE_URN);
        processVariables.put(BOOK_INTERPRETERS_APPLICATION_PREFIX+IS_DELETABLE_FIELD, true);
        processVariables.put(BOOK_INTERPRETERS_APPLICATION_PREFIX+IS_DEFERRABLE_FIELD, true);

        final ProcessInstance processInstance = runtimeService
                .startProcessInstanceByKey(HEARING_LISTED_BPMN_PROCESS_NAME, HEARING_ID, processVariables);

        assertThat(processInstance.isEnded(), is(false));
        assertThat(processInstance.isSuspended(), is(false));

        final List<Task> activeTaskList = taskService.createTaskQuery().processInstanceId(processInstance.getId()).active().list();
        assertThat(activeTaskList.size(), is(1));

        final Task actualCancelInterpretersTask = activeTaskList.get(0);
        assertThat(actualCancelInterpretersTask.getTaskDefinitionKey(), is(BOOK_INTERPRETERS_APPLICATION_TASK_ID));

        verify(sender, times(1)).sendAsAdmin(jsonEnvelopeArgumentCaptor.capture());

        final Metadata metadata = jsonEnvelopeArgumentCaptor.getValue().metadata();
        assertThat(metadata.name(), CoreMatchers.is("businessprocesses.command.record-task-created"));

        final String actualCancelInterpretersTaskExecutionId = actualCancelInterpretersTask.getExecutionId();

        assertThat(runtimeService.getVariable(actualCancelInterpretersTaskExecutionId, BOOK_INTERPRETERS_APPLICATION_PREFIX+TASK_NAME_FIELD), is(BOOK_INTERPRETERS_APPLICATION_TASK_ID));
        assertThat(runtimeService.getVariable(actualCancelInterpretersTaskExecutionId, APPLICATION_ID_FIELD), is(APPLICATION_ID));
        assertThat(runtimeService.getVariable(actualCancelInterpretersTaskExecutionId, HEARING_ID_FIELD), is(HEARING_ID));
        assertThat(runtimeService.getVariable(actualCancelInterpretersTaskExecutionId, HEARING_DATE_FIELD), is(HEARING_DATE));
        assertThat(runtimeService.getVariable(actualCancelInterpretersTaskExecutionId, DEFENDANT_ID_FIELD), is(DEFENDANT_ID));
        assertThat(runtimeService.getVariable(actualCancelInterpretersTaskExecutionId, DEFENDANT_NAME_FIELD), is(DEFENDANT_NAME));
        assertThat(runtimeService.getVariable(actualCancelInterpretersTaskExecutionId, NOTE_FIELD), is(NOTES));
        assertThat(runtimeService.getVariable(actualCancelInterpretersTaskExecutionId, BOOK_INTERPRETERS_APPLICATION_PREFIX+DEEP_LINK_FIELD), is(APPLICATION_DEEP_LINK));
        assertThat(runtimeService.getVariable(actualCancelInterpretersTaskExecutionId, BOOK_INTERPRETERS_APPLICATION_PREFIX+WORK_QUEUE_FIELD), is(WORK_QUEUE));
        assertThat(runtimeService.getVariable(actualCancelInterpretersTaskExecutionId, LAST_UPDATED_BY_ID), is(SYSTEM_USER_ID));
        assertThat(runtimeService.getVariable(actualCancelInterpretersTaskExecutionId, LAST_UPDATED_BY_NAME), is(SYSTEM_USER_NAME));
        assertThat(runtimeService.getVariable(actualCancelInterpretersTaskExecutionId, JURISDICTION), is(CROWN_JURISDICTION_TYPE));
        assertThat(runtimeService.getVariable(actualCancelInterpretersTaskExecutionId, BOOK_INTERPRETERS_APPLICATION_PREFIX+TASK_TYPE_ID), is(TASK_TYPE_ID_VALUE));
        assertThat(runtimeService.getVariable(actualCancelInterpretersTaskExecutionId, BOOK_INTERPRETERS_APPLICATION_PREFIX+IS_DELETABLE_FIELD), is(true));
        assertThat(runtimeService.getVariable(actualCancelInterpretersTaskExecutionId, BOOK_INTERPRETERS_APPLICATION_PREFIX+IS_DEFERRABLE_FIELD), is(true));
    }

    @Test
    @Deployment(resources = {HEARING_LISTED_BPMN_FILE_NAME})
    public void shouldStartAndStopListedHearingProcessWhenNoTasksRequired() {

        final HashMap<String, Object> processVariables = new HashMap<>();
        processVariables.put(BOOK_INTERPRETER_FIELD, false);
        processVariables.put(HAS_CASE_ID_FIELD, true);

        processVariables.put(CASE_ID_FIELD, CASE_ID);
        processVariables.put(DEFENDANT_ID_FIELD, DEFENDANT_ID);
        processVariables.put(HEARING_ID_FIELD, HEARING_ID);
        processVariables.put(HEARING_DATE_FIELD, HEARING_DATE);
        processVariables.put(DEFENDANT_NAME_FIELD, DEFENDANT_NAME);
        processVariables.put(DEEP_LINK_FIELD, CASE_DEEP_LINK);
        processVariables.put(WORK_QUEUE_FIELD, WORK_QUEUE);
        processVariables.put(CASE_URN_FIELD, CASE_URN);
        processVariables.put(NOTE_FIELD, NOTES);
        processVariables.put(DUE_DATE_FIELD, DUE_DATE);
        processVariables.put(LAST_UPDATED_BY_ID, SYSTEM_USER_ID);
        processVariables.put(LAST_UPDATED_BY_NAME, SYSTEM_USER_NAME);
        processVariables.put(JURISDICTION, CROWN_JURISDICTION_TYPE);
        processVariables.put(TASK_TYPE_ID, TASK_TYPE_ID_VALUE);

        final ProcessInstance processInstance = runtimeService
                .startProcessInstanceByKey(HEARING_LISTED_BPMN_PROCESS_NAME, HEARING_ID, processVariables);

        verify(sender, never()).sendAsAdmin(jsonEnvelopeArgumentCaptor.capture());

        assertThat(processInstance.isEnded(), is(true));

        final List<Task> activeTaskList = taskService.createTaskQuery().processInstanceId(processInstance.getId()).active().list();
        assertThat(activeTaskList.size(), is(0));
    }

    @AfterEach
    public void tearDown() throws Exception {
        Mocks.reset();
    }

}