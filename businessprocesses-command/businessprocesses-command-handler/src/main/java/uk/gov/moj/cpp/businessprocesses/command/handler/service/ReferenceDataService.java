package uk.gov.moj.cpp.businessprocesses.command.handler.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import uk.gov.justice.services.common.converter.jackson.ObjectMapperProducer;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.messaging.MetadataBuilder;
import uk.gov.moj.cpp.businessprocesses.pojo.WorkQueue;

import javax.inject.Inject;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonValue;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import static java.util.UUID.randomUUID;
import static javax.json.Json.createObjectBuilder;
import static org.slf4j.LoggerFactory.getLogger;
import static uk.gov.justice.services.core.annotation.Component.COMMAND_HANDLER;
import static uk.gov.justice.services.messaging.Envelope.metadataBuilder;
import static uk.gov.justice.services.messaging.JsonEnvelope.envelopeFrom;

public class ReferenceDataService {
    private static final Logger LOGGER = getLogger(ReferenceDataService.class);


    private static final String REFERENCE_DATA_QUERY_WORK_QUEUES = "referencedata.query.work-queues";
    private static final String WORK_QUEUE_ID = "workQueueId";
    public static final String WORK_QUEUES = "workQueues";

    private static final ObjectMapper objectMapper = new ObjectMapperProducer().objectMapper();


    @Inject
    @ServiceComponent(COMMAND_HANDLER)
    private Requester requester;

    public Optional<WorkQueue> getWorkQueueByWorkQueueId(final UUID workQueueId) {
        final JsonObject payload = createObjectBuilder()
                .add(WORK_QUEUE_ID, workQueueId.toString())
                .build();
        final MetadataBuilder metadataBuilder = metadataBuilder()
                .withId(randomUUID())
                .withName(REFERENCE_DATA_QUERY_WORK_QUEUES);
        final Envelope<JsonObject> envelope = requester.requestAsAdmin(envelopeFrom(metadataBuilder, payload), JsonObject.class);

        return mapToWorkQueue(envelope);
    }

    public static Optional<WorkQueue> mapToWorkQueue(final Envelope<JsonObject> jsonObjectEnvelope) {
        final JsonArray jsonArray = jsonObjectEnvelope.payload().getJsonArray(WORK_QUEUES);
        return jsonArray.stream()
                .map(mapToWorkQueues())
                .toList()
                .stream()
                .findFirst();
    }

    private static Function<JsonValue, WorkQueue> mapToWorkQueues() {
        return jsonValue -> {
            try {
                return objectMapper.readValue(jsonValue.toString(), WorkQueue.class);
            } catch (IOException ex) {
                LOGGER.error("Unable to unmarshal WorkQueue. Payload :{}", jsonValue, ex);
                return null;
            }
        };
    }
}
