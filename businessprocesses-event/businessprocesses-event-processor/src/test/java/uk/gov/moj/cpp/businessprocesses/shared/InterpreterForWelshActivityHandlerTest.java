package uk.gov.moj.cpp.businessprocesses.shared;

import static com.google.common.io.Resources.getResource;
import static java.nio.charset.Charset.defaultCharset;
import static java.util.Optional.of;
import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.justice.services.test.utils.core.reflection.ReflectionUtil.setField;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.TASK_NAME_BOOK_WELSH_INTERPRETER_FOR_APPLICATION;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.TASK_NAME_BOOK_WELSH_INTERPRETER_FOR_CASE;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.DEEP_LINK;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.DUE_DATE;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.WELSH_LANGUAGE_UNIT_WORK_QUEUE;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.WORK_QUEUE;
import static uk.gov.moj.cpp.businessprocesses.util.FileUtil.getFileContentAsJson;
import static uk.gov.moj.cpp.businessprocesses.util.JsonUtil.stringToJsonObjectConverter;
import static uk.gov.moj.cpp.businessprocesses.util.TestDataProvider.getWorkflowTaskType;

import uk.gov.justice.core.courts.Hearing;
import uk.gov.justice.services.common.converter.JsonObjectToObjectConverter;
import uk.gov.justice.services.common.converter.jackson.ObjectMapperProducer;
import uk.gov.justice.services.core.dispatcher.SystemUserProvider;
import uk.gov.moj.cpp.businessprocesses.service.DueDateCalculationService;
import uk.gov.moj.cpp.businessprocesses.service.HearingService;
import uk.gov.moj.cpp.businessprocesses.service.ProgressionService;
import uk.gov.moj.cpp.businessprocesses.service.ReferenceDataService;
import uk.gov.moj.cpp.businessprocesses.service.TaskTypeService;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.json.JsonObject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.Resources;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.engine.task.TaskQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({MockitoExtension.class})
class InterpreterForWelshActivityHandlerTest {

    private static final String PROGRESSION_PROSECUTION_CASE = "progression-prosecution-case.json";

    private static final String DEEP_LINK_BASE_AAAG = "/prosecution-casefile/application-at-a-glance/";
    private static final String DEEP_LINK_BASE_CAAG = "/prosecution-casefile/case-at-a-glance/";
    private static final String APPLICATION_ID_VALUE = "f5772929-9511-4235-a65f-66ecfd08acfb";
    private static final String CASE_ID_VALUE = "e3852f62-4412-40b2-b326-b6b1b62482ee";

    private static final String DUE_DATE_VALUE = "2020-03-05";
    private static final String TASK_TYPE_SERVICE = "taskTypeService";

    @InjectMocks
    private InterpreterForWelshActivityHandler interpreterForWelshActivityHandler;

    @Mock
    private RuntimeService runtimeService;

    @InjectMocks
    private TaskTypeService taskTypeService;

    @Mock
    private SystemUserProvider systemUserProvider;

    @Mock
    private ProgressionService progressionService;

    @Mock
    private TaskService taskService;

    @Mock
    private TaskQuery taskQuery;

    @Mock
    private HearingService hearingService;

    @Mock
    private ReferenceDataService referenceDataService;

    @Mock
    private DueDateCalculationService dueDateCalculationService;

    @Spy
    private final ObjectMapper objectMapper = new ObjectMapperProducer().objectMapper();

    @Spy
    private final JsonObjectToObjectConverter jsonObjectToObjectConverter = new JsonObjectToObjectConverter(objectMapper);

    @Captor
    private ArgumentCaptor<Map<String, Object>> processVariablesCaptor;

    @BeforeEach
    void setUp() {
        setField(interpreterForWelshActivityHandler, TASK_TYPE_SERVICE, taskTypeService);
        mockTaskService(List.of());
    }

    private void mockWorkflowTaskTypes(final String workflowTaskTypeName) throws IOException {
        when(referenceDataService.getWorkflowTaskType(anyString()))
                .thenReturn(of(getWorkflowTaskType(workflowTaskTypeName)));

        when(dueDateCalculationService.calculateDueDate(any(), any()))
                .thenReturn(DUE_DATE_VALUE);
    }

    @Test
    public void shouldCreateInterpreterForWelshActivityAndAssignToWelshLang() throws IOException {

        final String hearingId = UUID.randomUUID().toString();
        mockHearing("json/welsh-interpreter/hearing.get.hearing-case.json", hearingId);
        mockWorkflowTaskTypes(TASK_NAME_BOOK_WELSH_INTERPRETER_FOR_CASE);
        setWelshCourt(true);
        String progressionResponsePayload = getProgressionResponsePayload()
                .replace("\"interpreterLanguageNeeds\": \"INTERPRETER_LANGUAGE_NEEDS\"", "\"interpreterLanguageNeeds\": \"WELSH\"");
        when(progressionService.getProsecutionCase(any())).thenReturn(stringToJsonObjectConverter.convert(progressionResponsePayload));
        interpreterForWelshActivityHandler.handleWelshInterpreterForCaseInitiated(hearingId);
        verifyAndAssertResults(WELSH_LANGUAGE_UNIT_WORK_QUEUE, DEEP_LINK_BASE_CAAG.concat(CASE_ID_VALUE), DUE_DATE_VALUE);
    }

    @Test
    public void shouldNotCreateInterpreterForWelshActivityAndAssign() throws IOException {
        reset(taskService);
        reset(taskQuery);
        final String hearingId = UUID.randomUUID().toString();
        mockHearing("json/welsh-interpreter/hearing.get.hearing-case.json", hearingId);
        setWelshCourt(true);
        String progressionResponsePayload = getProgressionResponsePayload()
                .replace("\"hearingLanguageNeeds\": \"HEARING_LANGUAGE_NEEDS\"", "\"hearingLanguageNeeds\": \"WELSH\"");
        when(progressionService.getProsecutionCase(any())).thenReturn(stringToJsonObjectConverter.convert(progressionResponsePayload));
        interpreterForWelshActivityHandler.handleWelshInterpreterForCaseInitiated(hearingId);
        verify(runtimeService, never()).startProcessInstanceByKey(anyString(), anyString(), processVariablesCaptor.capture());
        verify(progressionService, times(1)).getProsecutionCase(any());
    }

    @Test
    public void shouldNotCreateInterpreterForWelshActivityForNonWelshCourt() throws IOException {
        reset(taskService, taskQuery);
        final String hearingId = UUID.randomUUID().toString();
        mockHearing("json/welsh-interpreter/hearing.get.hearing-case.json", hearingId);
        setWelshCourt(false);
        interpreterForWelshActivityHandler.handleWelshInterpreterForCaseInitiated(hearingId);
        verify(runtimeService, never()).startProcessInstanceByKey(anyString(), anyString(), processVariablesCaptor.capture());
        verify(progressionService, never()).getProsecutionCase(any());
    }

    @Test
    public void shouldCreateInterpreterForWelshActivityAndAssignToWelshLangUnit() throws IOException {
        final String hearingId = UUID.randomUUID().toString();
        mockHearing("json/welsh-interpreter/hearing.get.hearing-case.json", hearingId);
        mockWorkflowTaskTypes(TASK_NAME_BOOK_WELSH_INTERPRETER_FOR_CASE);
        setWelshCourt(true);
        mockProgressionCall(Map.of("\"interpreterLanguageNeeds\": \"INTERPRETER_LANGUAGE_NEEDS\"", "\"interpreterLanguageNeeds\": \"WELSH\"",
                "\"hearingLanguageNeeds\": \"HEARING_LANGUAGE_NEEDS\"", "\"hearingLanguageNeeds\": \"WELSH\""));
        interpreterForWelshActivityHandler.handleWelshInterpreterForCaseInitiated(hearingId);
        verifyAndAssertResults(WELSH_LANGUAGE_UNIT_WORK_QUEUE, DEEP_LINK_BASE_CAAG.concat(CASE_ID_VALUE), DUE_DATE_VALUE);
    }

    @Test
    public void shouldNotCreateInterpreterForWelshActivity() throws IOException {
        reset(taskService, taskQuery);
        final String hearingId = UUID.randomUUID().toString();
        mockHearing("json/welsh-interpreter/hearing.get.hearing-case.json", hearingId);
        setWelshCourt(true);
        String progressionResponsePayload = getProgressionResponsePayload()
                .replace("\"interpreterLanguageNeeds\": \"INTERPRETER_LANGUAGE_NEEDS\",", "")
                .replace("\"hearingLanguageNeeds\": \"HEARING_LANGUAGE_NEEDS\",", "");
        when(progressionService.getProsecutionCase(any())).thenReturn(stringToJsonObjectConverter.convert(progressionResponsePayload));
        interpreterForWelshActivityHandler.handleWelshInterpreterForCaseInitiated(hearingId);
        verify(runtimeService, never()).startProcessInstanceByKey(anyString(), anyString(), processVariablesCaptor.capture());
    }

    @Test
    public void shouldNotCreateInterpreterForWelshActivityWhenProsecutionCaseIsEmpty() throws IOException {
        reset(taskService, taskQuery);
        final String hearingId = UUID.randomUUID().toString();
        mockHearing("json/welsh-interpreter/hearing.get.hearing-case.json", hearingId);
        setWelshCourt(true);
        String progressionResponsePayload = "{}";
        when(progressionService.getProsecutionCase(any())).thenReturn(stringToJsonObjectConverter.convert(progressionResponsePayload));
        interpreterForWelshActivityHandler.handleWelshInterpreterForCaseInitiated(hearingId);
        verify(runtimeService, never()).startProcessInstanceByKey(anyString(), anyString(), processVariablesCaptor.capture());
    }

    @Test
    public void shouldNotCreateInterpreterForWelshActivityWhenDefendantDetailsIsEmpty() throws IOException {
        reset(taskService, taskQuery);
        final String hearingId = UUID.randomUUID().toString();
        mockHearing("json/welsh-interpreter/hearing.get.hearing-case.json", hearingId);
        setWelshCourt(true);
        String progressionResponsePayload = "{\"prosecutionCase\": {}}";
        when(progressionService.getProsecutionCase(any())).thenReturn(stringToJsonObjectConverter.convert(progressionResponsePayload));
        interpreterForWelshActivityHandler.handleWelshInterpreterForCaseInitiated(hearingId);
        verify(runtimeService, never()).startProcessInstanceByKey(anyString(), anyString(), processVariablesCaptor.capture());
    }

    @Test
    public void shouldNotCreateInterpreterForWelshActivityCaseOne() throws IOException {
        reset(taskService, taskQuery);
        final String hearingId = UUID.randomUUID().toString();
        mockHearing("json/welsh-interpreter/hearing.get.hearing-case.json", hearingId);
        setWelshCourt(true);
        String progressionResponsePayload = getProgressionResponsePayload()
                .replace("\"interpreterLanguageNeeds\": \"INTERPRETER_LANGUAGE_NEEDS\"", "\"interpreterLanguageNeeds\": \"ENGLISH\"")
                .replace("\"hearingLanguageNeeds\": \"HEARING_LANGUAGE_NEEDS\",", "");
        when(progressionService.getProsecutionCase(any())).thenReturn(stringToJsonObjectConverter.convert(progressionResponsePayload));
        interpreterForWelshActivityHandler.handleWelshInterpreterForCaseInitiated(hearingId);
        verify(runtimeService, never()).startProcessInstanceByKey(anyString(), anyString(), processVariablesCaptor.capture());
    }

    @Test
    public void shouldCreateIWForApplicationAssignToWelshLang() throws IOException {
        final String hearingId = UUID.randomUUID().toString();
        mockHearing("json/welsh-interpreter/hearing.get.hearing-application.json", hearingId);
        mockWorkflowTaskTypes(TASK_NAME_BOOK_WELSH_INTERPRETER_FOR_APPLICATION);
        setWelshCourt(true);
        mockProgressionCall(Map.of("\"interpreterLanguageNeeds\": \"INTERPRETER_LANGUAGE_NEEDS\"", "\"interpreterLanguageNeeds\": \"WELSH\""));
        interpreterForWelshActivityHandler.handleWelshInterpreterForApplicationInitiated(hearingId);
        verifyAndAssertResults(WELSH_LANGUAGE_UNIT_WORK_QUEUE, DEEP_LINK_BASE_AAAG.concat(APPLICATION_ID_VALUE), DUE_DATE_VALUE);
    }

    @Test
    public void shouldNotCreateIWForApplicationAssignToWelsh() throws IOException {
        reset(taskService);
        reset(taskQuery);
        final String hearingId = UUID.randomUUID().toString();
        mockHearing("json/welsh-interpreter/hearing.get.hearing-application.json", hearingId);
        setWelshCourt(true);
        mockProgressionCall(Map.of("\"hearingLanguageNeeds\": \"HEARING_LANGUAGE_NEEDS\"", "\"hearingLanguageNeeds\": \"WELSH\""));
        interpreterForWelshActivityHandler.handleWelshInterpreterForApplicationInitiated(hearingId);
        verify(runtimeService, never()).startProcessInstanceByKey(anyString(), anyString(), processVariablesCaptor.capture());
        verify(progressionService, times(1)).getProsecutionCase(any());
    }

    @Test
    public void shouldNotCreateIWForApplicationForNonWelshCourts() throws IOException {
        reset(taskService, taskQuery);
        final String hearingId = UUID.randomUUID().toString();
        mockHearing("json/welsh-interpreter/hearing.get.hearing-application.json", hearingId);
        setWelshCourt(false);
        interpreterForWelshActivityHandler.handleWelshInterpreterForApplicationInitiated(hearingId);
        verify(runtimeService, never()).startProcessInstanceByKey(anyString(), anyString(), processVariablesCaptor.capture());
        verify(progressionService, never()).getProsecutionCase(any());
    }

    @Test
    public void shouldCreateIWForApplicationAssignToWelshLangCase1() throws IOException {
        final String hearingId = UUID.randomUUID().toString();
        mockHearing("json/welsh-interpreter/hearing.get.hearing-application.json", hearingId);
        mockWorkflowTaskTypes(TASK_NAME_BOOK_WELSH_INTERPRETER_FOR_APPLICATION);
        setWelshCourt(true);
        mockProgressionCall(Map.of("\"interpreterLanguageNeeds\": \"INTERPRETER_LANGUAGE_NEEDS\"", "\"interpreterLanguageNeeds\": \"WELSH\"",
                "\"hearingLanguageNeeds\": \"HEARING_LANGUAGE_NEEDS\"", "\"hearingLanguageNeeds\": \"WELSH\""));
        interpreterForWelshActivityHandler.handleWelshInterpreterForApplicationInitiated(hearingId);
        verifyAndAssertResults(WELSH_LANGUAGE_UNIT_WORK_QUEUE, DEEP_LINK_BASE_AAAG.concat(APPLICATION_ID_VALUE), DUE_DATE_VALUE);
    }

    @Test
    public void shouldNotCreateIWForApplication() throws IOException {
        reset(taskService, taskQuery);
        final String hearingId = UUID.randomUUID().toString();
        mockHearing("json/welsh-interpreter/hearing.get.hearing-application.json", hearingId);
        setWelshCourt(true);
        mockProgressionCall(Map.of("\"interpreterLanguageNeeds\": \"INTERPRETER_LANGUAGE_NEEDS\",", "",
                "\"hearingLanguageNeeds\": \"HEARING_LANGUAGE_NEEDS\",", ""));
        interpreterForWelshActivityHandler.handleWelshInterpreterForApplicationInitiated(hearingId);
        verify(runtimeService, never()).startProcessInstanceByKey(anyString(), anyString(), processVariablesCaptor.capture());
    }


    @Test
    public void shouldProcessLinkedApplicationIfCourtApplicationsPresent() throws IOException {
        final String hearingId = UUID.randomUUID().toString();
        mockHearing("json/welsh-interpreter/hearing.get.hearing-application.json", hearingId);
        mockWorkflowTaskTypes(TASK_NAME_BOOK_WELSH_INTERPRETER_FOR_APPLICATION);
        setWelshCourt(true);
        mockProgressionCall(Map.of("\"interpreterLanguageNeeds\": \"INTERPRETER_LANGUAGE_NEEDS\"", "\"interpreterLanguageNeeds\": \"WELSH\""));
        interpreterForWelshActivityHandler.handleWelshInterpreterForHearingUpdated(hearingId);
        verify(runtimeService, times(1)).startProcessInstanceByKey(anyString(), anyString(), processVariablesCaptor.capture());
    }

    @Test
    public void shouldProcessProsecutionCasesIfNoCourtApplicationsPresent() throws IOException {
        final String hearingId = UUID.randomUUID().toString();
        mockHearing("json/welsh-interpreter/hearing.get.hearing-case.json", hearingId);
        mockWorkflowTaskTypes(TASK_NAME_BOOK_WELSH_INTERPRETER_FOR_APPLICATION);
        setWelshCourt(true);
        mockProgressionCall(Map.of("\"interpreterLanguageNeeds\": \"INTERPRETER_LANGUAGE_NEEDS\"", "\"interpreterLanguageNeeds\": \"WELSH\""));
        interpreterForWelshActivityHandler.handleWelshInterpreterForHearingUpdated(hearingId);
        verify(runtimeService, times(1)).startProcessInstanceByKey(anyString(), anyString(), processVariablesCaptor.capture());
    }

    @Test
    public void shouldNotProcessAnythingIfNotWelshCourt() throws IOException {
        reset(taskService, taskQuery);
        final String hearingId = UUID.randomUUID().toString();
        mockHearing("json/welsh-interpreter/hearing.get.hearing-case.json", hearingId);
        setWelshCourt(false);
        interpreterForWelshActivityHandler.handleWelshInterpreterForHearingUpdated(hearingId);
        verify(runtimeService, never()).startProcessInstanceByKey(anyString(), anyString(), processVariablesCaptor.capture());
    }


    private String getProgressionResponsePayload() throws IOException {
        return Resources.toString(getResource(InterpreterForWelshActivityHandlerTest.PROGRESSION_PROSECUTION_CASE), defaultCharset());
    }

    private void verifyAndAssertResults(final String workQueue, final String deepLink, final String dueDate) {
        verify(runtimeService, times(1)).startProcessInstanceByKey(anyString(), anyString(), processVariablesCaptor.capture());
        Map<String, Object> processVariables = processVariablesCaptor.getValue();
        assertThat(processVariables.get(DEEP_LINK), is(deepLink));
        assertThat(processVariables.get(DUE_DATE), is(dueDate));
        assertThat(processVariables.get(WORK_QUEUE), is(workQueue));
    }

    private void mockTaskService(final List<Task> results) {
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.processDefinitionKey(any())).thenReturn(taskQuery);
        when(taskQuery.processInstanceBusinessKey(any())).thenReturn(taskQuery);
        when(taskQuery.active()).thenReturn(taskQuery);
        when(taskQuery.list()).thenReturn(results);
    }

    private void mockProgressionCall(Map<String, String> replacements) throws IOException {
        String progressionResponsePayload = getProgressionResponsePayload();

        progressionResponsePayload = replacements.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getValue() != null)
                .reduce(progressionResponsePayload,
                        (payload, entry) -> payload.replace(entry.getKey(), entry.getValue()),
                        (payload1, payload2) -> payload1);

        when(progressionService.getProsecutionCase(any())).thenReturn(stringToJsonObjectConverter.convert(progressionResponsePayload));
    }

    private void mockHearing(final String path, final String hearingId) throws JsonProcessingException {
        final JsonObject response = getFileContentAsJson(path);
        final Hearing hearing = objectMapper.readValue(response.get("hearing").toString(), Hearing.class);
        when(hearingService.getHearing(hearingId)).thenReturn(hearing);
    }

    private void setWelshCourt(boolean isWelshCourt) {
        when(referenceDataService.isWelshCourt(any())).thenReturn(isWelshCourt);
    }

}