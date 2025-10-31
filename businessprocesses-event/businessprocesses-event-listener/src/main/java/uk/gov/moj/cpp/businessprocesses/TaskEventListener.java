package uk.gov.moj.cpp.businessprocesses;


import static java.util.Objects.nonNull;
import static uk.gov.justice.json.schemas.businessprocesses.event.AssignmentType.UNASSIGNED;
import static uk.gov.justice.services.core.annotation.Component.EVENT_LISTENER;

import uk.gov.justice.json.schemas.businessprocesses.event.TaskAssigned;
import uk.gov.justice.json.schemas.businessprocesses.event.TaskCompleted;
import uk.gov.justice.json.schemas.businessprocesses.event.TaskCreated;
import uk.gov.justice.json.schemas.businessprocesses.event.TaskDeleted;
import uk.gov.justice.json.schemas.businessprocesses.event.TaskDueDateUpdated;
import uk.gov.justice.json.schemas.businessprocesses.event.TaskWorkqueueUpdated;
import uk.gov.justice.services.common.util.UtcClock;
import uk.gov.justice.services.core.annotation.Handles;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.moj.cpp.businessprocesses.persistence.entity.TaskEntity;
import uk.gov.moj.cpp.businessprocesses.persistence.entity.TaskHistoryEntity;
import uk.gov.moj.cpp.businessprocesses.persistence.repository.TaskRepository;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@ServiceComponent(EVENT_LISTENER)
public class TaskEventListener {

    private static final UtcClock UTC_CLOCK = new UtcClock();

    private static final Logger LOGGER = LoggerFactory.getLogger(TaskEventListener.class);

    @Inject
    private TaskRepository taskRepository;

    @Handles("businessprocesses.event.task-created")
    public void taskCreated(final Envelope<TaskCreated> taskCreatedEnvelope) {
        LOGGER.info("Processing event 'businessprocesses.event.task-created' for task: {}", taskCreatedEnvelope.payload().getId());

        final TaskCreated taskCreated = taskCreatedEnvelope.payload();

        final TaskEntity taskEntity = new TaskEntity();
        taskEntity.setCreatedDate(taskCreated.getCreatedDate());
        taskEntity.setCourtId(taskCreated.getCourtId());
        taskEntity.setDueDate(taskCreated.getDueDate());
        taskEntity.setHearingDate(taskCreated.getHearingDate());
        taskEntity.setTaskTypeId(taskCreated.getTaskTypeId());

        if (taskCreated.getJurisdiction() != null) {
            taskEntity.setJurisdiction(taskCreated.getJurisdiction().toString());
        }
        taskEntity.setReference(taskCreated.getReference());
        taskEntity.setStatus("CREATED");
        taskEntity.setTaskId(taskCreated.getId());
        taskEntity.setTaskTypeId(taskCreated.getTaskTypeId());
        taskEntity.setType(taskCreated.getType());
        taskEntity.setWorkqueue(taskCreated.getWorkQueue());

        taskEntity.setTaskHistory(createTaskHistoryEntities(taskCreated, taskEntity));
        taskRepository.save(taskEntity);

    }


    @Handles("businessprocesses.event.task-deleted")
    public void taskDeleted(final Envelope<TaskDeleted> taskDeletedEnvelope) {
        final TaskDeleted taskDeleted = taskDeletedEnvelope.payload();

        LOGGER.info("Processing event 'businessprocesses.event.task-deleted' for task: {}", taskDeletedEnvelope.payload().getId());

        final ZonedDateTime createdAt = taskDeletedEnvelope.metadata().createdAt().orElse(UTC_CLOCK.now());


        final TaskEntity taskEntity = taskRepository.findBy(taskDeleted.getId());

        if (Objects.nonNull(taskEntity)) {
            final TaskHistoryEntity taskHistoryEntity = createTaskHistoryEntity(createdAt, "Task Canceled", taskDeleted.getChangeAuthor(), taskEntity, null);

            taskEntity.setStatus("DELETED");
            taskEntity.getTaskHistory().add(taskHistoryEntity);

            taskRepository.save(taskEntity);
        }
    }

    @Handles("businessprocesses.event.task-completed")
    public void taskCompleted(final Envelope<TaskCompleted> taskCompletedEnvelope) {
        LOGGER.info("Processing event 'businessprocesses.event.task-completed' for task: {}", taskCompletedEnvelope.payload().getId());

        final TaskCompleted taskCompleted = taskCompletedEnvelope.payload();
        final ZonedDateTime completedDate = taskCompleted.getCompletedDate();
        final TaskEntity taskEntity = taskRepository.findBy(taskCompleted.getId());
        if (Objects.nonNull(taskEntity)) {
            final TaskHistoryEntity taskHistoryEntity = createTaskHistoryEntity(completedDate, "Task Completed", taskCompleted.getChangeAuthor(), taskEntity, null);

            taskEntity.getTaskHistory().add(taskHistoryEntity);
            taskEntity.setStatus("COMPLETED");
            taskEntity.setCompletedDate(completedDate);
            taskRepository.save(taskEntity);
        }
    }

    @Handles("businessprocesses.event.task-assigned")
    public void taskAssigned(final Envelope<TaskAssigned> taskAssignedEnvelope) {
        LOGGER.info("Processing event 'businessprocesses.event.task-assigned' for task: {}", taskAssignedEnvelope.payload().getId());

        final TaskAssigned taskAssigned = taskAssignedEnvelope.payload();
        final ZonedDateTime createdAt = taskAssignedEnvelope.metadata().createdAt().orElse(UTC_CLOCK.now());

        final TaskEntity taskEntity = taskRepository.findBy(taskAssigned.getId());
        if (Objects.nonNull(taskEntity)) {
            final TaskHistoryEntity taskHistoryEntity = createTaskHistoryEntity(createdAt, "Task " + toCamelCase(taskAssigned.getAssignmentType().toString()), taskAssigned.getChangeAuthor(), taskEntity, taskAssigned.getDetails());

            taskEntity.getTaskHistory().add(taskHistoryEntity);

            if (UNASSIGNED.equals(taskAssigned.getAssignmentType())) {
                taskEntity.setStatus("CREATED");
            } else {
                taskEntity.setStatus("ASSIGNED");
            }

            taskRepository.save(taskEntity);
        }
    }


    @Handles("businessprocesses.event.task-due-date-updated")
    public void taskDueDateUpdated(final Envelope<TaskDueDateUpdated> taskDueDateUpdatedEnvelope) {
        LOGGER.info("Processing event 'businessprocesses.event.task-due-date-updated' for task: {}", taskDueDateUpdatedEnvelope.payload().getId());

        final TaskDueDateUpdated taskDueDateUpdated = taskDueDateUpdatedEnvelope.payload();
        final ZonedDateTime updatedAt = taskDueDateUpdatedEnvelope.metadata().createdAt().orElse(UTC_CLOCK.now());

        final TaskEntity taskEntity = taskRepository.findBy(taskDueDateUpdated.getId());
        if (Objects.nonNull(taskEntity)) {
            final TaskHistoryEntity taskHistoryEntity = createTaskHistoryEntity(updatedAt, "Task Due Date Updated", taskDueDateUpdated.getChangeAuthor(), taskEntity, taskDueDateUpdated.getDetails());

            taskEntity.getTaskHistory().add(taskHistoryEntity);
            taskEntity.setDueDate(taskDueDateUpdated.getDueDate());

            taskRepository.save(taskEntity);
        }
    }

    @Handles("businessprocesses.event.task-workqueue-updated")
    public void taskWorkQueueUpdated(final Envelope<TaskWorkqueueUpdated> taskWorkqueueUpdatedEnvelope) {
        LOGGER.info("Processing event 'businessprocesses.event.task-workqueue-updated' for task: {}", taskWorkqueueUpdatedEnvelope.payload().getId());

        final TaskWorkqueueUpdated taskWorkqueueUpdated = taskWorkqueueUpdatedEnvelope.payload();
        final ZonedDateTime updatedAt = taskWorkqueueUpdatedEnvelope.metadata().createdAt().orElse(UTC_CLOCK.now());

        final TaskEntity taskEntity = taskRepository.findBy(taskWorkqueueUpdated.getId());
        if (Objects.nonNull(taskEntity)) {
            final TaskHistoryEntity taskHistoryEntity = createTaskHistoryEntity(updatedAt, "Task Work Queue Updated", taskWorkqueueUpdated.getChangeAuthor(), taskEntity, taskWorkqueueUpdated.getDetails());

            taskEntity.getTaskHistory().add(taskHistoryEntity);
            taskEntity.setWorkqueue(taskWorkqueueUpdated.getWorkQueue());

            taskRepository.save(taskEntity);
        }
    }

    private TaskHistoryEntity createTaskHistoryEntity(final ZonedDateTime createdAt, final String eventType, final String changeAuthor, final TaskEntity task, final String details) {

        final TaskHistoryEntity taskHistoryEntity = new TaskHistoryEntity();
        taskHistoryEntity.setId(UUID.randomUUID());
        taskHistoryEntity.setEventType(eventType);
        taskHistoryEntity.setEventDate(createdAt);
        taskHistoryEntity.setChangeAuthor(changeAuthor);
        taskHistoryEntity.setTask(task);
        if (nonNull(details)) {
            taskHistoryEntity.setDetails(details);
        }
        return taskHistoryEntity;
    }

    private List<TaskHistoryEntity> createTaskHistoryEntities(final TaskCreated taskCreated, final TaskEntity taskEntity) {
        final List<TaskHistoryEntity> taskHistoryEntities = new ArrayList<>();
        final TaskHistoryEntity taskHistoryEntity = new TaskHistoryEntity();
        taskHistoryEntity.setChangeAuthor(taskCreated.getChangeAuthor());
        taskHistoryEntity.setEventDate(taskCreated.getCreatedDate());
        taskHistoryEntity.setEventType("Task Created");
        taskHistoryEntity.setId(UUID.randomUUID());
        taskHistoryEntity.setTask(taskEntity);
        taskHistoryEntities.add(taskHistoryEntity);
        return taskHistoryEntities;
    }

    private String toCamelCase(final String input) {
        return input.substring(0, 1).toUpperCase() +
                input.substring(1).toLowerCase();
    }
}
