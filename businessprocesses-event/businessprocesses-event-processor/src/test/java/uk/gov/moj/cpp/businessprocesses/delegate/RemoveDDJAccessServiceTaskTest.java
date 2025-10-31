package uk.gov.moj.cpp.businessprocesses.delegate;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.justice.services.messaging.Envelope.envelopeFrom;
import static uk.gov.justice.services.test.utils.core.messaging.MetadataBuilderFactory.metadataWithRandomUUID;
import static uk.gov.justice.services.test.utils.core.reflection.ReflectionUtil.setField;

import uk.gov.justice.services.common.converter.ListToJsonArrayConverter;
import uk.gov.justice.services.common.converter.StringToJsonObjectConverter;
import uk.gov.justice.services.core.featurecontrol.FeatureControlGuard;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.justice.services.core.sender.Sender;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.MetadataBuilder;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.json.JsonArray;
import javax.json.JsonObject;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import static org.hamcrest.MatcherAssert.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class RemoveDDJAccessServiceTaskTest {

    private static final String PERMISSIONS_QUERY_API = "usersgroups.permissions";
    private static final String PERMISSIONS_DELETE_BULK_API = "usersgroups.delete-bulk-permission";
    private static final String DDJ_ACCESS_DESCRIPTION = "Permission for DEPUTY_DISTRICT_JUDGE:Ketan Bond CaseId:";
    private static final String NON_DDJ_ACCESS_DESCRIPTION = "a test 1 permission";
    private static final String TARGET_FIELD = "target";
    private static final String ACTION_FIELD = "action";
    private static final String OBJECT_FIELD = "object";

    private static final String CASE_ID_FIELD = "caseId";
    private static final String CASE_ID = randomUUID().toString();

    @Mock
    private DelegateExecution execution;

    @Mock
    private Requester requester;

    @Mock
    protected Sender sender;

    @InjectMocks
    private RemoveDDJAccessServiceTask removeDDJAccessServiceTask;

    @Spy
    private ListToJsonArrayConverter listToJsonArrayConverter;

    @Spy
    private StringToJsonObjectConverter stringToJsonObjectConverter;

    @Captor
    protected ArgumentCaptor<JsonEnvelope> permissionCommandApi;

    @Mock
    private Envelope<?> envelope;

    @Captor
    protected ArgumentCaptor<JsonEnvelope> permissionQueryApi;

    @Mock
    private FeatureControlGuard featureControlGuard;

    @BeforeEach
    public void setup() throws IOException {
        setField(this.listToJsonArrayConverter, "stringToJsonObjectConverter", stringToJsonObjectConverter);
    }

    @Test
    public void executeRemoveDDJAccessServiceTask_ShouldSendPermissionIdsToUserGroupsDeleteBulkPermissionsApi() throws Exception {
        final String permissionId = randomUUID().toString();
        final MetadataBuilder metadataBuilder = metadataWithRandomUUID(PERMISSIONS_QUERY_API);
        final Envelope<JsonObject> jsonObjectEnvelope = envelopeFrom(metadataBuilder, getPermissionsList(permissionId, DDJ_ACCESS_DESCRIPTION, CASE_ID));

        when(execution.getVariables()).thenReturn(getVariables());
        when(featureControlGuard.isFeatureEnabled(any())).thenReturn(true);
        when(requester.requestAsAdmin(any(), eq(JsonObject.class))).thenReturn(jsonObjectEnvelope);

        removeDDJAccessServiceTask.execute(execution);

        verify(requester, times(1)).requestAsAdmin(permissionQueryApi.capture(), eq(JsonObject.class));
        verify(sender, times(1)).sendAsAdmin(permissionCommandApi.capture());

        final JsonEnvelope queryApiValue = permissionQueryApi.getValue();
        final JsonObject jsonObject = permissionQueryApi.getValue().payloadAsJsonObject();

        assertThat(queryApiValue.metadata().name(), is(PERMISSIONS_QUERY_API));
        assertThat(jsonObject.size(), is(3));
        assertThat(jsonObject.getString(TARGET_FIELD), is(CASE_ID));
        assertThat(jsonObject.getString(ACTION_FIELD), is("Access"));
        assertThat(jsonObject.getString(OBJECT_FIELD), is("Case"));

        final JsonEnvelope commandApiValue = permissionCommandApi.getValue();
        final JsonObject commandAPiObject = permissionCommandApi.getValue().payloadAsJsonObject();
        final JsonArray permissionIdsToCommandApi = commandAPiObject.getJsonArray("permissionIds");

        assertThat(commandApiValue.metadata().name(), is(PERMISSIONS_DELETE_BULK_API));
        assertThat(permissionIdsToCommandApi.size(), is(1));
        assertThat(permissionIdsToCommandApi.getString(0), is(permissionId));
    }

    @Test
    public void executeRemoveDDJAccessServiceTask_ShouldNotSendPermissionIdsToUserGroups_IfCaseDoesNotHaveDDJPermission() throws Exception {
        final String permissionId = randomUUID().toString();

        final MetadataBuilder metadataBuilder = metadataWithRandomUUID(PERMISSIONS_QUERY_API);
        final Envelope<JsonObject> jsonObjectEnvelope = envelopeFrom(metadataBuilder, getPermissionsList(permissionId, NON_DDJ_ACCESS_DESCRIPTION, CASE_ID));

        when(execution.getVariables()).thenReturn(getVariables());
        when(featureControlGuard.isFeatureEnabled(any())).thenReturn(true);
        when(requester.requestAsAdmin(any(), eq(JsonObject.class))).thenReturn(jsonObjectEnvelope);

        removeDDJAccessServiceTask.execute(execution);
        verify(requester, times(1)).requestAsAdmin(permissionQueryApi.capture(), eq(JsonObject.class));
        verify(sender, never()).send(permissionCommandApi.capture());

        final JsonEnvelope queryApiValue = permissionQueryApi.getValue();
        final JsonObject jsonObject = permissionQueryApi.getValue().payloadAsJsonObject();

        assertThat(queryApiValue.metadata().name(), is(PERMISSIONS_QUERY_API));
        assertThat(jsonObject.size(), is(3));
        assertThat(jsonObject.getString(TARGET_FIELD), is(CASE_ID));
        assertThat(jsonObject.getString(ACTION_FIELD), is("Access"));
        assertThat(jsonObject.getString(OBJECT_FIELD), is("Case"));

    }

    @Test
    public void executeRemoveDDJAccessServiceTask_ShouldNotSendPermissionIdsToUserGroupsDeleteBulkPermissionsApi_WhenFeatureIsDisabled() throws Exception {
        when(featureControlGuard.isFeatureEnabled(any())).thenReturn(false);

        removeDDJAccessServiceTask.execute(execution);

        verify(requester, never()).requestAsAdmin(any(), eq(JsonObject.class));
        verify(sender, never()).sendAsAdmin(any());
    }

    private Map<String, Object> getVariables() {
        Map<String, Object> variables = new HashMap<>();
        variables.put(CASE_ID_FIELD, CASE_ID);
        return variables;
    }

    private JsonObject getPermissionsList(final String permissionId, final String description, final String caseId) {
        return stringToJsonObjectConverter.convert("{\n" +
                "  \"permissions\": [\n" +
                "    {\n" +
                "      \"permissionId\": \"" + permissionId + "\",\n" +
                "      \"description\": \"" + description.concat(caseId) + "\",\n" +
                "      \"object\" :\"CaseList\",\n" +
                "      \"action\" : \"View\",\n" +
                "      \"active\": \"true\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"permissionId\": \"5b6f843e-d639-40b3-8611-8015f3a34758\",\n" +
                "      \"description\": \"a test 1 permission\",\n" +
                "      \"object\" :\"CaseList\",\n" +
                "      \"action\" : \"View\",\n" +
                "      \"source\": \"4a18bec5-ab1a-410a-9889-885694356402\",\n" +
                "      \"target\": \"faee972d-f9dd-43d3-9f41-8acc3b908d09\",\n" +
                "      \"active\": \"true\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"id\":\"ca0c917d-54c3-4b35-896d-d516a7a6f628\",\n" +
                "      \"object\": \"GrantExtendedHearingAccess\",\n" +
                "      \"action\": \"GrantAccess\",\n" +
                "      \"active\": true,\n" +
                "      \"startDate\": \"2020-08-07\",\n" +
                "      \"endDate\": \"2020-08-07\",\n" +
                "      \"startTime\": \"20:00\",\n" +
                "      \"endTime\": \"22:00\"\n" +
                "    }\n" +
                "  ]\n" +
                "}\n");
    }

}
