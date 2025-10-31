package uk.gov.moj.cpp.businessprocesses.service;

import static org.mockito.Mockito.verify;

import uk.gov.justice.services.core.annotation.Component;
import uk.gov.justice.services.core.audit.AuditService;
import uk.gov.justice.services.core.sender.Sender;
import uk.gov.justice.services.messaging.JsonEnvelope;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class PublishEventServiceTest {

    @Mock
    private AuditService auditService;

    @Mock
    private Sender sender;

    @Mock
    private JsonEnvelope jsonEnvelope;

    @InjectMocks
    private PublishEventService publishEventService;

    @Test
    public void testSendAndAuditEvent() {
        publishEventService.sendAndAuditEvent(jsonEnvelope);

        verify(auditService).audit(jsonEnvelope, Component.EVENT_PROCESSOR);
        verify(sender).send(jsonEnvelope);

    }

}