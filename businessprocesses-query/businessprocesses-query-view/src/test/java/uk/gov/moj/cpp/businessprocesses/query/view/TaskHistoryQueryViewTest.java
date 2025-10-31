package uk.gov.moj.cpp.businessprocesses.query.view;

import static javax.json.Json.createObjectBuilder;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;
import static uk.gov.justice.services.messaging.JsonEnvelope.envelopeFrom;
import static uk.gov.justice.services.test.utils.core.messaging.MetadataBuilderFactory.metadataWithRandomUUID;

import uk.gov.justice.services.adapter.rest.exception.BadRequestException;
import uk.gov.justice.services.common.util.UtcClock;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.moj.cpp.businessprocesses.persistence.entity.TaskHistoryEntity;
import uk.gov.moj.cpp.businessprocesses.persistence.repository.TaskHistoryRepository;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.json.JsonObject;
import javax.ws.rs.NotFoundException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class TaskHistoryQueryViewTest {
    private static final String TASK_ID = "taskId";
    private static final String HISTORY = "history";
    private static final String ID = "id";
    private static final String EVENT_TYPE = "eventType";
    private static final String EVENT_DATE = "eventDate";
    private static final String CHANGE_AUTHOR = "changeAuthor";
    private static final String DETAILS = "details";
    private static final String EVENT_TYPE_CREATED = "CREATED";
    private static final String EVENT_TYPE_ASSIGNED = "ASSIGNED";
    private static final String EVENT_TYPE_COMPLETED = "COMPLETED";
    private static final String CHANGE_AUTHOR_CREATED = "Bill Smith";
    private static final String CHANGE_AUTHOR__ASSIGNED = "Dave Jones";
    private static final String CHANGE_AUTHOR__COMPLETED = "Jessica Dox";
    private static final UUID ID_CREATED = UUID.randomUUID();
    private static final UUID ID_ASSIGNED = UUID.randomUUID();
    private static final UUID ID_COMPLETED = UUID.randomUUID();
    private static final String DETAILS_TYPE_ASSIGNED = "Assigned to: Bob Smith";
    private static final ZonedDateTime EVENT_DATE_VALUE = new UtcClock().now();
    @Mock
    private JsonEnvelope query;
    @Mock
    private TaskHistoryRepository taskHistoryRepository;
    @InjectMocks
    private TaskHistoryQueryView taskHistoryQueryView;
    @Test
    public void shouldReturnEmptyTaskHistory() {
        // Given
        final UUID taskID = UUID.randomUUID();
        final JsonObject requestPayload = createObjectBuilder().add(TASK_ID, taskID.toString()).build();
        final JsonEnvelope query = envelopeFrom(
                metadataWithRandomUUID("businessprocesses.query.task-history"),
                requestPayload);
        List<TaskHistoryEntity> taskHistoryEntityList = new ArrayList<>();
        when(taskHistoryRepository.findByTaskId(taskID)).thenReturn(taskHistoryEntityList);


        // When
        final JsonEnvelope result = taskHistoryQueryView.getTaskHistory(query);
        // Then
        assertThat(result.payloadAsJsonObject().getJsonString(TASK_ID).getString(), is(taskID.toString()));
        assertThat(result.payloadAsJsonObject().getJsonArray(HISTORY).size(), is(0));
    }
    @Test
    public void shouldNotFoundExceptionWhenTaskNotMatch() {
        // Given
        final UUID taskID = UUID.randomUUID();
        final JsonObject requestPayload = createObjectBuilder().add(TASK_ID, taskID.toString()).build();
        final JsonEnvelope query = envelopeFrom(
                metadataWithRandomUUID("businessprocesses.query.task-history"),
                requestPayload);
        when(taskHistoryRepository.findByTaskId(taskID)).thenReturn(null);

        // when
        assertThrows(NotFoundException.class, () -> taskHistoryQueryView.getTaskHistory(query));
    }
    @Test
    public void shouldBadRequestExceptionWhenNoTaskIdProvided() {
        // Given
        final UUID taskID = UUID.randomUUID();
        final JsonObject requestPayload = createObjectBuilder().add("NO_TASK_ID", taskID.toString()).build();
        final JsonEnvelope query = envelopeFrom(
                metadataWithRandomUUID("businessprocesses.query.task-history"),
                requestPayload);

        assertThrows(BadRequestException.class, () -> taskHistoryQueryView.getTaskHistory(query));
    }
    @Test
    public void shouldReturnValidTaskHistory() {
        // Given
        final UUID taskID = UUID.randomUUID();
        final JsonObject requestPayload = createObjectBuilder().add(TASK_ID, taskID.toString()).build();
        final JsonEnvelope query = envelopeFrom(
                metadataWithRandomUUID("businessprocesses.query.task-history"),
                requestPayload);
        List<TaskHistoryEntity> taskHistoryEntityList = getTaskHistoryEntityList();
        when(taskHistoryRepository.findByTaskId(taskID)).thenReturn(taskHistoryEntityList);


        // When
        final JsonEnvelope result = taskHistoryQueryView.getTaskHistory(query);
        // Then
        assertThat(result.payloadAsJsonObject().getJsonString(TASK_ID).getString(), is(taskID.toString()));
        assertThat(result.payloadAsJsonObject().getJsonArray(HISTORY).size(), is(taskHistoryEntityList.size()));
        final JsonObject history1 = result.payloadAsJsonObject().getJsonArray(HISTORY).getJsonObject(0);
        assertThat(history1.getJsonString(ID).getString(), is(ID_CREATED.toString()));
        assertThat(history1.getJsonString(EVENT_TYPE).getString(), is(EVENT_TYPE_CREATED));
        assertThat(history1.getJsonString(CHANGE_AUTHOR).getString(), is(CHANGE_AUTHOR_CREATED));
        assertThat(history1.getJsonString(EVENT_DATE).getString(), is(EVENT_DATE_VALUE.toString()));
        final JsonObject history2 = result.payloadAsJsonObject().getJsonArray(HISTORY).getJsonObject(1);
        assertThat(history2.getJsonString(ID).getString(), is(ID_ASSIGNED.toString()));
        assertThat(history2.getJsonString(EVENT_TYPE).getString(), is(EVENT_TYPE_ASSIGNED));
        assertThat(history2.getJsonString(CHANGE_AUTHOR).getString(), is(CHANGE_AUTHOR__ASSIGNED));
        assertThat(history2.getJsonString(EVENT_DATE).getString(), is(EVENT_DATE_VALUE.toString()));
        assertThat(history2.getJsonString(DETAILS).getString(), is(DETAILS_TYPE_ASSIGNED));
        final JsonObject history3 = result.payloadAsJsonObject().getJsonArray(HISTORY).getJsonObject(2);
        assertThat(history3.getJsonString(ID).getString(), is(ID_COMPLETED.toString()));
        assertThat(history3.getJsonString(EVENT_TYPE).getString(), is(EVENT_TYPE_COMPLETED));
        assertThat(history3.getJsonString(CHANGE_AUTHOR).getString(), is(CHANGE_AUTHOR__COMPLETED));
        assertThat(history3.getJsonString(EVENT_DATE).getString(), is(EVENT_DATE_VALUE.toString()));
    }
    private List<TaskHistoryEntity> getTaskHistoryEntityList() {
        final List<TaskHistoryEntity> taskHistoryEntityList = new ArrayList<>();
        taskHistoryEntityList.add(getTaskHistoryEntity(EVENT_TYPE_CREATED, CHANGE_AUTHOR_CREATED, ID_CREATED, null));
        taskHistoryEntityList.add(getTaskHistoryEntity(EVENT_TYPE_ASSIGNED, CHANGE_AUTHOR__ASSIGNED, ID_ASSIGNED, DETAILS_TYPE_ASSIGNED));
        taskHistoryEntityList.add(getTaskHistoryEntity(EVENT_TYPE_COMPLETED, CHANGE_AUTHOR__COMPLETED, ID_COMPLETED, null));
        return taskHistoryEntityList;
    }

    private TaskHistoryEntity getTaskHistoryEntity(final String eventType, final String changeAuthor, final UUID id, final String details) {
        final TaskHistoryEntity taskHistoryEntity = new TaskHistoryEntity();
        taskHistoryEntity.setEventType(eventType);
        taskHistoryEntity.setChangeAuthor(changeAuthor);
        taskHistoryEntity.setEventDate(EVENT_DATE_VALUE);
        taskHistoryEntity.setId(id);
        taskHistoryEntity.setDetails(details);
        return taskHistoryEntity;
    }
}