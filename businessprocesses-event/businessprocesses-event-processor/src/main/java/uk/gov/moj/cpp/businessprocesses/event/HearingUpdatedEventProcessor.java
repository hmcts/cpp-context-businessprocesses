package uk.gov.moj.cpp.businessprocesses.event;


import static uk.gov.justice.services.core.annotation.Component.EVENT_PROCESSOR;
import uk.gov.justice.services.core.annotation.Handles;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.core.featurecontrol.FeatureControlGuard;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.moj.cpp.businessprocesses.shared.Constants;
import uk.gov.moj.cpp.businessprocesses.shared.InterpreterForWelshActivityHandler;

import javax.inject.Inject;
import javax.json.JsonObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ServiceComponent(EVENT_PROCESSOR)
@SuppressWarnings({"squid:S3457"})
public class HearingUpdatedEventProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(HearingUpdatedEventProcessor.class);

    @Inject
    private FeatureControlGuard featureControlGuard;

    @Inject
    private InterpreterForWelshActivityHandler interpreterForWelshActivityHandler;

    @Handles("public.events.hearing.hearing-detail-changed")
    public void handleHearingDetailsChanged(final JsonEnvelope jsonEnvelope) {
        LOGGER.info("Event public.events.hearing.hearing-detail-changed {} ", jsonEnvelope);
        if (featureControlGuard.isFeatureEnabled("camunda-hearing-initiated")) {
            final JsonObject eventPayload = jsonEnvelope.payloadAsJsonObject();
            final String hearingId = eventPayload.getString(Constants.ID);
            interpreterForWelshActivityHandler.handleWelshInterpreterForHearingUpdated(hearingId);
        }
    }

    @Handles("public.events.hearing.defendant-details-updated")
    public void handleHearingDefendantDetailsChangedEvent(final JsonEnvelope jsonEnvelope) {
        LOGGER.info("Event public.events.hearing.defendant-details-updated {} ", jsonEnvelope);
        if (featureControlGuard.isFeatureEnabled("camunda-hearing-initiated")) {
            final JsonObject eventPayload = jsonEnvelope.payloadAsJsonObject();
            final String hearingId = eventPayload.getString(Constants.HEARING_ID);
            interpreterForWelshActivityHandler.handleWelshInterpreterForHearingUpdated(hearingId);
        }
    }

}
