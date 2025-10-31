package uk.gov.moj.cpp.businessprocesses.event;

import static java.util.stream.Collectors.toList;
import static uk.gov.justice.services.core.annotation.Component.EVENT_PROCESSOR;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.BPMN_PROCESS_BCM_WELSH_LANGUAGE_TRANSLATION_REQUEST;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.DOCUMENT_META_DATA;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.HEARING_DATE_TIME;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.IS_WELSH;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.SYSTEM_USER_NAME;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.TASK_NAME_REQUEST_WLU_TO_TRANSLATE_WELSH_DOCUMENT;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.CASE_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.CASE_URN;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.COURT_FORM_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.HEARING_DATE;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.LAST_UPDATED_BY_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.LAST_UPDATED_BY_NAME;

import uk.gov.justice.services.core.annotation.Handles;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.core.dispatcher.SystemUserProvider;
import uk.gov.justice.services.core.featurecontrol.FeatureControlGuard;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.moj.cpp.businessprocesses.service.TaskTypeService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.inject.Inject;
import javax.json.JsonObject;

import org.camunda.bpm.engine.RuntimeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ServiceComponent(EVENT_PROCESSOR)
public class FormFinalisedPublicEventProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(FormFinalisedPublicEventProcessor.class);

    @Inject
    private RuntimeService runtimeService;

    @Inject
    private TaskTypeService taskTypeService;

    @Inject
    private SystemUserProvider systemUserProvider;

    @Inject
    private FeatureControlGuard featureControlGuard;

    @Handles("public.progression.form-finalised")
    public void handleFormFinalisedProcessor(final JsonEnvelope jsonEnvelope) {
        if (featureControlGuard.isFeatureEnabled("camunda-progression-form-finalised")) {
            handleFormFinalised(jsonEnvelope);
        }
    }

    public void handleFormFinalised(final JsonEnvelope jsonEnvelope) {

        LOGGER.info("Received the event from Progression that triggers finalised form event");

        final JsonObject eventPayload = jsonEnvelope.payloadAsJsonObject();
        final List<Boolean> isWelshList = eventPayload.getJsonArray(DOCUMENT_META_DATA).getValuesAs(JsonObject.class).stream().map(item -> item.getBoolean(IS_WELSH)).collect(toList());

        if (isWelshList.contains(true)) {
            startWelshDocumentTranslationProcess(eventPayload);
        }
    }

    private void startWelshDocumentTranslationProcess(final JsonObject formData) {
        final Map<String, Object> processVariables = taskTypeService.getTaskVariablesFromRefData(TASK_NAME_REQUEST_WLU_TO_TRANSLATE_WELSH_DOCUMENT, formData.getString(CASE_ID));
        final String courtFormId = formData.getString(COURT_FORM_ID);
        processVariables.put(COURT_FORM_ID, courtFormId);
        processVariables.put(CASE_ID, formData.getString(CASE_ID));
        processVariables.put(CASE_URN, formData.getString(CASE_URN, null));
        final String userId = systemUserProvider.getContextSystemUserId().map(UUID::toString).orElse(null);
        processVariables.put(LAST_UPDATED_BY_ID, userId);
        processVariables.put(LAST_UPDATED_BY_NAME, SYSTEM_USER_NAME);
        processVariables.put(HEARING_DATE, formData.getString(HEARING_DATE_TIME, null));

        runtimeService.startProcessInstanceByKey(BPMN_PROCESS_BCM_WELSH_LANGUAGE_TRANSLATION_REQUEST, courtFormId, processVariables);

        LOGGER.info("Welsh Language Translation process started with key as courtFormId {}", courtFormId);
    }

}
