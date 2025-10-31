package uk.gov.moj.cpp.businessprocesses.listener;

import static java.lang.Boolean.TRUE;
import static java.util.Arrays.stream;
import static java.util.Optional.of;
import static java.util.UUID.randomUUID;
import static java.util.stream.Collectors.toList;
import static org.camunda.bpm.engine.test.mock.Mocks.register;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.justice.services.test.utils.core.reflection.ReflectionUtil.setField;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.LAST_UPDATED_BY_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.LAST_UPDATED_BY_NAME;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.REGION;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.TASK_TYPE_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.WorkflowTaskTypeMapper.mapToWorkflowTaskType;
import static uk.gov.moj.cpp.businessprocesses.util.JsonUtil.getJsonObjectFromResource;
import static uk.gov.moj.cpp.businessprocesses.util.TestDataProvider.createTaskVariableCompleteJson;

import uk.gov.justice.services.core.dispatcher.SystemUserProvider;
import uk.gov.justice.services.core.sender.Sender;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.Metadata;
import uk.gov.moj.cpp.businessprocesses.delegate.CustomTaskServiceTask;
import uk.gov.moj.cpp.businessprocesses.pojo.WorkflowCustomTaskType;
import uk.gov.moj.cpp.businessprocesses.pojo.WorkflowTaskType;
import uk.gov.moj.cpp.businessprocesses.service.CalendarWeekDayOperationsService;
import uk.gov.moj.cpp.businessprocesses.service.CourtCentreService;
import uk.gov.moj.cpp.businessprocesses.service.DueDateCalculationService;
import uk.gov.moj.cpp.businessprocesses.service.ReferenceDataService;
import uk.gov.moj.cpp.businessprocesses.shared.TaskAuditService;
import uk.gov.moj.cpp.businessprocesses.shared.WorkflowTaskTypeMapper;

import java.io.IOException;
import java.sql.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.DelegateTask;
import org.camunda.bpm.engine.impl.TaskServiceImpl;
import org.camunda.bpm.engine.impl.persistence.entity.TaskEntity;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.task.IdentityLink;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.engine.test.Deployment;
import org.camunda.bpm.engine.test.mock.Mocks;
import org.camunda.bpm.extension.junit5.test.ProcessEngineExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
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
public class CustomTaskCreatedListenerTest {

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
    protected CustomTaskCreatedListener customTaskCreatedListener;

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

    @Mock
    private CustomTaskServiceTask customTaskServiceTask;

    @Mock
    private DelegateExecution delegateExecution;

    @Mock
    protected ReferenceDataService referenceDataService;

    protected TaskServiceImpl taskServiceImpl;

    protected ProcessInstance processInstance;

    private static final String REGEX = ",";
    private static final String HEARING_TYPE = "hearingType";
    private static final String CUSTODY_TIME_LIMIT = "custodyTimeLimit";
    private static final String TASK_VARIABLE_JSON_STRING = "taskVariablesJsonString";
    private static final String IS_DEFERRABLE = "isDeferrable";
    private static final String IS_DELETABLE = "isDeletable";
    private static final String CUSTOM_TASK_TYPE = "customTaskType";
    private static final String CHANGE_AUTHOR = "John Smith";
    private static final String CHANGE_AUTHOR_ID = randomUUID().toString();
    private static final String WORKFLOW_TASK_TYPES_JSON = "workflow-task-types.json";
    private static final String TASK_TYPE_ID_VALUE = randomUUID().toString();

    private static final String REGION_VALUE = "Wales";

    private static final String TASK_NAME = "Book_interpreter";

    private static final String SINGLE_CUSTOM_TASK_PROCESS_BPMN = "single_custom_task_process.bpmn";

    private static final String TASK_VARIABLE_JSON_COMPLETE_WITH_HEARING_DATE_VALUE = "{\"displayName\":\"Check phone call2\",\"taskName\":\"Book_interpreter\",\"due\":\"2021-06-27T23:59:00.000Z\",\"hearingDate\":\"2025-08-27\",\"custodyTimeLimit\":\"2025-01-27\",\"caseURN\":\"29GD7875621\",\"hearingType\":\"Plea and Trial Preparation\",\"assignee\":\"checkOuCodeLogic\",\"candidateGroups\":\"3d1b2be0-f92a-4291-9b99-17af7e645904\",\"isDeletable\":true,\"isDeferrable\":true,\"organisationId\":\"7f2b2be0-f92a-4291-9b99-17af7e645321\",\"caseId\":\"8e4b2be0-f92a-4291-9b99-17af7e645472\",\"businessUnitCodes\":\"businessUnitOuCode\",\"courtCodes\":\"courtOuCode\",\"isUrgent\":false,\"defendants\":[{\"id\":\"1\",\"firstName\":\"Smith\",\"lastName\":\"Philip\"},{\"id\":\"2\",\"firstName\":\"Mona\",\"lastName\":\"Lisa\"},{\"id\":\"3\",\"firstName\":\"Victor\",\"lastName\":\"Hugo\"},{\"id\":\"4\",\"firstName\":\"Leo\",\"lastName\":\"Tolstoy\"}],\"comment\":\"This is test comment\"}";
    private static final String TASK_VARIABLE_JSON_COMPLETE_WITHOUT_HEARING_DATE_VALUE = "{\"displayName\":\"Check phone call2\",\"taskName\":\"Book_interpreter\",\"due\":\"2021-06-27T23:59:00.000Z\",\"custodyTimeLimit\":\"2025-01-27\",\"caseURN\":\"29GD7875621\",\"hearingType\":\"Plea and Trial Preparation\",\"assignee\":\"checkOuCodeLogic\",\"candidateGroups\":\"3d1b2be0-f92a-4291-9b99-17af7e645904\",\"isDeletable\":true,\"isDeferrable\":true,\"organisationId\":\"7f2b2be0-f92a-4291-9b99-17af7e645321\",\"caseId\":\"8e4b2be0-f92a-4291-9b99-17af7e645472\",\"businessUnitCodes\":\"businessUnitOuCode\",\"courtCodes\":\"courtOuCode\",\"isUrgent\":false,\"defendants\":[{\"id\":\"1\",\"firstName\":\"Smith\",\"lastName\":\"Philip\"},{\"id\":\"2\",\"firstName\":\"Mona\",\"lastName\":\"Lisa\"},{\"id\":\"3\",\"firstName\":\"Victor\",\"lastName\":\"Hugo\"},{\"id\":\"4\",\"firstName\":\"Leo\",\"lastName\":\"Tolstoy\"}],\"comment\":\"This is test comment\"}";
    private DueDateCalculationService dueDateCalculationService = new DueDateCalculationService();

    private CalendarWeekDayOperationsService calendarWeekDayOperationsService = new CalendarWeekDayOperationsService();

    @BeforeEach
    public void setUp() throws Exception {
        runtimeService = extension.getProcessEngine().getRuntimeService();
        taskService = extension.getProcessEngine().getTaskService();
        taskServiceImpl = (TaskServiceImpl) taskService;
        register("customTaskServiceTask", customTaskServiceTask);
        register("customTaskCreatedListener", customTaskCreatedListener);
        setField(customTaskCreatedListener, "dueDateCalculationService", dueDateCalculationService);
        setField(dueDateCalculationService, "calendarWeekDayOperationsService", calendarWeekDayOperationsService);
        setField(calendarWeekDayOperationsService, "referenceDataService", referenceDataService);
        when(referenceDataService.getWorkflowTaskType(anyString())).thenReturn(of(getWorkflowTaskType()));
        when(courtCentreService.getCourtCentreRegionByCourtCode(anyString())).thenReturn(REGION_VALUE);
    }

    @Test
    @Deployment(resources = {SINGLE_CUSTOM_TASK_PROCESS_BPMN})
    public void shouldRaisePublicEventAfterCreation() throws Exception {

        processInstance = runtimeService.createProcessInstanceByKey("single_custom_task_process")
                .businessKey("single_custom_task_process")
                // Adding complete json since customTaskServiceTask is mocked will not added data to json as part of service task execution.
                .setVariable(TASK_VARIABLE_JSON_STRING, TASK_VARIABLE_JSON_COMPLETE_WITH_HEARING_DATE_VALUE)
                .setVariable(CUSTOM_TASK_TYPE, "customTask1")
                .setVariable(LAST_UPDATED_BY_ID, CHANGE_AUTHOR_ID)
                .setVariable(LAST_UPDATED_BY_NAME, CHANGE_AUTHOR)
                .setVariable(TASK_TYPE_ID, TASK_TYPE_ID_VALUE)
                .execute();
        final Task activeTask = taskService.createTaskQuery().processInstanceId(processInstance.getId()).active().list().get(0);
        assertThat("custom_activity", is(activeTask.getTaskDefinitionKey()));
        verify(customTaskCreatedListener).notify(delegateTaskArgumentCaptor.capture());

        final DelegateTask delegateTask = delegateTaskArgumentCaptor.getValue();
        assertTaskUpdate((TaskEntity) delegateTask);
        assertThat("Check phone call2", is(delegateTask.getName()));
        assertThat("single_custom_task_process", is(delegateTask.getExecution().getProcessInstance().getBusinessKey()));
        assertThat(delegateTask.getCreateTime().toString(), notNullValue());

        verify(sender, times(1)).sendAsAdmin(jsonEnvelopeArgumentCaptor.capture());

        final Metadata metadata = jsonEnvelopeArgumentCaptor.getValue().metadata();
        assertThat(metadata.name(), is("businessprocesses.command.record-task-created"));

    }

    @Test
    @Deployment(resources = {SINGLE_CUSTOM_TASK_PROCESS_BPMN})
    public void shouldRaisePublicEventAfterCreationWithoutHearingDate() throws Exception {

        processInstance = runtimeService.createProcessInstanceByKey("single_custom_task_process")
                .businessKey("single_custom_task_process")
                // Adding complete json since customTaskServiceTask is mocked will not added data to json as part of service task execution.
                .setVariable(TASK_VARIABLE_JSON_STRING, TASK_VARIABLE_JSON_COMPLETE_WITHOUT_HEARING_DATE_VALUE)
                .setVariable(CUSTOM_TASK_TYPE, "customTask1")
                .setVariable(LAST_UPDATED_BY_ID, CHANGE_AUTHOR_ID)
                .setVariable(LAST_UPDATED_BY_NAME, CHANGE_AUTHOR)
                .setVariable(TASK_TYPE_ID, TASK_TYPE_ID_VALUE)
                .execute();
        final Task activeTask = taskService.createTaskQuery().processInstanceId(processInstance.getId()).active().list().get(0);
        assertThat("custom_activity", is(activeTask.getTaskDefinitionKey()));
        verify(customTaskCreatedListener).notify(delegateTaskArgumentCaptor.capture());

        final DelegateTask delegateTask = delegateTaskArgumentCaptor.getValue();
        assertTaskUpdate((TaskEntity) delegateTask);
        assertThat("Check phone call2", is(delegateTask.getName()));
        assertThat("single_custom_task_process", is(delegateTask.getExecution().getProcessInstance().getBusinessKey()));
        assertThat(delegateTask.getCreateTime().toString(), notNullValue());

        verify(sender, times(1)).sendAsAdmin(jsonEnvelopeArgumentCaptor.capture());

        final Metadata metadata = jsonEnvelopeArgumentCaptor.getValue().metadata();
        assertThat(metadata.name(), is("businessprocesses.command.record-task-created"));


    }

    private void assertTaskUpdate(final TaskEntity activeTask) {
        final WorkflowCustomTaskType workflowCustomTaskType = WorkflowTaskTypeMapper.mapToWorkflowCustomTaskType(createTaskVariableCompleteJson());
        final List<String> actualCandidates = activeTask.getCandidates().stream().map(IdentityLink::getGroupId).collect(Collectors.toList());
        final List<String> expectedCandidates = stream(workflowCustomTaskType.getCandidateGroups().split(REGEX)).map(String::trim).collect(toList());

        assertThat(activeTask.getVariableLocal(IS_DELETABLE), is(workflowCustomTaskType.getDeletable()));
        assertThat(activeTask.getVariableLocal(IS_DEFERRABLE), is(workflowCustomTaskType.getDeferrable()));
        assertThat(actualCandidates.containsAll(expectedCandidates), is(TRUE));
        assertThat(activeTask.getVariableLocal(HEARING_TYPE), is(workflowCustomTaskType.getHearingType()));
        assertThat(activeTask.getVariableLocal(CUSTODY_TIME_LIMIT), is(Date.valueOf(workflowCustomTaskType.getCustodyTimeLimit())));
        assertThat(activeTask.getTaskDefinitionKey(), is(workflowCustomTaskType.getTaskName()));
        assertThat(activeTask.getVariableLocal(REGION), is(REGION_VALUE));
    }


    private WorkflowTaskType getWorkflowTaskType() throws IOException {
        return mapToWorkflowTaskType(getJsonObjectFromResource(WORKFLOW_TASK_TYPES_JSON), TASK_NAME).get();
    }

    @AfterEach
    public void tearDown() throws Exception {
        Mocks.reset();
    }

}