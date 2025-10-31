package uk.gov.moj.cpp.businessprocesses.delegate;

import static java.util.UUID.randomUUID;
import static javax.json.Json.createObjectBuilder;
import static uk.gov.justice.services.messaging.JsonEnvelope.envelopeFrom;
import static uk.gov.justice.services.messaging.JsonEnvelope.metadataBuilder;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.CASE_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.DEFENDANT_ID;

import uk.gov.justice.services.core.annotation.Component;
import uk.gov.justice.services.core.annotation.FrameworkComponent;
import uk.gov.justice.services.core.dispatcher.SystemUserProvider;
import uk.gov.justice.services.core.sender.Sender;
import uk.gov.justice.services.messaging.JsonEnvelope;

import java.util.Optional;
import java.util.UUID;

import javax.inject.Inject;
import javax.inject.Named;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Named("sendMeansInformationDeletedEvent")
public class SendMeansInformationDeletedEvent implements JavaDelegate {

    private static final Logger LOGGER = LoggerFactory.getLogger(SendMeansInformationDeletedEvent.class);

    @Inject
    private SystemUserProvider systemUserProvider;

    @FrameworkComponent(Component.EVENT_PROCESSOR)
    @Inject
    private Sender sender;

    @Override
    public void execute(DelegateExecution execution) {

        final String caseId = (String) execution.getVariable(CASE_ID);
        final String defendantId = (String) execution.getVariable(DEFENDANT_ID);

        LOGGER.info("Delegate will attempt to publish the Means Information Deleted Event");

        final JsonEnvelope request = envelopeFrom(
                metadataBuilder()
                        .withId(randomUUID())
                        .withName("public.bpm.financial-means-information-deleted")
                        .withUserId(getSystemUserId()),
                createObjectBuilder()
                        .add(CASE_ID, caseId)
                        .add(DEFENDANT_ID, defendantId)
                        .build());
        sender.send(request);
        LOGGER.info("Financial means information deleted event published");
    }


    private String getSystemUserId() {
        final Optional<UUID> contextSystemUserId = systemUserProvider
                .getContextSystemUserId();
        if (!contextSystemUserId.isPresent()) {
            LOGGER.error("System user for business processes is not present!");
        }
        return contextSystemUserId
                .orElseThrow(IllegalArgumentException::new)
                .toString();
    }
}
