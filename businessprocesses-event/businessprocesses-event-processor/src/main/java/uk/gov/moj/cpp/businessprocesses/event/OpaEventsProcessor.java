package uk.gov.moj.cpp.businessprocesses.event;


import static uk.gov.justice.services.core.annotation.Component.EVENT_PROCESSOR;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.BPMN_PROCESS_OPA_CUSTOM_TASK_PROCESS;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.SYSTEM_USER_NAME;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.TASK_NAME_OPA_CUSTOM_ACTIVITY;
import static uk.gov.moj.cpp.businessprocesses.shared.DateConverter.addDaysSkippingWeekends;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.DUE_DATE;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.LAST_UPDATED_BY_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.LAST_UPDATED_BY_NAME;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.OPA_TASK_VARIABLES_JSON_STRING;

import uk.gov.justice.services.common.converter.ObjectToJsonObjectConverter;
import uk.gov.justice.services.common.converter.jackson.ObjectMapperProducer;
import uk.gov.justice.services.core.annotation.FeatureControl;
import uk.gov.justice.services.core.annotation.Handles;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.core.dispatcher.SystemUserProvider;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.moj.cpp.businessprocesses.pojo.WorkflowOpaTaskType;
import uk.gov.moj.cpp.businessprocesses.service.TaskTypeService;
import uk.gov.moj.cpp.progression.json.schemas.event.OpaTaskRequested;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import javax.inject.Inject;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.bpm.engine.RuntimeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ServiceComponent(EVENT_PROCESSOR)
public class OpaEventsProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpaEventsProcessor.class);

    private final ObjectMapper objectMapper = new ObjectMapperProducer().objectMapper();
    private final ObjectToJsonObjectConverter objectToJsonObjectConverter = new ObjectToJsonObjectConverter(objectMapper);

    @Inject
    private RuntimeService runtimeService;
    @Inject
    private TaskTypeService taskTypeService;
    @Inject
    private SystemUserProvider systemUserProvider;

    @Handles("public.defence.opa-task-requested")
    public void handleOpaTaskRequestEvent(final Envelope<OpaTaskRequested> event) {
        final OpaTaskRequested opaTaskRequested = event.payload();
        LOGGER.info("Received '{}' event with payload {}", "public.defence.opa-task-requested", opaTaskRequested);

        final WorkflowOpaTaskType workflowOpaTaskType = WorkflowOpaTaskType.WorkflowOpaTaskTypeBuilder.aWorkflowOpaTaskType()
                .withId(UUID.randomUUID())
                .withOpaId(opaTaskRequested.getOpaId().toString())
                .withTaskName(opaTaskRequested.getTaskName())
                .withDisplayName(opaTaskRequested.getTaskName())
                .withCourtCodes(opaTaskRequested.getCourtCode())
                .withTaskCreationDate(LocalDate.now())
                .withCaseURN(opaTaskRequested.getCaseUrn())
                .withDue(addDaysSkippingWeekends(LocalDate.now(), opaTaskRequested.getNumberOfDays()).atStartOfDay())
                .withOrganisationId(opaTaskRequested.getOrganisationId().toString())
                .withCandidateGroups(opaTaskRequested.getRoles()) //"Listing Officer,Operational Delivery Admin (Crown),CTSC admin (Mags)"
                .build();
        startOpaTaskProcess(workflowOpaTaskType);

    }

    private void startOpaTaskProcess(final WorkflowOpaTaskType workflowOpaTaskType) {
        final Map<String, Object> processVariables = taskTypeService.getTaskVariablesFromRefData(TASK_NAME_OPA_CUSTOM_ACTIVITY, null);
        processVariables.put(OPA_TASK_VARIABLES_JSON_STRING, objectToJsonObjectConverter.convert(workflowOpaTaskType).toString());
        final String userId = systemUserProvider.getContextSystemUserId().map(UUID::toString).orElse(null);
        processVariables.put(DUE_DATE, workflowOpaTaskType.getDue().toLocalDate().toString());
        processVariables.put(LAST_UPDATED_BY_ID, userId);
        processVariables.put(LAST_UPDATED_BY_NAME, SYSTEM_USER_NAME);
        runtimeService.startProcessInstanceByKey(BPMN_PROCESS_OPA_CUSTOM_TASK_PROCESS, processVariables);
        LOGGER.info("OPA Workflow process started for OPA ID {}", workflowOpaTaskType.getOpaId());
    }

}
