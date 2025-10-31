package uk.gov.moj.cpp.businessprocesses.command.handler.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.services.common.converter.JsonObjectToObjectConverter;
import uk.gov.justice.services.common.converter.ObjectToJsonObjectConverter;
import uk.gov.justice.services.common.converter.jackson.ObjectMapperProducer;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.moj.cpp.businessprocesses.pojo.WorkQueue;

import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import java.util.Optional;
import java.util.UUID;

import static java.lang.Boolean.TRUE;
import static java.util.UUID.randomUUID;
import static javax.json.Json.createArrayBuilder;
import static javax.json.Json.createObjectBuilder;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static uk.gov.justice.services.messaging.Envelope.envelopeFrom;
import static uk.gov.justice.services.test.utils.core.messaging.MetadataBuilderFactory.metadataWithRandomUUID;
import static uk.gov.justice.services.test.utils.core.reflection.ReflectionUtil.setField;

@ExtendWith(MockitoExtension.class)
public class ReferenceDataServiceTest {

    private static final String REFERENCE_DATA_QUERY_WORK_QUEUES = "referencedata.query.work-queues";

    public static final String WORK_QUEUES = "workQueues";

    private static final UUID FIRST_WORK_QUEUE_ID = randomUUID();

    @Captor
    private ArgumentCaptor<JsonEnvelope> envelopeArgumentCaptor;

    @Mock
    private Requester requester;

    @InjectMocks
    private ReferenceDataService target;

    @Spy
    private final ObjectMapper objectMapper = new ObjectMapperProducer().objectMapper();

    @Spy
    private final JsonObjectToObjectConverter jsonToObjectConverter = new JsonObjectToObjectConverter(objectMapper);

    @Spy
    private ObjectToJsonObjectConverter objectToJsonObjectConverter;

    @BeforeEach
    public void setUp() {
        setField(objectToJsonObjectConverter, "mapper", new ObjectMapperProducer().objectMapper());
    }

    @Test
    public void shouldGetWorkflowTaskType() {
        when(requester.requestAsAdmin(any(Envelope.class), eq(JsonObject.class))).thenReturn(getWorkQueueListWithOneElement());
        Optional<WorkQueue> optionalWorkQueue = target.getWorkQueueByWorkQueueId(FIRST_WORK_QUEUE_ID);
        assertThat(optionalWorkQueue.isPresent(), is(TRUE));
        assertThat(optionalWorkQueue.get().getQueueName(), is("Welsh Unit"));
    }

    @Test
    public void shouldGetWorkQueue() {
        when(requester.requestAsAdmin(any(Envelope.class), eq(JsonObject.class))).thenReturn(getWorkQueueListWithOneElement());
        Optional<WorkQueue> optionalWorkQueue = target.getWorkQueueByWorkQueueId(FIRST_WORK_QUEUE_ID);
        assertThat(optionalWorkQueue.isPresent(), is(TRUE));
        final WorkQueue workQueue = optionalWorkQueue.get();
        assertThat(workQueue.getQueueName(), is("Welsh Unit"));
        assertThat(workQueue.getId(), is(FIRST_WORK_QUEUE_ID));
        assertThat(workQueue.getSeqNum(), is(10));
        assertThat(workQueue.getCrownFlag(), is(true));
        assertThat(workQueue.getMagistratesFlag(), is(false));
    }

    @Test
    public void shouldGetEmptyWorkQueueList() {
        when(requester.requestAsAdmin(any(Envelope.class), eq(JsonObject.class))).thenReturn(getEmptyWorkQueueList());
        Optional<WorkQueue> optionalWorkQueue = target.getWorkQueueByWorkQueueId(randomUUID());
        assertThat(optionalWorkQueue.isPresent(), is(false));
    }

    private Envelope<JsonObject> getWorkQueueListWithOneElement() {
        final JsonArrayBuilder workQueueBuilder = createArrayBuilder();

        final JsonObject workQueue = createObjectBuilder()
                .add("id", FIRST_WORK_QUEUE_ID.toString())
                .add("seqNum", 10)
                .add("queueName", "Welsh Unit")
                .add("crownFlag", true)
                .add("magistratesFlag", false)
                .add("validFrom", "2024-09-17")
                .add("validTo", "2025-09-17")
                .build();

        return envelopeFrom(
                metadataWithRandomUUID(REFERENCE_DATA_QUERY_WORK_QUEUES),
                createObjectBuilder()
                        .add(WORK_QUEUES, workQueueBuilder
                                .add(workQueue))
                        .build());
    }

    private Envelope<JsonObject> getEmptyWorkQueueList() {
        final JsonArrayBuilder workQueueBuilder = createArrayBuilder();

        return envelopeFrom(
                metadataWithRandomUUID(REFERENCE_DATA_QUERY_WORK_QUEUES),
                createObjectBuilder()
                        .add(WORK_QUEUES, workQueueBuilder.build())
                        .build());
    }
}