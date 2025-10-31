package uk.gov.moj.cpp.businessprocesses.event;

import static java.util.UUID.randomUUID;
import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareAssertions.assertThat;
import static org.camunda.bpm.engine.test.mock.Mocks.register;
import static org.hamcrest.core.Is.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import org.camunda.bpm.extension.junit5.test.ProcessEngineExtension;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.moj.cpp.businessprocesses.delegate.IssueDeleteMeansInformationCommands;
import uk.gov.moj.cpp.businessprocesses.delegate.SendMeansInformationDeletedEvent;

import java.util.HashMap;
import java.util.Map;

import org.camunda.bpm.engine.ManagementService;
import org.camunda.bpm.engine.ProcessEngineConfiguration;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.runtime.Execution;
import org.camunda.bpm.engine.runtime.Job;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.test.Deployment;
import org.camunda.bpm.engine.test.RequiredHistoryLevel;
import org.camunda.bpm.engine.test.mock.Mocks;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({MockitoExtension.class, ProcessEngineExtension.class})
public class DeleteFinancialMeansProcessTest {

    @Mock
    private IssueDeleteMeansInformationCommands issueDeleteMeansInformationCommands;

    @Mock
    private SendMeansInformationDeletedEvent sendMeansInformationDeletedEvent;

    private static final String PROCESS_DELETE_FINANCIAL_MEANS_INFORMATION = "delete-financial-means-information";

    private static final String JOB_WAIT_FOR_28_DAYS = "WaitFor28Days";
    private static final String JOB_ISSUE_DELETE_MEANS_INFORMATION_COMMANDS = "IssueDeleteMeansInformationCommands";
    private static final String JOB_SEND_MEANS_INFORMATION_DELETED_EVENT = "SendMeansInformationDeletedEvent";

    private static final String DELAY_TIME_PERIOD = "delayBeforeFinancialMeansDeletion";
    private static final String CASE_ID_FIELD = "caseId";
    private static final String DEFENDANT_ID_FIELD = "defendantId";

    private static final String DELAY_28_DAYS = "P28D";

    private RuntimeService runtimeService;
    private ManagementService managementService;

    @RegisterExtension
    ProcessEngineExtension extension = ProcessEngineExtension.builder()
            .configurationResource("camunda.cfg.xml")
            .build();

    @RequiredHistoryLevel(ProcessEngineConfiguration.HISTORY_ACTIVITY)
    @BeforeEach
    public void setup() {
        runtimeService = extension.getProcessEngine().getRuntimeService();
        managementService = extension.getProcessEngine().getManagementService();

        register("issueDeleteMeansInformationCommands", issueDeleteMeansInformationCommands);
        register("sendMeansInformationDeletedEvent", sendMeansInformationDeletedEvent);
    }

    @Test
    @Deployment(resources = {"delete-financial-means-information.bpmn"})
    public void testTriggersTimer() {

        final String caseId = randomUUID().toString();
        final String defendantId = randomUUID().toString();
        final Map<String, Object> processVariables = createProcessVariables(DELAY_28_DAYS, caseId, defendantId);

        final ProcessInstance processInstance = runtimeService
                .startProcessInstanceByKey(PROCESS_DELETE_FINANCIAL_MEANS_INFORMATION, caseId, processVariables);

        //ensure that the process is started
        assertThat(processInstance).isStarted().isNotEnded();

        //ensure that it is waiting at the timer
        assertThat(processInstance).isActive().isWaitingAt(JOB_WAIT_FOR_28_DAYS);

        //ensure variables are available for the tasks
        final Execution execution = runtimeService.createExecutionQuery()
                .processInstanceId(processInstance.getProcessInstanceId())
                .activityId(JOB_WAIT_FOR_28_DAYS)
                .singleResult();
        MatcherAssert.assertThat(runtimeService.getVariable(execution.getId(), DELAY_TIME_PERIOD), is(DELAY_28_DAYS));
        MatcherAssert.assertThat(runtimeService.getVariable(execution.getId(), CASE_ID_FIELD), is(caseId));
        MatcherAssert.assertThat(runtimeService.getVariable(execution.getId(), DEFENDANT_ID_FIELD), is(defendantId));

        //move the job to the next one
        final Job waitFor28DaysJob = getJobIn(processInstance, JOB_WAIT_FOR_28_DAYS);
        managementService.executeJob(waitFor28DaysJob.getId());

        //ensure that it is waiting at the send delete financial means (transaction barrier)
        assertThat(processInstance).isActive().isWaitingAt(JOB_ISSUE_DELETE_MEANS_INFORMATION_COMMANDS);
        final Job IssueDeleteMeansInformationCommandsJob = getJobIn(processInstance, JOB_ISSUE_DELETE_MEANS_INFORMATION_COMMANDS);
        managementService.executeJob(IssueDeleteMeansInformationCommandsJob.getId());
        //verify that the delegate is called
        verify(issueDeleteMeansInformationCommands).execute(any());

        //ensure that it is waiting at the issue financial means deleted (transaction barrier)
        assertThat(processInstance).isActive().isWaitingAt(JOB_SEND_MEANS_INFORMATION_DELETED_EVENT);
        final Job SendMeansInformationDeletedEventJob = getJobIn(processInstance, JOB_SEND_MEANS_INFORMATION_DELETED_EVENT);
        managementService.executeJob(SendMeansInformationDeletedEventJob.getId());
        //verify that the delegate is called
        verify(sendMeansInformationDeletedEvent).execute(any());

        //verify that the process is now completed
        assertThat(processInstance).isEnded();

    }

    @AfterEach
    public void tearDown() {
        Mocks.reset();
    }

    private HashMap<String, Object> createProcessVariables(final String delay, final String caseId, final String defendantId) {
        final HashMap<String, Object> processVariables = new HashMap<>();
        processVariables.put(DELAY_TIME_PERIOD, delay);
        processVariables.put(CASE_ID_FIELD, caseId);
        processVariables.put(DEFENDANT_ID_FIELD, defendantId);
        return processVariables;
    }

    private Job getJobIn(final ProcessInstance processInstance, final String activity) {
        return managementService.createJobQuery().processInstanceId(processInstance.getProcessInstanceId()).activityId(activity).singleResult();
    }

}