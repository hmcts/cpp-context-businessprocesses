package uk.gov.moj.cpp.businessprocesses.service;

import static java.util.UUID.randomUUID;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;
import static org.slf4j.LoggerFactory.getLogger;
import static uk.gov.justice.services.core.annotation.Component.EVENT_PROCESSOR;
import static uk.gov.justice.services.messaging.Envelope.metadataBuilder;
import static uk.gov.justice.services.messaging.JsonEnvelope.envelopeFrom;

import uk.gov.justice.courts.progression.query.Caag;
import uk.gov.justice.services.common.converter.jackson.ObjectMapperProducer;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.justice.services.messaging.JsonEnvelope;

import java.io.IOException;

import javax.inject.Inject;
import javax.json.JsonObject;
import javax.json.JsonValue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;

public class ProgressionService {

    private static final Logger LOGGER = getLogger(ProgressionService.class);
    private static final String PROGRESSION_QUERY_PROSECUTION_CASE_CAAG = "progression.query.prosecutioncase.caag";
    private static final String PROGRESSION_QUERY_PROSECUTION_CASE = "progression.query.prosecutioncase";

    private static final String PROGRESSION_QUERY_PROSECUTION_CASE_EXIST_BY_CASE_URN = "progression.query.case-exist-by-caseurn";
    private static final ObjectMapper objectMapper = new ObjectMapperProducer().objectMapper();

    private static final String CASE_ID = "caseId";

    @Inject
    @ServiceComponent(EVENT_PROCESSOR)
    private Requester requester;


    public Caag getProsecutionCaseCaag(final String userId, final String caseId) {
        LOGGER.info("calling progression prosecutioncase with case id : {}", caseId);
        final JsonEnvelope queryEnvelope = envelopeFrom(metadataBuilder()
                        .withId(randomUUID())
                        .withName(PROGRESSION_QUERY_PROSECUTION_CASE_CAAG)
                        .withUserId(userId),
                createObjectBuilder().
                        add(CASE_ID, caseId));

        final JsonValue response = requester.request(queryEnvelope, JsonObject.class).payload();

        Caag caag = null;
        if (null != response) {
            try {
                caag = objectMapper.readValue(response.toString(), Caag.class);
            } catch (IOException e) {
                throw new IllegalStateException("Unable to unmarshal prosecutioncase", e);
            }
        }
        return caag;
    }

    public JsonObject getProsecutionCase(final String caseId) {
        LOGGER.info("calling progression prosecutioncase with case id : {}", caseId);
        final JsonObject query = createObjectBuilder()
                .add(CASE_ID, caseId)
                .build();

        final JsonEnvelope jsonEnvelope = envelopeFrom(
                JsonEnvelope.metadataBuilder()
                        .withName(PROGRESSION_QUERY_PROSECUTION_CASE)
                        .withId(randomUUID())
                        .build(),
                query);

        return requester.requestAsAdmin(jsonEnvelope, JsonObject.class).payload();
    }

    public String getProsecutionCaseExistByCaseUrn(final String caseUrn) {
        LOGGER.info("calling progression prosecutioncase exist by case urn : {}", caseUrn);
        final JsonObject query = createObjectBuilder()
                .add("caseUrn", caseUrn)
                .build();

        final JsonEnvelope jsonEnvelope = envelopeFrom(
                JsonEnvelope.metadataBuilder()
                        .withName(PROGRESSION_QUERY_PROSECUTION_CASE_EXIST_BY_CASE_URN)
                        .withId(randomUUID())
                        .build(),
                query);

        final JsonObject responsePayload = requester.requestAsAdmin(jsonEnvelope, JsonObject.class).payload();

        if(responsePayload != null && responsePayload.containsKey(CASE_ID)) {
            return responsePayload.getString(CASE_ID);
        }
        return null;
    }
}
