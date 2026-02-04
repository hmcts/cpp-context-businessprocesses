package uk.gov.moj.cpp.businessprocesses.query.view;

import static java.lang.String.format;
import static java.util.Objects.nonNull;
import static uk.gov.justice.services.messaging.JsonObjects.createArrayBuilder;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;
import static uk.gov.justice.services.messaging.JsonEnvelope.envelopeFrom;

import uk.gov.justice.services.adapter.rest.exception.BadRequestException;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.JsonObjects;
import uk.gov.moj.cpp.businessprocesses.persistence.entity.TaskHistoryEntity;
import uk.gov.moj.cpp.businessprocesses.persistence.repository.TaskHistoryRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.inject.Inject;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.ws.rs.NotFoundException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TaskHistoryQueryView {

    private static final Logger LOGGER = LoggerFactory.getLogger(TaskHistoryQueryView.class);

    private static final String TASK_ID = "taskId";
    private static final String HISTORY = "history";
    private static final String ID = "id";
    private static final String EVENT_TYPE = "eventType";
    private static final String EVENT_DATE = "eventDate";
    private static final String CHANGE_AUTHOR = "changeAuthor";
    private static final String DETAILS = "details";

    @Inject
    private TaskHistoryRepository taskHistoryRepository;
    
    public JsonEnvelope getTaskHistory(final JsonEnvelope envelope) {
        final Optional<String> taskId = JsonObjects.getString(envelope.payloadAsJsonObject(), TASK_ID);

        if (taskId.isPresent()) {
            LOGGER.info("Processing query 'businessprocesses.query.task-history' for task: {}", taskId.get());

            final List<TaskHistoryEntity> taskHistoryEntityList = taskHistoryRepository.findByTaskId(UUID.fromString(taskId.get()));

            if (nonNull(taskHistoryEntityList)) {
                final JsonArrayBuilder arrayBuilder = createArrayBuilder();

                taskHistoryEntityList.forEach(taskHistoryEntity -> arrayBuilder.add(buildHistoryJson(taskHistoryEntity)));

                return envelopeFrom(envelope.metadata(),
                        createObjectBuilder()
                                .add(TASK_ID, taskId.get())
                                .add(HISTORY, arrayBuilder).build());

            } else {
                throw new NotFoundException("Please provide a valid task id");
            }
        } else {
            throw new BadRequestException(format("Parameter is required: %s", TASK_ID));
        }
    }

    private JsonObject buildHistoryJson(final TaskHistoryEntity taskHistoryEntity) {
        final JsonObjectBuilder jsonObjectBuilder = createObjectBuilder();
        jsonObjectBuilder.add(ID, taskHistoryEntity.getId().toString());
        jsonObjectBuilder.add(EVENT_TYPE, taskHistoryEntity.getEventType());
        jsonObjectBuilder.add(EVENT_DATE, taskHistoryEntity.getEventDate().toString());
        jsonObjectBuilder.add(CHANGE_AUTHOR, taskHistoryEntity.getChangeAuthor());
        if(nonNull(taskHistoryEntity.getDetails())) {
            jsonObjectBuilder.add(DETAILS, taskHistoryEntity.getDetails());
        }
        return jsonObjectBuilder.build();
    }

}
