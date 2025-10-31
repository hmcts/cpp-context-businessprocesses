package uk.gov.moj.cpp.businessprocesses.event;


import static java.util.Optional.of;
import static java.util.UUID.randomUUID;
import static org.camunda.bpm.engine.test.mock.Mocks.register;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static uk.gov.justice.services.test.utils.core.reflection.ReflectionUtil.setField;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.SYSTEM_USER_NAME;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.CUSTODY_TIME_LIMIT;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.HEARING_DATE;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.TASK_TYPE_ID;

import uk.gov.justice.services.common.util.UtcClock;
import uk.gov.justice.services.core.dispatcher.SystemUserProvider;
import uk.gov.justice.services.core.featurecontrol.FeatureControlGuard;
import uk.gov.moj.cpp.businessprocesses.listener.TasksCompletedListener;
import uk.gov.moj.cpp.businessprocesses.listener.TasksCreatedListener;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.UUID;

import org.camunda.bpm.engine.ProcessEngineConfiguration;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.test.Deployment;
import org.camunda.bpm.engine.test.RequiredHistoryLevel;
import org.camunda.bpm.extension.junit5.test.ProcessEngineExtension;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.moj.cpp.businessprocesses.listener.TasksUpdatedListener;

@ExtendWith({MockitoExtension.class, ProcessEngineExtension.class})
@SuppressWarnings({"squid:S2187"})
public class ListingHearingListedEventProcessorTest {

    private static final String TASK_NAME = "taskName";
    private static final String CANDIDATE_GROUPS = "candidateGroups";
    private static final String CASE_URN = "caseURN";
    private static final String REGION = "region";
    public static final String DUE_DATE = "dueDate";
    public static final String IS_DELETABLE = "isDeletable";
    public static final String IS_DEFERRABLE = "isDeferrable";
    @InjectMocks
    private ListingHearingListedEventProcessor listingHearingListedEventProcessor;
    @Mock
    private TasksCreatedListener tasksCreatedListener;
    @Mock
    private TasksCompletedListener tasksCompletedListener;
    @Mock
    protected TasksUpdatedListener tasksUpdatedListener;
    @Mock
    private TaskService taskService;
    @Mock
    private SystemUserProvider systemUserProvider;

    @Mock
    private FeatureControlGuard featureControlGuard;

    private static final String USER_ID = randomUUID().toString();
    private static final String HEARING_ID = "hearingId";
    private static final String HEARING_TYPE = "hearingType";
    private static final String LAST_UPDATED_BY_ID = "lastUpdatedByID";
    private static final String LAST_UPDATED_BY_NAME = "lastUpdatedByName";

    private static final String BAIL_APPEAL_PROCESS_NAME = "list_bail_appeal_hearing_process";
    private static final String BAIL_APPEAL_TASK_NAME = "list_bail_appeal_hearing";
    private static final String MURDER_CASE_APPEAL_PROCESS_NAME = "list_murder_case_for_bail_hearing_process";
    private static final String MURDER_CASE_APPEAL_TASK_NAME = "list_murder_case_for_bail_hearing";

    private static final String BLA_HEARING_TYPE = "BLA";

    private static final String BVA_HEARING_TYPE = "BVA";

    private static final String SUB_HEARING_TYPE = "SUB";

    private static final String SWAS_HEARING_TYPE = "SWAS";
    private static final UUID SYSTEM_USER_ID = randomUUID();

    private static final ZonedDateTime HEARING_DATE_VAL = new UtcClock().now();

    private static final String CUSTODY_TIME_LIMIT_VAL = "2026-05-21";
    private RuntimeService runtimeService;
    private TaskService camundaTaskService;

    @RegisterExtension
    ProcessEngineExtension extension = ProcessEngineExtension.builder()
            .configurationResource("camunda.cfg.xml")
            .build();


    @RequiredHistoryLevel(ProcessEngineConfiguration.HISTORY_ACTIVITY)
    @BeforeEach
    public void setup() {
        runtimeService = extension.getProcessEngine().getRuntimeService();
        camundaTaskService = extension.getProcessEngine().getTaskService();
        register("tasksCreatedListener", tasksCreatedListener);
        register("tasksCompletedListener", tasksCompletedListener);
        register("tasksUpdatedListener", tasksUpdatedListener);
        setField(this.listingHearingListedEventProcessor, "taskService", camundaTaskService);
    }

    @Test
    @Deployment(resources = {"list_bail_appeal_hearing_process.bpmn"})
    public void shouldAutoCompleteBailHearingTasks() {
        String caseId1 = randomUUID().toString();
        String urn1 = "TFL4359536";
        String hearingId1 = randomUUID().toString();
        when(systemUserProvider.getContextSystemUserId()).thenReturn(of(SYSTEM_USER_ID));
        when(featureControlGuard.isFeatureEnabled(any())).thenReturn(true);

        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(BAIL_APPEAL_PROCESS_NAME, caseId1, createProcessVariables(hearingId1, BLA_HEARING_TYPE, BAIL_APPEAL_TASK_NAME, urn1));

        String caseId2 = randomUUID().toString();
        String hearingId2 = randomUUID().toString();
        String urn2 = "TFL4359537";
        processInstance = runtimeService.startProcessInstanceByKey(BAIL_APPEAL_PROCESS_NAME, caseId2, createProcessVariables(hearingId2, BLA_HEARING_TYPE, BAIL_APPEAL_TASK_NAME, urn2));

        String caseId3 = randomUUID().toString();
        String hearingId3 = randomUUID().toString();
        String urn3 = "TFL4359538";
        processInstance = runtimeService.startProcessInstanceByKey(BAIL_APPEAL_PROCESS_NAME, caseId3, createProcessVariables(hearingId3, SWAS_HEARING_TYPE, BAIL_APPEAL_TASK_NAME, urn3));


        listingHearingListedEventProcessor.completeBailHearingTasks(caseId2, hearingId2, BLA_HEARING_TYPE, BAIL_APPEAL_PROCESS_NAME, MURDER_CASE_APPEAL_PROCESS_NAME);
        camundaTaskService.createTaskQuery().list();
        assertThat(camundaTaskService.createTaskQuery().count(), Matchers.is(2L));
    }

    @Test
    @Deployment(resources = {"list_bail_appeal_hearing_process.bpmn"})
    public void shouldNotAutoCompleteBailHearingTasksWhenFeatureIsDisabled() {
        when(featureControlGuard.isFeatureEnabled(any())).thenReturn(false);
        String caseId1 = randomUUID().toString();
        String urn1 = "TFL4359536";
        String hearingId1 = randomUUID().toString();

        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(BAIL_APPEAL_PROCESS_NAME, caseId1, createProcessVariables(hearingId1, BLA_HEARING_TYPE, BAIL_APPEAL_TASK_NAME, urn1));

        String caseId2 = randomUUID().toString();
        String hearingId2 = randomUUID().toString();
        String urn2 = "TFL4359537";
        processInstance = runtimeService.startProcessInstanceByKey(BAIL_APPEAL_PROCESS_NAME, caseId2, createProcessVariables(hearingId2, BLA_HEARING_TYPE, BAIL_APPEAL_TASK_NAME, urn2));

        String caseId3 = randomUUID().toString();
        String hearingId3 = randomUUID().toString();
        String urn3 = "TFL4359538";
        processInstance = runtimeService.startProcessInstanceByKey(BAIL_APPEAL_PROCESS_NAME, caseId3, createProcessVariables(hearingId3, SWAS_HEARING_TYPE, BAIL_APPEAL_TASK_NAME, urn3));


        listingHearingListedEventProcessor.completeBailHearingTasks(caseId2, hearingId2, BLA_HEARING_TYPE, BAIL_APPEAL_PROCESS_NAME, MURDER_CASE_APPEAL_PROCESS_NAME);
        camundaTaskService.createTaskQuery().list();
        assertThat(camundaTaskService.createTaskQuery().count(), Matchers.is(3L));
    }

    @Test
    @Deployment(resources = {"list_murder_case_for_bail_hearing_process.bpmn"})
    public void shouldAutoCompleteSeriousCaseBailHearingTasks() {
        when(featureControlGuard.isFeatureEnabled(any())).thenReturn(true);
        String caseId1 = randomUUID().toString();
        String hearingId1 = randomUUID().toString();
        String urn1 = "TFL4359536";
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(MURDER_CASE_APPEAL_PROCESS_NAME, caseId1, createProcessVariables(hearingId1, BLA_HEARING_TYPE, MURDER_CASE_APPEAL_TASK_NAME, urn1));

        String caseId2 = randomUUID().toString();
        String hearingId2 = randomUUID().toString();
        String urn2 = "TFL4359537";
        processInstance = runtimeService.startProcessInstanceByKey(MURDER_CASE_APPEAL_PROCESS_NAME, caseId2, createProcessVariables(hearingId2, BLA_HEARING_TYPE, MURDER_CASE_APPEAL_TASK_NAME, urn2));

        String caseId3 = randomUUID().toString();
        String hearingId3 = randomUUID().toString();
        String urn3 = "TFL4359538";
        processInstance = runtimeService.startProcessInstanceByKey(MURDER_CASE_APPEAL_PROCESS_NAME, caseId3, createProcessVariables(hearingId3, SWAS_HEARING_TYPE, MURDER_CASE_APPEAL_TASK_NAME, urn3));

        camundaTaskService.createTaskQuery().list();
        listingHearingListedEventProcessor.completeBailHearingTasks(caseId2, hearingId2, BLA_HEARING_TYPE, BAIL_APPEAL_PROCESS_NAME, MURDER_CASE_APPEAL_PROCESS_NAME);
        assertThat(camundaTaskService.createTaskQuery().count(), Matchers.is(2L));
    }

    @Test
    @Deployment(resources = {"list_bail_appeal_hearing_process.bpmn"})
    public void shouldNotAutoCompleteBailHearingTasksWhenInvalidHearingType() {
        when(featureControlGuard.isFeatureEnabled(any())).thenReturn(true);
        String caseId1 = randomUUID().toString();
        String hearingId1 = randomUUID().toString();
        String urn1 = "TFL4359536";
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(BAIL_APPEAL_PROCESS_NAME, caseId1, createProcessVariables(hearingId1, BLA_HEARING_TYPE, BAIL_APPEAL_TASK_NAME, urn1));

        String caseId2 = randomUUID().toString();
        String hearingId2 = randomUUID().toString();
        String urn2 = "TFL4359537";
        processInstance = runtimeService.startProcessInstanceByKey(BAIL_APPEAL_PROCESS_NAME, caseId2, createProcessVariables(hearingId2, BLA_HEARING_TYPE, BAIL_APPEAL_TASK_NAME, urn2));

        String caseId3 = randomUUID().toString();
        String hearingId3 = randomUUID().toString();
        String urn3 = "TFL4359538";
        processInstance = runtimeService.startProcessInstanceByKey(BAIL_APPEAL_PROCESS_NAME, caseId3, createProcessVariables(hearingId3, SWAS_HEARING_TYPE, BAIL_APPEAL_TASK_NAME, urn3));

        camundaTaskService.createTaskQuery().list();
        listingHearingListedEventProcessor.completeBailHearingTasks(caseId2, hearingId2, SUB_HEARING_TYPE, BAIL_APPEAL_PROCESS_NAME, MURDER_CASE_APPEAL_PROCESS_NAME);
        assertThat(camundaTaskService.createTaskQuery().count(), Matchers.is(2L));
    }

    @Test
    @Deployment(resources = {"list_murder_case_for_bail_hearing_process.bpmn"})
    public void shouldNotAutoCompleteSeriousCaseBailHearingTasksWhenInvalidHearingType() {
        when(featureControlGuard.isFeatureEnabled(any())).thenReturn(true);
        String caseId1 = randomUUID().toString();
        String hearingId1 = randomUUID().toString();
        String urn1 = "TFL4359536";
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(MURDER_CASE_APPEAL_PROCESS_NAME, caseId1, createProcessVariables(hearingId1, BLA_HEARING_TYPE, MURDER_CASE_APPEAL_TASK_NAME, urn1));

        String caseId2 = randomUUID().toString();
        String hearingId2 = randomUUID().toString();
        String urn2 = "TFL4359537";
        processInstance = runtimeService.startProcessInstanceByKey(MURDER_CASE_APPEAL_PROCESS_NAME, caseId2, createProcessVariables(hearingId2, BLA_HEARING_TYPE, MURDER_CASE_APPEAL_TASK_NAME, urn2));

        String caseId3 = randomUUID().toString();
        String hearingId3 = randomUUID().toString();
        String urn3 = "TFL4359538";
        processInstance = runtimeService.startProcessInstanceByKey(MURDER_CASE_APPEAL_PROCESS_NAME, caseId3, createProcessVariables(hearingId3, SWAS_HEARING_TYPE, MURDER_CASE_APPEAL_TASK_NAME, urn3));

        camundaTaskService.createTaskQuery().list();
        listingHearingListedEventProcessor.completeBailHearingTasks(caseId2, hearingId2, BVA_HEARING_TYPE, BAIL_APPEAL_PROCESS_NAME, MURDER_CASE_APPEAL_PROCESS_NAME);
        assertThat(camundaTaskService.createTaskQuery().count(), Matchers.is(2L));
    }

    @Test
    @Deployment(resources = {"list_bail_appeal_hearing_process.bpmn"})
    public void shouldNotAutoCompleteBailHearingTasksWhenInvalidProcessDefinitionKey() {
        String caseId1 = randomUUID().toString();
        String hearingId1 = randomUUID().toString();
        String urn1 = "TFL4359536";
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(BAIL_APPEAL_PROCESS_NAME, caseId1, createProcessVariables(hearingId1, BLA_HEARING_TYPE, BAIL_APPEAL_TASK_NAME, urn1));

        String caseId2 = randomUUID().toString();
        String hearingId2 = randomUUID().toString();
        String urn2 = "TFL4359537";
        processInstance = runtimeService.startProcessInstanceByKey(BAIL_APPEAL_PROCESS_NAME, caseId2, createProcessVariables(hearingId2, BLA_HEARING_TYPE, BAIL_APPEAL_TASK_NAME, urn2));

        String caseId3 = randomUUID().toString();
        String hearingId3 = randomUUID().toString();
        String urn3 = "TFL4359538";
        processInstance = runtimeService.startProcessInstanceByKey(BAIL_APPEAL_PROCESS_NAME, caseId3, createProcessVariables(hearingId3, SWAS_HEARING_TYPE, BAIL_APPEAL_TASK_NAME, urn3));

        camundaTaskService.createTaskQuery().list();
        listingHearingListedEventProcessor.completeBailHearingTasks(caseId2, hearingId2, BLA_HEARING_TYPE, "invalid_list_bail_appeal_hearing");
        assertThat(camundaTaskService.createTaskQuery().count(), Matchers.is(3L));
    }

    @Test
    @Deployment(resources = {"list_murder_case_for_bail_hearing_process.bpmn"})
    public void shouldNotAutoCompleteSeriousCaseBailHearingTasksWhenInvalidProcessDefinitionKey() {
        String caseId1 = randomUUID().toString();
        String hearingId1 = randomUUID().toString();
        String urn1 = "TFL4359536";
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(MURDER_CASE_APPEAL_PROCESS_NAME, caseId1, createProcessVariables(hearingId1, BLA_HEARING_TYPE, MURDER_CASE_APPEAL_TASK_NAME, urn1));

        String caseId2 = randomUUID().toString();
        String hearingId2 = randomUUID().toString();
        String urn2 = "TFL4359537";
        processInstance = runtimeService.startProcessInstanceByKey(MURDER_CASE_APPEAL_PROCESS_NAME, caseId2, createProcessVariables(hearingId2, BLA_HEARING_TYPE, MURDER_CASE_APPEAL_TASK_NAME, urn2));

        String caseId3 = randomUUID().toString();
        String hearingId3 = randomUUID().toString();
        String urn3 = "TFL4359538";
        processInstance = runtimeService.startProcessInstanceByKey(MURDER_CASE_APPEAL_PROCESS_NAME, caseId3, createProcessVariables(hearingId3, SWAS_HEARING_TYPE, MURDER_CASE_APPEAL_TASK_NAME, urn3));

        camundaTaskService.createTaskQuery().list();
        listingHearingListedEventProcessor.completeBailHearingTasks(caseId2, hearingId2, BLA_HEARING_TYPE, "invalid_list_serious_case_bail_hearing");
        assertThat(camundaTaskService.createTaskQuery().count(), Matchers.is(3L));
    }

    private HashMap<String, Object> createProcessVariables(final String hearingId, final String hearingType, final String taskName, final String caseUrn) {
        final HashMap<String, Object> processVariables = new HashMap<>();
        processVariables.put(HEARING_ID, hearingId);
        processVariables.put(HEARING_TYPE, hearingType);
        processVariables.put(LAST_UPDATED_BY_ID, USER_ID);
        processVariables.put(LAST_UPDATED_BY_NAME, SYSTEM_USER_NAME);
        processVariables.put(TASK_TYPE_ID, randomUUID());
        processVariables.put(TASK_NAME, taskName);
        processVariables.put(CANDIDATE_GROUPS, "any candidateGroups");
        processVariables.put(CASE_URN, caseUrn);
        processVariables.put(REGION, "any region");
        processVariables.put(DUE_DATE, "2020-03-05");
        processVariables.put(IS_DEFERRABLE, true);
        processVariables.put(IS_DELETABLE, true);
        processVariables.put(HEARING_DATE, HEARING_DATE_VAL);
        processVariables.put(CUSTODY_TIME_LIMIT, CUSTODY_TIME_LIMIT_VAL);

        return processVariables;
    }

}