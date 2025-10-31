package uk.gov.moj.cpp.businessprocesses.shared;

import static java.util.UUID.randomUUID;
import static uk.gov.justice.services.core.annotation.Component.EVENT_PROCESSOR;
import static uk.gov.justice.services.messaging.JsonEnvelope.envelopeFrom;
import static uk.gov.justice.services.messaging.JsonEnvelope.metadataBuilder;

import uk.gov.justice.services.core.audit.AuditService;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.Metadata;

import javax.inject.Inject;

public class TaskAuditService {

    @Inject
    private AuditService auditService;

    public static Metadata getMetadata(final String commandHandlerName, final String userId) {
        return metadataBuilder()
                .withId(randomUUID())
                .withName(commandHandlerName)
                .withUserId(userId).build();
    }

    /**
     * updating auditEnvelope userId with changeAuthId for audit
     * @param auditEnvelope
     * @param userId is changeAuthId from listeners
     */

    public void audit(final JsonEnvelope auditEnvelope, final String userId) {
        final JsonEnvelope jsonEnvelope = envelopeFrom(getMetadata(auditEnvelope.metadata().name(), userId), auditEnvelope.payloadAsJsonObject());
        auditService.audit(jsonEnvelope, EVENT_PROCESSOR);
    }
}
