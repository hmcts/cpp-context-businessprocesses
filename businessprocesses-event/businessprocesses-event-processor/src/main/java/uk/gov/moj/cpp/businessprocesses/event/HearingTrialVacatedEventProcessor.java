package uk.gov.moj.cpp.businessprocesses.event;

import static java.lang.String.format;
import static java.util.Objects.isNull;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.SPACE;
import static uk.gov.justice.services.core.annotation.Component.EVENT_PROCESSOR;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.APPLICATION_DETAILS;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.APPLICATION_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.APPLICATION_REFERENCE;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.BPMN_PROCESS_HEARING_VACATED;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.CASEURN;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.CASE_DETAILS;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.CASE_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.CROWN_COURT_ADMIN_WORK_QUEUE_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.CROWN_JURISDICTION_TYPE;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.DEFENDANT_DETAILS;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.DEFENDANT_FIRST_NAME;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.DEFENDANT_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.DEFENDANT_LAST_NAME;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.DEFENDANT_REMAND_STATUS;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.HAS_INTERPRETER;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.HEARING_DATE_TIME;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.HEARING_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.JURISDICTION_TYPE;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.SUBJECT;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.SYSTEM_USER_NAME;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.TASK_NAME_CANCEL_INTERPRETER_APPLICATION;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.TASK_NAME_CANCEL_INTERPRETER_CASE;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.VACATED_TRIAL_REASON_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.CASE_URN;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.DEFENDANT_NAME;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.HAS_CASES;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.HEARING_DATE;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.JURISDICTION;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.LAST_UPDATED_BY_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.LAST_UPDATED_BY_NAME;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.WORK_QUEUE;

import uk.gov.justice.services.core.annotation.Handles;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.core.dispatcher.SystemUserProvider;
import uk.gov.justice.services.core.featurecontrol.FeatureControlGuard;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.moj.cpp.businessprocesses.service.TaskTypeService;
import uk.gov.moj.cpp.businessprocesses.shared.HearingTrialCaseApplicationDetailsNotFoundException;
import uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants;

import java.util.Map;
import java.util.UUID;

import javax.inject.Inject;
import javax.json.JsonArray;
import javax.json.JsonObject;

import org.camunda.bpm.engine.RuntimeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ServiceComponent(EVENT_PROCESSOR)
@SuppressWarnings({"squid:S3457"})
public class HearingTrialVacatedEventProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(HearingTrialVacatedEventProcessor.class);

    @Inject
    private RuntimeService runtimeService;

    @Inject
    private TaskTypeService taskTypeService;

    @Inject
    private SystemUserProvider systemUserProvider;

    @Inject
    private FeatureControlGuard featureControlGuard;

    @Handles("public.hearing.trial-vacated")
    public void handleHearingVacatedProcessor(final JsonEnvelope jsonEnvelope) {
        if (featureControlGuard.isFeatureEnabled("camunda-hearing-trial-vacated")) {
            handleHearingVacated(jsonEnvelope);
        }
    }

    public void handleHearingVacated(final JsonEnvelope jsonEnvelope) {

        final JsonObject eventPayload = jsonEnvelope.payloadAsJsonObject();

        if (eventPayload.containsKey(VACATED_TRIAL_REASON_ID) && eventPayload.getString(VACATED_TRIAL_REASON_ID) != null) {
            final String hearingId = eventPayload.getString(HEARING_ID);

            LOGGER.info("Received event 'public.hearing.trial-vacated' for vacated hearing: {}", hearingId);

            final String hearingDate = eventPayload.getString(HEARING_DATE_TIME);
            final boolean hasInterpreter = eventPayload.getBoolean(HAS_INTERPRETER);
            final String jurisdiction = eventPayload.getString(JURISDICTION_TYPE);
            final JsonArray caseDetails = eventPayload.getJsonArray(CASE_DETAILS);
            final JsonArray applicationDetails = eventPayload.getJsonArray(APPLICATION_DETAILS);

            if (eventPayload.containsKey(CASE_DETAILS) && eventPayload.getJsonArray(CASE_DETAILS) != null && !(eventPayload.getJsonArray(CASE_DETAILS).isEmpty())) {
                extractCaseDetailsAndStartProcessForHearingVacated(caseDetails, hasInterpreter, hearingId, hearingDate, jurisdiction);
            } else {
                extractApplicationDetailsAndStartProcessForHearingVacated(applicationDetails, hasInterpreter, hearingId, hearingDate, jurisdiction);
            }
        }
    }

    private void extractCaseDetailsAndStartProcessForHearingVacated(final JsonArray details, final boolean hasInterpreter, final String hearingId, final String hearingDate, final String jurisdiction) {

        if (!details.isEmpty()) {
            final JsonObject caseDetails = details.getJsonObject(0);
            final String id = caseDetails.getString(CASE_ID);
            final JsonArray defendantsArray = caseDetails.getJsonArray(DEFENDANT_DETAILS);
            final String urn = caseDetails.containsKey(CASEURN) ? caseDetails.getString(CASEURN) : EMPTY;

            if (!defendantsArray.isEmpty()) {
                final JsonObject defendantDetails = defendantsArray.getJsonObject(0);
                extractDetailsAndStartProcessForHearingVacated(id, ProcessVariableConstants.CASE_ID, defendantDetails, hasInterpreter, hearingId, hearingDate, jurisdiction, TASK_NAME_CANCEL_INTERPRETER_CASE, true, urn);
            } else {
                throw new HearingTrialCaseApplicationDetailsNotFoundException(format("hearing trial vacated case details are empty need to investigate payload ", details));
            }
        }
    }

    private void extractApplicationDetailsAndStartProcessForHearingVacated(final JsonArray details, final boolean hasInterpreter, final String hearingId, final String hearingDate, final String jurisdiction) {

        if (!details.isEmpty()) {
            final JsonObject applicationDetails = details.getJsonObject(0);
            final String id = applicationDetails.getString(APPLICATION_ID);
            final String applicationReference = applicationDetails.containsKey(APPLICATION_REFERENCE) ? applicationDetails.getString(APPLICATION_REFERENCE) : EMPTY;
            if (!applicationDetails.isEmpty()) {
                final JsonObject defendantDetails = applicationDetails.getJsonObject(SUBJECT);
                extractDetailsAndStartProcessForHearingVacated(id, ProcessVariableConstants.APPLICATION_ID, defendantDetails, hasInterpreter, hearingId, hearingDate, jurisdiction, TASK_NAME_CANCEL_INTERPRETER_APPLICATION, false, applicationReference);
            } else {
                throw new HearingTrialCaseApplicationDetailsNotFoundException(format("hearing trial vacated application details are empty need to investigate payload ", details));
            }
        }
    }

    private void extractDetailsAndStartProcessForHearingVacated(final String id, final String idType, final JsonObject defendantDetails, final boolean hasInterpreter, final String hearingId, final String hearingDate, final String jurisdiction, final String taskName, final boolean hasCases, final String urn) {

        final String defendantId = defendantDetails.getString(DEFENDANT_ID, null);
        final String defendantFirstName = defendantDetails.getString(DEFENDANT_FIRST_NAME, null);
        final String defendantLastName = defendantDetails.getString(DEFENDANT_LAST_NAME, null);
        final String defendantRemandStatus = defendantDetails.getString(DEFENDANT_REMAND_STATUS, null);
        final String defendantName = buildDefendantName(defendantFirstName, defendantLastName);

        startProcessForHearingVacated(hasInterpreter, hearingId, hearingDate, id, idType, defendantId, defendantName, defendantRemandStatus, jurisdiction, taskName, hasCases, urn);
    }

    private void startProcessForHearingVacated(final boolean hasInterpreter, final String hearingId, final String hearingDate, final String id, final String idType, final String defendantId, final String defendantName, final String defendantRemandStatus, final String jurisdiction, final String taskName, final boolean hasCases, final String urn) {

        final Map<String, Object> processVariables = taskTypeService.getTaskVariablesFromRefDataWithPrefix(taskName, id, getPrefix(taskName), hearingDate);

        processVariables.put(ProcessVariableConstants.HAS_INTERPRETER, hasInterpreter);
        processVariables.put(HAS_CASES, hasCases);

        processVariables.put(idType, id);
        processVariables.put(ProcessVariableConstants.HEARING_ID, hearingId);
        processVariables.put(HEARING_DATE, hearingDate);
        processVariables.put(ProcessVariableConstants.DEFENDANT_ID, defendantId);
        processVariables.put(DEFENDANT_NAME, defendantName);
        processVariables.put(ProcessVariableConstants.DEFENDANT_REMAND_STATUS, defendantRemandStatus);
        processVariables.put(CASE_URN, urn);

        if (CROWN_JURISDICTION_TYPE.equals(jurisdiction)) {
            processVariables.put(WORK_QUEUE, CROWN_COURT_ADMIN_WORK_QUEUE_ID);
        }

        final String userId = systemUserProvider.getContextSystemUserId().map(UUID::toString).orElse(null);
        processVariables.put(LAST_UPDATED_BY_ID, userId);
        processVariables.put(LAST_UPDATED_BY_NAME, SYSTEM_USER_NAME);
        processVariables.put(JURISDICTION, jurisdiction);
        runtimeService.startProcessInstanceByKey(BPMN_PROCESS_HEARING_VACATED, hearingId, processVariables);
        LOGGER.info("Hearing Trial Vacated Process Started with processVariables {}", processVariables);
    }

    private static String getPrefix(final String taskName) {
        return taskName + "_";
    }

    private String buildDefendantName(final String defendantFirstName, final String defendantLastName) {
        if (isNull(defendantFirstName) && isNull(defendantLastName)) {
            return null;
        }
        if (isNull(defendantFirstName)) {
            return defendantLastName;
        }
        if (isNull(defendantLastName)) {
            return defendantFirstName;
        }
        return defendantFirstName.concat(SPACE).concat(defendantLastName);
    }

}
