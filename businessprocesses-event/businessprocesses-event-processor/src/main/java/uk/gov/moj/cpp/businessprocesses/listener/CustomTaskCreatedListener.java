package uk.gov.moj.cpp.businessprocesses.listener;

import static java.lang.String.format;
import static java.sql.Date.valueOf;
import static java.util.Arrays.stream;
import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static org.slf4j.LoggerFactory.getLogger;
import static uk.gov.justice.services.core.annotation.Component.EVENT_PROCESSOR;
import static uk.gov.justice.services.messaging.JsonEnvelope.envelopeFrom;
import static uk.gov.moj.cpp.businessprocesses.listener.Utils.DATE_FORMATTER;
import static uk.gov.moj.cpp.businessprocesses.shared.DateConverter.getDate;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.BUSINESS_UNIT_CODES;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.CASE_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.CASE_TAG;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.CASE_URN;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.COURT_CODES;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.CUSTODY_TIME_LIMIT;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.CUSTOM_TASK_TYPE;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.DEEP_LINK;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.DEFENDANTS;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.HEARING_DATE;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.HEARING_TYPE;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.IS_DEFERRABLE;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.IS_DELETABLE;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.IS_URGENT;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.LAST_UPDATED_BY_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.NOTE;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.ORGANISATION_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.PREVIOUS_DUE;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.PREVIOUS_WORK_QUEUE;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.REGION;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.TASK_TYPE;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.TASK_TYPE_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.WORK_QUEUE;
import static uk.gov.moj.cpp.businessprocesses.shared.TaskCreatedListenerHelper.getJsonObjectBuilder;
import static uk.gov.moj.cpp.businessprocesses.shared.TaskCreatedListenerHelper.getMetadataForRecordTaskCreated;
import static uk.gov.moj.cpp.businessprocesses.shared.TaskCreatedListenerHelper.validateChangeAuthorDetails;

import uk.gov.justice.services.common.converter.ObjectToJsonValueConverter;
import uk.gov.justice.services.common.converter.jackson.ObjectMapperProducer;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.core.dispatcher.SystemUserProvider;
import uk.gov.justice.services.core.sender.Sender;
import uk.gov.moj.cpp.businessprocesses.pojo.WorkflowCustomTaskType;
import uk.gov.moj.cpp.businessprocesses.pojo.WorkflowTaskType;
import uk.gov.moj.cpp.businessprocesses.service.CourtCentreService;
import uk.gov.moj.cpp.businessprocesses.service.DueDateCalculationService;
import uk.gov.moj.cpp.businessprocesses.service.ReferenceDataService;
import uk.gov.moj.cpp.businessprocesses.shared.MandatoryTaskVariablesNotFoundException;
import uk.gov.moj.cpp.businessprocesses.shared.WorkflowTaskTypeMapper;

import java.time.ZoneId;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import javax.inject.Inject;
import javax.inject.Named;
import javax.json.JsonObject;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.bpm.engine.delegate.DelegateTask;
import org.camunda.bpm.engine.delegate.TaskListener;
import org.camunda.bpm.engine.impl.persistence.entity.TaskEntity;
import org.camunda.bpm.engine.variable.impl.VariableMapImpl;
import org.slf4j.Logger;

@SuppressWarnings({"squid:CallToDeprecatedMethod", "squid:S2885"})
@Named("customTaskCreatedListener")
public class CustomTaskCreatedListener implements TaskListener {
    private static final Logger LOGGER = getLogger(CustomTaskCreatedListener.class);
    private static final String COMMA = ",";
    private final ObjectMapper objectMapper = new ObjectMapperProducer().objectMapper();
    private final ObjectToJsonValueConverter objectToJsonValueConverter = new ObjectToJsonValueConverter(objectMapper);

    private static final String DEFAULT_CALCULATION_FORMULA = "${now()+2d}";

    @Inject
    private ReferenceDataService referenceDataService;

    @Inject
    private CourtCentreService courtCentreService;

    @Inject
    private DueDateCalculationService dueDateCalculationService;

    @Inject
    private SystemUserProvider systemUserProvider;


    @ServiceComponent(EVENT_PROCESSOR)
    @Inject
    private Sender sender;

    @Override
    @SuppressWarnings("squid:MethodCyclomaticComplexity")
    public void notify(final DelegateTask delegateTask) {
        LOGGER.info("Task with ID '{}' and name '{}' created", delegateTask.getId(), delegateTask.getName());

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

        final String taskVariablesJsonString = (String) taskEntity.getVariable("taskVariablesJsonString");
        final WorkflowCustomTaskType workflowCustomTaskType = WorkflowTaskTypeMapper.mapToWorkflowCustomTaskType(taskVariablesJsonString);
        if (nonNull(workflowCustomTaskType)) {
            if (nonNull(workflowCustomTaskType.getDisplayName())) {
                taskEntity.setName(workflowCustomTaskType.getDisplayName());
            }

            if (nonNull(workflowCustomTaskType.getFollowUp())) {
                taskEntity.setFollowUpDate(valueOf(workflowCustomTaskType.getFollowUp()));
            }

            if (nonNull(workflowCustomTaskType.getCandidateGroups())) {
                taskEntity.addCandidateGroups(stream(workflowCustomTaskType.getCandidateGroups().split(COMMA)).map(String::trim).collect(toList()));
            }
            updateTaskLocalVariables(taskEntity, workflowCustomTaskType);
            updateTaskLocalVariablesExtended(taskEntity, workflowCustomTaskType);
            updateTaskLocalVariablesWithWorkQueueAndDeepLink(taskEntity, workflowCustomTaskType);
        }
    }

    @SuppressWarnings("squid:MethodCyclomaticComplexity")
    private void updateTaskLocalVariables(final TaskEntity taskEntity, final WorkflowCustomTaskType workflowCustomTaskType) {
        if (nonNull(workflowCustomTaskType.getTaskName())) {
            taskEntity.setVariableLocal(CUSTOM_TASK_TYPE, workflowCustomTaskType.getTaskName());
        }
        if (nonNull(workflowCustomTaskType.getTaskType())) {
            taskEntity.setVariableLocal(TASK_TYPE, workflowCustomTaskType.getTaskType());
        }
        if (nonNull(workflowCustomTaskType.getDeletable())) {
            taskEntity.setVariableLocal(IS_DELETABLE, workflowCustomTaskType.getDeletable());
        }
        if (nonNull(workflowCustomTaskType.getDeferrable())) {
            taskEntity.setVariableLocal(IS_DEFERRABLE, workflowCustomTaskType.getDeferrable());
        }
        if (nonNull(workflowCustomTaskType.getUrgent())) {
            taskEntity.setVariableLocal(IS_URGENT, workflowCustomTaskType.getUrgent());
        }
        if (nonNull(workflowCustomTaskType.getCaseURN())) {
            taskEntity.setVariableLocal(CASE_URN, workflowCustomTaskType.getCaseURN());
        }
        if (nonNull(workflowCustomTaskType.getCaseId())) {
            taskEntity.setVariableLocal(CASE_ID, workflowCustomTaskType.getCaseId());
        }
        if (nonNull(workflowCustomTaskType.getHearingType())) {
            taskEntity.setVariableLocal(HEARING_TYPE, workflowCustomTaskType.getHearingType());
        }
        if (nonNull(workflowCustomTaskType.getHearingDate())) {
            taskEntity.setVariableLocal(HEARING_DATE, valueOf(workflowCustomTaskType.getHearingDate()));
        }
        if (nonNull(workflowCustomTaskType.getCustodyTimeLimit())) {
            taskEntity.setVariableLocal(CUSTODY_TIME_LIMIT, valueOf(workflowCustomTaskType.getCustodyTimeLimit()));
        }
    }

    @SuppressWarnings("squid:MethodCyclomaticComplexity")
    private void updateTaskLocalVariablesExtended(final TaskEntity taskEntity, final WorkflowCustomTaskType workflowCustomTaskType) {
        if (nonNull(workflowCustomTaskType.getCourtCodes())) {
            taskEntity.setVariableLocal(COURT_CODES, workflowCustomTaskType.getCourtCodes());
            final String region = courtCentreService.getCourtCentreRegionByCourtCode(workflowCustomTaskType.getCourtCodes());
            if (nonNull(region)) {
                taskEntity.setVariableLocal(REGION, region);
            }
        }
        if (nonNull(workflowCustomTaskType.getBusinessUnitCodes())) {
            taskEntity.setVariableLocal(BUSINESS_UNIT_CODES, workflowCustomTaskType.getBusinessUnitCodes());
        }

        if (nonNull(workflowCustomTaskType.getOrganisationId())) {
            taskEntity.setVariableLocal(ORGANISATION_ID, workflowCustomTaskType.getOrganisationId());
        }
        if (nonNull(workflowCustomTaskType.getDefendants())) {
            taskEntity.setVariableLocal(DEFENDANTS, objectToJsonValueConverter.convert(workflowCustomTaskType.getDefendants()).toString());
        }
        if (nonNull(workflowCustomTaskType.getNote())) {
            taskEntity.setVariableLocal(NOTE, workflowCustomTaskType.getNote());
        }
        if (nonNull(workflowCustomTaskType.getCaseTag())) {
            taskEntity.setVariableLocal(CASE_TAG, workflowCustomTaskType.getCaseTag());
        }
    }

    private void updateTaskLocalVariablesWithWorkQueueAndDeepLink(final TaskEntity taskEntity, final WorkflowCustomTaskType workflowCustomTaskType) {
        if (workflowCustomTaskType.getTaskName() != null) {
            final Object object = referenceDataService.getWorkflowTaskType(workflowCustomTaskType.getTaskName());
            final Optional<WorkflowTaskType> optionalWorkflowTaskType = nonNull(object) ? (Optional<WorkflowTaskType>) object : Optional.empty();
            if (optionalWorkflowTaskType.isPresent()) {
                final WorkflowTaskType workflowTaskType = optionalWorkflowTaskType.get();
                if (nonNull(workflowTaskType.getWorkQueueId())) {
                    taskEntity.setVariableLocal(WORK_QUEUE, workflowTaskType.getWorkQueueId());
                    taskEntity.setVariableLocal(PREVIOUS_WORK_QUEUE, workflowTaskType.getWorkQueueId());
                }
                if (isNotEmpty(workflowTaskType.getDeepLink()) && isNotEmpty(workflowTaskType.getCaseId())) {
                    taskEntity.setVariableLocal(DEEP_LINK, workflowTaskType.getDeepLink() + workflowCustomTaskType.getCaseId());
                }

                setDueDate(workflowTaskType, taskEntity, workflowCustomTaskType);
                taskEntity.setVariableLocal(PREVIOUS_DUE, DATE_FORMATTER.format(taskEntity.getDueDate()));
                setTaskTypeId(taskEntity, workflowTaskType);
            }
        }
    }

    private static void setTaskTypeId(final TaskEntity taskEntity, final WorkflowTaskType workflowTaskType) {
        if (nonNull(workflowTaskType.getId())) {
            taskEntity.setVariableLocal(TASK_TYPE_ID, workflowTaskType.getId());
        }
    }

    private void setDueDate(final WorkflowTaskType workflowTaskType, final TaskEntity taskEntity, final WorkflowCustomTaskType workflowCustomTaskType) {

        if (nonNull(workflowCustomTaskType.getDue())) {
            taskEntity.setDueDate(getDate(workflowCustomTaskType.getDue()));
            return;
        }

        final String dueDateCalculation = workflowTaskType.getDueDateCalc();
        if (nonNull(dueDateCalculation)) {
            if (dueDateCalculation.contains(HEARING_DATE)) {
                if (workflowCustomTaskType.getHearingDate() != null) {
                    final ZoneId defaultZoneId = ZoneId.systemDefault();
                    final Date date = Date.from(workflowCustomTaskType.getHearingDate().atStartOfDay(defaultZoneId).toInstant());
                    taskEntity.setDueDate(dueDateCalculationService.getCalculatedDate(dueDateCalculation, DATE_FORMATTER.format(date)));
                } else {
                    taskEntity.setDueDate(dueDateCalculationService.getCalculatedDate(DEFAULT_CALCULATION_FORMULA, null));
                }
            } else {
                taskEntity.setDueDate(dueDateCalculationService.getCalculatedDate(dueDateCalculation, null));
            }
        }
    }
}
