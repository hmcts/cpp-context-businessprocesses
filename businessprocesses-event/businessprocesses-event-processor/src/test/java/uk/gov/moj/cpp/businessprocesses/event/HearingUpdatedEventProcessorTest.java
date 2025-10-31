package uk.gov.moj.cpp.businessprocesses.event;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.json.JsonObject;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import uk.gov.justice.services.core.featurecontrol.FeatureControlGuard;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.moj.cpp.businessprocesses.shared.Constants;
import uk.gov.moj.cpp.businessprocesses.shared.InterpreterForWelshActivityHandler;

@ExtendWith(MockitoExtension.class)
public class HearingUpdatedEventProcessorTest {

    @Mock
    private FeatureControlGuard featureControlGuard;

    @Mock
    private InterpreterForWelshActivityHandler interpreterForWelshActivityHandler;

    @InjectMocks
    private HearingUpdatedEventProcessor hearingUpdatedEventProcessor;

    @Mock
    private JsonEnvelope jsonEnvelope;

    @Mock
    private JsonObject jsonObject;

    private static final Logger LOGGER = LoggerFactory.getLogger(HearingUpdatedEventProcessor.class);

    @Test
    public void testHandleHearingDetailsChangedFeatureEnabled() {
        when(jsonEnvelope.payloadAsJsonObject()).thenReturn(jsonObject);
        when(jsonObject.getString(Constants.ID)).thenReturn("hearingId");
        when(featureControlGuard.isFeatureEnabled("camunda-hearing-initiated")).thenReturn(true);

        hearingUpdatedEventProcessor.handleHearingDetailsChanged(jsonEnvelope);

        verify(featureControlGuard).isFeatureEnabled("camunda-hearing-initiated");
        verify(interpreterForWelshActivityHandler).handleWelshInterpreterForHearingUpdated("hearingId");
    }

    @Test
    public void testHandleHearingDetailsChangedFeatureDisabled() {
        when(featureControlGuard.isFeatureEnabled("camunda-hearing-initiated")).thenReturn(false);

        hearingUpdatedEventProcessor.handleHearingDetailsChanged(jsonEnvelope);

        verify(featureControlGuard).isFeatureEnabled("camunda-hearing-initiated");
        verify(interpreterForWelshActivityHandler, never()).handleWelshInterpreterForHearingUpdated(anyString());
    }

    @Test
    public void testHandleHearingDefendantDetailsChangedEventFeatureEnabled() {
        when(jsonEnvelope.payloadAsJsonObject()).thenReturn(jsonObject);
        when(jsonObject.getString(Constants.HEARING_ID)).thenReturn("hearingId");
        when(featureControlGuard.isFeatureEnabled("camunda-hearing-initiated")).thenReturn(true);

        hearingUpdatedEventProcessor.handleHearingDefendantDetailsChangedEvent(jsonEnvelope);

        verify(featureControlGuard).isFeatureEnabled("camunda-hearing-initiated");
        verify(interpreterForWelshActivityHandler).handleWelshInterpreterForHearingUpdated("hearingId");
    }

    @Test
    public void testHandleHearingDefendantDetailsChangedEventFeatureDisabled() {
        when(featureControlGuard.isFeatureEnabled("camunda-hearing-initiated")).thenReturn(false);

        hearingUpdatedEventProcessor.handleHearingDefendantDetailsChangedEvent(jsonEnvelope);

        verify(featureControlGuard).isFeatureEnabled("camunda-hearing-initiated");
        verify(interpreterForWelshActivityHandler, never()).handleWelshInterpreterForHearingUpdated(anyString());
    }
}
