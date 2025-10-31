package uk.gov.moj.cpp.businessprocesses.event;

import static uk.gov.justice.services.core.annotation.Component.EVENT_PROCESSOR;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.BPMN_PROCESS_COTR_CUSTOM_TASK_PROCESS;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.SYSTEM_USER_NAME;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.TASK_NAME_COTR_CUSTOM_ACTIVITY;
import static uk.gov.moj.cpp.businessprocesses.shared.DateConverter.addDaysSkippingWeekends;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.COTR_TASK_VARIABLES_JSON_STRING;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.DUE_DATE;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.LAST_UPDATED_BY_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.LAST_UPDATED_BY_NAME;

import uk.gov.justice.services.common.converter.ObjectToJsonObjectConverter;
import uk.gov.justice.services.common.converter.jackson.ObjectMapperProducer;
import uk.gov.justice.services.core.annotation.FeatureControl;
import uk.gov.justice.services.core.annotation.Handles;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.core.dispatcher.SystemUserProvider;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.moj.cpp.businessprocesses.pojo.WorkflowCOTRTaskType;
import uk.gov.moj.cpp.businessprocesses.service.TaskTypeService;
import uk.gov.moj.cpp.progression.json.schemas.event.CotrTaskRequested;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import javax.inject.Inject;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.bpm.engine.RuntimeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ServiceComponent(EVENT_PROCESSOR)
public class CotrEventsProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(CotrEventsProcessor.class);

    private final ObjectMapper objectMapper = new ObjectMapperProducer().objectMapper();
    private final ObjectToJsonObjectConverter objectToJsonObjectConverter = new ObjectToJsonObjectConverter(objectMapper);
    @Inject
    private RuntimeService runtimeService;
    @Inject
    private TaskTypeService taskTypeService;
    @Inject
    private SystemUserProvider systemUserProvider;

    @Handles("public.progression.cotr-task-requested")
    public void handleCotrTaskRequested(final Envelope<CotrTaskRequested> event) {
        final CotrTaskRequested cotrTaskRequested = event.payload();
        LOGGER.info("Received '{}' event with payload {}", "public.progression.cotr-task-requested", cotrTaskRequested);

        final LocalDate hearingDate = cotrTaskRequested.getHearingDate() != null ? LocalDate.parse(cotrTaskRequested.getHearingDate()) : null;
        final WorkflowCOTRTaskType workflowCOTRTaskType = WorkflowCOTRTaskType.WorkflowCOTRTaskTypeBuilder.aWorkflowCOTRTaskType()
                .withId(UUID.randomUUID())
                .withCotrId(cotrTaskRequested.getCotrId().toString())
                .withTaskName(cotrTaskRequested.getTaskName())
                .withDisplayName(cotrTaskRequested.getTaskName())
                .withCourtCodes(cotrTaskRequested.getCourtCode())
                .withComment(cotrTaskRequested.getComments())
                .withTaskCreationDate(LocalDate.now())
                .withDue(addDaysSkippingWeekends(LocalDate.now(), cotrTaskRequested.getNumberOfDays()).atStartOfDay())
                .withHearingDate(hearingDate)
                .withOrganisationId(cotrTaskRequested.getOrganisationId().toString())
                .withCandidateGroups(cotrTaskRequested.getRoles()) //"Listing Officer,Operational Delivery Admin (Crown),CTSC admin (Mags)"
                .build();
        startCotrTaskProcess(workflowCOTRTaskType);
    }

    private void startCotrTaskProcess(final WorkflowCOTRTaskType workflowCOTRTaskType) {
        final Map<String, Object> processVariables = taskTypeService.getTaskVariablesFromRefData(TASK_NAME_COTR_CUSTOM_ACTIVITY, null);
        processVariables.put(COTR_TASK_VARIABLES_JSON_STRING, objectToJsonObjectConverter.convert(workflowCOTRTaskType).toString());
        final String userId = systemUserProvider.getContextSystemUserId().map(UUID::toString).orElse(null);
        processVariables.put(DUE_DATE, workflowCOTRTaskType.getDue().toLocalDate().toString());
        processVariables.put(LAST_UPDATED_BY_ID, userId);
        processVariables.put(LAST_UPDATED_BY_NAME, SYSTEM_USER_NAME);
        runtimeService.startProcessInstanceByKey(BPMN_PROCESS_COTR_CUSTOM_TASK_PROCESS, processVariables);
        LOGGER.info("COTR Workflow process started for COTR ID {}", workflowCOTRTaskType.getCotrId());
    }
}