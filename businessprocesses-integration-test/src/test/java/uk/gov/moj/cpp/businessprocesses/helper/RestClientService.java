package uk.gov.moj.cpp.businessprocesses.helper;

import static jakarta.ws.rs.client.Entity.entity;
import static uk.gov.justice.services.test.utils.core.rest.ResteasyClientBuilderFactory.clientBuilder;

import uk.gov.justice.services.test.utils.core.rest.RestClient;

import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.StatusType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RestClientService extends RestClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(RestClientService.class);

    public RestClientService() {
        super();
    }


    public Response putCommand(final String url, final String contentType, final String requestPayload) {
        final Entity<String> entity = entity(requestPayload, MediaType.valueOf(contentType));
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Making PUT request to '{}' with Content Type '{}'", url, contentType);
            LOGGER.info("Request payload: '{}'", requestPayload);
        }

        final Response response = clientBuilder().build().target(url).request().put(entity);
        if (LOGGER.isInfoEnabled()) {
            StatusType statusType = response.getStatusInfo();
            LOGGER.info("Received response status '{}' '{}'", statusType.getStatusCode(), statusType.getReasonPhrase());
        }

        return response;
    }
}
