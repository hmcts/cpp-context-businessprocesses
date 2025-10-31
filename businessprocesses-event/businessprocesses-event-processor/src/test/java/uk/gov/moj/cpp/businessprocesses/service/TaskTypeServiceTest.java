package uk.gov.moj.cpp.businessprocesses.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.moj.cpp.businessprocesses.util.TestDataProvider.getWorkflowTaskType;

import uk.gov.moj.cpp.businessprocesses.shared.WorkFlowTaskTypeNotFoundException;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaskTypeServiceTest {
    @Mock
    private ReferenceDataService referenceDataService;

    @Mock
    private DueDateCalculationService dueDateCalculationService;

    @InjectMocks
    private TaskTypeService target;

    private final UUID RESOURCE_ID = UUID.randomUUID();
    private final String TEST_PREFIX = "TEST_";

    private final String TASK_SEND_EMAIL = "taskSendEmail";


    @Test
    void shouldSendTaskVariables() throws IOException {
        when(referenceDataService.getWorkflowTaskType(anyString())).thenReturn(Optional.of(getWorkflowTaskType(TASK_SEND_EMAIL)));
        final Map<String, Object> result = target.getTaskVariablesFromRefData("triage_incoming_document_identified", RESOURCE_ID.toString());
        Object[] keys = result.keySet().toArray();
        Arrays.sort(keys);
        assertNotNull(result);
        assertThat(keys[0], is("candidateGroups"));
        assertThat(keys[1], is("deepLink"));
        assertThat(keys[2], is("dueDate"));
        verify(dueDateCalculationService, times(1)).calculateDueDate(any());
    }

    @Test
    void shouldThrowWorkFlowTaskTypeNotFoundException() {
        try {
            final Map<String, Object> result = target.getTaskVariablesFromRefData("triage_incoming_document_identified", RESOURCE_ID.toString());
            verify(dueDateCalculationService, times(1)).calculateDueDate(any());
        } catch (Throwable ex) {
            assertTrue(ex instanceof WorkFlowTaskTypeNotFoundException);
        }

    }

    @Test
    void shouldSendTaskVariablesWithPrefix() throws IOException {
        when(referenceDataService.getWorkflowTaskType(anyString())).thenReturn(Optional.of(getWorkflowTaskType(TASK_SEND_EMAIL)));
        final Map<String, Object> result = target.getTaskVariablesFromRefDataWithPrefix("triage_incoming_document_identified", RESOURCE_ID.toString(), TEST_PREFIX);

        Object[] keys = result.keySet().toArray();
        Arrays.sort(keys);
        assertNotNull(result);
        assertThat(keys[0], is(TEST_PREFIX + "candidateGroups"));
        assertThat(keys[1], is(TEST_PREFIX + "deepLink"));
        assertThat(keys[2], is(TEST_PREFIX + "dueDate"));
        verify(dueDateCalculationService, times(1)).calculateDueDate(any());
    }

}
