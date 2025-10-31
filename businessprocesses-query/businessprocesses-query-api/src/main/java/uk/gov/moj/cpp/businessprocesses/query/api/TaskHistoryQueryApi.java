package uk.gov.moj.cpp.businessprocesses.query.api;

import uk.gov.justice.services.core.annotation.Component;
import uk.gov.justice.services.core.annotation.Handles;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.moj.cpp.businessprocesses.query.view.TaskHistoryQueryView;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ServiceComponent(Component.QUERY_API)
public class TaskHistoryQueryApi {

    private static final Logger LOGGER = LoggerFactory.getLogger(TaskHistoryQueryApi.class);
    @Inject
    private TaskHistoryQueryView taskHistoryQueryView;

    @Handles("businessprocesses.query.task-history")
    public Envelope getTaskHistory(final JsonEnvelope query) {
        LOGGER.info("API Processing query businessprocesses.query.task-history");
        final Envelope envelope = taskHistoryQueryView.getTaskHistory(query);
        LOGGER.info("API returning response for businessprocesses.query.task-history : {}", envelope.payload());
        return envelope;
    }
}