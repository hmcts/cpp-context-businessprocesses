package uk.gov.moj.cpp.businessprocesses.shared;

public class InvalidDueDateCalculationRuleException extends RuntimeException {

    public InvalidDueDateCalculationRuleException() {
        super();
    }

    public InvalidDueDateCalculationRuleException(final String error) {
        super(error);
    }
}
