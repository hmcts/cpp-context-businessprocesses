package uk.gov.moj.cpp.businessprocesses.delegate;

import static java.lang.String.format;
import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import uk.gov.justice.services.core.dispatcher.SystemUserProvider;
import uk.gov.justice.services.core.sender.Sender;
import uk.gov.justice.services.messaging.JsonEnvelope;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;


@ExtendWith(MockitoExtension.class)
public class IssueDeleteMeansInformationCommandsTest {

    private static final String CASE_ID_FIELD = "caseId";
    private static final String DEFENDANT_ID_FIELD = "defendantId";
    private static final UUID SYSTEM_USER = randomUUID();

    private final String caseId = randomUUID().toString();
    private final String defendantId = randomUUID().toString();

    @Captor
    private ArgumentCaptor<JsonEnvelope> envelopeCaptor;

    @Mock
    private Sender sender;

    @Mock
    private SystemUserProvider systemUserProvider;

    @Mock
    private DelegateExecution execution;

    @InjectMocks
    private IssueDeleteMeansInformationCommands issueDeleteMeansInformationCommands;

    @BeforeEach
    public void setup() {
        when(execution.getVariable(CASE_ID_FIELD)).thenReturn(caseId);
        when(execution.getVariable(DEFENDANT_ID_FIELD)).thenReturn(defendantId);
        when(systemUserProvider.getContextSystemUserId()).thenReturn(Optional.of(SYSTEM_USER));
    }

    @Test
    public void execute() {
        final String[] contexts = {"sjp", "progression"};//, "results", "hearing"};

        issueDeleteMeansInformationCommands.execute(execution);

        verify(sender, times(2)).send(envelopeCaptor.capture()); //fix

        final List<JsonEnvelope> actualRequestEnvelopes = envelopeCaptor.getAllValues();

        for (int i = 0; i < contexts.length; i++) {
            final String command = format("%s.delete-financial-means", contexts[i]);
            final JsonEnvelope actualRequestEnvelope = actualRequestEnvelopes.get(i);
            assertThat(actualRequestEnvelope.metadata().name(), is(command));
            assertThat(actualRequestEnvelope.payloadAsJsonObject().getString(CASE_ID_FIELD), is(caseId));
            assertThat(actualRequestEnvelope.payloadAsJsonObject().getString(DEFENDANT_ID_FIELD), is(defendantId));
        }
    }

}