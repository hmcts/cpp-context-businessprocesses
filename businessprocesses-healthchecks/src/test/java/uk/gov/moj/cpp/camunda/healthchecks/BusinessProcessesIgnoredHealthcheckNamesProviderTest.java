package uk.gov.moj.cpp.camunda.healthchecks;

import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static uk.gov.justice.services.healthcheck.healthchecks.EventStoreHealthcheck.EVENT_STORE_HEALTHCHECK_NAME;
import static uk.gov.justice.services.healthcheck.healthchecks.FileStoreHealthcheck.FILE_STORE_HEALTHCHECK_NAME;
import static uk.gov.justice.services.healthcheck.healthchecks.JobStoreHealthcheck.JOB_STORE_HEALTHCHECK_NAME;
import static uk.gov.justice.services.healthcheck.healthchecks.SystemDatabaseHealthcheck.SYSTEM_DATABASE_HEALTHCHECK_NAME;
import static uk.gov.justice.services.healthcheck.healthchecks.ViewStoreHealthcheck.VIEW_STORE_HEALTHCHECK_NAME;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class BusinessProcessesIgnoredHealthcheckNamesProviderTest {

    @InjectMocks
    private BusinessProcessesIgnoredHealthcheckNamesProvider ignoredHealthcheckNamesProvider;

    @Test
    public void shouldIgnoreEventStoreFileStoreJobStoreAndSystemHealthcheck() throws Exception {

        final List<String> namesOfIgnoredHealthChecks = ignoredHealthcheckNamesProvider.getNamesOfIgnoredHealthChecks();

        assertThat(namesOfIgnoredHealthChecks.size(), is(5));
        assertThat(namesOfIgnoredHealthChecks, hasItems(
                FILE_STORE_HEALTHCHECK_NAME,
                EVENT_STORE_HEALTHCHECK_NAME,
                JOB_STORE_HEALTHCHECK_NAME,
                SYSTEM_DATABASE_HEALTHCHECK_NAME,
                VIEW_STORE_HEALTHCHECK_NAME));
    }

}