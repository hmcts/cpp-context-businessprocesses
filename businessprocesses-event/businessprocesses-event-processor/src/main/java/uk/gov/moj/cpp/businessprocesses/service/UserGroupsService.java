package uk.gov.moj.cpp.businessprocesses.service;

import static java.util.UUID.randomUUID;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;
import static org.slf4j.LoggerFactory.getLogger;
import static uk.gov.justice.services.core.annotation.Component.EVENT_PROCESSOR;
import static uk.gov.justice.services.messaging.Envelope.metadataBuilder;
import static uk.gov.justice.services.messaging.JsonEnvelope.envelopeFrom;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.TARGET_ID;

import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.MetadataBuilder;

import javax.inject.Inject;
import javax.json.JsonObject;

import org.slf4j.Logger;

@SuppressWarnings({"squid:S3655", "squid:CallToDeprecatedMethod","squid:S2221"})
public class UserGroupsService {

    private static final Logger LOGGER = getLogger(UserGroupsService.class);
    private static final String USER_ID = "userId";

    private static final String RESTRICTED_CASE_PERMISSIONS_QUERY = "usersgroups.restricted-case-permissions";
    @Inject
    @ServiceComponent(EVENT_PROCESSOR)
    private Requester requester;

    public Boolean getCaseSensitive(final String caseId) {

        LOGGER.info("calling case sensitive information for case id : {}", caseId);
        final JsonEnvelope queryEnvelope = envelopeFrom(metadataBuilder()
                        .withId(randomUUID())
                        .withName(RESTRICTED_CASE_PERMISSIONS_QUERY),
                createObjectBuilder().
                        add(TARGET_ID, caseId));

        final JsonObject responsePayload = requester.requestAsAdmin(queryEnvelope).payloadAsJsonObject();
        if (null != responsePayload) {
            return responsePayload.getBoolean("isCaseSensitive");
        }
        return false;
    }

    public String getUserDetails(final String userId) {
        try {
            final Envelope<JsonObject> response = getUserDetailsAsAdmin(userId);
            final JsonObject jsonObject = response.payload();
            final String userDetails = jsonObject != null ? jsonObject.getString("firstName") + " " + jsonObject.getString("lastName") : userId;
            LOGGER.info("user details {} ", userDetails);
            return userDetails;
        } catch (Exception ex) {
            LOGGER.error("User details could not find ", ex);
            return userId;
        }
    }

    private Envelope<JsonObject> getUserDetailsAsAdmin(final String userId) {
        final MetadataBuilder metadataBuilder = JsonEnvelope.metadataBuilder()
                .withId(randomUUID())
                .withUserId(userId)
                .withName("usersgroups.get-user-details");

        return requester.requestAsAdmin(envelopeFrom(metadataBuilder, createObjectBuilder().add(USER_ID, userId).build()), JsonObject.class);
    }
}
