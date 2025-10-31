package uk.gov.moj.cpp.businessprocesses.helper;

import static java.lang.String.format;
import static java.lang.System.getProperty;
import static java.net.HttpURLConnection.HTTP_NO_CONTENT;
import static java.net.HttpURLConnection.HTTP_OK;
import static java.util.Collections.emptyList;
import static java.util.Objects.isNull;
import static java.util.stream.Collectors.toList;
import static javax.json.Json.createObjectBuilder;
import static javax.json.Json.createReader;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.Status.OK;
import static org.apache.commons.collections.CollectionUtils.isNotEmpty;
import static org.hamcrest.CoreMatchers.allOf;
import static org.slf4j.LoggerFactory.getLogger;
import static uk.gov.justice.services.test.utils.core.http.RequestParamsBuilder.requestParams;
import static uk.gov.justice.services.test.utils.core.http.RestPoller.poll;
import static uk.gov.justice.services.test.utils.core.matchers.ResponsePayloadMatcher.payload;
import static uk.gov.justice.services.test.utils.core.matchers.ResponseStatusMatcher.status;
import static uk.gov.moj.cpp.businessprocesses.helper.BpmDeployProcessHelper.deployProcessDefinition;
import static uk.gov.moj.cpp.businessprocesses.util.JsonUtil.getJsonStringFromResource;

import org.awaitility.Awaitility;
import uk.gov.justice.services.common.converter.ObjectToJsonValueConverter;
import uk.gov.justice.services.common.converter.jackson.ObjectMapperProducer;
import uk.gov.justice.services.test.utils.core.http.ResponseData;

import java.io.IOException;
import java.io.StringReader;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import javax.json.JsonValue;
import javax.ws.rs.core.Response;

import org.hamcrest.Matcher;
import org.json.JSONObject;
import org.slf4j.Logger;

public class BpmRestApiHelper {
    private static final Logger LOGGER = getLogger(BpmRestApiHelper.class);
    private static final String HOST = getProperty("INTEGRATION_HOST_KEY", "localhost");
    private static final int PORT = 8080;
    private static final String BASE_URI = "http://" + HOST + ":" + PORT + "/engine-rest";
    private static final String START_PROCESS_INSTANCE_URL = "/process-definition/key/%s/start";
    private static final String DELETE_PROCESS_INSTANCE_URL = "/process-instance/%s?skipCustomListeners=true";
    private static final String GET_PROCESS_INSTANCE_LIST_URL = "/process-instance?processDefinitionKey=%s";
    private static final String GET_PROCESS_INSTANCE_URL_WITH_BUSINESS_KEY = "/process-instance?processDefinitionKey=%s&businessKey=%s";
    private static final String GET_HISTORY_PROCESS_INSTANCE_URL_WITH_BUSINESS_KEY = "/history/process-instance?processDefinitionKey=%s&processInstanceBusinessKey=%s";
    private static final String GET_HISTORY_ACTIVITY_INSTANCES_BY_ACTIVITY_ID_AND_PROCESS_INSTANCE_ID = "/history/activity-instance?activityId=%s&processInstanceId=%s";
    private static final String GET_HISTORY_VARIABLES_BY_EXECUTION_ID = "/history/variable-instance?executionIdIn=%s";

    private static final String COMPLETE_TASK_URL = "/task/%s/complete";
    private static final String ASSIGN_TO_TASK_URL = "/task/%s/assignee";
    private static final String UPDATE_TASK_VARIABLES_URL = "/task/%s/variables/%s";
    private static final String UPDATE_TASK_LOCAL_VARIABLES_URL = "/task/%s/localVariables/%s";
    private static final String UPDATE_TASK_DUE_DATE = "/task/%s/";
    private static final String GET_TASK_LIST_URL = "/task?processInstanceId=%s";
    private static final String GET_FIRST_TASK_MATCHING_PROCESS_INSTANCE_AND_TASK_DEFINITION_KEY = "/task?processInstanceId=%s&taskDefinitionKey=%s";
    private static final String GET_VARIABLE_LIST_URL = "/task/%s/variables";
    private static final String GET_TASK_URL = "/task/%s";
    private static final String GET_IDENTITY_LINKS_URL = "/task/%s/identity-links";
    private static final String CREATE_DEPLOYMENT_URL = "/deployment/create";
    private static final String DELETE_DEPLOYMENT_URL = "/deployment/%s?cascade=true&skipCustomListeners=true";
    private static final String GET_DEPLOYMENTS_URL = "/deployment?name=%s";

    private static final RestClientService restClient = new RestClientService();

    public static void createProcessDefinition(final String processDefinitionName) throws IOException {
        final String url = BASE_URI + CREATE_DEPLOYMENT_URL;
        deployProcessDefinition(processDefinitionName, url);
    }

    public static void deleteProcessDefinition(final String processDefinitionName) {
        final ResponseData queryResponse = getProcessDefinitions(processDefinitionName);
        final JsonArray processDefinitions = createReader(new StringReader(queryResponse.getPayload())).readArray();
        LOGGER.info("Retrieved processDefinitions: {}", processDefinitions);

        for (final JsonValue processDefinition : processDefinitions) {
            final String processDefinitionId = ((JsonObject) processDefinition).getString("id");
            final String url = format(BASE_URI + DELETE_DEPLOYMENT_URL, processDefinitionId);
            final Response deleteResponse = restClient.deleteCommand(url, null, null);

            if (HTTP_NO_CONTENT == deleteResponse.getStatus()) {
                LOGGER.info("Process definition deleted [{}]", processDefinitionName);
            } else {
                LOGGER.warn("Could not delete process definition [{}], [{}]", processDefinitionName, deleteResponse.getStatus());
            }
        }
    }

    public static ResponseData getProcessDefinitions(final String processDefinitionName) {
        final String url = format(BASE_URI + GET_DEPLOYMENTS_URL, processDefinitionName);
        return validateAndGetResponse(url);
    }

    public static List<String> getProcessInstanceList(final String processDefinitionName) {
        final String url = format(BASE_URI + GET_PROCESS_INSTANCE_LIST_URL, processDefinitionName);
        final ResponseData responseData = validateAndGetResponse(url);
        final JsonArray processInstances = getJsonArray(responseData.getPayload());

        if (isNull(processInstances)) {
            return emptyList();
        }
        return processInstances.stream()
                .filter(Objects::nonNull)
                .filter(jsonValue -> ((JsonObject) jsonValue).containsKey("id"))
                .map(jsonValue -> ((JsonObject) jsonValue).getString("id")).collect(toList());
    }

    public static JsonArray getProcessInstanceList(final String processDefinitionName, final String businessKey) {
        final String businessKeyUrl = format(BASE_URI + GET_PROCESS_INSTANCE_URL_WITH_BUSINESS_KEY, processDefinitionName, businessKey);
        final ResponseData responseData = validateAndGetResponseWithDelay(1, businessKeyUrl);
        return getJsonArray(responseData.getPayload());
    }

    public static JsonArray getProcessInstanceHistoryList(final String processDefinitionName, final String businessKey) {
        final String businessKeyUrl = format(BASE_URI + GET_HISTORY_PROCESS_INSTANCE_URL_WITH_BUSINESS_KEY, processDefinitionName, businessKey);
        final ResponseData responseData = validateAndGetResponseWithDelay(3, businessKeyUrl);
        return getJsonArray(responseData.getPayload());
    }

    public static JsonArray getHistoricalActivityInstanceList(final String activityInstanceId, final String processInstanceId) {
        final String businessKeyUrl = format(BASE_URI + GET_HISTORY_ACTIVITY_INSTANCES_BY_ACTIVITY_ID_AND_PROCESS_INSTANCE_ID, activityInstanceId, processInstanceId);
        final ResponseData responseData = validateAndGetResponse(businessKeyUrl);
        return getJsonArray(responseData.getPayload());
    }

    public static JsonArray getHistoricalVariablesList(final String executionId) {
        final String businessKeyUrl = format(BASE_URI + GET_HISTORY_VARIABLES_BY_EXECUTION_ID, executionId);
        final ResponseData responseData = validateAndGetResponse(businessKeyUrl);
        return getJsonArray(responseData.getPayload());
    }

    public static List<String> getTaskList(final String processInstanceId) {
        final String url = format(BASE_URI + GET_TASK_LIST_URL, processInstanceId);
        final ResponseData responseData = validateAndGetResponse(url);
        final JsonArray tasks = getJsonArray(responseData.getPayload());

        if (isNull(tasks)) {
            return emptyList();
        }
        return tasks.stream()
                .filter(Objects::nonNull)
                .filter(jsonValue -> ((JsonObject) jsonValue).containsKey("id"))
                .map(jsonValue -> ((JsonObject) jsonValue).getString("id")).collect(toList());
    }


    public static String getFirstTaskIdMatching(final String processInstanceId, final String taskDefinitionKey) {
        final String url = format(BASE_URI + GET_FIRST_TASK_MATCHING_PROCESS_INSTANCE_AND_TASK_DEFINITION_KEY, processInstanceId, taskDefinitionKey);
        final ResponseData responseData = validateAndGetResponse(url);
        final JsonArray tasks = getJsonArray(responseData.getPayload());

        if (isNull(tasks)) {
            return null;
        }
        final List<String> collect = tasks.stream()
                .filter(Objects::nonNull)
                .filter(jsonValue -> ((JsonObject) jsonValue).containsKey("id"))
                .map(jsonValue -> ((JsonObject) jsonValue).getString("id")).collect(toList());

        if (isNotEmpty(collect)) {
            return collect.get(0);
        }

        return null;
    }

    public static JsonObject getTaskDetails(final String taskId) {
        return getJsonObjectResponse(BASE_URI + GET_TASK_URL, taskId);
    }

    public static JsonObject getVariablesLocal(final String taskId) {
        return getJsonObjectResponse(BASE_URI + GET_VARIABLE_LIST_URL, taskId);
    }

    public static List<String> getCandidateGroups(final String taskId) {
        final String formattedUrl = format(BASE_URI + GET_IDENTITY_LINKS_URL, taskId);
        final ResponseData responseData = validateAndGetResponse(formattedUrl);
        final JsonArray jsonArray = getJsonArray(responseData.getPayload());

        if (isNull(jsonArray)) {
            return emptyList();
        }
        return jsonArray.stream()
                .filter(Objects::nonNull)
                .map(jsonValue -> (JsonObject) jsonValue)
                .filter(jsonObject -> jsonObject.getString("type").equals("candidate"))
                .map(jsonObject -> jsonObject.getString("groupId")).collect(toList());
    }

    public static String startProcessInstance(final String processKey, final String businessKey) {
        return startProcessInstance(processKey, businessKey, "2021-06-26T09:56:10");
    }

    public static String startProcessInstance(final String processKey, final String businessKey, final String dueDate) {
        final String url = format(BASE_URI + START_PROCESS_INSTANCE_URL, processKey);

        final String requestPayload = format("{\"businessKey\":\"%s\", \"variables\": {\n" +
                "\"dueDate\": {\"value\":\"%s\"\n}}}", businessKey, dueDate);

        final Response response = restClient.postCommand(url, APPLICATION_JSON, requestPayload);
        if (response.getStatus() == HTTP_OK) {
            final String jsonString = response.readEntity(String.class);
            final JSONObject jsonObject = new JSONObject(jsonString);

            return jsonObject.getString("id");
        }
        return null;
    }


    public static String startProcessInstanceWithVariables(final String processKey, final String businessKey, Map<String, Object> variableMap) {
        final String url = format(BASE_URI + START_PROCESS_INSTANCE_URL, processKey);

        final String requestPayload = processStartPayload(businessKey, variableMap);

        final Response response = restClient.postCommand(url, APPLICATION_JSON, requestPayload);
        if (response.getStatus() == HTTP_OK) {
            final String jsonString = response.readEntity(String.class);
            final JSONObject jsonObject = new JSONObject(jsonString);

            return jsonObject.getString("id");
        }
        return null;
    }

    public static String startProcessInstanceWithRequestBody(final String processKey, final String filePath) throws IOException {
        final String url = format(BASE_URI + START_PROCESS_INSTANCE_URL, processKey);

        final String requestPayload = getJsonStringFromResource(filePath);

        final Response response = restClient.postCommand(url, APPLICATION_JSON, requestPayload);
        if (response.getStatus() == HTTP_OK) {
            final String jsonString = response.readEntity(String.class);
            final JSONObject jsonObject = new JSONObject(jsonString);

            return jsonObject.getString("id");
        }
        return null;
    }

    private static String processStartPayload(String businessKey, Map<String, Object> variableMap) {
        final JsonObjectBuilder objectBuilder = createObjectBuilder();
        final JsonObjectBuilder variablesObjectBuilder = createObjectBuilder();

        variableMap.entrySet().forEach((entry) -> {
            final JsonObjectBuilder varObjectBuilder = createObjectBuilder();
            varObjectBuilder.add("value", new ObjectToJsonValueConverter(new ObjectMapperProducer().objectMapper()).convert(entry.getValue()));
            varObjectBuilder.add("type", getValueType(entry.getValue()));
            variablesObjectBuilder.add(entry.getKey(), varObjectBuilder.build());
        });
        objectBuilder.add("variables", variablesObjectBuilder.build());
        objectBuilder.add("businessKey", businessKey);
        return objectBuilder.build().toString();
    }

    private static String getValueType(Object Object) {
        if (Object instanceof Boolean) {
            return "Boolean";
        } else if (Object instanceof String) {
            return "String";
        } else if (Object instanceof Double || Object instanceof Integer || Object instanceof Long) {
            return "Number";
        } else if (Object instanceof Date) {
            return "Date";
        }
        return null;
    }

    public static void deleteProcessInstance(final String processDefinitionName) {
        final List<String> processInstanceList = getProcessInstanceList(processDefinitionName);

        processInstanceList.forEach(processInstance -> {
            final String url = format(BASE_URI + DELETE_PROCESS_INSTANCE_URL, processInstance);
            restClient.deleteCommand(url, APPLICATION_JSON, null);
        });
    }

    public static void completeFirstTaskOfProcessInstance(final String processInstanceId) {
        final String firstTask = getTaskList(processInstanceId).stream().findFirst().get();
        final String url = format(BASE_URI + COMPLETE_TASK_URL, firstTask);
        restClient.postCommand(url, APPLICATION_JSON, null);
    }

    public static void updateLocalVariablesForFirstTask(final String processInstanceId, final String varName, final String varValue) {
        final String firstTask = getTaskList(processInstanceId).stream().findFirst().get();
        final String url = format(BASE_URI + UPDATE_TASK_LOCAL_VARIABLES_URL, firstTask, varName);
        final String requestPayload = createObjectBuilder().add("value", varValue).build().toString();
        restClient.putCommand(url, APPLICATION_JSON, requestPayload);
    }

    public static void completeTaskMatchingProcessInstanceAndTaskDefinitionKey(final String processInstanceId, final String taskDefinitionKey) {
        final String firstTask = getFirstTaskIdMatching(processInstanceId, taskDefinitionKey);
        completeTaskMatchingTaskId(firstTask);
    }

    public static void completeTaskMatchingTaskId(final String taskId) {
        final String url = format(BASE_URI + COMPLETE_TASK_URL, taskId);
        restClient.postCommand(url, APPLICATION_JSON, null);
        waitForEvent();
    }

    public static void updateVariablesOnTaskMatchingProcessInstanceAndTaskDefinitionKey(final String processInstanceId, final String taskDefinitionKey, final String varName, final String varValue) {
        final String firstTask = getFirstTaskIdMatching(processInstanceId, taskDefinitionKey);
        final String url = format(BASE_URI + UPDATE_TASK_VARIABLES_URL, firstTask, varName);
        final String requestPayload = createObjectBuilder().add("value", varValue).build().toString();
        restClient.putCommand(url, APPLICATION_JSON, requestPayload);
    }

    public static void updateLocalVariablesOnTaskMatchingProcessInstanceAndTaskDefinitionKey(final String processInstanceId, final String taskDefinitionKey, final String varName, final String varValue) {
        final String firstTask = getFirstTaskIdMatching(processInstanceId, taskDefinitionKey);
        final String url = format(BASE_URI + UPDATE_TASK_LOCAL_VARIABLES_URL, firstTask, varName);
        final JsonObjectBuilder objectBuilder = createObjectBuilder();
        if(varValue != null){
            objectBuilder.add("value", varValue);
        }
        final String requestPayload = objectBuilder.build().toString();
        restClient.putCommand(url, APPLICATION_JSON, requestPayload);
        waitForEvent();
    }


    public static void updateTaskDueDate(final String taskId, final String dueDate,final String assignee) {
        final String url = format(BASE_URI + UPDATE_TASK_DUE_DATE, taskId);
        final String requestPayload = createObjectBuilder()
                .add("due", dueDate)
                .add("assignee", assignee)
                .build().toString();
        restClient.putCommand(url, APPLICATION_JSON, requestPayload);
        waitForEvent();
    }

    public static void assignTaskToUser(final String taskId, final String userId) {
        final String url = format(BASE_URI + ASSIGN_TO_TASK_URL, taskId);
        final String requestPayload = createObjectBuilder().add("userId", userId).build().toString();
        restClient.postCommand(url, APPLICATION_JSON, requestPayload);
        waitForEvent();
    }

    private static JsonObject getJsonObjectResponse(final String url, final String taskId) {
        final String formattedUrl = format(url, taskId);
        final ResponseData responseData = validateAndGetResponse(formattedUrl);
        return getJsonObject(responseData.getPayload());
    }

    private static ResponseData validateAndGetResponse(String url, Matcher... matchers) {
        final ResponseData response = poll(requestParams(url, APPLICATION_JSON)).until(
                status().is(OK),
                payload().isJson(allOf(matchers))
        );
        LOGGER.info("Issuing Rest API call to URL: {} and response is \n {} ", url, response.getPayload());
        return response;
    }

    private static ResponseData validateAndGetResponseWithDelay(final long secondsDelay, final String url, Matcher... matchers) {
        final ResponseData response = poll(requestParams(url, APPLICATION_JSON)).pollDelay(secondsDelay, TimeUnit.SECONDS).until(
                status().is(OK),
                payload().isJson(allOf(matchers))
        );
        LOGGER.info("Issuing Rest API call to URL: {} and response is \n {} ", url, response.getPayload());
        return response;
    }

    private static JsonArray getJsonArray(String payload) {
        try (final JsonReader reader = Json.createReader(new StringReader(payload))) {
            return reader.readArray();
        }
    }

    private static JsonObject getJsonObject(String payload) {
        try (final JsonReader reader = Json.createReader(new StringReader(payload))) {
            return reader.readObject();
        }
    }

    public static void waitForEvent(){
        try {
            Thread.sleep(1_000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
