package uk.gov.moj.cpp.businessprocesses.service;

import static uk.gov.justice.services.core.annotation.Component.EVENT_PROCESSOR;

import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.core.audit.AuditService;
import uk.gov.justice.services.core.sender.Sender;
import uk.gov.justice.services.messaging.JsonEnvelope;

import javax.inject.Inject;

public class PublishEventService {

    @Inject
    private AuditService auditService;

    @ServiceComponent(EVENT_PROCESSOR)
    @Inject
    private Sender sender;

    public void sendAndAuditEvent(final JsonEnvelope jsonEnvelope) {
        auditService.audit(jsonEnvelope, EVENT_PROCESSOR);
        sender.send(jsonEnvelope);
    }
}
