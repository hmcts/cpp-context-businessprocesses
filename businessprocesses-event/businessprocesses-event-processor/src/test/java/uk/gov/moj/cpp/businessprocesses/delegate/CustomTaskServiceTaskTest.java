package uk.gov.moj.cpp.businessprocesses.delegate;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static uk.gov.moj.cpp.businessprocesses.util.TestDataProvider.createTaskVariableInputJson;
import static uk.gov.moj.cpp.businessprocesses.util.TestDataProvider.getHearingList;
import static uk.gov.moj.cpp.businessprocesses.util.TestDataProvider.getProsecutionCaseCaag;

import uk.gov.justice.services.common.converter.jackson.ObjectMapperProducer;
import uk.gov.moj.cpp.businessprocesses.create.CreateCustomTask;
import uk.gov.moj.cpp.businessprocesses.service.ListingService;
import uk.gov.moj.cpp.businessprocesses.service.ProgressionService;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.test.Deployment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@Deployment(resources = {"single_custom_task_process.bpmn"})
public class CustomTaskServiceTaskTest {

    private static final String TASK_VARIABLE_JSON_STRING = "taskVariablesJsonString";
    private static final String TASK_VARIABLE_JSON_STRING_TO_FAIL = "taskVariablesJsonStringFail";
    private static final String USER_ID = "userId";

    @Mock
    private ListingService listingService;

    @Mock
    private ProgressionService progressionService;

    @Mock
    private DelegateExecution execution;

    @InjectMocks
    private CustomTaskServiceTask customTaskServiceTask;

    @BeforeEach
    public void setup() throws Exception {
        final CreateCustomTask createCustomTask = new ObjectMapperProducer().objectMapper().readValue(createTaskVariableInputJson(), CreateCustomTask.class);
        when(execution.getVariable(TASK_VARIABLE_JSON_STRING)).thenReturn(createCustomTask);
        when(execution.getVariable(USER_ID)).thenReturn(randomUUID().toString());
        doReturn(getProsecutionCaseCaag()).when(progressionService).getProsecutionCaseCaag(anyString(), anyString());
        doReturn(getHearingList()).when(listingService).getHearings(anyString(), anyString());
    }

    @Test
    public void executePass() {
        try {
            customTaskServiceTask.execute(execution);
        } catch (Exception e) {
            assertThat("fail here", false);
        }
    }

}