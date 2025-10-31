package uk.gov.moj.cpp.businessprocesses.event;

import static uk.gov.justice.services.core.annotation.Component.EVENT_PROCESSOR;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.BPMN_PROCESS_WELSH_TRANSLATION_PROCESS;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.CASE_URNS;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.DEFENDANT_NAME;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.MASTER_DEFENDANT_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.MATERIAL_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.NOW_DOCUMENT_NOTIFICATION_SUPPRESSED;
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
public class SuppressWelshDocumentCreationPublicEventProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(SuppressWelshDocumentCreationPublicEventProcessor.class);

    @Inject
    private TaskTypeService taskTypeService;

    @Inject
    private ProgressionService progressionService;

    @Inject
    private ListingService listingService;

    @Inject
    private ReferenceDataService referenceDataService;
    @Inject
    private SystemUserProvider systemUserProvider;
    @Inject
    private RuntimeService runtimeService;

    @Handles("public.progression.now-notification-suppressed")
    public void handleNowNotificationSupressed(final JsonEnvelope jsonEnvelope) {
        LOGGER.info("Received the event from Progression that triggers suppress welsh document creation event");

        final JsonObject eventPayload = jsonEnvelope.payloadAsJsonObject();
        markSuppressWelshDocumentTranslationProcess(eventPayload);
    }

    @Handles("public.hearingnows.now-notification-suppressed")
    public void handleNowNotificationSupressedFromHearingNows(final JsonEnvelope jsonEnvelope) {
        LOGGER.info("Received the event from Hearing Nows that triggers suppress welsh document creation event");

        final JsonObject eventPayload = jsonEnvelope.payloadAsJsonObject();
        markSuppressWelshDocumentTranslationProcess(eventPayload);
    }

    private void markSuppressWelshDocumentTranslationProcess(final JsonObject eventPayload) {

        final Map<String, Object> processVariables = taskTypeService.getTaskVariablesFromRefData(TASK_NAME_TRANSLATE_TO_WELSH, null);

        final JsonObject nowDocumentNotificationSuppressedObject = eventPayload.getJsonObject(NOW_DOCUMENT_NOTIFICATION_SUPPRESSED);
        processVariables.put(ProcessVariableConstants.MASTER_DEFENDANT_ID, nowDocumentNotificationSuppressedObject.getString(MASTER_DEFENDANT_ID, null));
        processVariables.put(ProcessVariableConstants.DEFENDANT_NAME, nowDocumentNotificationSuppressedObject.getString(DEFENDANT_NAME, null));
        processVariables.put(ProcessVariableConstants.MATERIAL_ID, nowDocumentNotificationSuppressedObject.getString(MATERIAL_ID, null));
        processVariables.put(ProcessVariableConstants.TEMPLATE_NAME, nowDocumentNotificationSuppressedObject.getString(TEMPLATE_NAME, null));
        processVariables.put(CASE_URN, nowDocumentNotificationSuppressedObject.getJsonArray(CASE_URNS).getString(0));

        final List<Hearing> hearings = getHearings(nowDocumentNotificationSuppressedObject);
        final Hearing hearing = getLatestHearing(hearings);
        String hearingDate = HearingHelper.getHearingDate(hearing);
        processVariables.put(ProcessVariableConstants.HEARING_DATE, hearingDate);

        processVariables.put(ProcessVariableConstants.CUSTODY_TIME_LIMIT, getCustodyTimeLimit(hearings));

        setCourtCode(hearing, processVariables);

        final String userId = systemUserProvider.getContextSystemUserId().map(UUID::toString).orElse(null);
        processVariables.put(LAST_UPDATED_BY_ID, userId);
        processVariables.put(LAST_UPDATED_BY_NAME, SYSTEM_USER_NAME);

        runtimeService.startProcessInstanceByKey(BPMN_PROCESS_WELSH_TRANSLATION_PROCESS, nowDocumentNotificationSuppressedObject.getString(MATERIAL_ID, null), processVariables);

        LOGGER.info("Welsh Language Translation process suppressed with key as materialId {}", nowDocumentNotificationSuppressedObject.getString(MATERIAL_ID, null));
    }

    private List<Hearing> getHearings(final JsonObject eventPayload) {
        final String caseUrn = eventPayload.getJsonArray(CASE_URNS).getString(0);
        final String caseId = progressionService.getProsecutionCaseExistByCaseUrn(caseUrn);
        if (caseId != null) {
            final String userId = systemUserProvider.getContextSystemUserId().map(UUID::toString).orElse(null);
            return listingService.getHearings(userId, caseId);
        }
        return null;
    }

    private void setCourtCode(final Hearing hearing, final Map<String, Object> processVariables) {
        if (hearing != null) {
            UUID courtCentreId = hearing.getCourtCentreId();
            if (courtCentreId != null) {
                final JsonObject courtCentreDetails = referenceDataService.retrieveCourtCentreDetailsByCourtId(courtCentreId.toString());
                final String courtCode = ReferenceDataService.getCourtCentreOuCode(courtCentreDetails);
                if (courtCode != null) {
                    processVariables.put(ProcessVariableConstants.COURT_CODES, courtCode);
                }
            }
        }
    }
}
