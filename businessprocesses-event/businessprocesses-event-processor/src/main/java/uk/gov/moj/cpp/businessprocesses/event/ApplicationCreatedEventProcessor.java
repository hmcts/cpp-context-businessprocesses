package uk.gov.moj.cpp.businessprocesses.event;

import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.SPACE;
import static uk.gov.justice.services.core.annotation.Component.EVENT_PROCESSOR;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.BPMN_PROCESS_APPLICATION_RECEIVED;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.CROWN_COURT_ADMIN_WORK_QUEUE_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.CROWN_JURISDICTION_TYPE;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.DEFENCE;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.PROSECUTOR;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.SYSTEM_USER_NAME;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.TASK_NAME_PROCESS_APPLICATION;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.TYPE;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.APPLICATION_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.APP_TYPE;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.CASE_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.CASE_URN;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.DEFENDANT_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.DEFENDANT_NAME;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.IS_APPLICATION_PROSECUTOR_OR_DEFENCE;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.JURISDICTION;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.LAST_UPDATED_BY_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.LAST_UPDATED_BY_NAME;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.REGION;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.WORK_QUEUE;

import uk.gov.justice.services.core.annotation.Handles;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.core.dispatcher.SystemUserProvider;
import uk.gov.justice.services.core.featurecontrol.FeatureControlGuard;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.moj.cpp.businessprocesses.service.TaskTypeService;

import java.util.Map;
import java.util.UUID;

import javax.inject.Inject;
import javax.json.JsonArray;
import javax.json.JsonObject;

import org.camunda.bpm.engine.RuntimeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ServiceComponent(EVENT_PROCESSOR)
public class ApplicationCreatedEventProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApplicationCreatedEventProcessor.class);

    @Inject
    private RuntimeService runtimeService;

    @Inject
    private TaskTypeService taskTypeService;

    @Inject
    private SystemUserProvider systemUserProvider;

    @Inject
    private FeatureControlGuard featureControlGuard;

    @Handles("public.progression.court-application-created")
    public void handleCourtApplicationCreatedProcessor(final JsonEnvelope jsonEnvelope) {
        if (featureControlGuard.isFeatureEnabled("camunda-progression-court-application-created")) {
            handleCourtApplicationCreated(jsonEnvelope);
        }
    }

    public void handleCourtApplicationCreated(final JsonEnvelope jsonEnvelope) {

        final JsonObject eventPayload = jsonEnvelope.payloadAsJsonObject();

        final JsonObject courtApplication = eventPayload.getJsonObject("courtApplication");
        final String applicationId = courtApplication.getString("id");

        LOGGER.info("Received event 'public.progression.court-application-created' for application: {}", applicationId);

        final String applicationExternalCreatorType = courtApplication.containsKey("applicationExternalCreatorType") ? courtApplication.getString("applicationExternalCreatorType") : EMPTY;
        final boolean isApplicationProsecutorOrDefence = PROSECUTOR.equals(applicationExternalCreatorType) || DEFENCE.equals(applicationExternalCreatorType);

        final String appType = courtApplication.getJsonObject(TYPE).getString(TYPE);

        String caseId = EMPTY;
        String urn = EMPTY;

        if (courtApplication.containsKey("courtApplicationCases")) {

            final JsonArray caseDetails = courtApplication.getJsonArray("courtApplicationCases");

            if (!caseDetails.isEmpty()) {
                final JsonObject caseDetailsOne = caseDetails.getJsonObject(0);
                caseId = caseDetailsOne.containsKey("prosecutionCaseId") ? caseDetailsOne.getString("prosecutionCaseId") : EMPTY;
                final JsonObject prosecutionCaseIdentifier = caseDetailsOne.getJsonObject("prosecutionCaseIdentifier");
                urn = prosecutionCaseIdentifier.containsKey(CASE_URN) ? prosecutionCaseIdentifier.getString(CASE_URN) : EMPTY;
            }
        }

        final JsonObject subject = courtApplication.getJsonObject("subject");
        final String defendantId = subject.getString("id");
        final String defendantName = getDefendantName(subject);
        final String jurisdictionType = getJurisdictionType(eventPayload);

        startProcessForApplicationCreated(applicationId, caseId, urn, defendantId, defendantName, appType, isApplicationProsecutorOrDefence, jurisdictionType, extractRegionFromPayload(eventPayload));
    }

    private void startProcessForApplicationCreated(final String applicationId, final String caseId, final String urn, final String defendantId,
                                                   final String defendantName, final String appType, final boolean isApplicationProsecutorOrDefence, final String jurisdiction, final String region) {

        final Map<String, Object> processVariables = taskTypeService.getTaskVariablesFromRefData(TASK_NAME_PROCESS_APPLICATION, applicationId);

        processVariables.put(CASE_ID, caseId);
        processVariables.put(APPLICATION_ID, applicationId);
        processVariables.put(CASE_URN, urn);
        processVariables.put(DEFENDANT_ID, defendantId);
        processVariables.put(DEFENDANT_NAME, defendantName);
        processVariables.put(IS_APPLICATION_PROSECUTOR_OR_DEFENCE, isApplicationProsecutorOrDefence);
        processVariables.put(APP_TYPE, appType);
        processVariables.put(REGION, region);

        if (CROWN_JURISDICTION_TYPE.equals(jurisdiction)) {
            processVariables.put(WORK_QUEUE, CROWN_COURT_ADMIN_WORK_QUEUE_ID);
        }

        final String userId = systemUserProvider.getContextSystemUserId().map(UUID::toString).orElse(null);
        processVariables.put(LAST_UPDATED_BY_ID, userId);
        processVariables.put(LAST_UPDATED_BY_NAME, SYSTEM_USER_NAME);
        processVariables.put(JURISDICTION, jurisdiction);
        runtimeService.startProcessInstanceByKey(BPMN_PROCESS_APPLICATION_RECEIVED, applicationId, processVariables);
        LOGGER.info("New Application Created Process Started for applicationId {} with processVariables {}", applicationId, processVariables);
    }

    private String getDefendantName(final JsonObject subject) {
        String defendantName = EMPTY;
        final JsonObject personDetails = subject.containsKey("personDetails") ? subject.getJsonObject("personDetails") : null;
        if (personDetails != null) {
            final String defendantFirstName = personDetails.getString("firstName");
            final String defendantLastName = personDetails.getString("lastName");
            defendantName = defendantFirstName + SPACE + defendantLastName;
        }
        return defendantName;
    }

    private String getJurisdictionType(final JsonObject eventPayload) {
        final JsonObject courtHearing = eventPayload.containsKey("courtHearing") ? eventPayload.getJsonObject("courtHearing") : null;
        String jurisdictionType = EMPTY;
        if (courtHearing != null) {
            jurisdictionType = courtHearing.containsKey("jurisdictionType") ? courtHearing.getString("jurisdictionType") : EMPTY;
        }
        return jurisdictionType;
    }

    private String extractRegionFromPayload(JsonObject eventPayload) {
        final JsonObject courtHearing = eventPayload.getJsonObject("courtHearing");
        if (courtHearing != null) {
            final JsonObject courtCentre = courtHearing.getJsonObject("courtCentre");
            if (courtCentre != null) {
                final JsonObject address = courtCentre.getJsonObject("address");
                if (address != null) {
                    return address.getString("address2");
                }
            }
        }
        return null;
    }

}
