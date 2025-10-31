package uk.gov.moj.cpp.businessprocesses.event;

import static uk.gov.justice.services.core.annotation.Component.EVENT_PROCESSOR;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.BPMN_PROCESS_SJP_CASE_HEARING_DECISION_SAVED;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.DEFENDANT_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.DEFENDANT_NAME;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.SYSTEM_USER_NAME;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.TASK_NAME_REFER_SJS_CASE_FOR_HEARING;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.CASE_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.CASE_URN;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.HEARING_DATE;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.LAST_UPDATED_BY_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.LAST_UPDATED_BY_NAME;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.REFER_COURT_HEARING;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.URN;

import uk.gov.justice.services.core.annotation.Handles;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.core.dispatcher.SystemUserProvider;
import uk.gov.justice.services.core.featurecontrol.FeatureControlGuard;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.moj.cpp.businessprocesses.service.TaskTypeService;
import uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import javax.inject.Inject;
import javax.json.JsonArray;
import javax.json.JsonObject;

import org.camunda.bpm.engine.RuntimeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ServiceComponent(EVENT_PROCESSOR)
public class SJPEventProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(SJPEventProcessor.class);

    private static final String REFER_FOR_COURT_HEARING = "REFER_FOR_COURT_HEARING";

    @Inject
    private RuntimeService runtimeService;

    @Inject
    private TaskTypeService taskTypeService;

    @Inject
    private SystemUserProvider systemUserProvider;

    @Inject
    private FeatureControlGuard featureControlGuard;

    @Handles("public.events.sjp.case-referred-to-court")
    public void handleSjpCaseReferredToCourtProcessor(final JsonEnvelope jsonEnvelope) {
        if (featureControlGuard.isFeatureEnabled("camunda-sjp-case-referred-to-court")) {
            handleSjpCaseReferredToCourt(jsonEnvelope);
        }
    }

    public void handleSjpCaseReferredToCourt(final JsonEnvelope jsonEnvelope) {

        final JsonObject eventPayload = jsonEnvelope.payloadAsJsonObject();
        final String caseId = eventPayload.getString(CASE_ID);

        LOGGER.info("Received event 'public.events.sjp.case-referred-to-court' for case: {}", caseId);

        final JsonArray offenceDecisions = eventPayload.getJsonArray("offenceDecisions");
        final boolean isReferCourt = isReferToCourtBasedOnDecisionType(offenceDecisions);

        final String urn = eventPayload.getString(URN);
        final String defendantId = eventPayload.getString(DEFENDANT_ID);
        final String defendantName = eventPayload.getString(DEFENDANT_NAME);
        final String hearingDate = getHearingDate(offenceDecisions);
        startProcessForSJPCaseForHearing(caseId, isReferCourt, defendantId, defendantName, urn, hearingDate);
    }

    /*
     * Used to filter out SJP results that are not referring the case to criminal court
     */
    private boolean isReferToCourtBasedOnDecisionType(final JsonArray offenceDecisions) {
        return offenceDecisions.stream()
                .filter(Objects::nonNull)
                .filter(jsonValue -> ((JsonObject) jsonValue).containsKey("type"))
                .map(jsonValue -> ((JsonObject) jsonValue).getString("type"))
                .anyMatch(REFER_FOR_COURT_HEARING::equals);
    }

    private void startProcessForSJPCaseForHearing(final String caseId, final boolean isReferCourt, final String defendantId,
                                                  final String defendantName, final String urn, final String hearingDate) {
        final Map<String, Object> processVariables = taskTypeService.getTaskVariablesFromRefData(TASK_NAME_REFER_SJS_CASE_FOR_HEARING, caseId);

        processVariables.put(CASE_ID, caseId);
        processVariables.put(REFER_COURT_HEARING, isReferCourt);
        processVariables.put(ProcessVariableConstants.DEFENDANT_ID, defendantId);
        processVariables.put(ProcessVariableConstants.DEFENDANT_NAME, defendantName);
        processVariables.put(CASE_URN, urn);

        final String userId = systemUserProvider.getContextSystemUserId().map(UUID::toString).orElse(null);
        processVariables.put(LAST_UPDATED_BY_ID, userId);
        processVariables.put(LAST_UPDATED_BY_NAME, SYSTEM_USER_NAME);
        processVariables.put(HEARING_DATE, hearingDate);

        runtimeService.startProcessInstanceByKey(BPMN_PROCESS_SJP_CASE_HEARING_DECISION_SAVED, caseId, processVariables);
        LOGGER.info("New SJP Case Hearing Process Started with processVariables {}", processVariables);
    }

    private String getHearingDate(final JsonArray offenceDecisions) {
        return offenceDecisions.stream()
                .filter(Objects::nonNull)
                .filter(jsonValue -> ((JsonObject) jsonValue).containsKey("type"))
                .filter(jsonValue -> ((JsonObject) jsonValue).getString("type").equals(REFER_FOR_COURT_HEARING))
                .filter(jsonValue -> ((JsonObject) jsonValue).containsKey("nextHearing"))
                .map(jsonValue -> ((JsonObject) jsonValue).getJsonObject("nextHearing"))
                .filter(jsonValue -> jsonValue.containsKey("listedStartDateTime"))
                .map(jsonValue -> jsonValue.getString("listedStartDateTime"))
                .findFirst().orElse(null);
    }
}
