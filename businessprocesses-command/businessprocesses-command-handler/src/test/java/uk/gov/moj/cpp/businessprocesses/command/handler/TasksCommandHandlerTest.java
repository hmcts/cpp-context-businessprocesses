package uk.gov.moj.cpp.businessprocesses.command.handler;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static uk.gov.justice.services.core.annotation.Component.COMMAND_HANDLER;
import static uk.gov.justice.services.test.utils.core.helper.EventStreamMockHelper.verifyAppendAndGetArgumentFrom;
import static uk.gov.justice.services.test.utils.core.matchers.HandlerMatcher.isHandler;
import static uk.gov.justice.services.test.utils.core.matchers.HandlerMethodMatcher.method;
import static uk.gov.justice.services.test.utils.core.messaging.MetadataBuilderFactory.metadataWithRandomUUID;

import org.mockito.Mockito;
import uk.gov.justice.domain.aggregate.Aggregate;
import uk.gov.justice.json.schemas.businessprocesses.event.TaskAssigned;
import uk.gov.justice.json.schemas.businessprocesses.event.TaskCompleted;
import uk.gov.justice.json.schemas.businessprocesses.event.TaskCreated;
import uk.gov.justice.json.schemas.businessprocesses.event.TaskDeleted;
import uk.gov.justice.json.schemas.businessprocesses.event.TaskDueDateUpdated;
import uk.gov.justice.json.schemas.businessprocesses.event.TaskWorkqueueUpdated;
import uk.gov.justice.services.common.util.UtcClock;
import uk.gov.justice.services.core.aggregate.AggregateService;
import uk.gov.justice.services.core.enveloper.Enveloper;
import uk.gov.justice.services.eventsourcing.source.core.EventSource;
import uk.gov.justice.services.eventsourcing.source.core.EventStream;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.test.utils.core.enveloper.EnveloperFactory;
import uk.gov.moj.cpp.businessprocesses.aggregate.TaskAggregate;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import uk.gov.justice.services.messaging.JsonObjects;
import javax.json.JsonObject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.moj.cpp.businessprocesses.command.handler.service.ReferenceDataService;
import uk.gov.moj.cpp.businessprocesses.pojo.WorkQueue;

@ExtendWith(MockitoExtension.class)
public class TasksCommandHandlerTest {

    private final String ID_FIELD = "id";
    private final String CHANGE_AUTHOR_FIELD = "changeAuthor";
    private final String CHANGE_AUTHOR_ID_FIELD = "changeAuthorId";
    private final String WORK_QUEUE_FIELD = "workQueue";
    private final String WORK_QUEUE_NAME = "workQueueName";
    private final String CREATED_DATE_FIELD = "createdDate";
    private final String COURT_ID_FIELD = "courtId";
    private final String REFERENCE_FIELD = "reference";
    private final String JURISDICTION_FIELD = "jurisdiction";
    private final String DUE_DATE_FIELD = "dueDate";
    private final String HEARING_DATE_FIELD = "hearingDate";
    private final String TYPE_FIELD = "type";
    private final String ASSIGN_TO_FIELD = "assignTo";
    private final String COMPLETED_DATE_FIELD = "completedDate";
    private final String DELETED_DATE_FIELD = "deletedDate";
    private final String DELETION_REASON_FIELD = "deletionReason";
    private final String TASK_TYPE_ID_FIELD = "taskTypeId";
    private final String ASSIGN_TO_ID_FIELD = "assignToId";
    private final String DETAILS_FIELD = "details";
    private final String ASSIGNMENT_TYPE_FIELD = "assignmentType";


    private final UUID ID = randomUUID();
    private final UUID TASK_TYPE_ID_VALUE = randomUUID();
    private final String CHANGE_AUTHOR = "John Smith";
    private final UUID CHANGE_AUTHOR_ID = randomUUID();
    private final UUID WORK_QUEUE = randomUUID();
    private final ZonedDateTime CREATED_DATE = new UtcClock().now();
    private final UUID COURT_ID = randomUUID();
    private final String REFERENCE = "REF12345";
    private final String JURISDICTION = "CROWN";
    private final ZonedDateTime DUE_DATE = new UtcClock().now();
    private final ZonedDateTime HEARING_DATE = new UtcClock().now();
    private final String TYPE = "Hearing";
    private final UUID ASSIGN_TO_ID = randomUUID();
    private final String ASSIGN_TO = "john";
    private final ZonedDateTime COMPLETED_DATE = new UtcClock().now();
    private final ZonedDateTime DELETED_DATE = new UtcClock().now();
    private final String DELETION_REASON = "Task created in error";
    private final String DETAILS_PREFIX = "Assigned to: ";
    private final String ASSIGNMENT_TYPE = "ASSIGNED";


    @Mock
    private EventSource eventSource;

    @Mock
    private EventStream eventStream;

    @Mock
    private AggregateService aggregateService;

    @InjectMocks
    private TasksCommandHandler tasksCommandHandler;

    @Mock
    protected JsonEnvelope jsonEnvelope;

    @Mock
    private ReferenceDataService referenceDataService;

    @Spy
    private Enveloper enveloper = EnveloperFactory.createEnveloperWithEvents(TaskCreated.class, TaskAssigned.class, TaskCompleted.class, TaskDeleted.class, TaskDueDateUpdated.class, TaskWorkqueueUpdated.class);

    @BeforeEach
    public void createTask() {
        final TaskAggregate aggregate = new TaskAggregate();
    }

    @Test
    public void shouldHandleTasks() {
        assertThat(new TasksCommandHandler(), isHandler(COMMAND_HANDLER)
                .with(method("handleRecordTaskCreated")
                        .thatHandles("businessprocesses.command.record-task-created")
                )
                .with(method("handleRecordTaskAssigned")
                        .thatHandles("businessprocesses.command.record-task-assigned")
                )
                .with(method("handleRecordTaskCompleted")
                        .thatHandles("businessprocesses.command.record-task-completed")
                )
                .with(method("handleRecordTaskDeleted")
                        .thatHandles("businessprocesses.command.record-task-deleted")
                ));
    }

    @Test
    public void shouldCreateTask() throws Exception {

        //Given
        setupMockedEventStream(ID, this.eventStream, new TaskAggregate());
        when(this.eventSource.getStreamById(ID)).thenReturn(this.eventStream);

        final WorkQueue workQueue = Mockito.mock(WorkQueue.class);
        when (workQueue.getQueueName()).thenReturn("Queue Name");
        when(referenceDataService.getWorkQueueByWorkQueueId(any())).thenReturn(Optional.of(workQueue));

        final JsonObject commandPayload = JsonObjects.createObjectBuilder()
                .add(ID_FIELD, ID.toString())
                .add(TASK_TYPE_ID_FIELD, TASK_TYPE_ID_VALUE.toString())
                .add(REFERENCE_FIELD, REFERENCE)
                .add(CREATED_DATE_FIELD, CREATED_DATE.toString())
                .add(DUE_DATE_FIELD, DUE_DATE.toString())
                .add(HEARING_DATE_FIELD, HEARING_DATE.toString())
                .add(WORK_QUEUE_FIELD, WORK_QUEUE.toString())
                .add(COURT_ID_FIELD, COURT_ID.toString())
                .add(JURISDICTION_FIELD, JURISDICTION)
                .add(CHANGE_AUTHOR_FIELD, CHANGE_AUTHOR)
                .add(CHANGE_AUTHOR_ID_FIELD, CHANGE_AUTHOR_ID.toString())
                .add(TYPE_FIELD, TYPE)
                .build();

        final JsonEnvelope commandEnvelope = JsonEnvelope.envelopeFrom(metadataWithRandomUUID("businessprocesses.command.record-task-created").withUserId(UUID.randomUUID().toString()), commandPayload);
        tasksCommandHandler.handleRecordTaskCreated(commandEnvelope);

        final Stream<JsonEnvelope> envelopeStream = verifyAppendAndGetArgumentFrom(eventStream);

        final JsonEnvelope jsonEnvelope = envelopeStream.findFirst().orElse(null);

        assertThat(jsonEnvelope.metadata().name(), is("businessprocesses.event.task-created"));
        assertThat(jsonEnvelope.metadata().createdAt(), notNullValue());

        final JsonObject jsonObject = jsonEnvelope.payloadAsJsonObject();

        assertThat(jsonObject.getString(ID_FIELD), is(ID.toString()));
        assertThat(jsonObject.getString(TASK_TYPE_ID_FIELD), is(TASK_TYPE_ID_VALUE.toString()));
        assertThat(jsonObject.getString(REFERENCE_FIELD), is(REFERENCE));
        assertThat(jsonObject.getString(CREATED_DATE_FIELD), is(CREATED_DATE.toString()));
        assertThat(jsonObject.getString(DUE_DATE_FIELD), is(DUE_DATE.toString()));
        assertThat(jsonObject.getString(HEARING_DATE_FIELD), is(HEARING_DATE.toString()));
        assertThat(jsonObject.getString(WORK_QUEUE_FIELD), is(WORK_QUEUE.toString()));
        assertThat(jsonObject.getString(COURT_ID_FIELD), is(COURT_ID.toString()));
        assertThat(jsonObject.getString(JURISDICTION_FIELD), is(JURISDICTION));
        assertThat(jsonObject.getString(CHANGE_AUTHOR_FIELD), is(CHANGE_AUTHOR));
        assertThat(jsonObject.getString(CHANGE_AUTHOR_ID_FIELD), is(CHANGE_AUTHOR_ID.toString()));
        assertThat(jsonObject.getString(TYPE_FIELD), is(TYPE));
    }

    @Test
    public void shouldAssignTask() throws Exception {

        //Given
        setupMockedEventStream(ID, this.eventStream, new TaskAggregate());
        when(this.eventSource.getStreamById(ID)).thenReturn(this.eventStream);
        final JsonObject commandPayload = JsonObjects.createObjectBuilder()
                .add(ID_FIELD, ID.toString())
                .add(CHANGE_AUTHOR_FIELD, CHANGE_AUTHOR)
                .add(CHANGE_AUTHOR_ID_FIELD, CHANGE_AUTHOR_ID.toString())
                .add(ASSIGN_TO_FIELD, ASSIGN_TO)
                .add(ASSIGN_TO_ID_FIELD, ASSIGN_TO_ID.toString())
                .build();

        final JsonEnvelope commandEnvelope = JsonEnvelope.envelopeFrom(metadataWithRandomUUID("businessprocesses.command.record-task-assigned").withUserId(UUID.randomUUID().toString()), commandPayload);
        tasksCommandHandler.handleRecordTaskAssigned(commandEnvelope);

        final Stream<JsonEnvelope> envelopeStream = verifyAppendAndGetArgumentFrom(eventStream);

        final JsonEnvelope jsonEnvelope = envelopeStream.findFirst().orElse(null);

        assertThat(jsonEnvelope.metadata().name(), is("businessprocesses.event.task-assigned"));
        assertThat(jsonEnvelope.metadata().createdAt(), notNullValue());

        final JsonObject jsonObject = jsonEnvelope.payloadAsJsonObject();

        assertThat(jsonObject.getString(ID_FIELD), is(ID.toString()));
        assertThat(jsonObject.getString(CHANGE_AUTHOR_FIELD), is(CHANGE_AUTHOR));
        assertThat(jsonObject.getString(CHANGE_AUTHOR_ID_FIELD), is(CHANGE_AUTHOR_ID.toString()));
        assertThat(jsonObject.getString(ASSIGN_TO_FIELD), is(ASSIGN_TO_ID.toString()));
        assertThat(jsonObject.getString(DETAILS_FIELD), is(DETAILS_PREFIX + ASSIGN_TO));
        assertThat(jsonObject.getString(ASSIGNMENT_TYPE_FIELD), is(ASSIGNMENT_TYPE));
    }

    @Test
    public void shouldCompleteTask() throws Exception {

        //Given
        setupMockedEventStream(ID, this.eventStream, new TaskAggregate());
        when(this.eventSource.getStreamById(ID)).thenReturn(this.eventStream);

        final JsonObject commandPayload = JsonObjects.createObjectBuilder()
                .add(ID_FIELD, ID.toString())
                .add(CHANGE_AUTHOR_FIELD, CHANGE_AUTHOR)
                .add(CHANGE_AUTHOR_ID_FIELD, CHANGE_AUTHOR_ID.toString())
                .add(COMPLETED_DATE_FIELD, COMPLETED_DATE.toString())
                .build();

        final JsonEnvelope commandEnvelope = JsonEnvelope.envelopeFrom(metadataWithRandomUUID("businessprocesses.command.record-task-completed").withUserId(UUID.randomUUID().toString()), commandPayload);
        tasksCommandHandler.handleRecordTaskCompleted(commandEnvelope);

        final Stream<JsonEnvelope> envelopeStream = verifyAppendAndGetArgumentFrom(eventStream);

        final JsonEnvelope jsonEnvelope = envelopeStream.findFirst().orElse(null);

        assertThat(jsonEnvelope.metadata().name(), is("businessprocesses.event.task-completed"));
        assertThat(jsonEnvelope.metadata().createdAt(), notNullValue());

        final JsonObject jsonObject = jsonEnvelope.payloadAsJsonObject();

        assertThat(jsonObject.getString(ID_FIELD), is(ID.toString()));
        assertThat(jsonObject.getString(CHANGE_AUTHOR_FIELD), is(CHANGE_AUTHOR));
        assertThat(jsonObject.getString(CHANGE_AUTHOR_ID_FIELD), is(CHANGE_AUTHOR_ID.toString()));
        assertThat(getDateForCompare(jsonObject.getString(COMPLETED_DATE_FIELD)), is(getDateForCompare(COMPLETED_DATE)));
    }

    @Test
    public void shouldDeleteTask() throws Exception {

        //Given
        setupMockedEventStream(ID, this.eventStream, new TaskAggregate());
        when(this.eventSource.getStreamById(ID)).thenReturn(this.eventStream);

        final JsonObject commandPayload = JsonObjects.createObjectBuilder()
                .add(ID_FIELD, ID.toString())
                .add(CHANGE_AUTHOR_FIELD, CHANGE_AUTHOR)
                .add(CHANGE_AUTHOR_ID_FIELD, CHANGE_AUTHOR_ID.toString())
                .add(DELETION_REASON_FIELD, DELETION_REASON)
                .add(DELETED_DATE_FIELD, DELETED_DATE.toString())
                .build();

        final JsonEnvelope commandEnvelope = JsonEnvelope.envelopeFrom(metadataWithRandomUUID("businessprocesses.command.record-task-deleted").withUserId(UUID.randomUUID().toString()), commandPayload);
        tasksCommandHandler.handleRecordTaskDeleted(commandEnvelope);

        final Stream<JsonEnvelope> envelopeStream = verifyAppendAndGetArgumentFrom(eventStream);

        final JsonEnvelope jsonEnvelope = envelopeStream.findFirst().orElse(null);

        assertThat(jsonEnvelope.metadata().name(), is("businessprocesses.event.task-deleted"));
        assertThat(jsonEnvelope.metadata().createdAt(), notNullValue());

        final JsonObject jsonObject = jsonEnvelope.payloadAsJsonObject();

        assertThat(jsonObject.getString(ID_FIELD), is(ID.toString()));
        assertThat(jsonObject.getString(CHANGE_AUTHOR_FIELD), is(CHANGE_AUTHOR));
        assertThat(jsonObject.getString(CHANGE_AUTHOR_ID_FIELD), is(CHANGE_AUTHOR_ID.toString()));
        assertThat(jsonObject.getString(DELETION_REASON_FIELD), is(DELETION_REASON));
        assertThat(jsonObject.getString(DELETED_DATE_FIELD), is(DELETED_DATE.toString()));
    }

    @Test
    public void shouldUpdateTask() throws Exception {

        //Given
        setupMockedEventStream(ID, this.eventStream, new TaskAggregate());
        when(this.eventSource.getStreamById(ID)).thenReturn(this.eventStream);
        final WorkQueue mockWorkQueue = Mockito.mock(WorkQueue.class);
        when (mockWorkQueue.getQueueName()).thenReturn("Queue Name");
        when(referenceDataService.getWorkQueueByWorkQueueId(any())).thenReturn(Optional.of(mockWorkQueue));


        final JsonObject commandPayload = JsonObjects.createObjectBuilder()
                .add(ID_FIELD, ID.toString())
                .add(CHANGE_AUTHOR_FIELD, CHANGE_AUTHOR)
                .add(CHANGE_AUTHOR_ID_FIELD, CHANGE_AUTHOR_ID.toString())
                .add(WORK_QUEUE_FIELD, WORK_QUEUE.toString())
                .add(DUE_DATE_FIELD, DUE_DATE.toString())
                .build();

        final JsonEnvelope commandEnvelope = JsonEnvelope.envelopeFrom(metadataWithRandomUUID("businessprocesses.command.record-task-updated").withUserId(UUID.randomUUID().toString()), commandPayload);
        tasksCommandHandler.handleRecordTaskUpdated(commandEnvelope);

        final List<JsonEnvelope> events = verifyAppendAndGetArgumentFrom(eventStream).collect(Collectors.toList());
        assertThat(events.size(), is(2));

        final JsonEnvelope dueDateUpdatedEvent = events.get(0);
        assertThat(dueDateUpdatedEvent.metadata().name(), is("businessprocesses.event.task-due-date-updated"));
        assertThat(dueDateUpdatedEvent.metadata().createdAt(), notNullValue());

        final JsonObject dueDateEventPayload = dueDateUpdatedEvent.payloadAsJsonObject();
        assertThat(dueDateEventPayload.getString(ID_FIELD), is(ID.toString()));
        assertThat(dueDateEventPayload.getString(CHANGE_AUTHOR_FIELD), is(CHANGE_AUTHOR));
        assertThat(dueDateEventPayload.getString(CHANGE_AUTHOR_ID_FIELD), is(CHANGE_AUTHOR_ID.toString()));
        assertThat(dueDateEventPayload.getString(DUE_DATE_FIELD), is(DUE_DATE.toString()));

        final JsonEnvelope workqueueUpdatedEvent = events.get(1);
        assertThat(workqueueUpdatedEvent.metadata().name(), is("businessprocesses.event.task-workqueue-updated"));
        assertThat(workqueueUpdatedEvent.metadata().createdAt(), notNullValue());

        final JsonObject workqueueEventPayload = workqueueUpdatedEvent.payloadAsJsonObject();
        assertThat(workqueueEventPayload.getString(ID_FIELD), is(ID.toString()));
        assertThat(workqueueEventPayload.getString(CHANGE_AUTHOR_FIELD), is(CHANGE_AUTHOR));
        assertThat(workqueueEventPayload.getString(CHANGE_AUTHOR_ID_FIELD), is(CHANGE_AUTHOR_ID.toString()));
        assertThat(workqueueEventPayload.getString(WORK_QUEUE_FIELD), is(WORK_QUEUE.toString()));
        assertThat(workqueueEventPayload.getString(WORK_QUEUE_NAME), is("Queue Name"));
    }

    @Test
    public void shouldUpdateTask_WhenWorkQueueIsNotReturned_FromReferenceDataContext() throws Exception {

        //Given
        setupMockedEventStream(ID, this.eventStream, new TaskAggregate());
        when(this.eventSource.getStreamById(ID)).thenReturn(this.eventStream);
        when(referenceDataService.getWorkQueueByWorkQueueId(any())).thenReturn(Optional.empty());


        final JsonObject commandPayload = JsonObjects.createObjectBuilder()
                .add(ID_FIELD, ID.toString())
                .add(CHANGE_AUTHOR_FIELD, CHANGE_AUTHOR)
                .add(CHANGE_AUTHOR_ID_FIELD, CHANGE_AUTHOR_ID.toString())
                .add(WORK_QUEUE_FIELD, WORK_QUEUE.toString())
                .add(DUE_DATE_FIELD, DUE_DATE.toString())
                .build();

        final JsonEnvelope commandEnvelope = JsonEnvelope.envelopeFrom(metadataWithRandomUUID("businessprocesses.command.record-task-updated").withUserId(UUID.randomUUID().toString()), commandPayload);
        tasksCommandHandler.handleRecordTaskUpdated(commandEnvelope);

        final List<JsonEnvelope> events = verifyAppendAndGetArgumentFrom(eventStream).collect(Collectors.toList());
        assertThat(events.size(), is(2));

        final JsonEnvelope dueDateUpdatedEvent = events.get(0);
        assertThat(dueDateUpdatedEvent.metadata().name(), is("businessprocesses.event.task-due-date-updated"));
        assertThat(dueDateUpdatedEvent.metadata().createdAt(), notNullValue());

        final JsonObject dueDateEventPayload = dueDateUpdatedEvent.payloadAsJsonObject();
        assertThat(dueDateEventPayload.getString(ID_FIELD), is(ID.toString()));
        assertThat(dueDateEventPayload.getString(CHANGE_AUTHOR_FIELD), is(CHANGE_AUTHOR));
        assertThat(dueDateEventPayload.getString(CHANGE_AUTHOR_ID_FIELD), is(CHANGE_AUTHOR_ID.toString()));
        assertThat(dueDateEventPayload.getString(DUE_DATE_FIELD), is(DUE_DATE.toString()));

        final JsonEnvelope workqueueUpdatedEvent = events.get(1);
        assertThat(workqueueUpdatedEvent.metadata().name(), is("businessprocesses.event.task-workqueue-updated"));
        assertThat(workqueueUpdatedEvent.metadata().createdAt(), notNullValue());

        final JsonObject workqueueEventPayload = workqueueUpdatedEvent.payloadAsJsonObject();
        assertThat(workqueueEventPayload.getString(ID_FIELD), is(ID.toString()));
        assertThat(workqueueEventPayload.getString(CHANGE_AUTHOR_FIELD), is(CHANGE_AUTHOR));
        assertThat(workqueueEventPayload.getString(CHANGE_AUTHOR_ID_FIELD), is(CHANGE_AUTHOR_ID.toString()));
        assertThat(workqueueEventPayload.getString(WORK_QUEUE_FIELD), is(WORK_QUEUE.toString()));
        assertThat(workqueueEventPayload.containsKey(WORK_QUEUE_NAME), is(false));
    }

    @SuppressWarnings("unchecked")
    private <T extends Aggregate> void setupMockedEventStream(final UUID id, final EventStream eventStream, final T aggregate) {
        when(this.eventSource.getStreamById(id)).thenReturn(eventStream);
        final Class<T> clz = (Class<T>) aggregate.getClass();
        when(this.aggregateService.get(eventStream, clz)).thenReturn(aggregate);
    }
    
    private String getDateForCompare(ZonedDateTime date) {
        Date d = Date.from(date.toInstant());
        DateFormat df = new SimpleDateFormat("yyMMdd-HHmm");
        return df.format(d);
    }

    private String getDateForCompare(String date) throws ParseException {
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

        Date d = dateFormat.parse(date);
        DateFormat df = new SimpleDateFormat("yyMMdd-HHmm");
        return df.format(d);
    }
}
