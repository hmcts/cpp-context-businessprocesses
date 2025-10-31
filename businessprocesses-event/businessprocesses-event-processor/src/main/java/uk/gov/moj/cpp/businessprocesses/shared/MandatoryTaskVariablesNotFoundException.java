package uk.gov.moj.cpp.businessprocesses.shared;

public class MandatoryTaskVariablesNotFoundException extends RuntimeException {

    public MandatoryTaskVariablesNotFoundException(final String error) {
        super(error);
    }

}
