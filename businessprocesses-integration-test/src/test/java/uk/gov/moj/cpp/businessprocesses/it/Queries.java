package uk.gov.moj.cpp.businessprocesses.it;

import static java.util.UUID.randomUUID;
import static javax.ws.rs.core.Response.Status.OK;
import static org.hamcrest.CoreMatchers.allOf;
import static uk.gov.justice.services.common.http.HeaderConstants.USER_ID;
import static uk.gov.justice.services.test.utils.core.http.RequestParamsBuilder.requestParams;
import static uk.gov.justice.services.test.utils.core.http.RestPoller.poll;
import static uk.gov.justice.services.test.utils.core.matchers.ResponsePayloadMatcher.payload;
import static uk.gov.justice.services.test.utils.core.matchers.ResponseStatusMatcher.status;
import static uk.gov.moj.cpp.businessprocesses.util.WireMockStubUtils.getReadUrl;

import java.util.concurrent.TimeUnit;

import org.hamcrest.Matcher;

public class Queries {

    private static final String GET_TASK_HISTORY = "application/vnd.businessprocesses.query.task-history+json";

    public static String pollForTaskHistory(final String taskId, final Matcher... matchers) {
        return poll(requestParams(getReadUrl(String.format("/tasks/%s/history", taskId)),
                GET_TASK_HISTORY)
                .withHeader(USER_ID, randomUUID()))
                .timeout(40, TimeUnit.SECONDS)
                .pollDelay(100, TimeUnit.MICROSECONDS)
                .until(status().is(OK), payload().isJson(allOf(matchers)))
                .getPayload();
    }
}
