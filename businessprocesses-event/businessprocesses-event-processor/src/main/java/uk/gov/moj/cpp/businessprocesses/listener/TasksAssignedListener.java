package uk.gov.moj.cpp.businessprocesses.listener;

import static java.lang.String.format;
import static java.util.Objects.nonNull;
import static javax.json.Json.createObjectBuilder;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.slf4j.LoggerFactory.getLogger;
import static uk.gov.justice.services.core.annotation.Component.EVENT_PROCESSOR;
import static uk.gov.justice.services.messaging.JsonEnvelope.envelopeFrom;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.CHANGE_AUTHOR;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.CHANGE_AUTHOR_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.LAST_UPDATED_BY_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.LAST_UPDATED_BY_NAME;
import static uk.gov.moj.cpp.businessprocesses.shared.TaskAuditService.getMetadata;

import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.core.dispatcher.SystemUserProvider;
import uk.gov.justice.services.core.sender.Sender;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.moj.cpp.businessprocesses.service.UserGroupsService;
import uk.gov.moj.cpp.businessprocesses.shared.MandatoryTaskVariablesNotFoundException;
import uk.gov.moj.cpp.businessprocesses.shared.TaskAuditService;

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
 * Listener that is triggered by the BPMNs when a task is assigned. Sends the appropriate command to
 * record in the eventstore/viewstore.
 */
@SuppressWarnings("squid:CallToDeprecatedMethod")
@Named("tasksAssignedListener")
public class TasksAssignedListener implements TaskListener {

    private static final Logger LOGGER = getLogger(TasksAssignedListener.class);

    @ServiceComponent(EVENT_PROCESSOR)
    @Inject
    private Sender sender;

    @Inject
    private SystemUserProvider systemUserProvider;

    @Inject
    private TaskAuditService taskAuditService;

    @Inject
    private UserGroupsService userGroupsService;

    @Override
    public void notify(final DelegateTask delegateTask) {
        LOGGER.info("Task with ID '{}' and name '{}' assignee '{}' ", delegateTask.getId(), delegateTask.getName(), delegateTask.getAssignee());

        final TaskEntity taskEntity = (TaskEntity) delegateTask;

        final String taskId = taskEntity.getId();
        final String assigneeToId = taskEntity.getAssignee();

        final JsonObjectBuilder jsonObjectBuilder = createObjectBuilder().add("id", taskId);

        final String changeAuthorId = delegateTask.getVariableLocal(LAST_UPDATED_BY_ID).toString();
        final String changeAuthorName = delegateTask.getVariableLocal(LAST_UPDATED_BY_NAME).toString();
        jsonObjectBuilder.add(CHANGE_AUTHOR_ID, changeAuthorId);
        jsonObjectBuilder.add(CHANGE_AUTHOR, changeAuthorName);
        final String userId = systemUserProvider.getContextSystemUserId().map(UUID::toString).orElse(changeAuthorId);

        if (nonNull(assigneeToId) && !EMPTY.equals(assigneeToId)) {
            jsonObjectBuilder.add("assignToId", assigneeToId);
            final String userDetails = userGroupsService.getUserDetails(assigneeToId);
            jsonObjectBuilder.add("assignTo", userDetails);
        }

        if (delegateTask.getVariableLocal(LAST_UPDATED_BY_ID) == null || delegateTask.getVariableLocal(LAST_UPDATED_BY_NAME) == null) {
            throw new MandatoryTaskVariablesNotFoundException(format("Task Created Change Author Details Not found task: %s ", taskId));
        }

        final JsonObject commandPayload = jsonObjectBuilder.build();

        final JsonEnvelope commandEnvelope = envelopeFrom(getMetadata("businessprocesses.command.record-task-assigned", userId), commandPayload);

        sender.sendAsAdmin(commandEnvelope);
        taskAuditService.audit(commandEnvelope, changeAuthorId);
    }

}


