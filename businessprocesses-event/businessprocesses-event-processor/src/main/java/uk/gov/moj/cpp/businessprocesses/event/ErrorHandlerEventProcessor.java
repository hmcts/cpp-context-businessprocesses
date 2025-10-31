package uk.gov.moj.cpp.businessprocesses.event;

import static java.util.Objects.isNull;
import static uk.gov.justice.services.core.annotation.Component.EVENT_PROCESSOR;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.BPMN_PROCESS_CORRESPONDENCE_FAILURE_IDENTIFIED;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.BPMN_PROCESS_SPI_ERROR_IDENTIFIED;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.COURT_HEARING_LOCATION;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.DATE_OF_HEARING;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.DEFENDANT;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.DEFENDANTS;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.HEARINGS_AT_A_GLANCE;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.INITIAL_HEARING;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.PLACE_HOLDER;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.PROSECUTION_CASE_IDENTIFIER;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.PROSECUTOR_CASE_REFERENCE;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.SYSTEM_USER_NAME;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.TASK_NAME_CORRESPONDENCE_FAILURE;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.TASK_NAME_SPI_ERROR;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.TIME_OF_HEARING;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.CASE_DETAILS;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.CASE_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.CASE_URN;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.CODE;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.CORRESPONDENCE_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.COURT_CODES;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.DRAFT_CORRESPONDENCE_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.ERROR_CODE;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.HEARING_DATE;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.LAST_UPDATED_BY_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.LAST_UPDATED_BY_NAME;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.NOTE;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.PROBLEMS;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.PROSECUTION;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.URN;

import uk.gov.justice.services.core.annotation.Handles;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.core.dispatcher.SystemUserProvider;
import uk.gov.justice.services.core.featurecontrol.FeatureControlGuard;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.moj.cpp.businessprocesses.service.ProgressionService;
import uk.gov.moj.cpp.businessprocesses.service.ReferenceDataService;
import uk.gov.moj.cpp.businessprocesses.service.TaskTypeService;
import uk.gov.moj.cpp.businessprocesses.shared.DateConverter;

import java.util.Map;
import java.util.UUID;

import javax.inject.Inject;
import javax.json.JsonArray;
import javax.json.JsonObject;

import org.apache.commons.lang3.StringUtils;
import org.camunda.bpm.engine.RuntimeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ServiceComponent(EVENT_PROCESSOR)
public class ErrorHandlerEventProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(ErrorHandlerEventProcessor.class);

    @Inject
    private RuntimeService runtimeService;

    @Inject
    private TaskTypeService taskTypeService;

    @Inject
    private SystemUserProvider systemUserProvider;
    @Inject
    private ProgressionService progressionService;

    @Inject
    private FeatureControlGuard featureControlGuard;

    @Inject
    private ReferenceDataService referenceDataService;

    @Handles("public.prosecutioncasefile.events.case-validation-failed")
    public void handleCaseValidationFailedProcessor(final JsonEnvelope jsonEnvelope) {
        if (featureControlGuard.isFeatureEnabled("camunda-prosecutioncasefile-events-case-validation-failed")) {
            handleCaseValidationFailed(jsonEnvelope);
        }
    }

    public void handleCaseValidationFailed(final JsonEnvelope jsonEnvelope) {

        LOGGER.info("Received the case-validation-failed event from ProcesuctionCasefile that triggers SPI Error Identified process.");

        final JsonObject eventPayload = jsonEnvelope.payloadAsJsonObject();
        startSPIErrorIdentifiedProcess(eventPayload);
    }

    @Handles("public.prosecutioncasefile.events.defendant-validation-failed")
    public void handleDefendantValidationFailedProcessor(final JsonEnvelope jsonEnvelope) {
        if (featureControlGuard.isFeatureEnabled("camunda-prosecutioncasefile-events-defendant-validation-failed")) {
            handleDefendantValidationFailed(jsonEnvelope);
        }
    }

    public void handleDefendantValidationFailed(final JsonEnvelope jsonEnvelope) {

        LOGGER.info("Received the defendant-validation-failed event from ProcesuctionCasefile that triggers SPI Error Identified process.");
        final JsonObject eventPayload = jsonEnvelope.payloadAsJsonObject();
        final String caseId = eventPayload.getString(CASE_ID);
        final String urn = eventPayload.getString(URN);
        final StringBuilder errorCodes = new StringBuilder();
        final JsonArray problems = eventPayload.getJsonArray(PROBLEMS);
        if (isNull(problems) || problems.isEmpty()) {
            LOGGER.info("Received the defendant-validation-failed event but problems field is empty. Exit without creating the task!");
            return;
        }
        for (int i = 0; i < problems.size(); i++) {
            if (problems.getJsonObject(i).containsKey(CODE) && problems.getJsonObject(i).getString(CODE) != null) {
                final String code = problems.getJsonObject(i).getString(CODE);
                errorCodes.append(code).append(",");
            }
        }
        String courtCentreOuCode = getCourtCentreOuCodeByCourtRoomCode(extractCourtCodeFromDefendantValidationFailedPayload(eventPayload));
        String hearingDate = extractHearingDateFromDefendantValidationFailedPayload(eventPayload);
        startSPIErrorIdentifiedProcessWithCaseId(caseId, errorCodes.toString(), urn, courtCentreOuCode, hearingDate);
    }

    @Handles("public.prosecutioncasefile.events.sjp-validation-failed")
    public void handleSjpValidationFailedProcessor(final JsonEnvelope jsonEnvelope) {
        if (featureControlGuard.isFeatureEnabled("camunda-prosecutioncasefile-events-sjp-validation-failed")) {
            handleSjpValidationFailed(jsonEnvelope);
        }
    }

    public void handleSjpValidationFailed(final JsonEnvelope jsonEnvelope) {

        LOGGER.info("Received the sjp-validation-failed event from ProcesuctionCasefile that triggers SPI Error Identified process.");

        final JsonObject eventPayload = jsonEnvelope.payloadAsJsonObject();
        startSPIErrorIdentifiedProcess(eventPayload);
    }

    private void startSPIErrorIdentifiedProcess(final JsonObject eventPayload) {
        final StringBuilder errorCodes = new StringBuilder();
        final JsonArray problems = eventPayload.getJsonArray(PROBLEMS);

        if (isNull(problems) || problems.isEmpty()) {
            LOGGER.info("Problems field is empty. Exit without creating the task!");
            return;
        }

        for (int i = 0; i < problems.size(); i++) {
            if (problems.getJsonObject(i).containsKey(CODE) && problems.getJsonObject(i).getString(CODE) != null) {
                final String code = problems.getJsonObject(i).getString(CODE);
                errorCodes.append(code).append(",");
            }
        }
        if (eventPayload.containsKey(PROSECUTION) && eventPayload.getJsonObject(PROSECUTION) != null) {

            final JsonObject prosecution = eventPayload.getJsonObject(PROSECUTION);
            if (prosecution.containsKey(CASE_DETAILS) && prosecution.getJsonObject(CASE_DETAILS) != null) {
                final JsonObject caseDetails = prosecution.getJsonObject(CASE_DETAILS);
                final String urn = caseDetails.getString(PROSECUTOR_CASE_REFERENCE);
                final String caseId = caseDetails.getString(CASE_ID);
                final String courtCentreOuCode = getCourtCentreOuCodeByCourtRoomCode(extractCourtCodeFromProsecution(prosecution));
                final String hearingDate = extractHearingDateFromProsecution(prosecution);

                startSPIErrorIdentifiedProcessWithCaseId(caseId, errorCodes.toString(), urn, courtCentreOuCode, hearingDate);
            }
        }
    }

    private void startSPIErrorIdentifiedProcessWithCaseId(final String caseId, final String errorCode, final String urn, final String courtCentreOuCode, final String hearingDate) {

        final Map<String, Object> processVariables = taskTypeService.getTaskVariablesFromRefData(TASK_NAME_SPI_ERROR, caseId, null, urn);
        final String userId = systemUserProvider.getContextSystemUserId().map(UUID::toString).orElse(null);
        processVariables.put(LAST_UPDATED_BY_ID, userId);
        processVariables.put(LAST_UPDATED_BY_NAME, SYSTEM_USER_NAME);
        processVariables.put(CASE_URN, urn);
        processVariables.put(CASE_ID, caseId);
        processVariables.put(ERROR_CODE, errorCode);
        processVariables.put(NOTE, errorCode);

        if(courtCentreOuCode != null){
            processVariables.put(COURT_CODES, courtCentreOuCode);
        }

        if(StringUtils.isNotEmpty(hearingDate)){
            processVariables.put(HEARING_DATE, hearingDate);
        }

        runtimeService.startProcessInstanceByKey(BPMN_PROCESS_SPI_ERROR_IDENTIFIED, caseId, processVariables);
        LOGGER.info("SPI Error process started");
    }

    @Handles("public.correspondence.event.outbound-correspondence-notification-failed")
    public void handleOutboundCorrespondenceNotificationFailedProcessor(final JsonEnvelope jsonEnvelope) {
        if (featureControlGuard.isFeatureEnabled("camunda-correspondence-event-outbound-correspondence-notification-failed")) {
            handleOutboundCorrespondenceNotificationFailed(jsonEnvelope);
        }
    }

    public void handleOutboundCorrespondenceNotificationFailed(final JsonEnvelope jsonEnvelope) {

        LOGGER.info("Received the outbound-correspondence-notification-failed event from correspondence that triggers correspondence_failure_identified.");

        final JsonObject eventPayload = jsonEnvelope.payloadAsJsonObject();
        final String caseId = eventPayload.getString(CASE_ID);
        final String correspondenceId = eventPayload.getString(DRAFT_CORRESPONDENCE_ID);

        final Map<String, Object> processVariables = taskTypeService.getTaskVariablesFromRefData(TASK_NAME_CORRESPONDENCE_FAILURE, caseId);
        final String userId = systemUserProvider.getContextSystemUserId().map(UUID::toString).orElse(null);
        final JsonObject prosecutionCaseJson = progressionService.getProsecutionCase(caseId);
        final String caseUrn = extractCaseUrn(prosecutionCaseJson);

        processVariables.put(LAST_UPDATED_BY_ID, userId);
        processVariables.put(LAST_UPDATED_BY_NAME, SYSTEM_USER_NAME);
        processVariables.put(CORRESPONDENCE_ID, correspondenceId);
        processVariables.put(CASE_ID, caseId);
        processVariables.put(CASE_URN, caseUrn);
        runtimeService.startProcessInstanceByKey(BPMN_PROCESS_CORRESPONDENCE_FAILURE_IDENTIFIED, correspondenceId, processVariables);

        LOGGER.info("Correspondence Failure process started");
    }

    private static String extractCaseUrn(final JsonObject prosecutionCaseJson) {
        return (prosecutionCaseJson.containsKey(HEARINGS_AT_A_GLANCE) && prosecutionCaseJson.getJsonObject(HEARINGS_AT_A_GLANCE).containsKey(PROSECUTION_CASE_IDENTIFIER)) ? prosecutionCaseJson.getJsonObject(HEARINGS_AT_A_GLANCE).getJsonObject(PROSECUTION_CASE_IDENTIFIER).getString(CASE_URN, PLACE_HOLDER) : PLACE_HOLDER;
    }

    private String extractCourtCodeFromProsecution(final JsonObject prosecution) {
        final JsonArray defendants = prosecution.getJsonArray(DEFENDANTS);
        if (isNull(defendants) || defendants.isEmpty()) {
            LOGGER.info("Defendant list is empty");
            return null;
        }
        for (int i = 0; i < defendants.size(); i++) {
            JsonObject defendant = defendants.getJsonObject(i);
            String courtCode = extractCourtCodeFromDefendantPayload(defendant);
            if (courtCode != null) {
                return courtCode;
            }
        }
        return null;
    }

    private String extractHearingDateFromProsecution(final JsonObject prosecution) {
        final JsonArray defendants = prosecution.getJsonArray(DEFENDANTS);
        if (isNull(defendants) || defendants.isEmpty()) {
            LOGGER.info("Defendant list is empty");
            return null;
        }
        for (int i = 0; i < defendants.size(); i++) {
            JsonObject defendant = defendants.getJsonObject(i);
            String hearingDate = extractHearingDateFromDefendantPayload(defendant);
            if (StringUtils.isNotEmpty(hearingDate)) {
                return hearingDate;
            }
        }
        return "";
    }

    private String extractCourtCodeFromDefendantValidationFailedPayload(final JsonObject defendantValidationFailed) {
        return defendantValidationFailed.containsKey(DEFENDANT) ?
                extractCourtCodeFromDefendantPayload(defendantValidationFailed.getJsonObject(DEFENDANT)) : null;
    }

    private String extractHearingDateFromDefendantValidationFailedPayload(final JsonObject defendantValidationFailed) {
        return defendantValidationFailed.containsKey(DEFENDANT) ?
                extractHearingDateFromDefendantPayload(defendantValidationFailed.getJsonObject(DEFENDANT)) : null;
    }

    private String extractCourtCodeFromDefendantPayload(final JsonObject defendantPayload) {
        return defendantPayload.containsKey(INITIAL_HEARING)?
                extractFieldFromInitialHearingPayload(defendantPayload, COURT_HEARING_LOCATION): null;
    }

    private String extractHearingDateFromDefendantPayload(final JsonObject defendantPayload) {
        String hearingDate = defendantPayload.containsKey(INITIAL_HEARING)?
                extractFieldFromInitialHearingPayload(defendantPayload, DATE_OF_HEARING): null;

        String hearingTime = defendantPayload.containsKey(INITIAL_HEARING)?
                extractFieldFromInitialHearingPayload(defendantPayload, TIME_OF_HEARING): null;

        return DateConverter.formatHearingDate(hearingDate, hearingTime);
    }

    private String extractFieldFromInitialHearingPayload(final JsonObject jsonObject, final String fieldName) {
        return jsonObject.getJsonObject(INITIAL_HEARING).containsKey(fieldName) ?
                jsonObject.getJsonObject(INITIAL_HEARING).getString(fieldName) : null;
    }

    private String getCourtCentreOuCodeByCourtRoomCode(final String courtRoomCode) {
        final JsonObject courtCentreDetails = courtRoomCode != null ? referenceDataService.retrieveCourtCentreDetailsByCourtRoomCode(courtRoomCode) : null;
        return ReferenceDataService.getCourtCentreOuCode(courtCentreDetails);
    }
}
