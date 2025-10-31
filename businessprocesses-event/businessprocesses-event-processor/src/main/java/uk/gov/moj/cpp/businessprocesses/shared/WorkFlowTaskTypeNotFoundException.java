package uk.gov.moj.cpp.businessprocesses.shared;

public class WorkFlowTaskTypeNotFoundException extends RuntimeException {

    public WorkFlowTaskTypeNotFoundException() {
        super();
    }

    public WorkFlowTaskTypeNotFoundException(final String error) {
        super(error);
    }
}
