package uk.gov.moj.cpp.businessprocesses.event;

import static uk.gov.justice.services.core.annotation.Component.EVENT_PROCESSOR;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.BPMN_PROCESS_DELETE_FINANCIAL_MEANS_INFORMATION;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.CASE_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.DEFENDANT_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.DELAY_BEFORE_FINANCIAL_MEANS_DELETION;

import uk.gov.justice.services.common.configuration.Value;
import uk.gov.justice.services.core.annotation.Handles;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.messaging.JsonEnvelope;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.json.JsonObject;

import org.camunda.bpm.engine.RuntimeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ServiceComponent(EVENT_PROCESSOR)
public class FinancialMeansInformationEventProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(FinancialMeansInformationEventProcessor.class);

    @Inject
    @Value(key = "delayBeforeFinancialMeansDeletion", defaultValue = "P28D")
    private String delayBeforeFinancialMeansDeletion;

    @Inject
    private RuntimeService runtimeService;

    @Handles("public.sjp.all-offences-for-defendant-dismissed-or-withdrawn")
    public void handleAllOffencesForDefendantDismissedOrWithdrawn(final JsonEnvelope jsonEnvelope) {

        LOGGER.info("Received the event from SJP that triggers delete defendant's financial means information.");

        final JsonObject eventPayload = jsonEnvelope.payloadAsJsonObject();
        final String caseId = eventPayload.getString(CASE_ID);
        final String defendantId = eventPayload.getString(DEFENDANT_ID);
        startDeleteFinancialMeansProcess(caseId, defendantId);
    }

    @Handles("public.hearing.defendant-case-withdrawn-or-dismissed")
    public void handleDefendantCaseWithdrawnOrDismissed(final JsonEnvelope jsonEnvelope) {

        LOGGER.info("Received the event from Hearing that triggers delete defendant's financial means information.");

        final JsonObject eventPayload = jsonEnvelope.payloadAsJsonObject();
        final String caseId = eventPayload.getString(CASE_ID);
        final String defendantId = eventPayload.getString(DEFENDANT_ID);
        startDeleteFinancialMeansProcess(caseId, defendantId);
    }

    private void startDeleteFinancialMeansProcess(final String caseId, final String defendantId) {
        final Map<String, Object> processVariables = new HashMap<>();
        processVariables.put(CASE_ID, caseId);
        processVariables.put(DEFENDANT_ID, defendantId);
        processVariables.put(DELAY_BEFORE_FINANCIAL_MEANS_DELETION, delayBeforeFinancialMeansDeletion);
        runtimeService.startProcessInstanceByKey(BPMN_PROCESS_DELETE_FINANCIAL_MEANS_INFORMATION, caseId, processVariables);

        LOGGER.info("Delete financial means information process started. Will wait for {} before requesting the data to be deleted", delayBeforeFinancialMeansDeletion);
    }

}
