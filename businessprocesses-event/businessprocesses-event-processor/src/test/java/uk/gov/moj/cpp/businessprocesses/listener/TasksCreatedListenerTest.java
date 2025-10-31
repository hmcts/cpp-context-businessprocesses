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
import static org.mockito.Mockito.when;

import uk.gov.justice.services.core.dispatcher.SystemUserProvider;
import uk.gov.justice.services.core.sender.Sender;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.Metadata;
import uk.gov.moj.cpp.businessprocesses.service.CourtCentreService;
import uk.gov.moj.cpp.businessprocesses.shared.TaskAuditService;

import java.io.IOException;
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
public class TasksCreatedListenerTest {

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
    private static final String TRIAGE_INCOMING_DOCUMENT_IDENTIFIED = "triage_incoming_document_identified";
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
    private static final String COURT_ID_FIELD = "courtId";
    private static final String HEARING_DATE_FIELD = "hearingDate";
    private static final String JURISDICTION_FIELD = "jurisdiction";
    private static final String TASK_TYPE_ID = "taskTypeId";
    private static final String IS_DELETABLE_FIELD = "isDeletable";
    private static final String IS_DEFERRABLE_FIELD = "isDeferrable";
    private static final String COURT_CODES_FIELD = "courtCodes";

    private static final String CASE_ID = randomUUID().toString();
    private static final String TASK_TYPE_ID_VALUE = randomUUID().toString();
    private static final String DEFENDANT_ID = randomUUID().toString();
    private static final String DEFENDANT_NAME = "Roy Robbie";
    private static final String DEEP_LINK = "https://dummyUrl/prosecution-casefile/case-at-a-glance/".concat(CASE_ID);
    private static final String DUE_DATE = "2022-08-24T01:46:36.068Z";
    private static final String WORK_QUEUE = randomUUID().toString();
    private static final String CASE_URN = "TFL12345678";
    private static final String CHANGE_AUTHOR = "John Smith";
    private static final String CHANGE_AUTHOR_ID = randomUUID().toString();
    private static final String COURT_ID = randomUUID().toString();
    private static final String JURISDICTION = "CROWN";
    private static final String HEARING_DATE = "2022-08-24T01:46:36.068Z";
    private static final String COURT_CODES_VAL = "B01LY00";
    private static final String TRIAGE_INCOMING_DOCUMENT_IDENTIFIED_BPMN = "triage_incoming_document_identified.bpmn";
    private static final String SJP_CASE_HEARING_DECISION_SAVED_BPMN = "sjp-case-hearing-decision-saved.bpmn";

    @BeforeEach
    public void setUp() throws Exception {
        runtimeService = extension.getProcessEngine().getRuntimeService();
        taskService = extension.getProcessEngine().getTaskService();
        register("tasksCreatedListener", tasksCreatedListener);
        register("tasksAssignedListener", tasksAssignedListener);
        register("tasksCompletedListener", tasksCompletedListener);
    }

    @Test
    @Deployment(resources = {TRIAGE_INCOMING_DOCUMENT_IDENTIFIED_BPMN})
    public void shouldSendCommandToRecordTaskHasBeenCreatedAfterTriageIncomingDocumentCreation() throws IOException {
        processInstance = runtimeService.createProcessInstanceByKey(TRIAGE_INCOMING_DOCUMENT_IDENTIFIED)
                .businessKey("businessKey")
                // Adding complete json since customTaskServiceTask is mocked will not added data to json as part of service task execution.
                .setVariable(CASE_ID_FIELD, CASE_ID)
                .setVariable(DEFENDANT_ID_FIELD, DEFENDANT_ID)
                .setVariable(DEFENDANT_NAME_FIELD, DEFENDANT_NAME)
                .setVariable(DEEP_LINK_FIELD, DEEP_LINK)
                .setVariable(WORK_QUEUE_FIELD, WORK_QUEUE)
                .setVariable(LAST_UPDATED_BY_ID, CHANGE_AUTHOR_ID)
                .setVariable(LAST_UPDATED_BY_NAME, CHANGE_AUTHOR)
                .setVariable(TASK_TYPE_ID, TRIAGE_INCOMING_DOCUMENT_IDENTIFIED)
                .setVariable(TASK_NAME, TRIAGE_INCOMING_DOCUMENT_IDENTIFIED)
                .setVariable(DUE_DATE_FIELD, DUE_DATE)
                .setVariable(TASK_NAME_FIELD, TASK_NAME)
                .setVariable(IS_DELETABLE_FIELD, true)
                .setVariable(IS_DEFERRABLE_FIELD, true)
                .setVariable(COURT_CODES_FIELD, COURT_CODES_VAL)
                .execute();

        verify(taskAuditService, times(1)).audit(jsonEnvelopeArgument.capture(), eq(CHANGE_AUTHOR_ID));

        verify(tasksCreatedListener).notify(delegateTaskArgumentCaptor.capture());

        verify(sender, times(1)).sendAsAdmin(jsonEnvelopeArgumentCaptor.capture());

        final Metadata metadata = jsonEnvelopeArgumentCaptor.getValue().metadata();
        assertThat(metadata.name(), is("businessprocesses.command.record-task-created"));

        final JsonObject jsonObject = jsonEnvelopeArgumentCaptor.getValue().asJsonObject();

        assertThat(jsonObject.getString("id"), notNullValue());
        assertThat(jsonObject.getString("changeAuthorId"), is(CHANGE_AUTHOR_ID));
        assertThat(jsonObject.getString("changeAuthor"), is(CHANGE_AUTHOR));
        assertThat(jsonObject.getString("type"), notNullValue());
    }

    @Test
    @Deployment(resources = {SJP_CASE_HEARING_DECISION_SAVED_BPMN})
    public void shouldTriggerTasksCreatedListener() {

        when(systemUserProvider.getContextSystemUserId()).thenReturn(Optional.of(randomUUID()));

        startProcess(SJP_CASE_HEARING_DECISION_BPMN_PROCESS_NAME, CASE_ID);

        final Optional<Task> optionalTask = taskService.createTaskQuery().processInstanceId(processInstance.getId()).active().list().stream().filter(task -> task.getTaskDefinitionKey().equals(SJP_CASE_HEARING_DECISION_BPMN_TASK_NAME)).findFirst();
        assertThat(optionalTask.isPresent(), is(TRUE));

        verify(taskAuditService, times(1)).audit(jsonEnvelopeArgument.capture(), eq(CHANGE_AUTHOR_ID));

        verify(tasksCreatedListener).notify(delegateTaskArgumentCaptor.capture());

        verify(sender, times(1)).sendAsAdmin(jsonEnvelopeArgumentCaptor.capture());

        final Metadata metadata = jsonEnvelopeArgumentCaptor.getValue().metadata();
        assertThat(metadata.name(), is("businessprocesses.command.record-task-created"));

        final JsonObject jsonObject = jsonEnvelopeArgumentCaptor.getValue().asJsonObject();

        assertThat(jsonObject.getString("id"), notNullValue());
        assertThat(jsonObject.getString("changeAuthorId"), is(CHANGE_AUTHOR_ID));
        assertThat(jsonObject.getString("changeAuthor"), is(CHANGE_AUTHOR));
        assertThat(jsonObject.getString("type"), notNullValue());

        assertThat(jsonObject.getString("reference"), is(CASE_URN));
        assertThat(jsonObject.getString("createdDate"), notNullValue());

        assertThat(jsonObject.getString(DUE_DATE_FIELD), is("2022-08-24T01:46:36.068Z"));
        assertThat(jsonObject.getString(WORK_QUEUE_FIELD), is(WORK_QUEUE));
        assertThat(jsonObject.getString(TASK_TYPE_ID), is(TASK_TYPE_ID_VALUE));
        assertThat(jsonObject.getString(COURT_ID_FIELD), is(COURT_ID));
        assertThat(jsonObject.getString(JURISDICTION_FIELD), is(JURISDICTION));
        assertThat(jsonObject.getString(HEARING_DATE_FIELD), is("2022-08-24T01:46:36.068Z"));
    }

    private void startProcess(final String processDefinitionKey, final String businessKey) {
        processInstance = runtimeService.createProcessInstanceByKey(processDefinitionKey)
                .businessKey(businessKey)
                .setVariable(CASE_ID_FIELD, CASE_ID)
                .setVariable(CASE_URN_FIELD, CASE_URN)
                .setVariable(DEFENDANT_ID_FIELD, DEFENDANT_ID)
                .setVariable(DEFENDANT_NAME_FIELD, DEFENDANT_NAME)
                .setVariable(DEEP_LINK_FIELD, DEEP_LINK)
                .setVariable(REFER_COURT_HEARING_FIELD, true)
                .setVariable(DUE_DATE_FIELD, DUE_DATE)
                .setVariable(WORK_QUEUE_FIELD, WORK_QUEUE)
                .setVariable(LAST_UPDATED_BY_NAME, CHANGE_AUTHOR)
                .setVariable(LAST_UPDATED_BY_ID, CHANGE_AUTHOR_ID)
                .setVariable(COURT_ID_FIELD, COURT_ID)
                .setVariable(JURISDICTION_FIELD, JURISDICTION)
                .setVariable(HEARING_DATE_FIELD, HEARING_DATE)
                .setVariable(TASK_TYPE_ID, TASK_TYPE_ID_VALUE)
                .setVariable(TASK_NAME_FIELD, TASK_NAME)
                .setVariable(IS_DELETABLE_FIELD,true)
                .setVariable(IS_DEFERRABLE_FIELD,true)
                .setVariable(COURT_CODES_FIELD, COURT_CODES_VAL)
                .execute();
    }

    @AfterEach
    public void tearDown() throws Exception {
        Mocks.reset();
    }
}