package uk.gov.moj.cpp.businessprocesses.event;

import static java.util.Objects.nonNull;
import static uk.gov.justice.services.core.annotation.Component.EVENT_PROCESSOR;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.BPMN_PROCESS_TRIAGE_INCOMING_DOCUMENT_IDENTIFIED;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.CASE_JURISDICTION_TYPE;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.CROWN_COURT_ADMIN_WORK_QUEUE_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.CROWN_JURISDICTION_TYPE;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.HEARINGS_AT_A_GLANCE;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.SYSTEM_USER_NAME;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.TASK_NAME_TRIAGE_INCOMING_DOCUMENT;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.CASE_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.CASE_URN;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.DEFENDANT_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.DEFENDANT_NAME;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.DOCUMENT_TYPE_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.LAST_UPDATED_BY_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.LAST_UPDATED_BY_NAME;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.SECTION;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.SECTION_APPLICATIONS;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.SECTION_CORRESPONDENCE;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.SECTION_PLEA;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.WORK_QUEUE;

import uk.gov.justice.services.core.annotation.Handles;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.core.dispatcher.SystemUserProvider;
import uk.gov.justice.services.core.featurecontrol.FeatureControlGuard;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.moj.cpp.businessprocesses.service.ProgressionService;
import uk.gov.moj.cpp.businessprocesses.service.ReferenceDataService;
import uk.gov.moj.cpp.businessprocesses.service.TaskTypeService;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.inject.Inject;
import javax.json.JsonObject;

import org.camunda.bpm.engine.RuntimeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ServiceComponent(EVENT_PROCESSOR)
public class DocumentEventProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentEventProcessor.class);

    @Inject
    private RuntimeService runtimeService;

    @Inject
    private TaskTypeService taskTypeService;

    @Inject
    private ReferenceDataService referenceDataService;

    @Inject
    private ProgressionService progressionService;

    @Inject
    private SystemUserProvider systemUserProvider;

    @Inject
    private FeatureControlGuard featureControlGuard;

    @Handles("public.progression.document-added")
    public void handleDocumentAddedProcessor(final JsonEnvelope jsonEnvelope) {
        if (featureControlGuard.isFeatureEnabled("camunda-progression-document-added")) {
            handleDocumentAdded(jsonEnvelope);
        }
    }

    public void handleDocumentAdded(final JsonEnvelope jsonEnvelope) {

        LOGGER.info("Received the event Triage Incoming Document");
        final JsonObject eventPayload = jsonEnvelope.payloadAsJsonObject();
        final String documentTypeId = eventPayload.getString(DOCUMENT_TYPE_ID);

        final Optional<JsonObject> documentType = referenceDataService.getDocumentType(jsonEnvelope, documentTypeId);
        if (documentType.isPresent()) {
            final String section = documentType.get().getString(SECTION);
            if (section.equals(SECTION_APPLICATIONS) || section.equals(SECTION_PLEA) || section.equals(SECTION_CORRESPONDENCE)) {
                final String caseId = eventPayload.getString(CASE_ID);
                final String caseURN = eventPayload.getString(CASE_URN);
                final String defendantId = eventPayload.getString(DEFENDANT_ID);
                final String defendantName = eventPayload.getString(DEFENDANT_NAME);
                final Map<String, Object> processVariables = taskTypeService.getTaskVariablesFromRefData(TASK_NAME_TRIAGE_INCOMING_DOCUMENT, caseId);

                final String userId = systemUserProvider.getContextSystemUserId().map(UUID::toString).orElse(null);
                processVariables.put(LAST_UPDATED_BY_ID, userId);
                processVariables.put(LAST_UPDATED_BY_NAME, SYSTEM_USER_NAME);
                processVariables.put(CASE_ID, caseId);
                processVariables.put(CASE_URN, caseURN);
                processVariables.put(DEFENDANT_ID, defendantId);
                processVariables.put(DEFENDANT_NAME, defendantName);

                final JsonObject jsonObject = progressionService.getProsecutionCase(caseId);
                final String jurisdictionType = nonNull(jsonObject.getJsonObject(HEARINGS_AT_A_GLANCE)) ? jsonObject.getJsonObject(HEARINGS_AT_A_GLANCE).getString(CASE_JURISDICTION_TYPE, null) : null;
                if (nonNull(jurisdictionType) && CROWN_JURISDICTION_TYPE.equals(jurisdictionType)) {
                    processVariables.put(WORK_QUEUE, CROWN_COURT_ADMIN_WORK_QUEUE_ID);
                }

                runtimeService.startProcessInstanceByKey(BPMN_PROCESS_TRIAGE_INCOMING_DOCUMENT_IDENTIFIED, caseId, processVariables);
            }
        }
    }
}
