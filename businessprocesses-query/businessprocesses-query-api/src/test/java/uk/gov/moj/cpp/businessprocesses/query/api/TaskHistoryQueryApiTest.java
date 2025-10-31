package uk.gov.moj.cpp.businessprocesses.query.api;

import static java.util.Collections.singletonMap;
import static javax.json.Json.createObjectBuilder;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.justice.services.core.annotation.Component.QUERY_API;
import static uk.gov.justice.services.test.utils.core.matchers.HandlerMatcher.isHandler;
import static uk.gov.justice.services.test.utils.core.matchers.HandlerMethodMatcher.method;
import static uk.gov.justice.services.test.utils.core.messaging.MetadataBuilderFactory.metadataWithDefaults;

import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.moj.cpp.businessprocesses.query.view.TaskHistoryQueryView;

import java.util.Map;

import javax.json.JsonObjectBuilder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class TaskHistoryQueryApiTest {

    @Mock
    private TaskHistoryQueryView taskHistoryQueryView;

    @InjectMocks
    private TaskHistoryQueryApi taskHistoryQueryApi;


    @Test
    public void shouldHaveCorrectHandlerAnnotations() throws Exception {
        assertThat(taskHistoryQueryApi, isHandler(QUERY_API)
                .with(method("getTaskHistory")
                        .thatHandles("businessprocesses.query.task-history"))
        );
    }


    @Test
    public void shouldHandleValidateSuppliedTaskId() {
        // Given
        final JsonEnvelope queryEnvelop = buildRequestEnvelope(singletonMap("taskId", "77WR5722420"));
        final JsonEnvelope response = mock(JsonEnvelope.class);
        when(taskHistoryQueryView.getTaskHistory(queryEnvelop)).thenReturn(response);

        // When
        taskHistoryQueryApi.getTaskHistory(queryEnvelop);

        // Then
        verify(taskHistoryQueryView).getTaskHistory(queryEnvelop);
        assertThat(taskHistoryQueryApi.getTaskHistory(queryEnvelop), is(response));
    }

    private JsonEnvelope buildRequestEnvelope(final Map<String, String> props) {
        final JsonObjectBuilder builder = createObjectBuilder();
        props.entrySet().forEach(entry -> builder.add(entry.getKey(), entry.getValue()));

        return JsonEnvelope.envelopeFrom(
                metadataWithDefaults().withName("businessprocesses.query.task-history"),
                builder.build());
    }

}