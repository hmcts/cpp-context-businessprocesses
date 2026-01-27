package uk.gov.moj.cpp.businessprocesses.listener;

import static java.lang.String.format;
import static java.util.Objects.nonNull;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.slf4j.LoggerFactory.getLogger;
import static uk.gov.justice.services.core.annotation.Component.EVENT_PROCESSOR;
import static uk.gov.justice.services.messaging.JsonEnvelope.envelopeFrom;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.CHANGE_AUTHOR;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.CHANGE_AUTHOR_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.COMPLETED_DATE;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.DELETED_DATE;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.DELETION_REASON;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.LAST_UPDATED_BY_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.LAST_UPDATED_BY_NAME;
import static uk.gov.moj.cpp.businessprocesses.shared.TaskAuditService.getMetadata;

import uk.gov.justice.services.common.util.UtcClock;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.core.dispatcher.SystemUserProvider;
import uk.gov.justice.services.core.sender.Sender;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.moj.cpp.businessprocesses.shared.Constants;
import uk.gov.moj.cpp.businessprocesses.shared.MandatoryTaskVariablesNotFoundException;
import uk.gov.moj.cpp.businessprocesses.shared.TaskAuditService;

import java.time.format.DateTimeFormatter;
import java.util.UUID;

import javax.inject.Inject;
import javax.inject.Named;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

import org.camunda.bpm.engine.delegate.DelegateTask;
import org.camunda.bpm.engine.delegate.TaskListener;
import org.camunda.bpm.engine.impl.persistence.entity.TaskEntity;
import org.slf4j.Logger;

/**
 * Listener that is triggered by the BPMNs when a task is either Completed or Deleted/Cancelled.
 * Sends the appropriate command to record in the eventstore/viewstore.
 * <p>
 * If a deletionReason has been set against the task, the task is deemed to have been DELETED, if it
 * is not present then the task was COMPLETED successfully.
 */
@SuppressWarnings("squid:CallToDeprecatedMethod")
@Named("tasksCompletedListener")
public class TasksCompletedListener implements TaskListener {

    private static final Logger LOGGER = getLogger(TasksCompletedListener.class);

    @ServiceComponent(EVENT_PROCESSOR)
    @Inject
    private Sender sender;

    @Inject
    private UtcClock utcClock;

    @Inject
    private SystemUserProvider systemUserProvider;

    @Inject
    private TaskAuditService taskAuditService;

    @Override
    public void notify(final DelegateTask delegateTask) {
        LOGGER.info("Task with ID '{}' and name '{}' completed", delegateTask.getId(), delegateTask.getName());

        final TaskEntity taskEntity = (TaskEntity) delegateTask;

        final String taskId = taskEntity.getId();

        final JsonObjectBuilder jsonObjectBuilder = createObjectBuilder()
                .add("id", taskId);

        if (delegateTask.getVariableLocal(LAST_UPDATED_BY_ID) == null || delegateTask.getVariableLocal(LAST_UPDATED_BY_NAME) == null) {
            throw new MandatoryTaskVariablesNotFoundException(format("Task complete Change Author Details Not found task: %s ", taskId));
        }

        final String changeAuthorId = delegateTask.getVariableLocal(LAST_UPDATED_BY_ID).toString();
        final String changeAuthorName = delegateTask.getVariableLocal(LAST_UPDATED_BY_NAME).toString();
        jsonObjectBuilder.add(CHANGE_AUTHOR_ID, changeAuthorId);
        jsonObjectBuilder.add(CHANGE_AUTHOR, changeAuthorName);

        final String deletionReason = getTaskDeletionReason(taskEntity);
        final boolean isDeleted = nonNull(deletionReason) && isNotBlank(deletionReason);

        final String completedDate = new UtcClock().now().format(DateTimeFormatter.ISO_ZONED_DATE_TIME);
        jsonObjectBuilder.add(isDeleted ? DELETED_DATE : COMPLETED_DATE, completedDate);

        if (isDeleted) {
            jsonObjectBuilder.add(Constants.DELETION_REASON, deletionReason);
        }

        final JsonObject commandPayload = jsonObjectBuilder.build();

        final String userId = systemUserProvider.getContextSystemUserId().map(UUID::toString).orElse(changeAuthorId);

        final String commandHandlerName = isDeleted ? "businessprocesses.command.record-task-deleted" : "businessprocesses.command.record-task-completed";

        final JsonEnvelope commandEnvelope = envelopeFrom(getMetadata(commandHandlerName, userId), commandPayload);

        sender.sendAsAdmin(commandEnvelope);
        taskAuditService.audit(commandEnvelope, changeAuthorId);
    }

    private String getTaskDeletionReason(final TaskEntity taskEntity) {
        return taskEntity.getVariables().getValue(DELETION_REASON, String.class);
    }

}
