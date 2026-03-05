package uk.gov.moj.cpp.businessprocesses.service;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.withJsonPath;
import static java.lang.Boolean.TRUE;
import static java.util.UUID.randomUUID;
import static javax.json.Json.createArrayBuilder;
import static javax.json.Json.createObjectBuilder;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.justice.services.messaging.Envelope.envelopeFrom;
import static uk.gov.justice.services.messaging.Envelope.metadataBuilder;
import static uk.gov.justice.services.test.utils.core.matchers.JsonEnvelopeMatcher.jsonEnvelope;
import static uk.gov.justice.services.test.utils.core.matchers.JsonEnvelopeMetadataMatcher.metadata;
import static uk.gov.justice.services.test.utils.core.matchers.JsonEnvelopePayloadMatcher.payloadIsJson;
import static uk.gov.justice.services.test.utils.core.messaging.MetadataBuilderFactory.metadataWithRandomUUID;
import static uk.gov.justice.services.test.utils.core.reflection.ReflectionUtil.setField;

import uk.gov.justice.services.common.converter.JsonObjectToObjectConverter;
import uk.gov.justice.services.common.converter.ObjectToJsonObjectConverter;
import uk.gov.justice.services.common.converter.jackson.ObjectMapperProducer;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.moj.cpp.businessprocesses.pojo.PublicHoliday;
import uk.gov.moj.cpp.businessprocesses.pojo.WorkflowTaskType;
import uk.gov.moj.cpp.businessprocesses.refdata.query.api.Resultdefinition;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.ws.rs.BadRequestException;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hamcrest.core.Is;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ReferenceDataServiceTest {

    private static final String REFERENCE_DATA_QUERY_WORKFLOW_TASK_TYPES = "referencedata.query.workflow-task-types";
    private static final String REFERENCEDATA_QUERY_PUBLIC_HOLIDAYS_NAME = "referencedata.query.public-holidays";
    private static final String WORK_FLOW_TASK_TYPES = "workflowWorkQueueMappings";
    private static final String PUBLIC_HOLIDAYS = "publicHolidays";
    private static final String ENGLAND_AND_WALES_DIVISION = "england-and-wales";

    private static final String FIRST_TASK_ID = randomUUID().toString();
    private static final String SECOND_TASK_ID = randomUUID().toString();
    private static final String FIRST_HOLIDAY_ID = randomUUID().toString();
    private static final String SECOND_HOLIDAY_ID = randomUUID().toString();

    @Captor
    private ArgumentCaptor<JsonEnvelope> envelopeArgumentCaptor;

    @Mock
    private Requester requester;

    @InjectMocks
    private ReferenceDataService target;

    @Spy
    private final ObjectMapper objectMapper = new ObjectMapperProducer().objectMapper();

    @Spy
    private final JsonObjectToObjectConverter jsonToObjectConverter = new JsonObjectToObjectConverter(objectMapper);

    @Spy
    private ObjectToJsonObjectConverter objectToJsonObjectConverter;

    @BeforeEach
    public void setUp() {
        setField(objectToJsonObjectConverter, "mapper", new ObjectMapperProducer().objectMapper());
    }

    @Test
    public void shouldGetWorkflowTaskType() {
        when(requester.requestAsAdmin(any(Envelope.class), eq(JsonObject.class))).thenReturn(getWorkflowTaskTypeList());
        Optional<WorkflowTaskType> optionalWorkflowTaskType = target.getWorkflowTaskType("myTask1");
        assertThat(optionalWorkflowTaskType.isPresent(), is(TRUE));
        assertThat(optionalWorkflowTaskType.get().getId().toString(), is(FIRST_TASK_ID));

        optionalWorkflowTaskType = target.getWorkflowTaskType("myTask2");
        assertThat(optionalWorkflowTaskType.isPresent(), is(TRUE));
        assertThat(optionalWorkflowTaskType.get().getId().toString(), is(SECOND_TASK_ID));
    }

    @Test
    public void shouldGetPublicHolidays() {
        when(requester.requestAsAdmin(any(Envelope.class), eq(JsonObject.class))).thenReturn(getPublicHolidaysList());
        final List<PublicHoliday> publicHolidays = target.getPublicHolidays("england-and-wales", LocalDate.now(), LocalDate.now().plusDays(1));

        assertThat(publicHolidays.size(), is(2));

        final PublicHoliday christmasDayHoliday = publicHolidays.get(0);
        assertThat(christmasDayHoliday.getId().toString(), is(FIRST_HOLIDAY_ID));
        assertThat(christmasDayHoliday.getTitle(), is("Christmas Day"));
        assertThat(christmasDayHoliday.getDate(), is(LocalDate.of(2022, 12, 25)));

        final PublicHoliday boxingDayHoliday = publicHolidays.get(1);
        assertThat(boxingDayHoliday.getId().toString(), is(SECOND_HOLIDAY_ID));
        assertThat(boxingDayHoliday.getTitle(), is("Boxing Day"));
        assertThat(boxingDayHoliday.getDate(), is(LocalDate.of(2022, 12, 26)));
    }

    private Envelope<JsonObject> getWorkflowTaskTypeList() {
        final JsonArrayBuilder workflowTaskTypeBuilder = createArrayBuilder();

        final JsonObject workflowTaskType_1 = createObjectBuilder()
                .add("id", FIRST_TASK_ID)
                .add("taskName", "myTask1")
                .add("taskGroup", "Legal Advisers")
                .add("isDeletable", true)
                .add("isDeferrable", true)
                .add("duration", 6)
                .add("followUpInterval", 4)
                .build();

        final JsonObject workflowTaskType_2 = createObjectBuilder()
                .add("id", SECOND_TASK_ID)
                .add("taskName", "myTask2")
                .add("taskGroup", "Listing Officers")
                .add("isDeletable", false)
                .add("isDeferrable", false)
                .add("duration", 7)
                .add("followUpInterval", 5)
                .build();

        return envelopeFrom(
                metadataWithRandomUUID(REFERENCE_DATA_QUERY_WORKFLOW_TASK_TYPES),
                createObjectBuilder()
                        .add(WORK_FLOW_TASK_TYPES, workflowTaskTypeBuilder
                                .add(workflowTaskType_1)
                                .add(workflowTaskType_2))
                        .build());
    }

    private Envelope<JsonObject> getPublicHolidaysList() {
        final JsonArrayBuilder publicHolidaysList = createArrayBuilder();

        final JsonObject publicHoliday1 = createObjectBuilder()
                .add("id", FIRST_HOLIDAY_ID)
                .add("title", "Christmas Day")
                .add("division", "england-and-wales")
                .add("date", LocalDate.of(2022, 12, 25).toString())
                .build();
        publicHolidaysList.add(publicHoliday1);

        final JsonObject publicHoliday2 = createObjectBuilder()
                .add("id", SECOND_HOLIDAY_ID)
                .add("title", "Boxing Day")
                .add("division", ENGLAND_AND_WALES_DIVISION)
                .add("date", LocalDate.of(2022, 12, 26).toString())
                .build();
        publicHolidaysList.add(publicHoliday2);

        return envelopeFrom(
                metadataWithRandomUUID(REFERENCEDATA_QUERY_PUBLIC_HOLIDAYS_NAME),
                createObjectBuilder()
                        .add(PUBLIC_HOLIDAYS, publicHolidaysList)
                        .build());
    }

    @Test
    public void getResultDefinitionsById() {
        final UUID id = randomUUID();
        final Resultdefinition mockResultDefinition = Resultdefinition.resultdefinition().withId(id).build();
        final JsonEnvelope envelope = JsonEnvelope.envelopeFrom(metadataWithRandomUUID("referencedata.get-result-definition"),
                objectToJsonObjectConverter.convert(mockResultDefinition));
        when(requester.requestAsAdmin(any(JsonEnvelope.class), any())).thenAnswer(mock -> envelope);
        Resultdefinition resultDefinition = target.getResultDefinition(id.toString());
        assertThat(resultDefinition.getId(), is(id));
        verify(requester).requestAsAdmin(envelopeArgumentCaptor.capture(), any());

        assertThat(envelopeArgumentCaptor.getValue(), Is.is(jsonEnvelope(
                metadata()
                        .withName("referencedata.get-result-definition"),
                payloadIsJson(allOf(
                        withJsonPath("$.resultDefinitionId", equalTo(id.toString()))
                )))
        ));
        verifyNoMoreInteractions(requester);
    }

    @Test
    public void testIsWelshCourt() {
        when(requester.requestAsAdmin(any(JsonEnvelope.class))).thenReturn(courtRoomResponseEnvelope());
        Boolean isWelshCourt =  target.isWelshCourt("testId");
        assertTrue(isWelshCourt);
        verify(requester).requestAsAdmin(envelopeArgumentCaptor.capture());

        assertThat(envelopeArgumentCaptor.getValue(), Is.is(jsonEnvelope(
                metadata()
                        .withName("referencedata.query.courtroom"),
                payloadIsJson(allOf(
                        withJsonPath("$.id", equalTo("testId"))
                )))
        ));
        verifyNoMoreInteractions(requester);
    }

    @Test
    public void retrieveCourtCentreDetailsByCourtRoomCode() {
        when(requester.requestAsAdmin(any(JsonEnvelope.class))).thenReturn( courtRoomsResponseEnvelope());
        String ouCode = ReferenceDataService.getCourtCentreOuCode(target.retrieveCourtCentreDetailsByCourtRoomCode("testOuCourtRoomCode"));
        assertThat(ouCode, is("oucode"));
        verify(requester).requestAsAdmin(envelopeArgumentCaptor.capture());

        assertThat(envelopeArgumentCaptor.getValue(), Is.is(jsonEnvelope(
                metadata()
                        .withName("referencedata.query.ou.courtrooms.ou-courtroom-code"),
                payloadIsJson(allOf(
                        withJsonPath("$.ouCourtRoomCode", equalTo("testOuCourtRoomCode"))
                )))
        ));
        verifyNoMoreInteractions(requester);
    }

    @Test
    public void retrieveCourtCentreDetailsByCourtRoomName() {
        when(requester.requestAsAdmin(any(JsonEnvelope.class))).thenReturn(courtRoomNameResponseEnvelope());
        String ouCode = ReferenceDataService.getCourtCentreOuCode(target.retrieveCourtCentreDetailsByCourtRoomName("testOuCourtRoomName"));
        assertThat(ouCode, is("oucode"));
        verify(requester).requestAsAdmin(envelopeArgumentCaptor.capture());

        assertThat(envelopeArgumentCaptor.getValue(), Is.is(jsonEnvelope(
                metadata().withName("referencedata.query.ou.courtrooms.ou-courtroom-name"),
                payloadIsJson(allOf(
                        withJsonPath("$.ouCourtRoomName", equalTo("testOuCourtRoomName"))
                )))
        ));
        verifyNoMoreInteractions(requester);
    }

    private JsonEnvelope courtRoomResponseEnvelope() {
        return JsonEnvelope.envelopeFrom(
                metadataBuilder().
                        withName("referencedata.query.courtroom").
                        withId(randomUUID()),
                       createObjectBuilder()
                                        .add("id", randomUUID().toString())
                                        .add("isWelsh", true));
    }

    private JsonEnvelope courtRoomsResponseEnvelope() {
        return JsonEnvelope.envelopeFrom(
                metadataBuilder().
                        withName("referencedata.query.courtrooms").
                        withId(randomUUID()),
                createObjectBuilder()
                        .add("id", randomUUID().toString())
                        .add("oucode", "oucode"));
    }

    private JsonEnvelope courtRoomNameResponseEnvelope() {
        return JsonEnvelope.envelopeFrom(
                metadataBuilder().
                        withName("referencedata.query.ou.courtrooms.ou-courtroom-name").
                        withId(randomUUID()),
                createObjectBuilder()
                        .add("id", randomUUID().toString())
                        .add("oucode", "oucode"));
    }

}