package uk.gov.moj.cpp.businessprocesses;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.justice.json.schemas.businessprocesses.event.AssignmentType.ASSIGNED;
import static uk.gov.justice.json.schemas.businessprocesses.event.AssignmentType.UNASSIGNED;
import static uk.gov.justice.services.messaging.Envelope.envelopeFrom;
import static uk.gov.justice.services.messaging.Envelope.metadataBuilder;

import uk.gov.justice.core.courts.JurisdictionType;
import uk.gov.justice.json.schemas.businessprocesses.event.TaskAssigned;
import uk.gov.justice.json.schemas.businessprocesses.event.TaskCompleted;
import uk.gov.justice.json.schemas.businessprocesses.event.TaskCreated;
import uk.gov.justice.json.schemas.businessprocesses.event.TaskDeleted;
import uk.gov.justice.json.schemas.businessprocesses.event.TaskDueDateUpdated;
import uk.gov.justice.json.schemas.businessprocesses.event.TaskWorkqueueUpdated;
import uk.gov.justice.services.common.converter.ZonedDateTimes;
import uk.gov.justice.services.common.util.UtcClock;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.moj.cpp.businessprocesses.persistence.entity.TaskEntity;
import uk.gov.moj.cpp.businessprocesses.persistence.repository.TaskRepository;

import java.time.ZonedDateTime;
import java.util.UUID;

import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class TaskEventListenerTest {

    private static final UtcClock UTC_CLOCK = new UtcClock();

    @Mock
    private TaskRepository taskRepository;

    @InjectMocks
    private TaskEventListener taskEventListener;


    @Test
    public void shouldCreateNewTask() {


        final TaskCreated taskCreated = getTaskCreated();
        final Envelope<TaskCreated> taskCreatedEnvelope = envelopeFrom(metadataBuilder().
                withName("businessprocesses.event.task-created").
                withId(randomUUID()).createdAt(UTC_CLOCK.now()), taskCreated);

        //when
        taskEventListener.taskCreated(taskCreatedEnvelope);

        //then
        final ArgumentCaptor<TaskEntity> taskEntityArgumentCaptor = ArgumentCaptor.forClass(TaskEntity.class);

        verify(taskRepository, times(1)).save(taskEntityArgumentCaptor.capture());
        TaskEntity taskEntity = taskEntityArgumentCaptor.getValue();
        assertThat(taskEntity.getCourtId(), is(taskCreated.getCourtId()));
        assertThat(taskEntity.getStatus(), is("CREATED"));
        assertThat(taskEntity.getCreatedDate(), CoreMatchers.is(notNullValue()));
        assertThat(taskEntity.getDueDate(), CoreMatchers.is(notNullValue()));
        assertThat(taskEntity.getHearingDate(), CoreMatchers.is(notNullValue()));
        assertThat(taskEntity.getJurisdiction(), is("CROWN"));
        assertThat(taskEntity.getType(), is("type"));
        assertThat(taskEntity.getTaskTypeId(), is(notNullValue()));
        assertThat(taskEntity.getReference(), is("reference"));
        assertThat(taskEntity.getTaskHistory().size(), is(1));
        assertThat(taskEntity.getTaskHistory().get(0).getEventType(), is("Task Created"));
    }

    @Test
    public void shouldAddDeletedHistoryToTask() {

        //given
        final TaskDeleted taskDeleted = TaskDeleted.taskDeleted().withId(randomUUID()).withChangeAuthor("egungor").withChangeAuthorId(randomUUID()).build();
        final Envelope<TaskDeleted> taskCreatedEnvelope = envelopeFrom(metadataBuilder().
                withName("businessprocesses.event.task-deleted").
                withId(randomUUID()).createdAt(UTC_CLOCK.now()), taskDeleted);
        final TaskEntity taskEntity = new TaskEntity();
        taskEntity.setTaskId(taskDeleted.getId());
        taskEntity.setStatus("CREATED");
        when(taskRepository.findBy(taskDeleted.getId())).thenReturn(taskEntity);
        //when
        taskEventListener.taskDeleted(taskCreatedEnvelope);

        //then
        final ArgumentCaptor<TaskEntity> taskEntityArgumentCaptor = ArgumentCaptor.forClass(TaskEntity.class);

        verify(taskRepository, times(1)).save(taskEntityArgumentCaptor.capture());
        TaskEntity taskEntityCaptor = taskEntityArgumentCaptor.getValue();
        assertThat(taskEntityCaptor.getStatus(), is("DELETED"));
        assertThat(taskEntityCaptor.getTaskHistory().size(), is(1));
        assertThat(taskEntityCaptor.getTaskHistory().get(0).getEventType(), is("Task Canceled"));
    }

    @Test
    public void shouldAddAssignedHistoryToTask() {

        //given
        final TaskAssigned taskAssigned = TaskAssigned.taskAssigned().withId(randomUUID()).withChangeAuthor("egungor")
                .withChangeAuthorId(randomUUID()).withAssignmentType(ASSIGNED).withDetails("Assigned to: John Doe").withAssignTo(randomUUID()).build();

        final Envelope<TaskAssigned> taskAssignedEnvelope = envelopeFrom(metadataBuilder().
                withName("businessprocesses.event.task-assigned").
                withId(randomUUID()).createdAt(UTC_CLOCK.now()), taskAssigned);
        final TaskEntity taskEntity = new TaskEntity();
        taskEntity.setTaskId(taskAssigned.getId());
        taskEntity.setStatus("CREATED");
        when(taskRepository.findBy(taskAssigned.getId())).thenReturn(taskEntity);
        //when
        taskEventListener.taskAssigned(taskAssignedEnvelope);

        //then
        final ArgumentCaptor<TaskEntity> taskEntityArgumentCaptor = ArgumentCaptor.forClass(TaskEntity.class);

        verify(taskRepository, times(1)).save(taskEntityArgumentCaptor.capture());
        TaskEntity taskEntityCaptor = taskEntityArgumentCaptor.getValue();
        assertThat(taskEntityCaptor.getStatus(), is("ASSIGNED"));
        assertThat(taskEntityCaptor.getTaskHistory().size(), is(1));
        assertThat(taskEntityCaptor.getTaskHistory().get(0).getEventType(), is("Task Assigned"));
        assertThat(taskEntityCaptor.getTaskHistory().get(0).getDetails(), is("Assigned to: John Doe"));
    }

    @Test
    public void shouldAddUnAssignedWithDetailsInHistoryToTask() {

        //given
        final TaskAssigned taskAssigned = TaskAssigned.taskAssigned().withId(randomUUID()).withChangeAuthor("egungor")
                .withChangeAuthorId(randomUUID()).withAssignmentType(UNASSIGNED).withDetails("Unassigned from: Bob Smith").withAssignTo(randomUUID()).build();
        final Envelope<TaskAssigned> taskAssignedEnvelope = envelopeFrom(metadataBuilder().
                withName("businessprocesses.events.task-assigned").
                withId(randomUUID()).createdAt(UTC_CLOCK.now()), taskAssigned);
        final TaskEntity taskEntity = new TaskEntity();
        taskEntity.setTaskId(taskAssigned.getId());
        taskEntity.setStatus("CREATED");
        when(taskRepository.findBy(taskAssigned.getId())).thenReturn(taskEntity);

        //when
        taskEventListener.taskAssigned(taskAssignedEnvelope);

        //then
        final ArgumentCaptor<TaskEntity> taskEntityArgumentCaptor = ArgumentCaptor.forClass(TaskEntity.class);

        verify(taskRepository, times(1)).save(taskEntityArgumentCaptor.capture());
        TaskEntity taskEntityCaptor = taskEntityArgumentCaptor.getValue();
        assertThat(taskEntityCaptor.getStatus(), is("CREATED"));
        assertThat(taskEntityCaptor.getTaskHistory().size(), is(1));
        assertThat(taskEntityCaptor.getTaskHistory().get(0).getEventType(), is("Task Unassigned"));
        assertThat(taskEntityCaptor.getTaskHistory().get(0).getDetails(), is("Unassigned from: Bob Smith"));
    }

    @Test
    public void shouldUpdateDueDatedWithDetailsInHistoryToTask() {

        // Given
        final ZonedDateTime dueDate = new UtcClock().now();
        final ZonedDateTime newDueDate = dueDate.plusDays(1);
        final TaskDueDateUpdated taskDueDateUpdated = TaskDueDateUpdated.taskDueDateUpdated().withId(randomUUID()).withChangeAuthor("egungor").withDueDate(newDueDate)
                .withChangeAuthorId(randomUUID()).withDetails("Changed DueDate from: " + ZonedDateTimes.toString(dueDate) + ", to: " + ZonedDateTimes.toString(newDueDate)).build();
        final Envelope<TaskDueDateUpdated> taskDueDateUpdatedEnvelope = envelopeFrom(metadataBuilder().
                withName("businessprocesses.event.task-due-date-updated").
                withId(randomUUID()).createdAt(UTC_CLOCK.now()), taskDueDateUpdated);
        final TaskEntity taskEntity = new TaskEntity();
        taskEntity.setTaskId(taskDueDateUpdated.getId());
        taskEntity.setStatus("Created");
        when(taskRepository.findBy(taskDueDateUpdated.getId())).thenReturn(taskEntity);

        // When
        taskEventListener.taskDueDateUpdated(taskDueDateUpdatedEnvelope);

        // Then
        final ArgumentCaptor<TaskEntity> taskEntityArgumentCaptor = ArgumentCaptor.forClass(TaskEntity.class);

        verify(taskRepository, times(1)).save(taskEntityArgumentCaptor.capture());
        TaskEntity taskEntityCaptor = taskEntityArgumentCaptor.getValue();
        assertThat(taskEntityCaptor.getStatus(), is("Created"));
        assertThat(taskEntityCaptor.getTaskHistory().size(), is(1));
        assertThat(taskEntityCaptor.getTaskHistory().get(0).getEventType(), is("Task Due Date Updated"));
        assertThat(taskEntityCaptor.getTaskHistory().get(0).getDetails(), is("Changed DueDate from: " + ZonedDateTimes.toString(dueDate) + ", to: " + ZonedDateTimes.toString(newDueDate)));
    }

    @Test
    public void shouldUpdateWorkQueuedWithDetailsInHistoryToTask() {

        // Given
        final UUID workQueue = randomUUID();
        final TaskWorkqueueUpdated taskWorkqueueUpdated = TaskWorkqueueUpdated.taskWorkqueueUpdated().withId(randomUUID()).withChangeAuthor("egungor")
                .withWorkQueue(workQueue).withChangeAuthorId(randomUUID()).withDetails("Assigned to WorkQueue: " + workQueue).build();
        final Envelope<TaskWorkqueueUpdated> taskWorkqueueUpdatedEnvelope = envelopeFrom(metadataBuilder().
                withName("businessprocesses.event.task-workqueue-updated").
                withId(randomUUID()).createdAt(UTC_CLOCK.now()), taskWorkqueueUpdated);
        final TaskEntity taskEntity = new TaskEntity();
        taskEntity.setTaskId(taskWorkqueueUpdated.getId());
        taskEntity.setStatus("Created");
        when(taskRepository.findBy(taskWorkqueueUpdated.getId())).thenReturn(taskEntity);

        // When
        taskEventListener.taskWorkQueueUpdated(taskWorkqueueUpdatedEnvelope);

        // Then
        final ArgumentCaptor<TaskEntity> taskEntityArgumentCaptor = ArgumentCaptor.forClass(TaskEntity.class);

        verify(taskRepository, times(1)).save(taskEntityArgumentCaptor.capture());
        TaskEntity taskEntityCaptor = taskEntityArgumentCaptor.getValue();
        assertThat(taskEntityCaptor.getStatus(), is("Created"));
        assertThat(taskEntityCaptor.getTaskHistory().size(), is(1));
        assertThat(taskEntityCaptor.getTaskHistory().get(0).getEventType(), is("Task Work Queue Updated"));
        assertThat(taskEntityCaptor.getTaskHistory().get(0).getDetails(), is("Assigned to WorkQueue: " + workQueue));
    }

    @Test
    public void shouldAddCompletedHistoryToTask() {

        //given
        final TaskCompleted taskCompleted = TaskCompleted.taskCompleted().withId(randomUUID()).withCompletedDate(new UtcClock().now()).withChangeAuthor("egungor").withChangeAuthorId(randomUUID()).build();
        final Envelope<TaskCompleted> taskCompletedEnvelope = envelopeFrom(metadataBuilder().
                withName("businessprocesses.event.task-completed").
                withId(randomUUID()).createdAt(UTC_CLOCK.now()), taskCompleted);
        final TaskEntity taskEntity = new TaskEntity();
        taskEntity.setTaskId(taskCompleted.getId());
        taskEntity.setStatus("CREATED");
        when(taskRepository.findBy(taskCompleted.getId())).thenReturn(taskEntity);
        //when
        taskEventListener.taskCompleted(taskCompletedEnvelope);

        //then
        final ArgumentCaptor<TaskEntity> taskEntityArgumentCaptor = ArgumentCaptor.forClass(TaskEntity.class);

        verify(taskRepository, times(1)).save(taskEntityArgumentCaptor.capture());
        TaskEntity taskEntityCaptor = taskEntityArgumentCaptor.getValue();
        assertThat(taskEntityCaptor.getStatus(), is("COMPLETED"));
        assertThat(taskEntityCaptor.getCompletedDate(), CoreMatchers.is(notNullValue()));
        assertThat(taskEntityCaptor.getTaskHistory().size(), is(1));
        assertThat(taskEntityCaptor.getTaskHistory().get(0).getEventType(), is("Task Completed"));
    }

    private TaskCreated getTaskCreated() {

        return TaskCreated.taskCreated()
                .withCreatedDate(new UtcClock().now())
                .withTaskTypeId(UUID.randomUUID())
                .withChangeAuthor("egungor")
                .withChangeAuthorId(UUID.randomUUID())
                .withCourtId(UUID.randomUUID())
                .withDueDate(new UtcClock().now())
                .withHearingDate(new UtcClock().now())
                .withJurisdiction(JurisdictionType.CROWN)
                .withId(UUID.randomUUID())
                .withReference("reference")
                .withType("type")
                .withWorkQueue(UUID.randomUUID())
                .build();
    }
}
