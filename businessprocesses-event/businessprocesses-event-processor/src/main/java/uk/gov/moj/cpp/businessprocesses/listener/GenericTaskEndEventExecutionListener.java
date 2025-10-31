package uk.gov.moj.cpp.businessprocesses.listener;

import static org.slf4j.LoggerFactory.getLogger;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.GENERIC_TASK_VARIABLES_JSON_STRING;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.TASK_VARIABLES_JSON_STRING;

import uk.gov.justice.services.common.converter.jackson.ObjectMapperProducer;
import uk.gov.moj.cpp.businessprocesses.create.CreateGenericTask;

import javax.inject.Named;
import javax.json.JsonObject;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.ExecutionListener;
import org.slf4j.Logger;

@Named("genericTaskEndEventExecutionListener")
public class GenericTaskEndEventExecutionListener implements ExecutionListener {
    private static final Logger LOGGER = getLogger(GenericTaskEndEventExecutionListener.class);
    public static final String TASK_TITLE = "taskTitle";


    private final ObjectMapper objectMapper = new ObjectMapperProducer().objectMapper();

    @Override
    public void notify(final DelegateExecution delegateExecution) throws Exception {
        LOGGER.info("Generic Task with ID '{}' and activity name '{}' ", delegateExecution.getId(), delegateExecution.getCurrentActivityName());
        final CreateGenericTask createGenericTask = objectMapper.convertValue(delegateExecution.getVariable(GENERIC_TASK_VARIABLES_JSON_STRING), CreateGenericTask.class);
        final JsonObject taskVariablesPayloadJsonObject = objectMapper.convertValue(createGenericTask, JsonObject.class);
        delegateExecution.setVariable(TASK_VARIABLES_JSON_STRING, taskVariablesPayloadJsonObject.toString());
        delegateExecution.setVariable(TASK_TITLE, createGenericTask.getTaskTitle());
    }
}
