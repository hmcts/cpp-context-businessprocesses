package uk.gov.moj.cpp.businessprocesses.shared;

import static java.util.Objects.nonNull;
import static java.util.UUID.randomUUID;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;
import static uk.gov.justice.services.messaging.JsonEnvelope.metadataBuilder;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.CHANGE_AUTHOR;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.CHANGE_AUTHOR_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.COURT_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.REFERENCE;
import static uk.gov.moj.cpp.businessprocesses.shared.DateConverter.getUTCZonedDateTimeString;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.CASE_URN;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.DUE_DATE;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.HEARING_DATE;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.JURISDICTION;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.LAST_UPDATED_BY_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.LAST_UPDATED_BY_NAME;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.NOTE;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.TASK_TYPE_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.WORK_QUEUE;

import uk.gov.justice.services.messaging.Metadata;

import javax.json.JsonObjectBuilder;

import org.camunda.bpm.engine.impl.persistence.entity.TaskEntity;
import org.camunda.bpm.engine.variable.impl.VariableMapImpl;

public class TaskCreatedListenerHelper {

    private TaskCreatedListenerHelper() {
    }

    @SuppressWarnings("squid:MethodCyclomaticComplexity")
    public static JsonObjectBuilder getJsonObjectBuilder(final VariableMapImpl variableMap, final TaskEntity taskEntity) {
        final String taskId = taskEntity.getId();
        final String createdDate = getUTCZonedDateTimeString(taskEntity.getCreateTime());
        final String taskType = taskEntity.getName();
        final JsonObjectBuilder jsonObjectBuilder = createObjectBuilder()
                .add("id", taskId)
                .add("createdDate", createdDate)
                .add("type", taskType);

        final String changeAuthorId = variableMap.getValue(LAST_UPDATED_BY_ID, String.class);
        jsonObjectBuilder.add(CHANGE_AUTHOR_ID, changeAuthorId);
        jsonObjectBuilder.add(CHANGE_AUTHOR, variableMap.getValue(LAST_UPDATED_BY_NAME, String.class));

        if (taskEntity.getVariableLocal(TASK_TYPE_ID) != null) {
            jsonObjectBuilder.add(TASK_TYPE_ID, taskEntity.getVariableLocal(TASK_TYPE_ID).toString());
        }

        if (taskEntity.getVariableLocal(WORK_QUEUE) != null) {
            jsonObjectBuilder.add(WORK_QUEUE, taskEntity.getVariableLocal(WORK_QUEUE).toString());
        }

        if (taskEntity.getDueDate() != null) {
            final String dateTimeString = getUTCZonedDateTimeString(taskEntity.getDueDate());
            jsonObjectBuilder.add(DUE_DATE, dateTimeString);
        }

        if (variableMap.containsKey(CASE_URN) && nonNull(variableMap.getValue(CASE_URN, String.class))) {
            final String caseUrn = variableMap.getValue(CASE_URN, String.class);
            jsonObjectBuilder.add(REFERENCE, caseUrn);
        }

        if (variableMap.containsKey(NOTE)) {
            final String note = variableMap.getValue(NOTE, String.class);
            jsonObjectBuilder.add(NOTE, note);
        }

        if (variableMap.containsKey(HEARING_DATE)) {
            final String hearingDate = variableMap.get(HEARING_DATE).toString();
            jsonObjectBuilder.add(HEARING_DATE, hearingDate);
        }

        if (variableMap.containsKey(COURT_ID)) {
            final String courtId = variableMap.getValue(COURT_ID, String.class);
            jsonObjectBuilder.add(COURT_ID, courtId);
        }

        if (variableMap.containsKey(JURISDICTION)) {
            final String jurisdiction = variableMap.getValue(JURISDICTION, String.class);
            jsonObjectBuilder.add(JURISDICTION, jurisdiction);
        }

        return jsonObjectBuilder;
    }

    public static Metadata getMetadataForRecordTaskCreated(final String userId) {
        final Metadata metadataEvent = metadataBuilder()
                .withId(randomUUID())
                .withName("businessprocesses.command.record-task-created")
                .withUserId(userId).build();
        return metadataEvent;
    }

    public static boolean validateChangeAuthorDetails(final VariableMapImpl variableMap) {
        return variableMap.containsKey(LAST_UPDATED_BY_ID) && variableMap.containsKey(LAST_UPDATED_BY_NAME);
    }
}
