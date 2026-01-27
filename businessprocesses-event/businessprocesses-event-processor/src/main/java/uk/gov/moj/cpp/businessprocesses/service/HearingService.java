package uk.gov.moj.cpp.businessprocesses.service;

import static java.util.UUID.randomUUID;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;
import static org.slf4j.LoggerFactory.getLogger;
import static uk.gov.justice.services.core.annotation.Component.EVENT_PROCESSOR;
import static uk.gov.justice.services.messaging.Envelope.metadataBuilder;
import static uk.gov.justice.services.messaging.JsonEnvelope.envelopeFrom;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.HEARING_ID;

import uk.gov.justice.core.courts.Hearing;
import uk.gov.justice.services.common.converter.jackson.ObjectMapperProducer;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.justice.services.messaging.JsonEnvelope;

import java.io.IOException;

import javax.inject.Inject;
import javax.json.JsonObject;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;

public class HearingService {

    private static final Logger LOGGER = getLogger(HearingService.class);
    private static final String HEARING_GET_HEARING_QUERY = "hearing.get.hearing";
    private static final ObjectMapper objectMapper = new ObjectMapperProducer().objectMapper();

    @Inject
    @ServiceComponent(EVENT_PROCESSOR)
    private Requester requester;

    public Hearing getHearing(final String hearingId) {
        LOGGER.info("calling hearing.get-haring with hearing Id : {}", hearingId);
        final JsonEnvelope queryEnvelope = envelopeFrom(metadataBuilder()
                        .withId(randomUUID())
                        .withName(HEARING_GET_HEARING_QUERY),
                createObjectBuilder().
                        add(HEARING_ID, hearingId));

        final JsonObject response = requester.requestAsAdmin(queryEnvelope, JsonObject.class).payload();

        Hearing hearing = null;
        if (null != response && response.get("hearing") != null) {
            try {
                hearing = objectMapper.readValue(response.get("hearing").toString(), Hearing.class);
            } catch (IOException e) {
                throw new IllegalStateException("Unable to unmarshal Hearings", e);

            }
        }

        return hearing;
    }
}
