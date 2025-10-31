package uk.gov.moj.cpp.businessprocesses.util;

import static java.util.Collections.emptyList;
import static java.util.Optional.ofNullable;
import static org.slf4j.LoggerFactory.getLogger;
import static uk.gov.justice.services.messaging.Envelope.envelopeFrom;
import static uk.gov.justice.services.test.utils.core.messaging.MetadataBuilderFactory.metadataWithRandomUUID;
import static uk.gov.moj.cpp.businessprocesses.shared.WorkflowTaskTypeMapper.mapToWorkflowTaskType;
import static uk.gov.moj.cpp.businessprocesses.util.JsonUtil.getJsonObjectFromResource;

import uk.gov.justice.courts.progression.query.Caag;
import uk.gov.justice.listing.events.Hearing;
import uk.gov.justice.services.common.converter.jackson.ObjectMapperProducer;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.moj.cpp.businessprocesses.json.schemas.listing.Hearings;
import uk.gov.moj.cpp.businessprocesses.pojo.WorkflowTaskType;

import java.io.IOException;
import java.util.List;

import javax.json.JsonObject;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;

public class TestDataProvider {

    private static final Logger LOGGER = getLogger(TestDataProvider.class);
    private static final String HEARINGS_JSON = "hearings.json";
    private static final String PROSECUTION_CASE_CAAG_JSON = "prosecutionCaseCaag.json";
    private static final String PROSECUTION_CASE_JSON = "progression.query.prosecutioncase.json";
    private static final String PROSECUTION_CASE_EXIST_JSON = "progression.query.case-exists-by-caseurn.json";
    private static final String LISTING_SEARCH_HEARINGS_QUERY = "listing.allocated.and.unallocated.hearings";
    private static final String TASK_VARIABLE_JSON_INPUT_VALUE = "{\"displayName\":\"Check phone call2\",\"taskName\":\"customTask1\",\"due\":\"2021-06-27\",\"caseURN\":\"29GD7875621\",\"assignee\":\"checkOuCodeLogic\",\"candidateGroups\":\"3d1b2be0-f92a-4291-9b99-17af7e645904\",\"isDeletable\":true,\"isDeferrable\":true,\"organisationId\":\"7f2b2be0-f92a-4291-9b99-17af7e645321\",\"caseId\":\"8e4b2be0-f92a-4291-9b99-17af7e645472\",\"businessUnitCodes\":\"businessUnitOuCode\",\"courtCodes\":\"courtOuCode\",\"isUrgent\":false}";
    private static final String TASK_VARIABLE_JSON_COMPLETE_VALUE = "{\"displayName\":\"Check phone call2\",\"taskName\":\"custom_activity\",\"due\":\"2021-06-27T23:59:00.000Z\",\"hearingDate\":\"2025-08-27\",\"custodyTimeLimit\":\"2025-01-27\",\"caseURN\":\"29GD7875621\",\"hearingType\":\"Plea and Trial Preparation\",\"assignee\":\"checkOuCodeLogic\",\"candidateGroups\":\"3d1b2be0-f92a-4291-9b99-17af7e645904\",\"isDeletable\":true,\"isDeferrable\":true,\"organisationId\":\"7f2b2be0-f92a-4291-9b99-17af7e645321\",\"caseId\":\"8e4b2be0-f92a-4291-9b99-17af7e645472\",\"businessUnitCodes\":\"businessUnitOuCode\",\"courtCodes\":\"courtOuCode\",\"isUrgent\":false,\"defendants\":[{\"id\":\"1\",\"firstName\":\"Smith\",\"lastName\":\"Philip\"},{\"id\":\"2\",\"firstName\":\"Mona\",\"lastName\":\"Lisa\"},{\"id\":\"3\",\"firstName\":\"Victor\",\"lastName\":\"Hugo\"},{\"id\":\"4\",\"firstName\":\"Leo\",\"lastName\":\"Tolstoy\"}],\"comment\":\"This is test comment\"}";
    private static final String PROGRESSION_QUERY_PROSECUTION_CASE_CAAG = "progression.query.prosecutioncase.caag";
    private static final String PROGRESSION_QUERY_PROSECUTION_CASE_EXIST_BY_CASEURN = "progression.query.case-exist-by-caseurn";
    private static final String WORKFLOW_TASK_TYPES_JSON = "workflow-task-types.json";
    private static final String REFERENCE_DATA_COURT_ROOMS_JSON = "referencedata.court.rooms.welsh.json";
    private static final ObjectMapper objectMapper = new ObjectMapperProducer().objectMapper();


    public static Envelope<JsonObject> getResponseEnvelopeFromProgressionCaag() {
        JsonObject responseJsonObject = null;
        try {
            responseJsonObject = getJsonObjectFromResource(PROSECUTION_CASE_CAAG_JSON);
        } catch (IOException e) {
            LOGGER.error("error while parsing prosecution json");
        }
        return envelopeFrom(metadataWithRandomUUID(PROGRESSION_QUERY_PROSECUTION_CASE_CAAG), responseJsonObject);
    }

    public static Envelope<JsonObject> getResponseEnvelopeFromListing() {
        JsonObject responseJsonObject = null;
        try {
            responseJsonObject = getJsonObjectFromResource(HEARINGS_JSON);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return envelopeFrom(metadataWithRandomUUID(LISTING_SEARCH_HEARINGS_QUERY), responseJsonObject);
    }


    public static Caag getProsecutionCaseCaag() throws IOException {
        final JsonObject jsonObject = getJsonObjectFromResource(PROSECUTION_CASE_CAAG_JSON);

        Caag caag = null;
        if (null != jsonObject) {
            try {
                caag = objectMapper.readValue(jsonObject.toString(), Caag.class);
            } catch (IOException e) {
                throw new IllegalStateException("Unable to unmarshal prosecutioncase", e);
            }
        }
        return caag;
    }

    public static List<Hearing> getHearingList() throws IOException {
        final JsonObject jsonObject = getJsonObjectFromResource(HEARINGS_JSON);

        Hearings hearings = null;
        if (null != jsonObject) {
            try {
                hearings = objectMapper.readValue(jsonObject.toString(), Hearings.class);
            } catch (IOException e) {
                throw new IllegalStateException("Unable to unmarshal Hearings", e);

            }
        }
        return ofNullable(hearings).map(Hearings::getHearings).orElse(emptyList());
    }

    public static String createTaskVariableInputJson() {
        return TASK_VARIABLE_JSON_INPUT_VALUE;
    }

    public static String createTaskVariableCompleteJson() {
        return TASK_VARIABLE_JSON_COMPLETE_VALUE;
    }

    public static WorkflowTaskType getWorkflowTaskType(final String taskName) throws IOException {
        return mapToWorkflowTaskType(getJsonObjectFromResource(WORKFLOW_TASK_TYPES_JSON), taskName).get();
    }

    public static JsonObject getJurisdictionType() throws IOException {

        return getJsonObjectFromResource(PROSECUTION_CASE_JSON);
    }

    public static JsonObject getResponseEnvelopeFromProgressionProsecutionCaseExist() throws IOException {
        return getJsonObjectFromResource(PROSECUTION_CASE_EXIST_JSON);
    }

    public static JsonObject getReferenceDataCourtRoomsPayload() throws IOException {
        return getJsonObjectFromResource(REFERENCE_DATA_COURT_ROOMS_JSON);
    }
}
