package uk.gov.moj.cpp.businessprocesses.service;

import static java.util.Collections.emptyList;
import static java.util.Optional.ofNullable;
import static java.util.UUID.randomUUID;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;
import static org.slf4j.LoggerFactory.getLogger;
import static uk.gov.justice.services.core.annotation.Component.EVENT_PROCESSOR;
import static uk.gov.justice.services.messaging.Envelope.metadataBuilder;
import static uk.gov.justice.services.messaging.JsonEnvelope.envelopeFrom;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.CASE_ID;

import uk.gov.justice.listing.events.Hearing;
import uk.gov.justice.services.common.converter.jackson.ObjectMapperProducer;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.moj.cpp.businessprocesses.json.schemas.listing.Hearings;

import java.io.IOException;
import java.util.List;

import javax.inject.Inject;
import javax.json.JsonObject;
import javax.json.JsonValue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;

public class ListingService {

    private static final Logger LOGGER = getLogger(ListingService.class);
    private static final String LISTING_SEARCH_HEARINGS_QUERY = "listing.allocated.and.unallocated.hearings";
    private static final ObjectMapper objectMapper = new ObjectMapperProducer().objectMapper();

    @Inject
    @ServiceComponent(EVENT_PROCESSOR)
    private Requester requester;

    public List<Hearing> getHearings(final String userId, final String caseId) {
        LOGGER.info("calling listing allocated unallocated hearings with case id : {}", caseId);
        final JsonEnvelope queryEnvelope = envelopeFrom(metadataBuilder()
                        .withId(randomUUID())
                        .withName(LISTING_SEARCH_HEARINGS_QUERY)
                        .withUserId(userId),
                createObjectBuilder().
                        add(CASE_ID, caseId));

        final JsonValue response = requester.request(queryEnvelope, JsonObject.class).payload();

        Hearings hearings = null;
        if (null != response) {
            try {
                hearings = objectMapper.readValue(response.toString(), Hearings.class);
            } catch (IOException e) {
                throw new IllegalStateException("Unable to unmarshal Hearings", e);

            }
        }

        return ofNullable(hearings).map(Hearings::getHearings).orElse(emptyList());
    }
}
