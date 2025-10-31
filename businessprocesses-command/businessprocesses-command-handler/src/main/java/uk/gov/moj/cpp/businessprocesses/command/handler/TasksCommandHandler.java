package uk.gov.moj.cpp.businessprocesses.command.handler;

import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static uk.gov.justice.services.core.annotation.Component.COMMAND_HANDLER;
import static uk.gov.justice.services.core.enveloper.Enveloper.toEnvelopeWithMetadataFrom;

import uk.gov.justice.services.common.converter.ZonedDateTimes;
import uk.gov.justice.services.core.aggregate.AggregateService;
import uk.gov.justice.services.core.annotation.Handles;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.eventsourcing.source.core.EventSource;
import uk.gov.justice.services.eventsourcing.source.core.EventStream;
import uk.gov.justice.services.eventsourcing.source.core.exception.EventStreamException;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.moj.cpp.businessprocesses.aggregate.TaskAggregate;

import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.json.JsonObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.moj.cpp.businessprocesses.command.handler.service.ReferenceDataService;
import uk.gov.moj.cpp.businessprocesses.pojo.WorkQueue;

@ServiceComponent(COMMAND_HANDLER)
public class TasksCommandHandler extends AbstractCommandHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(TasksCommandHandler.class);

    private static final String ID_FIELD = "id";
    private static final String CHANGE_AUTHOR_FIELD = "changeAuthor";
    private static final String CHANGE_AUTHOR_ID_FIELD = "changeAuthorId";
    private static final String WORK_QUEUE_FIELD = "workQueue";
    private static final String CREATED_DATE_FIELD = "createdDate";
    private static final String COURT_ID_FIELD = "courtId";
    private static final String REFERENCE_FIELD = "reference";
    private static final String NOTE_FIELD = "note";
    private static final String JURISDICTION_FIELD = "jurisdiction";
    private static final String DUE_DATE_FIELD = "dueDate";
    private static final String HEARING_DATE_FIELD = "hearingDate";
    private static final String ASSIGN_TO_FIELD = "assignTo";
    private static final String ASSIGN_TO_ID_FIELD = "assignToId";
    private static final String COMPLETED_DATE_FIELD = "completedDate";
    private static final String DELETED_DATE_FIELD = "deletedDate";
    private static final String DELETION_REASON_FIELD = "deletionReason";
    private static final String TASK_TYPE_FIELD = "type";
    private static final String TASK_TYPE_ID = "taskTypeId";

    @Inject
    private EventSource eventSource;

    @Inject
    private AggregateService aggregateService;

    @Inject
    private ReferenceDataService referenceDataService;

    @Handles("businessprocesses.command.record-task-created")
    public void handleRecordTaskCreated(final JsonEnvelope envelope) throws EventStreamException {

        LOGGER.debug("businessprocesses.command.record-task-created {}", envelope.payload());
        final JsonObject createTaskPayload = envelope.payloadAsJsonObject();

        final UUID taskId = extractId(createTaskPayload.getString(ID_FIELD));
        final UUID taskTypeId = extractId(createTaskPayload.getString(TASK_TYPE_ID));
        final UUID changeAuthorId = extractId(createTaskPayload.getString(CHANGE_AUTHOR_ID_FIELD));
        final String changeAuthor = createTaskPayload.getString(CHANGE_AUTHOR_FIELD);
        final ZonedDateTime createdDate = extractDate(createTaskPayload.getString(CREATED_DATE_FIELD));
        final String taskType = createTaskPayload.getString(TASK_TYPE_FIELD);


        final UUID workQueue = extractId(createTaskPayload.containsKey(WORK_QUEUE_FIELD) ? createTaskPayload.getString(WORK_QUEUE_FIELD) : null);
        final String workQueueName = nonNull(workQueue) ? referenceDataService.getWorkQueueByWorkQueueId(workQueue).get().getQueueName() : null;

        final UUID courtId = extractId(createTaskPayload.containsKey(COURT_ID_FIELD) ? createTaskPayload.getString(COURT_ID_FIELD) : null);

        final String reference = createTaskPayload.containsKey(REFERENCE_FIELD) ? createTaskPayload.getString(REFERENCE_FIELD) : null;
        final String note = createTaskPayload.containsKey(NOTE_FIELD) ? createTaskPayload.getString(NOTE_FIELD) : EMPTY;
        final String jurisdiction = createTaskPayload.containsKey(JURISDICTION_FIELD) ? createTaskPayload.getString(JURISDICTION_FIELD) : null;

        final ZonedDateTime dueDate = extractDate(createTaskPayload.containsKey(DUE_DATE_FIELD) ? createTaskPayload.getString(DUE_DATE_FIELD) : null);
        final ZonedDateTime hearingDate = extractDate(createTaskPayload.containsKey(HEARING_DATE_FIELD) ? createTaskPayload.getString(HEARING_DATE_FIELD) : null);


        final EventStream eventStream = eventSource.getStreamById(taskId);
        final TaskAggregate taskAggregate = aggregateService.get(eventStream, TaskAggregate.class);
        final Stream<Object> events = taskAggregate.recordTaskCreated(taskId, taskTypeId, taskType, reference, note, createdDate, dueDate, hearingDate, workQueue, workQueueName, courtId, jurisdiction, changeAuthor, changeAuthorId);

        appendEventsToStream(envelope, eventStream, events);
    }

    @Handles("businessprocesses.command.record-task-assigned")
    public void handleRecordTaskAssigned(final JsonEnvelope envelope) throws EventStreamException {

        LOGGER.debug("businessprocesses.command.record-task-assigned {}", envelope.payload());
        final JsonObject assignedTaskPayload = envelope.payloadAsJsonObject();
        final String assignTo = assignedTaskPayload.containsKey(ASSIGN_TO_FIELD) ? assignedTaskPayload.getString(ASSIGN_TO_FIELD) : null;
        final UUID assignToId = assignedTaskPayload.containsKey(ASSIGN_TO_ID_FIELD) ? extractId(assignedTaskPayload.getString(ASSIGN_TO_ID_FIELD)) : null;

        final UUID taskId = extractId(assignedTaskPayload.getString(ID_FIELD));
        final UUID changeAuthorId = extractId(assignedTaskPayload.getString(CHANGE_AUTHOR_ID_FIELD));
        final String changeAuthor = assignedTaskPayload.getString(CHANGE_AUTHOR_FIELD);

        final EventStream eventStream = eventSource.getStreamById(taskId);
        final TaskAggregate taskAggregate = aggregateService.get(eventStream, TaskAggregate.class);
        final Stream<Object> events = taskAggregate.recordTaskAssigned(taskId, assignToId, assignTo, changeAuthor, changeAuthorId);

        eventStream.append(events.map(toEnvelopeWithMetadataFrom(envelope)));
    }

    @Handles("businessprocesses.command.record-task-completed")
    public void handleRecordTaskCompleted(final JsonEnvelope envelope) throws EventStreamException {

        LOGGER.debug("businessprocesses.command.record-task-completed {}", envelope.payload());
        final JsonObject completedTaskPayload = envelope.payloadAsJsonObject();

        final UUID taskId = extractId(completedTaskPayload.getString(ID_FIELD));
        final UUID changeAuthorId = extractId(completedTaskPayload.getString(CHANGE_AUTHOR_ID_FIELD));
        final String changeAuthor = completedTaskPayload.getString(CHANGE_AUTHOR_FIELD);
        final ZonedDateTime completedDate = extractDate(completedTaskPayload.getString(COMPLETED_DATE_FIELD));

        final EventStream eventStream = eventSource.getStreamById(taskId);
        final TaskAggregate taskAggregate = aggregateService.get(eventStream, TaskAggregate.class);
        final Stream<Object> events = taskAggregate.recordTaskCompleted(taskId, completedDate, changeAuthor, changeAuthorId);

        eventStream.append(events.map(toEnvelopeWithMetadataFrom(envelope)));
    }

    @Handles("businessprocesses.command.record-task-deleted")
    public void handleRecordTaskDeleted(final JsonEnvelope envelope) throws EventStreamException {

        LOGGER.debug("businessprocesses.command.record-task-deleted {}", envelope.payload());
        final JsonObject deletedTaskPayload = envelope.payloadAsJsonObject();

        final UUID taskId = extractId(deletedTaskPayload.getString(ID_FIELD));
        final UUID changeAuthorId = extractId(deletedTaskPayload.getString(CHANGE_AUTHOR_ID_FIELD));
        final String changeAuthor = deletedTaskPayload.getString(CHANGE_AUTHOR_FIELD);
        final String deletionReason = deletedTaskPayload.getString(DELETION_REASON_FIELD);

        final ZonedDateTime deletedDate = extractDate(deletedTaskPayload.getString(DELETED_DATE_FIELD));

        final EventStream eventStream = eventSource.getStreamById(taskId);
        final TaskAggregate taskAggregate = aggregateService.get(eventStream, TaskAggregate.class);
        final Stream<Object> events = taskAggregate.recordTaskDeleted(taskId, deletionReason, deletedDate, changeAuthor, changeAuthorId);

        eventStream.append(events.map(toEnvelopeWithMetadataFrom(envelope)));
    }

    @Handles("businessprocesses.command.record-task-updated")
    public void handleRecordTaskUpdated(final JsonEnvelope envelope) throws EventStreamException {
        LOGGER.debug("businessprocesses.command.record-task-updated {}", envelope.payload());

        final JsonObject updatedTaskPayload = envelope.payloadAsJsonObject();
        final UUID taskId = extractId(updatedTaskPayload.getString(ID_FIELD));
        final UUID workQueue = extractId(updatedTaskPayload.containsKey(WORK_QUEUE_FIELD) ? updatedTaskPayload.getString(WORK_QUEUE_FIELD) : null);
        final String workQueueName = nonNull(workQueue) ? extractWorkQueueName(workQueue) : null;
        final ZonedDateTime dueDate = extractDate(updatedTaskPayload.containsKey(DUE_DATE_FIELD) ? updatedTaskPayload.getString(DUE_DATE_FIELD) : null);
        final UUID changeAuthorId = extractId(updatedTaskPayload.getString(CHANGE_AUTHOR_ID_FIELD));
        final String changeAuthor = updatedTaskPayload.getString(CHANGE_AUTHOR_FIELD);

        final EventStream eventStream = eventSource.getStreamById(taskId);
        final TaskAggregate taskAggregate = aggregateService.get(eventStream, TaskAggregate.class);
        final Stream<Object> events = taskAggregate.recordTaskUpdated(taskId, dueDate, workQueue, workQueueName, changeAuthor, changeAuthorId);

        eventStream.append(events.map(toEnvelopeWithMetadataFrom(envelope)));
    }

    private ZonedDateTime extractDate(final String date) {
        return Optional.ofNullable(date)
                .map(ZonedDateTimes::fromString)
                .orElse(null);
    }

    private String extractWorkQueueName(final UUID workQueue) {
        return referenceDataService.getWorkQueueByWorkQueueId(workQueue)
                .map(WorkQueue::getQueueName)
                .orElse(null);
    }

    private UUID extractId(final String idFieldName) {
        return Optional.ofNullable(idFieldName)
                .map(UUID::fromString)
                .orElse(null);
    }

}

