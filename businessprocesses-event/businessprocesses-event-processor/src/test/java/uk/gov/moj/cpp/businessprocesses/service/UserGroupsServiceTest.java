package uk.gov.moj.cpp.businessprocesses.service;

import static javax.json.Json.createArrayBuilder;
import static javax.json.Json.createObjectBuilder;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static uk.gov.justice.services.test.utils.core.enveloper.EnvelopeFactory.createEnvelope;

import uk.gov.justice.services.core.requester.Requester;
import uk.gov.justice.services.messaging.JsonEnvelope;

import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class UserGroupsServiceTest{
    private static final String RESTRICTED_CASE_PERMISSIONS_QUERY = "usersgroups.restricted-case-permissions";

    @InjectMocks
    private UserGroupsService target;

    @Mock
    private Requester requester;

    @BeforeEach
    public void setUp() {
        when(requester.requestAsAdmin(any(JsonEnvelope.class))).thenReturn(getCaseSensitiveResponse());
    }

    private JsonEnvelope getCaseSensitiveResponse() {
        final JsonArrayBuilder permissions = createArrayBuilder();
        final JsonObject permission1 = getPermission("5b6f843e-d639-40b3-8611-8015f3a34758", "a test 1 permission", "RestrictedCase", "View" ,"4a18bec5-ab1a-410a-9889-885694356492", "faee972d-f9dd-43d3-9f41-8acc3b908d08",true);

        final JsonObject permission2 = getPermission("5b6f843e-d639-40b3-8611-8015f3a34798", "a test 2 permission", "RestrictedCase", "View", "4a18bec5-ab1a-410a-9889-885694356402", "faee972d-f9dd-43d3-9f41-8acc3b908d09", true);
        permissions.add(permission1).add(permission2);
        final JsonObject responseOBject = createObjectBuilder().add("isCaseSensitive", true).add("permissions", permissions).build();
        return createEnvelope(RESTRICTED_CASE_PERMISSIONS_QUERY, responseOBject);
    }

    private JsonObject getPermission(String permissionId, String description, String object, String action, String source, String target, Boolean active) {
        return createObjectBuilder()
                .add("permissionId", permissionId)
                .add("description", description)
                .add("object", object)
                .add("action", action)
                .add("source", source)
                .add("target", target)
                .add("active", active).build();
    }

    @Test
    public void shouldGetIsCaseSensitiveFlagTrue() {
        assertThat(target.getCaseSensitive("test"), is(true));
    }

}