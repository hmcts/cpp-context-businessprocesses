package uk.gov.moj.cpp.businessprocesses.listener;

import org.camunda.bpm.engine.delegate.DelegateTask;
import org.camunda.bpm.engine.delegate.TaskListener;
import org.camunda.bpm.engine.impl.persistence.entity.TaskEntity;
import org.camunda.bpm.engine.variable.VariableMap;
import org.slf4j.Logger;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.core.dispatcher.SystemUserProvider;
import uk.gov.justice.services.core.sender.Sender;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.moj.cpp.businessprocesses.shared.MandatoryTaskVariablesNotFoundException;
import uk.gov.moj.cpp.businessprocesses.shared.TaskAuditService;

import javax.inject.Inject;
import javax.inject.Named;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import java.text.SimpleDateFormat;
import java.util.UUID;

import static java.lang.String.format;
import static java.util.Objects.nonNull;
import static javax.json.Json.createObjectBuilder;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static org.slf4j.LoggerFactory.getLogger;
import static uk.gov.justice.services.core.annotation.Component.EVENT_PROCESSOR;
import static uk.gov.justice.services.messaging.JsonEnvelope.envelopeFrom;
import static uk.gov.moj.cpp.businessprocesses.listener.Utils.DATE_FORMATTER;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.CHANGE_AUTHOR;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.CHANGE_AUTHOR_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.DateConverter.getUTCZonedDateTimeString;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.DUE_DATE;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.LAST_UPDATED_BY_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.LAST_UPDATED_BY_NAME;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.PREVIOUS_DUE;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.PREVIOUS_WORK_QUEUE;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.WORK_QUEUE;
import static uk.gov.moj.cpp.businessprocesses.shared.TaskAuditService.getMetadata;

@SuppressWarnings("squid:CallToDeprecatedMethod")
@Named("tasksUpdatedListener")
public class TasksUpdatedListener implements TaskListener {

    @Inject
    private TaskAuditService taskAuditService;
    private static final String TASK_UPDATED_COMMAND_NAME = "businessprocesses.command.record-task-updated";

    @Inject
    private SystemUserProvider systemUserProvider;

    @ServiceComponent(EVENT_PROCESSOR)
    @Inject
    private Sender sender;

    private static final Logger LOGGER = getLogger(TasksUpdatedListener.class);

    @Override
    public void notify(final DelegateTask updatedTask) {
        LOGGER.info("Task with ID '{}' and name '{}' updated", updatedTask.getId(), updatedTask.getName());

        final TaskEntity taskEntity = (TaskEntity) updatedTask;
        final VariableMap variableMapLocal = taskEntity.getVariablesLocal();
        final String taskId = taskEntity.getId();

        if (isDueDateChanged(taskEntity, variableMapLocal) || isWorkQueueChanged(variableMapLocal)) {
            LOGGER.debug("Task with ID '{}' and name '{}' due date or workQueue changed, skipping due: {} previousDue: {} workQueue: {} previousWorkQueue: {}",
                    updatedTask.getId(), updatedTask.getName(),
                    taskEntity.getDueDate(), variableMapLocal.getValue(PREVIOUS_DUE, String.class),
                    variableMapLocal.getValue(WORK_QUEUE, String.class), variableMapLocal.getValue(PREVIOUS_WORK_QUEUE, String.class));

            if (!validateChangeAuthorDetails(variableMapLocal)) {
                throw new MandatoryTaskVariablesNotFoundException(format("Task Updated Change Author Details Not found for taskId : %s ", updatedTask.getId()));
            }

            final JsonObjectBuilder jsonObjectBuilder = createObjectBuilder()
                    .add("id", taskId);

            final String changeAuthorName = updatedTask.getVariableLocal(LAST_UPDATED_BY_NAME).toString();
            final String changeAuthorId = updatedTask.getVariableLocal(LAST_UPDATED_BY_ID).toString();

            jsonObjectBuilder.add(CHANGE_AUTHOR_ID, changeAuthorId);
            jsonObjectBuilder.add(CHANGE_AUTHOR, changeAuthorName);

            if (taskEntity.getDueDate() != null) {
                final String dateTimeString = getUTCZonedDateTimeString(taskEntity.getDueDate());
                jsonObjectBuilder.add(DUE_DATE, dateTimeString);
            }

            if (variableMapLocal.containsKey(WORK_QUEUE)) {
                jsonObjectBuilder.add(WORK_QUEUE, variableMapLocal.getValue(WORK_QUEUE, String.class));
            }

            final JsonObject commandPayload = jsonObjectBuilder.build();
            final String userId = systemUserProvider.getContextSystemUserId().map(UUID::toString).orElse(changeAuthorId);
            final JsonEnvelope commandEnvelope = envelopeFrom(getMetadata(TASK_UPDATED_COMMAND_NAME, userId), commandPayload);

            sender.sendAsAdmin(commandEnvelope);
            taskAuditService.audit(commandEnvelope, changeAuthorId);
        } else {
            LOGGER.debug("Task with ID '{}' and name '{}' due date / workQueue not changed, skipping update listener. due: {} previousDue: {} workQueue: {} previousWorkQueue: {}",
                    updatedTask.getId(), updatedTask.getName(),
                    taskEntity.getDueDate(), variableMapLocal.getValue(PREVIOUS_DUE, String.class),
                    variableMapLocal.getValue(WORK_QUEUE, String.class), variableMapLocal.getValue(PREVIOUS_WORK_QUEUE, String.class));
        }
    }

    private boolean validateChangeAuthorDetails(final VariableMap localVariables) {
        return localVariables.containsKey(LAST_UPDATED_BY_ID) && localVariables.containsKey(LAST_UPDATED_BY_NAME);
    }

    private boolean isDueDateChanged(final TaskEntity taskEntity, final VariableMap variableMap) {
        final String previousDue = nonNull(variableMap.getValue(PREVIOUS_DUE, String.class)) ? variableMap.getValue(PREVIOUS_DUE, String.class) : EMPTY;
        final String due = nonNull(taskEntity.getDueDate()) ? DATE_FORMATTER.format(taskEntity.getDueDate()) : EMPTY;
        return isNotEmpty(due) && !due.equals(previousDue) || isEmpty(due) && isNotEmpty(previousDue);
    }

    private boolean isWorkQueueChanged(final VariableMap variableMap) {
        final String previousWorkQueue = nonNull(variableMap.getValue(PREVIOUS_WORK_QUEUE, String.class)) ? variableMap.getValue(PREVIOUS_WORK_QUEUE, String.class) : EMPTY;
        final String workQueue = nonNull(variableMap.getValue(WORK_QUEUE, String.class)) ? variableMap.getValue(WORK_QUEUE, String.class) : EMPTY;
        return isNotEmpty(workQueue) && !workQueue.equals(previousWorkQueue) || isEmpty(workQueue) && isNotEmpty(previousWorkQueue);
    }
}
