package uk.gov.moj.cpp.businessprocesses.event;

import static java.util.Arrays.stream;
import static org.apache.commons.collections.CollectionUtils.isNotEmpty;
import static uk.gov.justice.services.core.annotation.Component.EVENT_PROCESSOR;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.BPMN_PROCESS_LIST_BAIL_APPEAL_HEARING_PROCESS;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.BPMN_PROCESS_LIST_MURDER_CASE_FOR_BAIL_HEARING_PROCESS;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.SYSTEM_USER_NAME;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.LAST_UPDATED_BY_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.LAST_UPDATED_BY_NAME;

import uk.gov.justice.core.courts.Hearing;
import uk.gov.justice.services.common.converter.JsonObjectToObjectConverter;
import uk.gov.justice.services.core.annotation.Handles;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.core.dispatcher.SystemUserProvider;
import uk.gov.justice.services.core.featurecontrol.FeatureControlGuard;
import uk.gov.justice.services.messaging.JsonEnvelope;

import java.util.List;
import java.util.UUID;

import javax.inject.Inject;

import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.task.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ServiceComponent(EVENT_PROCESSOR)
public class ListingHearingListedEventProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(ListingHearingListedEventProcessor.class);

    @Inject
    private TaskService taskService;

    @Inject
    private SystemUserProvider systemUserProvider;

    @Inject
    private FeatureControlGuard featureControlGuard;

    @Inject
    private JsonObjectToObjectConverter jsonObjectToObjectConverter;

    @Handles("public.listing.hearing-listed")
    public void handleHearingListedProcessor(final JsonEnvelope jsonEnvelope) {
        if (featureControlGuard.isFeatureEnabled("camunda-listing-hearing-listed")) {
            handleHearingListed(jsonEnvelope);
        }
    }

    public void handleHearingListed(final JsonEnvelope jsonEnvelope) {
        LOGGER.info("Received the Bail payload.  {}", jsonEnvelope.payload());
        final Hearing hearing = jsonObjectToObjectConverter.convert(jsonEnvelope.payloadAsJsonObject().getJsonObject("hearing"), Hearing.class);
        if (isNotEmpty(hearing.getProsecutionCases())) {
            hearing.getProsecutionCases().forEach(listedCase -> {
                        final String caseURN = listedCase.getProsecutionCaseIdentifier().getCaseURN();
                        final String hearingId = hearing.getId().toString();

                        final String hearingType = hearing.getType().getDescription();
                        LOGGER.info("Complete Bail Hearing tasks for caseURN {}, hearingId {} and hearingType {}", caseURN, hearingId, hearingType);
                        if ("Bail Application".equals(hearingType)) {
                            completeBailHearingTasks(caseURN, hearingId, hearingType, BPMN_PROCESS_LIST_BAIL_APPEAL_HEARING_PROCESS, BPMN_PROCESS_LIST_MURDER_CASE_FOR_BAIL_HEARING_PROCESS);
                        }
                    }
            );
        }
    }

    public void completeBailHearingTasks(final String caseURN, final String hearingId, final String hearingType, String... processDefinitionKeys) {
        stream(processDefinitionKeys).forEach(processDefinitionKey -> {
            if (featureControlGuard.isFeatureEnabled(processDefinitionKey)) {
                LOGGER.info("Calling task query caseURN {}, hearingId {} and hearingType {} for processKey {}", caseURN, hearingId, hearingType, processDefinitionKey);
                final List<Task> tasks = taskService.createTaskQuery()
                        .processDefinitionKey(processDefinitionKey)
                        .processInstanceBusinessKey(caseURN)
                        .active()
                        .list();

                LOGGER.info("tasks retrieved from  task query caseURN {}, hearingId {} and hearingType {} and processKey {} list of tasks  {}", caseURN, hearingId, hearingType, processDefinitionKey, tasks);
                autoComplete(tasks);
            }
        });
    }

    public void autoComplete(final List<Task> tasks) {
        final String userId = systemUserProvider.getContextSystemUserId().map(UUID::toString).orElse(null);
        tasks.forEach(task -> {
            LOGGER.info("closing task {} with taskVariables {} and processKey {} ", task.getId(), taskService.getVariablesLocal(task.getId()), task.getProcessDefinitionId());
            taskService.setVariableLocal(task.getId(), LAST_UPDATED_BY_NAME, SYSTEM_USER_NAME);
            taskService.setVariableLocal(task.getId(), LAST_UPDATED_BY_ID, userId);
            taskService.complete(task.getId());

        });
    }
}
