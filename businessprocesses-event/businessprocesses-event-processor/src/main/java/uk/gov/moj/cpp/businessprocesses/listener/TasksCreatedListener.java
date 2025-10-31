package uk.gov.moj.cpp.businessprocesses.listener;

import static java.lang.String.format;
import static java.util.Objects.nonNull;
import static javax.json.Json.createObjectBuilder;
import static org.slf4j.LoggerFactory.getLogger;
import static uk.gov.justice.services.core.annotation.Component.EVENT_PROCESSOR;
import static uk.gov.justice.services.messaging.JsonEnvelope.envelopeFrom;
import static uk.gov.moj.cpp.businessprocesses.listener.Utils.DATE_FORMATTER;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.CHANGE_AUTHOR;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.CHANGE_AUTHOR_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.COURT_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.REFERENCE;
import static uk.gov.moj.cpp.businessprocesses.shared.DateConverter.getUTCZonedDateTimeString;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.CANDIDATE_GROUPS;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.CASE_URN;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.COURT_CODES;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.DUE_DATE;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.HEARING_DATE;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.JURISDICTION;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.LAST_UPDATED_BY_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.LAST_UPDATED_BY_NAME;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.NOTE;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.PREVIOUS_DUE;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.PREVIOUS_WORK_QUEUE;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.REGION;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.TASK_TYPE_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.WORK_QUEUE;
import static uk.gov.moj.cpp.businessprocesses.shared.TaskAuditService.getMetadata;

import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.core.dispatcher.SystemUserProvider;
import uk.gov.justice.services.core.sender.Sender;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.moj.cpp.businessprocesses.service.CourtCentreService;
import uk.gov.moj.cpp.businessprocesses.shared.MandatoryTaskVariablesNotFoundException;
import uk.gov.moj.cpp.businessprocesses.shared.TaskAuditService;

import java.util.List;
import java.util.UUID;

import javax.inject.Inject;
import javax.inject.Named;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

import org.camunda.bpm.engine.delegate.DelegateTask;
import org.camunda.bpm.engine.delegate.TaskListener;
import org.camunda.bpm.engine.impl.persistence.entity.TaskEntity;
import org.camunda.bpm.engine.variable.impl.VariableMapImpl;
import org.slf4j.Logger;

/**
 * Listener that is triggered by the BPMNs when a task is created. Sends the appropriate command to
 * record in the eventstore/viewstore.
 */
@SuppressWarnings("squid:CallToDeprecatedMethod")
@Named("tasksCreatedListener")
public class TasksCreatedListener implements TaskListener {

    private static final Logger LOGGER = getLogger(TasksCreatedListener.class);

    @ServiceComponent(EVENT_PROCESSOR)
    @Inject
    private Sender sender;

    @Inject
    private SystemUserProvider systemUserProvider;

    @Inject
    private TaskAuditService taskAuditService;

    @Inject
    private CourtCentreService courtCentreService;

    @Override
    public void notify(final DelegateTask delegateTask) {
        LOGGER.info("Task with ID '{}' and name '{}' created", delegateTask.getId(), delegateTask.getName());

        final TaskEntity taskEntity = (TaskEntity) delegateTask;

        final String taskId = taskEntity.getId();
        final String createdDate = getUTCZonedDateTimeString(taskEntity.getCreateTime());
        final String taskType = taskEntity.getName();

        final JsonObjectBuilder jsonObjectBuilder = createObjectBuilder()
                .add("id", taskId)
                .add("createdDate", createdDate)
                .add("type", taskType);
        final VariableMapImpl variableMap = taskEntity.getExecution().getVariables();

        if (!validateChangeAuthorDetails(variableMap)) {
            throw new MandatoryTaskVariablesNotFoundException(format("Task Created Change Author Details Not found task: %s ", taskId));
        }

        String lastUpdatedById = variableMap.getValue(LAST_UPDATED_BY_ID, String.class);
        String lastUpdatedByName = variableMap.getValue(LAST_UPDATED_BY_NAME, String.class);

        jsonObjectBuilder.add(CHANGE_AUTHOR_ID, lastUpdatedById);
        jsonObjectBuilder.add(CHANGE_AUTHOR, lastUpdatedByName);

        taskEntity.setVariableLocal(LAST_UPDATED_BY_ID, lastUpdatedById);
        taskEntity.setVariableLocal(LAST_UPDATED_BY_NAME, lastUpdatedByName);

        if (!variableMap.containsKey(TASK_TYPE_ID)) {
            throw new MandatoryTaskVariablesNotFoundException(format("Task Type Id Details Not found task: %s ", taskId));
        }

        final String taskTypeId = variableMap.get(TASK_TYPE_ID).toString();
        jsonObjectBuilder.add(TASK_TYPE_ID, taskTypeId);

        if (taskEntity.getDueDate() != null) {
            final String dateTimeString = getUTCZonedDateTimeString(taskEntity.getDueDate());
            jsonObjectBuilder.add(DUE_DATE, dateTimeString);
            taskEntity.setVariableLocal(PREVIOUS_DUE, DATE_FORMATTER.format(taskEntity.getDueDate()));
        }
        if (variableMap.containsKey(CASE_URN)) {
            final String caseUrn = variableMap.getValue(CASE_URN, String.class);
            jsonObjectBuilder.add(REFERENCE, caseUrn);
        }
        if (variableMap.containsKey(NOTE)) {
            final String workQueue = variableMap.getValue(NOTE, String.class);
            jsonObjectBuilder.add(NOTE, workQueue);
        }
        if (variableMap.containsKey(HEARING_DATE) && variableMap.get(HEARING_DATE) != null) {
            final String hearingDate = variableMap.get(HEARING_DATE).toString();
            jsonObjectBuilder.add(HEARING_DATE, hearingDate);
        }
        if (variableMap.containsKey(WORK_QUEUE)) {
            final String workQueue = variableMap.getValue(WORK_QUEUE, String.class);
            jsonObjectBuilder.add(WORK_QUEUE, workQueue);
            taskEntity.removeVariable(WORK_QUEUE);
            taskEntity.setVariableLocal(WORK_QUEUE, workQueue);
            taskEntity.setVariableLocal(PREVIOUS_WORK_QUEUE, workQueue);
        }
        if (variableMap.containsKey(COURT_ID)) {
            final String courtId = variableMap.getValue(COURT_ID, String.class);
            jsonObjectBuilder.add(COURT_ID, courtId);
        }
        if (variableMap.containsKey(JURISDICTION)) {
            final String jurisdiction = variableMap.getValue(JURISDICTION, String.class);
            jsonObjectBuilder.add(JURISDICTION, jurisdiction);
        }

        if (variableMap.containsKey(CANDIDATE_GROUPS)) {
            final List<String> candidateGroups = variableMap.getValue(CANDIDATE_GROUPS, List.class);
            taskEntity.addCandidateGroups(candidateGroups);
        }

        if (variableMap.containsKey(COURT_CODES)) {
            final String courtCode = variableMap.getValue(COURT_CODES, String.class);
            taskEntity.setVariableLocal(COURT_CODES, courtCode);
            final String region = courtCentreService.getCourtCentreRegionByCourtCode(courtCode);
            if (nonNull(region)) {
                taskEntity.setVariableLocal(REGION, region);
            }
        }

        final JsonObject commandPayload = jsonObjectBuilder.build();

        final String userId = systemUserProvider.getContextSystemUserId().map(UUID::toString).orElse(lastUpdatedById);

        final JsonEnvelope commandEnvelope = envelopeFrom(getMetadata("businessprocesses.command.record-task-created", userId), commandPayload);

        sender.sendAsAdmin(commandEnvelope);
        taskAuditService.audit(commandEnvelope, lastUpdatedById);
    }

    private boolean validateChangeAuthorDetails(final VariableMapImpl variableMap) {
        return variableMap.containsKey(LAST_UPDATED_BY_ID) && variableMap.containsKey(LAST_UPDATED_BY_NAME);
    }
}
