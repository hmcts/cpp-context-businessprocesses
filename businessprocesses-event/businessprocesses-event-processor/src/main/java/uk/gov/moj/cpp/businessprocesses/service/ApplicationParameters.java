package uk.gov.moj.cpp.businessprocesses.service;

import uk.gov.justice.services.common.configuration.Value;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@ApplicationScoped
public class ApplicationParameters {

    @Inject
    @Value(key = "cppAppUrl", defaultValue = "https://dummyUrl/")
    private String cppAppUrl;

    public String getCppAppUrl() {
        return cppAppUrl;
    }

}
