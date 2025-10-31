package uk.gov.moj.cpp.businessprocesses.aggregate;

import static java.util.UUID.randomUUID;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import uk.gov.justice.json.schemas.businessprocesses.event.TaskAssigned;
import uk.gov.justice.json.schemas.businessprocesses.event.TaskCompleted;
import uk.gov.justice.json.schemas.businessprocesses.event.TaskCreated;
import uk.gov.justice.json.schemas.businessprocesses.event.TaskDeleted;
import uk.gov.justice.json.schemas.businessprocesses.event.TaskDueDateUpdated;
import uk.gov.justice.json.schemas.businessprocesses.event.TaskWorkqueueUpdated;
import uk.gov.justice.services.common.util.UtcClock;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;

public class TaskAggregateTest {

    private final UUID taskId = randomUUID();
    private final UUID workQueue = randomUUID();
    private final String workQueueName = "Work Queue Name";
    private final UUID workQueue2 = randomUUID();
    private final String workQueueName2 = "Work Queue 2 Name";
    private final UUID courtId = randomUUID();
    private final UUID taskTypeId = randomUUID();
    private final ZonedDateTime createdDate = new UtcClock().now();
    private final ZonedDateTime dueDate = new UtcClock().now();
    private final ZonedDateTime hearingDate = new UtcClock().now();
    private final ZonedDateTime completedAt = new UtcClock().now();
    private final ZonedDateTime deletedAt = new UtcClock().now();
    private final String reference = "TFL34652354";
    private final String note = "Some task note";
    private final String jurisdiction = "CROWN";
    private final String changeAuthor = "egungor";
    private final String assignToName = "john";
    private final UUID assignToId = randomUUID();
    private final String reAssignToName = "joe";
    private final UUID reAssignToId = randomUUID();
    private final UUID changeAuthorId = randomUUID();
    private final String type = "Book Interpreter";
    private final String deletionReason = "Task created in error";
    private static final DateTimeFormatter DUE_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");


    @InjectMocks
    private TaskAggregate aggregate;

    @BeforeEach
    public void setUp() {
        aggregate = new TaskAggregate();
    }

    @Test
    public void shouldCreateTaskCreatedEvent() {
        final List<Object> eventStream = aggregate.recordTaskCreated(taskId, taskTypeId, type, reference, note, createdDate, dueDate, hearingDate, workQueue,workQueueName, courtId, jurisdiction, changeAuthor, changeAuthorId)
                .collect(toList());
        assertThat(eventStream.size(), is(1));
        final Object object = eventStream.get(0);
        assertThat(object.getClass(), is(CoreMatchers.equalTo(TaskCreated.class)));
    }


    @Test
    public void ShouldAssignedReAssignAndUnAssignTask() {
        final List<Object> eventStream = aggregate.recordTaskAssigned(taskId, assignToId, assignToName, changeAuthor, changeAuthorId)
                .collect(toList());
        assertThat(eventStream.size(), is(1));
        final Object object = eventStream.get(0);
        assertThat(object.getClass(), is(CoreMatchers.equalTo(TaskAssigned.class)));
        final TaskAssigned taskAssigned = (TaskAssigned) object;
        assertThat(taskAssigned.getDetails(), is("Assigned to: john"));
        assertThat(taskAssigned.getId(), is(taskId));
        assertThat(taskAssigned.getAssignName(), is(assignToName));
        assertThat(taskAssigned.getAssignTo(), is(assignToId));
        assertThat(taskAssigned.getChangeAuthorId(), is(changeAuthorId));
        assertThat(taskAssigned.getChangeAuthor(), is(changeAuthor));
        assertThat(taskAssigned.getAssignmentType().toString(), is("ASSIGNED"));

        final List<Object> reAssignedEventStream = aggregate.recordTaskAssigned(taskId, reAssignToId, reAssignToName, changeAuthor, changeAuthorId)
                .collect(toList());
        assertThat(reAssignedEventStream.size(), is(1));
        final Object reAssignedObject = reAssignedEventStream.get(0);
        assertThat(object.getClass(), is(CoreMatchers.equalTo(TaskAssigned.class)));
        final TaskAssigned taskReAssigned = (TaskAssigned) reAssignedObject;
        assertThat(taskReAssigned.getDetails(), is("Re-assigned from: john, to: joe"));
        assertThat(taskReAssigned.getId(), is(taskId));
        assertThat(taskReAssigned.getAssignName(), is(reAssignToName));
        assertThat(taskReAssigned.getAssignTo(), is(reAssignToId));
        assertThat(taskReAssigned.getChangeAuthorId(), is(changeAuthorId));
        assertThat(taskReAssigned.getChangeAuthor(), is(changeAuthor));
        assertThat(taskReAssigned.getAssignmentType().toString(), is("REASSIGNED"));

        final List<Object> unAssignedEventStream = aggregate.recordTaskAssigned(taskId, null, null, changeAuthor, changeAuthorId)
                .collect(toList());
        assertThat(unAssignedEventStream.size(), is(1));
        final Object unAssignedObject = unAssignedEventStream.get(0);
        assertThat(object.getClass(), is(CoreMatchers.equalTo(TaskAssigned.class)));
        final TaskAssigned taskUnAssigned = (TaskAssigned) unAssignedObject;
        assertThat(taskUnAssigned.getDetails(), is("Un-assigned from: joe"));
        assertThat(taskUnAssigned.getId(), is(taskId));
        assertThat(taskUnAssigned.getAssignName(), is(nullValue()));
        assertThat(taskUnAssigned.getAssignTo(), is(nullValue()));
        assertThat(taskUnAssigned.getChangeAuthorId(), is(changeAuthorId));
        assertThat(taskUnAssigned.getChangeAuthor(), is(changeAuthor));
        assertThat(taskUnAssigned.getAssignmentType().toString(), is("UNASSIGNED"));
    }

    @Test
    public void shouldCreateTaskCompletedEvent() {
        final List<Object> eventStream = aggregate.recordTaskCompleted(taskId, completedAt, changeAuthor, changeAuthorId)
                .collect(toList());

        assertThat(eventStream.size(), is(1));
        final Object taskCompletedEvent = eventStream.get(0);

        assertThat(taskCompletedEvent.getClass(), is(CoreMatchers.equalTo(TaskCompleted.class)));

        final TaskCompleted taskCompleted = (TaskCompleted) taskCompletedEvent;
        assertThat(taskCompleted.getId(), is(taskId));
        assertThat(taskCompleted.getCompletedDate(), is(deletedAt));
        assertThat(taskCompleted.getChangeAuthorId(), is(changeAuthorId));
        assertThat(taskCompleted.getChangeAuthor(), is(changeAuthor));
    }

    @Test
    public void shouldCreateTaskDeletedEvent() {
        final List<Object> eventStream = aggregate.recordTaskDeleted(taskId, deletionReason, deletedAt, changeAuthor, changeAuthorId)
                .collect(toList());

        assertThat(eventStream.size(), is(1));
        final Object taskDeletedEvent = eventStream.get(0);
        assertThat(taskDeletedEvent.getClass(), is(CoreMatchers.equalTo(TaskDeleted.class)));

        final TaskDeleted taskDeleted = (TaskDeleted) taskDeletedEvent;
        assertThat(taskDeleted.getId(), is(taskId));
        assertThat(taskDeleted.getDeletionReason(), is(deletionReason));
        assertThat(taskDeleted.getDeletedDate(), is(deletedAt));
        assertThat(taskDeleted.getChangeAuthorId(), is(changeAuthorId));
        assertThat(taskDeleted.getChangeAuthor(), is(changeAuthor));
    }

    @Test
    public void shouldCreateTaskWorkQueueUpdatedEvent() {
        final List<Object> eventStream = aggregate.recordTaskUpdated(taskId, dueDate, workQueue, workQueueName, changeAuthor, changeAuthorId)
                .collect(toList());

        assertThat(eventStream.size(), is(2));
        final Object taskDueDateUpdatedEvent = eventStream.get(0);
        assertThat(taskDueDateUpdatedEvent.getClass(), is(CoreMatchers.equalTo(TaskDueDateUpdated.class)));

        final TaskDueDateUpdated taskDueDateUpdated = (TaskDueDateUpdated) taskDueDateUpdatedEvent;
        assertThat(taskDueDateUpdated.getId(), is(taskId));
        assertThat(taskDueDateUpdated.getDueDate(), is(dueDate));
        assertThat(taskDueDateUpdated.getChangeAuthorId(), is(changeAuthorId));
        assertThat(taskDueDateUpdated.getChangeAuthor(), is(changeAuthor));

        final Object taskWorkQueueUpdatedEvent = eventStream.get(1);
        assertThat(taskWorkQueueUpdatedEvent.getClass(), is(CoreMatchers.equalTo(TaskWorkqueueUpdated.class)));

        final TaskWorkqueueUpdated taskWorkQueueUpdated = (TaskWorkqueueUpdated) taskWorkQueueUpdatedEvent;
        assertThat(taskWorkQueueUpdated.getId(), is(taskId));
        assertThat(taskWorkQueueUpdated.getWorkQueue(), is(workQueue));
        assertThat(taskWorkQueueUpdated.getDetails(), is("Assigned to WorkQueue: " + workQueueName));
        assertThat(taskWorkQueueUpdated.getChangeAuthorId(), is(changeAuthorId));
        assertThat(taskWorkQueueUpdated.getChangeAuthor(), is(changeAuthor));

        final ZonedDateTime newDueDate = this.dueDate.plusDays(1);
        final List<Object> eventStream2 = aggregate.recordTaskUpdated(taskId, newDueDate, workQueue2, workQueueName2, changeAuthor, changeAuthorId)
                .collect(toList());

        assertThat(eventStream2.size(), is(2));
        final Object taskDueDateUpdatedEvent2 = eventStream2.get(0);
        assertThat(taskDueDateUpdatedEvent2.getClass(), is(CoreMatchers.equalTo(TaskDueDateUpdated.class)));

        final TaskDueDateUpdated taskDueDateUpdated2 = (TaskDueDateUpdated) taskDueDateUpdatedEvent2;
        assertThat(taskDueDateUpdated2.getId(), is(taskId));
        assertThat(taskDueDateUpdated2.getDueDate(), is(newDueDate));
        assertThat(taskDueDateUpdated2.getDetails(), is("Changed DueDate from: " + dueDate.format(DUE_DATE_FORMATTER) + ", to: " + newDueDate.format(DUE_DATE_FORMATTER)));
        assertThat(taskDueDateUpdated2.getChangeAuthorId(), is(changeAuthorId));
        assertThat(taskDueDateUpdated2.getChangeAuthor(), is(changeAuthor));

        final Object taskWorkQueueUpdatedEvent2 = eventStream2.get(1);
        assertThat(taskWorkQueueUpdatedEvent2.getClass(), is(CoreMatchers.equalTo(TaskWorkqueueUpdated.class)));

        final TaskWorkqueueUpdated taskWorkQueueUpdated2 = (TaskWorkqueueUpdated) taskWorkQueueUpdatedEvent2;
        assertThat(taskWorkQueueUpdated2.getId(), is(taskId));
        assertThat(taskWorkQueueUpdated2.getWorkQueue(), is(workQueue2));
        assertThat(taskWorkQueueUpdated2.getDetails(), is("Re-assigned WorkQueue from: " + workQueueName + ", to: " + workQueueName2));
        assertThat(taskWorkQueueUpdated2.getChangeAuthorId(), is(changeAuthorId));
        assertThat(taskWorkQueueUpdated2.getChangeAuthor(), is(changeAuthor));

        final List<Object> eventStream3 = aggregate.recordTaskUpdated(taskId, newDueDate, null, null, changeAuthor, changeAuthorId)
                .collect(toList());

        assertThat(eventStream3.size(), is(1));

        final Object taskWorkQueueUpdatedEvent3 = eventStream3.get(0);
        assertThat(taskWorkQueueUpdatedEvent3.getClass(), is(CoreMatchers.equalTo(TaskWorkqueueUpdated.class)));

        final TaskWorkqueueUpdated taskWorkQueueUpdated3 = (TaskWorkqueueUpdated) taskWorkQueueUpdatedEvent3;
        assertThat(taskWorkQueueUpdated3.getId(), is(taskId));
        assertThat(taskWorkQueueUpdated3.getWorkQueue(), nullValue());
        assertThat(taskWorkQueueUpdated3.getWorkQueueName(), nullValue());
        assertThat(taskWorkQueueUpdated3.getDetails(), is("Removed from WorkQueue: " + workQueueName2));
        assertThat(taskWorkQueueUpdated3.getChangeAuthorId(), is(changeAuthorId));
        assertThat(taskWorkQueueUpdated3.getChangeAuthor(), is(changeAuthor));


        final List<Object> eventStreamNoUpdates = aggregate.recordTaskUpdated(taskId, newDueDate, null, null, changeAuthor, changeAuthorId)
                .collect(toList());

        assertThat(eventStreamNoUpdates.size(), is(0));
    }

}