package uk.gov.moj.cpp.businessprocesses.shared;

import static java.util.Objects.nonNull;
import static java.util.Optional.empty;
import static org.slf4j.LoggerFactory.getLogger;

import uk.gov.justice.services.common.converter.jackson.ObjectMapperProducer;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.moj.cpp.businessprocesses.pojo.WorkflowCOTRTaskType;
import uk.gov.moj.cpp.businessprocesses.pojo.WorkflowOpaTaskType;
import uk.gov.moj.cpp.businessprocesses.pojo.WorkflowCustomTaskType;
import uk.gov.moj.cpp.businessprocesses.pojo.WorkflowTaskType;

import java.io.IOException;
import java.util.Optional;
import java.util.function.Function;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonValue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;

public class WorkflowTaskTypeMapper {
    private static final Logger LOGGER = getLogger(WorkflowTaskTypeMapper.class);

    private static final ObjectMapper objectMapper = new ObjectMapperProducer().objectMapper();

    private WorkflowTaskTypeMapper() {
    }

    public static Optional<WorkflowTaskType> mapToWorkflowTaskType(final Envelope<JsonObject> jsonObjectEnvelope, final String taskName) {
        if (nonNull(jsonObjectEnvelope)) {
            return mapToWorkflowTaskType(jsonObjectEnvelope.payload(), taskName);
        }

        return empty();
    }

    public static Optional<WorkflowTaskType> mapToWorkflowTaskType(final JsonObject jsonObject, final String taskName) {
        if (nonNull(jsonObject)) {
            final JsonArray jsonArray = jsonObject.getJsonArray(Constants.WORK_FLOW_TASK_TYPES);

            if (nonNull(jsonArray)) {
                return jsonArray.stream()
                        .map(mapToWorkflowTaskType())
                        .filter(workflowTaskType -> workflowTaskType.getTaskName().equals(taskName))
                        .toList()
                        .stream()
                        .findFirst();
            }
        }

        return empty();
    }

    private static Function<JsonValue, WorkflowTaskType> mapToWorkflowTaskType() {
        return jsonValue -> {
            try {
                return objectMapper.readValue(jsonValue.toString(), WorkflowTaskType.class);
            } catch (IOException ex) {
                LOGGER.error("Unable to unmarshal WorkflowTaskType. Payload :{}", jsonValue, ex);
                return null;
            }
        };
    }

    public static WorkflowCustomTaskType mapToWorkflowCustomTaskType(final String taskWithVariablesJsonString) {
        try {
            return objectMapper.readValue(taskWithVariablesJsonString, WorkflowCustomTaskType.class);
        } catch (IOException ex) {
            LOGGER.error("Unable to unmarshal taskWithVariablesJsonString. Payload :{}", taskWithVariablesJsonString, ex);
            return null;
        }
    }

    public static WorkflowCOTRTaskType mapToWorkflowCOTRTaskType(final String cotrTaskWithVariablesJsonString) {
        try {
            return objectMapper.readValue(cotrTaskWithVariablesJsonString, WorkflowCOTRTaskType.class);
        } catch (IOException ex) {
            LOGGER.error("Unable to unmarshal cotrTaskWithVariablesJsonString. Payload :{}", cotrTaskWithVariablesJsonString, ex);
            return null;
        }
    }

    public static WorkflowOpaTaskType mapToWorkflowOpaTaskType(final String opaTaskWithVariablesJsonString) {
        try {
            return objectMapper.readValue(opaTaskWithVariablesJsonString, WorkflowOpaTaskType.class);
        } catch (IOException ex) {
            LOGGER.error("Unable to unmarshal opaTaskWithVariablesJsonString. Payload :{}", opaTaskWithVariablesJsonString, ex);
            return null;
        }
    }
}
