package uk.gov.moj.cpp.businessprocesses.persistence;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import uk.gov.justice.services.common.util.UtcClock;
import uk.gov.moj.cpp.businessprocesses.persistence.entity.TaskEntity;
import uk.gov.moj.cpp.businessprocesses.persistence.entity.TaskHistoryEntity;
import uk.gov.moj.cpp.businessprocesses.persistence.repository.TaskRepository;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.inject.Inject;

import org.apache.deltaspike.testcontrol.api.junit.CdiTestRunner;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(CdiTestRunner.class)
public class TaskRepositoryTest {
    private static final UUID taskId = randomUUID();
    private static final UUID taskTypeId = randomUUID();
    private static final String reference = "TFL4359536";
    private static final String type = "Hearing";
    private static final ZonedDateTime createdDate = new UtcClock().now();
    private static final ZonedDateTime dueDate = new UtcClock().now();
    private static final ZonedDateTime completedDate = new UtcClock().now();
    private static final String status = "ACTIVE";
    private static final UUID workQueue = randomUUID();
    private static final UUID courtId = randomUUID();
    private static final String jurisdiction = "MAGISTRATES";
    private static final ZonedDateTime hearingDate = new UtcClock().now();

    private static final UUID taskHistoryId = randomUUID();
    private static final ZonedDateTime eventDate = new UtcClock().now();
    private static final String eventType = "Hearing";
    private static final String changeAuthor = "John";

    @Inject
    private TaskRepository taskRepository;

    @Before
    public void setup() {
        taskRepository.save(createTaskEntity());
    }

    @Test
    public void shouldFindTaskByTaskId() {

        // When
        final TaskEntity taskEntity = taskRepository.findBy(taskId);

        // Then
        assertThat(taskEntity.getTaskId(), equalTo(taskId));
        assertThat(taskEntity.getTaskTypeId(), equalTo(taskTypeId));
        assertThat(taskEntity.getReference(), equalTo(reference));
        assertThat(taskEntity.getType(), equalTo(type));
        assertThat(taskEntity.getCreatedDate(), equalTo(createdDate));
        assertThat(taskEntity.getDueDate(), equalTo(dueDate));
        assertThat(taskEntity.getCompletedDate(), equalTo(completedDate));
        assertThat(taskEntity.getStatus(), equalTo(status));
        assertThat(taskEntity.getWorkqueue(), equalTo(workQueue));
        assertThat(taskEntity.getCourtId(), equalTo(courtId));
        assertThat(taskEntity.getJurisdiction(), equalTo(jurisdiction));
        assertThat(taskEntity.getHearingDate(), equalTo(hearingDate));

        final List<TaskHistoryEntity> taskHistoryEntities = taskEntity.getTaskHistory();
        final TaskHistoryEntity taskHistoryEntity = taskHistoryEntities.get(0);

        assertThat(taskHistoryEntities, notNullValue());
        assertThat(taskHistoryEntities.size(), equalTo(1));
        assertThat(taskHistoryEntity.getId(), equalTo(taskHistoryId));
        assertThat(taskHistoryEntity.getEventDate(), equalTo(eventDate));
        assertThat(taskHistoryEntity.getEventType(), equalTo(eventType));
        assertThat(taskHistoryEntity.getChangeAuthor(), equalTo(changeAuthor));
    }

    private TaskEntity createTaskEntity() {
        TaskEntity taskEntity = new TaskEntity();
        taskEntity.setTaskId(taskId);
        taskEntity.setTaskTypeId(taskTypeId);
        taskEntity.setReference(reference);
        taskEntity.setType(type);
        taskEntity.setCreatedDate(createdDate);
        taskEntity.setDueDate(dueDate);
        taskEntity.setCompletedDate(completedDate);
        taskEntity.setStatus(status);
        taskEntity.setWorkqueue(workQueue);
        taskEntity.setCourtId(courtId);
        taskEntity.setJurisdiction(jurisdiction);
        taskEntity.setHearingDate(hearingDate);
        taskEntity.setTaskHistory(createTaskHistoryEntities());
        return taskEntity;
    }

    private List<TaskHistoryEntity> createTaskHistoryEntities() {
        List<TaskHistoryEntity> taskHistoryEntities = new ArrayList<>();
        TaskHistoryEntity taskHistoryEntity = new TaskHistoryEntity();
        taskHistoryEntity.setId(taskHistoryId);
        taskHistoryEntity.setEventDate(eventDate);
        taskHistoryEntity.setEventType(eventType);
        taskHistoryEntity.setChangeAuthor(changeAuthor);
        taskHistoryEntities.add(taskHistoryEntity);
        return taskHistoryEntities;
    }
}