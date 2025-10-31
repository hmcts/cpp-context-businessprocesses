package uk.gov.moj.cpp.businessprocesses.listener;

import static java.lang.String.format;
import static java.sql.Date.valueOf;
import static java.util.Arrays.stream;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static org.slf4j.LoggerFactory.getLogger;
import static uk.gov.justice.services.core.annotation.Component.EVENT_PROCESSOR;
import static uk.gov.justice.services.messaging.JsonEnvelope.envelopeFrom;
import static uk.gov.moj.cpp.businessprocesses.listener.Utils.DATE_FORMATTER;
import static uk.gov.moj.cpp.businessprocesses.shared.DateConverter.getDate;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.CASE_URN;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.COTR_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.COTR_TASK_VARIABLES_JSON_STRING;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.COURT_CODES;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.CUSTODY_TIME_LIMIT;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.CUSTOM_TASK_TYPE;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.DEFENDANTS;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.HEARING_DATE;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.IS_DEFERRABLE;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.IS_DELETABLE;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.LAST_UPDATED_BY_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.NOTE;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.ORGANISATION_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.PREVIOUS_DUE;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.PREVIOUS_WORK_QUEUE;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.REGION;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.WORK_QUEUE;
import static uk.gov.moj.cpp.businessprocesses.shared.TaskCreatedListenerHelper.getJsonObjectBuilder;
import static uk.gov.moj.cpp.businessprocesses.shared.TaskCreatedListenerHelper.getMetadataForRecordTaskCreated;
import static uk.gov.moj.cpp.businessprocesses.shared.TaskCreatedListenerHelper.validateChangeAuthorDetails;

import uk.gov.justice.services.common.converter.ObjectToJsonValueConverter;
import uk.gov.justice.services.common.converter.jackson.ObjectMapperProducer;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.core.dispatcher.SystemUserProvider;
import uk.gov.justice.services.core.sender.Sender;
import uk.gov.moj.cpp.businessprocesses.pojo.WorkflowCOTRTaskType;
import uk.gov.moj.cpp.businessprocesses.service.CourtCentreService;
import uk.gov.moj.cpp.businessprocesses.shared.MandatoryTaskVariablesNotFoundException;
import uk.gov.moj.cpp.businessprocesses.shared.WorkflowTaskTypeMapper;

import java.util.UUID;

import javax.inject.Inject;
import javax.inject.Named;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.delegate.DelegateTask;
import org.camunda.bpm.engine.delegate.TaskListener;
import org.camunda.bpm.engine.impl.persistence.entity.TaskEntity;
import org.camunda.bpm.engine.variable.impl.VariableMapImpl;
import org.slf4j.Logger;

@Named("cotrTaskCreatedListener")
public class CotrTaskCreatedListener implements TaskListener {
    private static final Logger LOGGER = getLogger(CotrTaskCreatedListener.class);
    private static final String COMMA = ",";

    private final ObjectMapper objectMapper = new ObjectMapperProducer().objectMapper();
    private final ObjectToJsonValueConverter objectToJsonValueConverter = new ObjectToJsonValueConverter(objectMapper);

    @Inject
    private TaskService tService;

    @Inject
    private SystemUserProvider systemUserProvider;

    @Inject
    private CourtCentreService courtCentreService;

    @ServiceComponent(EVENT_PROCESSOR)
    @Inject
    private Sender sender;

    @Override
    @SuppressWarnings({"squid:CallToDeprecatedMethod"})
    public void notify(final DelegateTask delegateTask) {
        LOGGER.info("Task with ID '{}'  and name '{}' created", delegateTask.getId(), delegateTask.getName());

        final TaskEntity taskEntity = (TaskEntity) delegateTask;
        updateTaskEntityForCreateTask(taskEntity);

        final VariableMapImpl variableMap = taskEntity.getExecution().getVariables();
        if (!validateChangeAuthorDetails(variableMap)) {
            throw new MandatoryTaskVariablesNotFoundException(format("Task Created Change Author Details Not found task: %s ", taskEntity.getId()));
        }

        sender.sendAsAdmin(envelopeFrom(getMetadataForRecordTaskCreated(systemUserProvider.getContextSystemUserId().map(UUID::toString)
                        .orElse(variableMap.getValue(LAST_UPDATED_BY_ID, String.class))),
                getJsonObjectBuilder(variableMap, taskEntity).build()));
    }

    private void updateTaskEntityForCreateTask(final TaskEntity taskEntity) {

        final String taskVariablesJsonString = (String) taskEntity.getVariable(COTR_TASK_VARIABLES_JSON_STRING);
        final WorkflowCOTRTaskType workflowCOTRTaskType = WorkflowTaskTypeMapper.mapToWorkflowCOTRTaskType(taskVariablesJsonString);
        if (nonNull(workflowCOTRTaskType)) {
            if (nonNull(workflowCOTRTaskType.getDisplayName())) {
                taskEntity.setName(workflowCOTRTaskType.getDisplayName());
            }

            if (nonNull(workflowCOTRTaskType.getTaskName())) {
                taskEntity.setName(workflowCOTRTaskType.getTaskName());
            }

            if (nonNull(workflowCOTRTaskType.getAssignee())) {
                taskEntity.setAssignee(workflowCOTRTaskType.getAssignee());
            }

            if (nonNull(workflowCOTRTaskType.getDue())) {
                taskEntity.setDueDate(getDate(workflowCOTRTaskType.getDue()));
                taskEntity.setVariableLocal(PREVIOUS_DUE, DATE_FORMATTER.format(taskEntity.getDueDate()));
            }

            if (nonNull(workflowCOTRTaskType.getTaskCreationDate())) {
                taskEntity.setCreateTime(valueOf(workflowCOTRTaskType.getTaskCreationDate()));
            }

            if (nonNull(workflowCOTRTaskType.getCandidateGroups())) {
                taskEntity.addCandidateGroups(stream(workflowCOTRTaskType.getCandidateGroups().split(COMMA)).map(String::trim).toList());
            }
            updateTaskLocalVariables(taskEntity, workflowCOTRTaskType);
        }
    }

    @SuppressWarnings("squid:MethodCyclomaticComplexity")
    private void updateTaskLocalVariables(final TaskEntity taskEntity, final WorkflowCOTRTaskType workflowCOTRTaskType) {
        if (nonNull(workflowCOTRTaskType.getTaskName())) {
            taskEntity.setVariableLocal(CUSTOM_TASK_TYPE, workflowCOTRTaskType.getTaskName());
        }
        if (nonNull(workflowCOTRTaskType.getDeletable())) {
            taskEntity.setVariableLocal(IS_DELETABLE, workflowCOTRTaskType.getDeletable());
        }
        if (nonNull(workflowCOTRTaskType.getDeferrable())) {
            taskEntity.setVariableLocal(IS_DEFERRABLE, workflowCOTRTaskType.getDeferrable());
        }
        if (nonNull(workflowCOTRTaskType.getCaseURN())) {
            taskEntity.setVariableLocal(CASE_URN, workflowCOTRTaskType.getCaseURN());
        }
        if (nonNull(workflowCOTRTaskType.getNote())) {
            taskEntity.setVariableLocal(NOTE, workflowCOTRTaskType.getNote());
        }
        if (nonNull(workflowCOTRTaskType.getCotrId())) {
            taskEntity.setVariableLocal(COTR_ID, workflowCOTRTaskType.getCotrId());
        }
        if (nonNull(workflowCOTRTaskType.getHearingDate())) {
            taskEntity.setVariableLocal(HEARING_DATE, valueOf(workflowCOTRTaskType.getHearingDate()));
        }
        if (nonNull(workflowCOTRTaskType.getCustodyTimeLimit())) {
            taskEntity.setVariableLocal(CUSTODY_TIME_LIMIT, valueOf(workflowCOTRTaskType.getCustodyTimeLimit()));
        }
        if (nonNull(workflowCOTRTaskType.getCourtCodes())) {
            taskEntity.setVariableLocal(COURT_CODES, workflowCOTRTaskType.getCourtCodes());
            final String region = courtCentreService.getCourtCentreRegionByCourtCode(workflowCOTRTaskType.getCourtCodes());
            if (nonNull(region)) {
                taskEntity.setVariableLocal(REGION, region);
            }
        }

        if (nonNull(workflowCOTRTaskType.getOrganisationId())) {
            taskEntity.setVariableLocal(ORGANISATION_ID, workflowCOTRTaskType.getOrganisationId());
        }
        if (nonNull(workflowCOTRTaskType.getDefendants())) {
            taskEntity.setVariableLocal(DEFENDANTS, objectToJsonValueConverter.convert(workflowCOTRTaskType.getDefendants()).toString());
        }

        if (isNotEmpty(workflowCOTRTaskType.getComment())) {
            tService.createComment(taskEntity.getId(), taskEntity.getProcessInstanceId(), workflowCOTRTaskType.getComment());
        }

        if (nonNull(workflowCOTRTaskType.getWorkQueueId())) {
            taskEntity.setVariableLocal(WORK_QUEUE, workflowCOTRTaskType.getWorkQueueId());
            taskEntity.setVariableLocal(PREVIOUS_WORK_QUEUE, workflowCOTRTaskType.getWorkQueueId());
        }
    }
}
