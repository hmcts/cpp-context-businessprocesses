package uk.gov.moj.cpp.businessprocesses.event;

import static uk.gov.justice.services.core.annotation.Component.EVENT_PROCESSOR;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.BPMN_PROCESS_WELSH_TRANSLATION_PROCESS;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.DEFENDANT_NAME;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.MASTER_DEFENDANT_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.MATERIAL_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.SYSTEM_USER_NAME;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.TASK_NAME_TRANSLATE_TO_WELSH;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.TEMPLATE_NAME;
import static uk.gov.moj.cpp.businessprocesses.shared.HearingHelper.getCustodyTimeLimit;
import static uk.gov.moj.cpp.businessprocesses.shared.HearingHelper.getLatestHearing;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.CASE_URN;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.LAST_UPDATED_BY_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.LAST_UPDATED_BY_NAME;

import uk.gov.justice.listing.events.Hearing;
import uk.gov.justice.services.core.annotation.Handles;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.core.dispatcher.SystemUserProvider;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.moj.cpp.businessprocesses.service.ListingService;
import uk.gov.moj.cpp.businessprocesses.service.ProgressionService;
import uk.gov.moj.cpp.businessprocesses.service.ReferenceDataService;
import uk.gov.moj.cpp.businessprocesses.service.TaskTypeService;
import uk.gov.moj.cpp.businessprocesses.shared.Constants;
import uk.gov.moj.cpp.businessprocesses.shared.HearingHelper;
import uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.inject.Inject;
import javax.json.JsonObject;

import org.camunda.bpm.engine.RuntimeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ServiceComponent(EVENT_PROCESSOR)
public class WelshTranslationRequiredEventProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(WelshTranslationRequiredEventProcessor.class);

    @Inject
    private RuntimeService runtimeService;
    @Inject
    private SystemUserProvider systemUserProvider;
    @Inject
    private TaskTypeService taskTypeService;

    @Inject
    private ProgressionService progressionService;

    @Inject
    private ListingService listingService;

    @Inject
    private ReferenceDataService referenceDataService;

    @Handles("public.progression.welsh-translation-required")
    public void handleWelshTranslationRequired(final JsonEnvelope jsonEnvelope) {

        LOGGER.info("Received the event from Progression that triggers welsh  translation required event");

        final JsonObject eventPayload = jsonEnvelope.payloadAsJsonObject();
        markAsWelshTranslationRequired(eventPayload);
    }


    private void markAsWelshTranslationRequired(final JsonObject eventPayload) {
        final Map<String, Object> processVariables = taskTypeService.getTaskVariablesFromRefData(TASK_NAME_TRANSLATE_TO_WELSH, null);
        final JsonObject welshTranslationRequiredObject = eventPayload.getJsonObject("welshTranslationRequired");
        processVariables.put(ProcessVariableConstants.MASTER_DEFENDANT_ID, welshTranslationRequiredObject.getString(Constants.MASTER_DEFENDANT_ID, null));
        processVariables.put(ProcessVariableConstants.DEFENDANT_NAME, welshTranslationRequiredObject.getString(DEFENDANT_NAME, null));
        processVariables.put(ProcessVariableConstants.MATERIAL_ID, welshTranslationRequiredObject.getString(MATERIAL_ID, null));
        processVariables.put(ProcessVariableConstants.TEMPLATE_NAME, welshTranslationRequiredObject.getString(TEMPLATE_NAME, null));
        processVariables.put(CASE_URN, welshTranslationRequiredObject.getString(CASE_URN));

        final List<Hearing> hearings = getHearings(welshTranslationRequiredObject);
        final Hearing hearing = getLatestHearing(hearings);
        final String courtCode = getCourtCode(hearing);
        if (courtCode != null) {
            processVariables.put(ProcessVariableConstants.COURT_CODES, courtCode);
        }

        processVariables.put(ProcessVariableConstants.CUSTODY_TIME_LIMIT, getCustodyTimeLimit(hearings));

        String hearingDate = HearingHelper.getHearingDate(hearing);
        processVariables.put(ProcessVariableConstants.HEARING_DATE, hearingDate);

        final String userId = systemUserProvider.getContextSystemUserId().map(UUID::toString).orElse(null);
        processVariables.put(LAST_UPDATED_BY_ID, userId);
        processVariables.put(LAST_UPDATED_BY_NAME, SYSTEM_USER_NAME);

        runtimeService.startProcessInstanceByKey(BPMN_PROCESS_WELSH_TRANSLATION_PROCESS, welshTranslationRequiredObject.getString(MATERIAL_ID, null), processVariables);

        LOGGER.info("Welsh Language Translation required process with key as masterDefendantId {}", welshTranslationRequiredObject.getString(MASTER_DEFENDANT_ID, null));
    }

    private List<Hearing> getHearings(final JsonObject eventPayload) {
        final String caseUrn = eventPayload.getString(CASE_URN);
        final String caseId = progressionService.getProsecutionCaseExistByCaseUrn(caseUrn);
        if (caseId != null) {
            final String userId = systemUserProvider.getContextSystemUserId().map(UUID::toString).orElse(null);
            return listingService.getHearings(userId, caseId);
        }
        return null;
    }

    private String getCourtCode(final Hearing hearing) {
        if (hearing != null) {
            UUID courtCentreId = hearing.getCourtCentreId();
            if (courtCentreId != null) {
                final JsonObject courtCentreDetails = referenceDataService.retrieveCourtCentreDetailsByCourtId(courtCentreId.toString());
                return ReferenceDataService.getCourtCentreOuCode(courtCentreDetails);
            }
        }
        return null;
    }
}
