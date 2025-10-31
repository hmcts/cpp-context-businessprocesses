package uk.gov.moj.cpp.businessprocesses.delegate;

import static java.lang.String.format;
import static java.util.UUID.randomUUID;
import static javax.json.Json.createObjectBuilder;
import static uk.gov.justice.services.messaging.JsonEnvelope.envelopeFrom;
import static uk.gov.justice.services.messaging.JsonEnvelope.metadataBuilder;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.CASE_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.DEFENDANT_ID;

import uk.gov.justice.services.core.annotation.Component;
import uk.gov.justice.services.core.annotation.FrameworkComponent;
import uk.gov.justice.services.core.annotation.Handles;
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

@Named("issueDeleteMeansInformationCommands")
public class IssueDeleteMeansInformationCommands implements JavaDelegate {

    private static final Logger LOGGER = LoggerFactory.getLogger(IssueDeleteMeansInformationCommands.class);

    @Inject
    private SystemUserProvider systemUserProvider;

    @FrameworkComponent(Component.EVENT_PROCESSOR)
    @Inject
    private Sender sender;

    private static final String COMMAND_DELETE_FINANCIAL_MEANS = "%s.delete-financial-means";

    @Handles("dummy.event")
    public void dummy(JsonEnvelope jsonEnvelope) {
        //dummy event which never gets called
    }

    @Override
    public void execute(DelegateExecution execution) {

        final String defendantId = (String) execution.getVariable(DEFENDANT_ID);
        final String caseId = (String) execution.getVariable(CASE_ID);

        sendCommandForContext(defendantId, caseId, "sjp");
        sendCommandForContext(defendantId, caseId, "progression");

        LOGGER.info("Commands now issued on all relevant contexts to delete defendant's financial means information");

    }

    protected void sendCommandForContext(final String defendantId, final String caseId, final String context) {
        final String command = format(COMMAND_DELETE_FINANCIAL_MEANS, context);

        LOGGER.info("Issue {}", command);

        final JsonEnvelope request = envelopeFrom(
                metadataBuilder()
                        .withId(randomUUID())
                        .withName(command)
                        .withUserId(getSystemUserId()),
                createObjectBuilder()
                        .add(DEFENDANT_ID, defendantId)
                        .add(CASE_ID, caseId)
                        .build());
        sender.send(request);

        LOGGER.info("Issue {}", command);
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
