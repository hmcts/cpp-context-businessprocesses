package uk.gov.moj.cpp.businessprocesses.aggregate;

import static uk.gov.justice.domain.aggregate.matcher.EventSwitcher.match;
import static uk.gov.justice.domain.aggregate.matcher.EventSwitcher.otherwiseDoNothing;
import static uk.gov.justice.domain.aggregate.matcher.EventSwitcher.when;
import static uk.gov.justice.json.schemas.businessprocesses.event.AssignmentType.ASSIGNED;
import static uk.gov.justice.json.schemas.businessprocesses.event.AssignmentType.REASSIGNED;
import static uk.gov.justice.json.schemas.businessprocesses.event.AssignmentType.UNASSIGNED;

import uk.gov.justice.core.courts.JurisdictionType;
import uk.gov.justice.domain.aggregate.Aggregate;
import uk.gov.justice.json.schemas.businessprocesses.event.AssignmentType;
import uk.gov.justice.json.schemas.businessprocesses.event.TaskAssigned;
import uk.gov.justice.json.schemas.businessprocesses.event.TaskCompleted;
import uk.gov.justice.json.schemas.businessprocesses.event.TaskCreated;
import uk.gov.justice.json.schemas.businessprocesses.event.TaskDeleted;
import uk.gov.justice.json.schemas.businessprocesses.event.TaskDueDateUpdated;
import uk.gov.justice.json.schemas.businessprocesses.event.TaskWorkqueueUpdated;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.stream.Stream;

@SuppressWarnings({"squid:S1172", "squid:S3358"})
public class TaskAggregate implements Aggregate {

    private static final long serialVersionUID = 103L;

    private static final String ASSIGNED_DETAILS = "Assigned to: %s";
    private static final String UNASSIGNED_DETAILS = "Un-assigned from: %s";
    private static final String REASSIGNED_DETAILS = "Re-assigned from: %s, to: %s";
    private static final String ASSIGNED_WORK_QUEUE_DETAILS = "Assigned to WorkQueue: %s";
    private static final String REMOVED_FROM_WORK_QUEUE_DETAILS = "Removed from WorkQueue: %s";
    private static final String REASSIGNED_WORK_QUEUE_DETAILS = "Re-assigned WorkQueue from: %s, to: %s";
    private static final String SET_DUE_DATE_DETAILS = "Set Due Date to: %s";
    private static final String REMOVED_DUE_DATE_DETAILS = "Removed Due Date, previous due date was: %s";
    private static final String CHANGED_DUE_DATE_DETAILS = "Changed DueDate from: %s, to: %s";
    private static final DateTimeFormatter DUE_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");

    private UUID assigneeId;
    private String assigneeName;
    private ZonedDateTime dueDate;
    private UUID workQueue;
    private String workQueueName;

    @SuppressWarnings("squid:S2250")
    @Override
    public Object apply(final Object event) {
        return match(event).with(
                when(TaskCreated.class).apply(e -> {
                    dueDate = e.getDueDate();
                    workQueue = e.getWorkQueue();
                    workQueueName = e.getWorkQueueName();
                }),
                when(TaskAssigned.class).apply(e -> {
                    assigneeId = e.getAssignTo();
                    assigneeName = e.getAssignName();
                }),
                when(TaskWorkqueueUpdated.class).apply(e -> {
                    workQueue = e.getWorkQueue();
                    workQueueName = e.getWorkQueueName();
                }),
                when(TaskDueDateUpdated.class).apply(e -> dueDate = e.getDueDate()),
                otherwiseDoNothing());
    }

    @SuppressWarnings("squid:S00107")
    public Stream<Object> recordTaskCreated(final UUID taskId, final UUID taskTypeId, final String type, final String reference, final String note,
                                            final ZonedDateTime createdDate, final ZonedDateTime dueDate, final ZonedDateTime hearingDate,
                                            final UUID workQueue, final String workQueueName, final UUID courtId, final String jurisdiction, final String changeAuthor, final UUID changeAuthorId) {
        return apply(Stream.of(TaskCreated.taskCreated()
                .withTaskTypeId(taskTypeId)
                .withId(taskId)
                .withReference(reference)
                .withNote(note)
                .withCreatedDate(createdDate)
                .withDueDate(dueDate)
                .withHearingDate(hearingDate)
                .withWorkQueue(workQueue)
                .withWorkQueueName(workQueueName)
                .withCourtId(courtId)
                .withType(type)
                .withJurisdiction(jurisdiction == null ? null : JurisdictionType.valueOf(jurisdiction))
                .withChangeAuthor(changeAuthor)
                .withChangeAuthorId(changeAuthorId)
                .build()));
    }


    public Stream<Object> recordTaskAssigned(final UUID taskId, final UUID assigneeId, final String assigneeName, final String changeAuthor, final UUID changeAuthorId) {
        final AssignmentType assignmentType = getAssignmentType(assigneeId);
        final String assignmentDetails = getAssignmentDetails(assignmentType, assigneeName);

        return apply(Stream.of(TaskAssigned.taskAssigned()
                .withId(taskId)
                .withAssignTo(assigneeId)
                .withAssignName(assigneeName)
                .withAssignmentType(assignmentType)
                .withDetails(assignmentDetails)
                .withChangeAuthor(changeAuthor)
                .withChangeAuthorId(changeAuthorId)
                .build()));
    }

    public Stream<Object> recordTaskCompleted(final UUID taskId, final ZonedDateTime completedAt, final String changeAuthor, final UUID changeAuthorId) {
        return apply(Stream.of(TaskCompleted.taskCompleted()
                .withId(taskId)
                .withCompletedDate(completedAt)
                .withChangeAuthor(changeAuthor)
                .withChangeAuthorId(changeAuthorId)
                .build()));
    }

    public Stream<Object> recordTaskDeleted(final UUID taskId, final String deletionReason, final ZonedDateTime deletedDate, final String changeAuthor, final UUID changeAuthorId) {
        return apply(Stream.of(TaskDeleted.taskDeleted()
                .withId(taskId)
                .withDeletionReason(deletionReason)
                .withDeletedDate(deletedDate)
                .withChangeAuthor(changeAuthor)
                .withChangeAuthorId(changeAuthorId)
                .build()));
    }

    public Stream<Object> recordTaskUpdated(final UUID taskId, final ZonedDateTime dueDate, final UUID workQueue, final String workQueueName, final String changeAuthor, final UUID changeAuthorId) {
        final Stream.Builder<Object> streamBuilder = Stream.builder();

        if (isDueDateUpdated(dueDate)) {
            streamBuilder.add(TaskDueDateUpdated.taskDueDateUpdated()
                    .withId(taskId)
                    .withDueDate(dueDate)
                    .withDetails(getDueDateDetails(dueDate))
                    .withChangeAuthor(changeAuthor)
                    .withChangeAuthorId(changeAuthorId)
                    .build());
        }

        if (isWorkQueueUpdated(workQueue)) {
            streamBuilder.add(TaskWorkqueueUpdated.taskWorkqueueUpdated()
                    .withId(taskId)
                    .withWorkQueue(workQueue)
                    .withWorkQueueName(workQueueName)
                    .withDetails(getWorkQueueDetails(workQueueName))
                    .withChangeAuthor(changeAuthor)
                    .withChangeAuthorId(changeAuthorId)
                    .build());
        }

        return apply(streamBuilder.build());
    }

    private boolean isWorkQueueUpdated(final UUID workQueue) {
        return this.workQueue != null && !this.workQueue.equals(workQueue) || this.workQueue == null && workQueue != null;
    }

    private boolean isDueDateUpdated(final ZonedDateTime dueDate) {
        return this.dueDate != null && !this.dueDate.isEqual(dueDate) || this.dueDate == null && dueDate != null;
    }

    private AssignmentType getAssignmentType(final UUID newAssigneeId) {
        return newAssigneeId == null ? UNASSIGNED
                : assigneeId == null ? ASSIGNED
                : REASSIGNED;
    }

    private String getAssignmentDetails(final AssignmentType assignmentType, final String newAssigneeName) {
        return UNASSIGNED.equals(assignmentType) ? String.format(UNASSIGNED_DETAILS, assigneeName)
                : ASSIGNED.equals(assignmentType) ? String.format(ASSIGNED_DETAILS, newAssigneeName)
                : String.format(REASSIGNED_DETAILS, assigneeName, newAssigneeName);
    }

    private String getWorkQueueDetails(final String newWorkQueue) {
        return this.workQueue == null
                ? String.format(ASSIGNED_WORK_QUEUE_DETAILS, newWorkQueue)
                : newWorkQueue == null ? String.format(REMOVED_FROM_WORK_QUEUE_DETAILS, this.workQueueName)
                : String.format(REASSIGNED_WORK_QUEUE_DETAILS, this.workQueueName, newWorkQueue);
    }

    private String getDueDateDetails(final ZonedDateTime newDueDate) {
        return this.dueDate == null
                ? String.format(SET_DUE_DATE_DETAILS, newDueDate.format(DUE_DATE_FORMATTER))
                : newDueDate == null ? String.format(REMOVED_DUE_DATE_DETAILS, this.dueDate.format(DUE_DATE_FORMATTER))
                : String.format(CHANGED_DUE_DATE_DETAILS, this.dueDate.format(DUE_DATE_FORMATTER), newDueDate.format(DUE_DATE_FORMATTER));
    }
}