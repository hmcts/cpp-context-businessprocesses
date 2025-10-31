package uk.gov.moj.cpp.businessprocesses.service;

import static java.util.Optional.ofNullable;
import static java.util.UUID.fromString;
import static java.util.UUID.randomUUID;
import static javax.json.Json.createObjectBuilder;
import static uk.gov.justice.services.core.annotation.Component.EVENT_PROCESSOR;
import static uk.gov.justice.services.core.enveloper.Enveloper.envelop;
import static uk.gov.justice.services.messaging.Envelope.metadataBuilder;
import static uk.gov.justice.services.messaging.JsonEnvelope.envelopeFrom;
import static uk.gov.moj.cpp.businessprocesses.shared.WorkflowTaskTypeMapper.mapToWorkflowTaskType;

import uk.gov.justice.services.common.converter.JsonObjectToObjectConverter;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.MetadataBuilder;
import uk.gov.moj.cpp.businessprocesses.pojo.PublicHoliday;
import uk.gov.moj.cpp.businessprocesses.pojo.WorkflowTaskType;
import uk.gov.moj.cpp.businessprocesses.refdata.query.api.Resultdefinition;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.inject.Inject;
import javax.json.JsonArray;
import javax.json.JsonObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReferenceDataService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReferenceDataService.class);
    private static final String REFERENCE_DATA_QUERY_WORKFLOW_TASK_TYPES = "referencedata.query.workflow-task-types";
    private static final String REFERENCEDATA_GET_DOCUMENT_TYPE_INFO = "referencedata.query.document-type-access";
    private static final String REFERENCEDATA_GET_WORK_QUEUE_NAME = "referencedata.query.get-workqueue-name";
    private static final String REFERENCE_DATA_QUERY_RESULT_DEFINITION_BY_ID = "referencedata.get-result-definition";
    private static final String TASK_NAME = "taskName";
    private static final String ID = "id";
    private static final String OUCODE = "oucode";
    private static final String REGION = "region";
    private static final String PUBLIC_HOLIDAYS = "publicHolidays";
    private static final String REFERENCEDATA_QUERY_PUBLIC_HOLIDAYS_NAME = "referencedata.query.public-holidays";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String REFERENCE_DATA_QUERY_COURT_CENTRES_BY_COURT_ROOM = "referencedata.query.courtroom";
    private static final String REFERENCE_DATA_QUERY_COURT_CENTRES_BY_COURT_ROOMS = "referencedata.query.ou.courtrooms.ou-courtroom-code";

    private static final String RESULT_DEFINITION_ID = "resultDefinitionId";
    @Inject
    private JsonObjectToObjectConverter jsonObjectToObjectConverter;

    @Inject
    @ServiceComponent(EVENT_PROCESSOR)
    private Requester requester;

    public Optional<WorkflowTaskType> getWorkflowTaskType(final String taskName) {
        final MetadataBuilder metadataBuilder = JsonEnvelope.metadataBuilder()
                .withId(randomUUID())
                .withName(REFERENCE_DATA_QUERY_WORKFLOW_TASK_TYPES);
        final Envelope<JsonObject> envelope = requester.requestAsAdmin(envelopeFrom(metadataBuilder, createObjectBuilder().add(TASK_NAME, taskName).build()), JsonObject.class);

        return mapToWorkflowTaskType(envelope, taskName);
    }

    public Optional<JsonObject> getWorkQueue(final String taskName) {
        final JsonObject payload = createObjectBuilder()
                .add(TASK_NAME, taskName)
                .build();
        final MetadataBuilder metadataBuilder = metadataBuilder()
                .withId(randomUUID())
                .withName(REFERENCEDATA_GET_WORK_QUEUE_NAME);
        final Envelope<JsonObject> responseEnvelope = requester.requestAsAdmin(envelopeFrom(metadataBuilder, payload), JsonObject.class);

        return ofNullable(responseEnvelope.payload());
    }

    public Optional<JsonObject> getDocumentType(final JsonEnvelope event, final String documentTypeId) {

        final Envelope<JsonObject> envelope = requester.request(envelop(createObjectBuilder().add(ID, documentTypeId).build())
                .withName(REFERENCEDATA_GET_DOCUMENT_TYPE_INFO)
                .withMetadataFrom(event), JsonObject.class);

        return Optional.ofNullable(envelope.payload());
    }


    @SuppressWarnings({"squid:S1172", "squid:S1168"})
    public List<PublicHoliday> getPublicHolidays(final String division,
                                                 final LocalDate fromDate,
                                                 final LocalDate toDate) {

        final MetadataBuilder metadataBuilder = JsonEnvelope.metadataBuilder()
                .withId(randomUUID())
                .withName(REFERENCEDATA_QUERY_PUBLIC_HOLIDAYS_NAME);

        final JsonObject params = getParams(division, fromDate, toDate);

        final Envelope<JsonObject> jsonObjectEnvelope = requester.requestAsAdmin(envelopeFrom(metadataBuilder, params), JsonObject.class);

        return transform(jsonObjectEnvelope);
    }

    public Resultdefinition getResultDefinition(final String resultDefinitionId) {
        final JsonObject payload = createObjectBuilder()
                .add(RESULT_DEFINITION_ID, resultDefinitionId)
                .build();

        final MetadataBuilder metadataBuilder = metadataBuilder()
                .withId(randomUUID())
                .withName(REFERENCE_DATA_QUERY_RESULT_DEFINITION_BY_ID);

        final Envelope<JsonObject> responseEnvelope = requester.requestAsAdmin(envelopeFrom(metadataBuilder, payload), JsonObject.class);
        return jsonObjectToObjectConverter.convert(responseEnvelope.payload(), Resultdefinition.class);
    }

    public Boolean isWelshCourt(final String courtId) {
        JsonObject responsePayload = retrieveCourtCentreDetailsByCourtId(courtId);
        final boolean isWelsh = responsePayload.getBoolean("isWelsh", false);
        LOGGER.info("CourtId = {}, isWelsh= {}", courtId, isWelsh);
        return isWelsh;
    }

    public JsonObject retrieveCourtCentreDetailsByCourtId(final String courtId) {
        try {
            final JsonObject payload = createObjectBuilder().add("id", courtId).build();

            final JsonEnvelope requestEnvelope = envelopeFrom(
                    JsonEnvelope.metadataBuilder()
                            .withName(REFERENCE_DATA_QUERY_COURT_CENTRES_BY_COURT_ROOM)
                            .withId(randomUUID()).build(), payload);

            return requester.requestAsAdmin(envelopeFrom(requestEnvelope.metadata(), requestEnvelope.payload())).asJsonObject();
        } catch (Exception e) {
            LOGGER.error("Error retrieving court centre details for courtId: {}", courtId, e);
            return null;
        }
    }

    public JsonObject retrieveCourtCentreDetailsByCourtRoomCode(final String courtRoomCode) {
        try {
            final JsonObject payload = createObjectBuilder().add("ouCourtRoomCode", courtRoomCode).build();

            final JsonEnvelope requestEnvelope = envelopeFrom(
                    JsonEnvelope.metadataBuilder()
                            .withName(REFERENCE_DATA_QUERY_COURT_CENTRES_BY_COURT_ROOMS)
                            .withId(randomUUID()).build(), payload);

            return requester.requestAsAdmin(envelopeFrom(requestEnvelope.metadata(), requestEnvelope.payload())).asJsonObject();
        } catch (Exception e) {
            LOGGER.error("Error retrieving court centre details for courtRoomCode: {}", courtRoomCode, e);
            return null;
        }
    }

    public static String getCourtCentreOuCode(final JsonObject responsePayload){
        if (responsePayload != null && responsePayload.containsKey(OUCODE)) {
            return responsePayload.getString(OUCODE);
        }
        return null;
    }

    public static String getCourtCentreId(final JsonObject responsePayload){
        if (responsePayload != null && responsePayload.containsKey(ID)) {
            return responsePayload.getString(ID);
        }
        return null;
    }

    public static String getCourtCentreRegion(final JsonObject responsePayload){
        if (responsePayload != null && responsePayload.containsKey(REGION)) {
            return responsePayload.getString(REGION);
        }
        return null;
    }

    private JsonObject getParams(final String division,
                                 final LocalDate fromDate,
                                 final LocalDate toDate) {
        return createObjectBuilder()
                .add("division", division)
                .add("dateFrom", fromDate.toString())
                .add("dateTo", toDate.toString())
                .build();
    }

    private List<PublicHoliday> transform(final Envelope<JsonObject> envelope) {
        final List<PublicHoliday> publicHolidays = new ArrayList();
        final JsonObject payload = envelope.payload();
        if (payload.containsKey(PUBLIC_HOLIDAYS)) {
            final JsonArray jsonArray = payload.getJsonArray(PUBLIC_HOLIDAYS);
            if (!jsonArray.isEmpty()) {
                final List<JsonObject> publicHolidaysArray = jsonArray.getValuesAs(JsonObject.class);
                for (final JsonObject pd : publicHolidaysArray) {
                    publicHolidays.add(toPublicHoliday(pd));
                }
            }
        }
        return publicHolidays;
    }

    private PublicHoliday toPublicHoliday(final JsonObject pd) {
        final UUID id = fromString(pd.getString(ID));
        final String title = pd.getString("title");
        final String division = pd.getString("division");
        final LocalDate date = LocalDate.parse(pd.getString("date"), DATE_FORMATTER);
        return new PublicHoliday(id, division, title, date);
    }
}
