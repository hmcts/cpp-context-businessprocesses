package uk.gov.moj.cpp.businessprocesses.event;


import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import uk.gov.justice.core.courts.Hearing;
import uk.gov.justice.services.common.converter.JsonObjectToObjectConverter;
import uk.gov.justice.services.core.featurecontrol.FeatureControlGuard;
import uk.gov.justice.services.messaging.JsonEnvelope;

import java.util.Collections;

import javax.json.JsonObject;

import org.camunda.bpm.extension.junit5.test.ProcessEngineExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith({MockitoExtension.class, ProcessEngineExtension.class})
@MockitoSettings(strictness = Strictness.LENIENT)
@SuppressWarnings({"squid:S2187"})
public class ListingHearingListedEventProcessorFeatureTest {
    @Mock
    private FeatureControlGuard featureControlGuard;

    @Mock
    private JsonObjectToObjectConverter jsonObjectToObjectConverter;

    @Mock
    private JsonEnvelope jsonEnvelope;

    @Mock
    private JsonObject jsonObject;

    @InjectMocks
    private ListingHearingListedEventProcessor listingHearingListedEventProcessor;

    @BeforeEach
    void setUp() {
        when(jsonEnvelope.payloadAsJsonObject()).thenReturn(jsonObject);
    }

    @Test
    void shouldCallHandleHearingListedWhenFeatureIsEnabled() {
        when(featureControlGuard.isFeatureEnabled("camunda-listing-hearing-listed")).thenReturn(true);
        Hearing hearing = mock(Hearing.class);
        when(jsonObjectToObjectConverter.convert(any(), any())).thenReturn(hearing);
        when(hearing.getProsecutionCases()).thenReturn(Collections.emptyList()); // Adjust as needed based on your scenario

        listingHearingListedEventProcessor.handleHearingListedProcessor(jsonEnvelope);

        verify(featureControlGuard, times(1)).isFeatureEnabled("camunda-listing-hearing-listed");
        verify(jsonObjectToObjectConverter, times(1)).convert(any(), any());
    }


    @Test
    void shouldNotCallHandleHearingListedWhenFeatureIsDisabled() {
        when(featureControlGuard.isFeatureEnabled("camunda-listing-hearing-listed")).thenReturn(false);

        listingHearingListedEventProcessor.handleHearingListedProcessor(jsonEnvelope);

        verify(featureControlGuard, times(1)).isFeatureEnabled("camunda-listing-hearing-listed");
        verifyNoInteractions(jsonObjectToObjectConverter);
    }
}