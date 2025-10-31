package uk.gov.moj.cpp.businessprocesses.contants;

public enum BailAppealEnum {
    RIBA48("RIBA48"),
    EXRIBA48("EXRIBA48"),
    RIBAWL("RIBAWL");

    private String description;

    private BailAppealEnum(final String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public static BailAppealEnum getBailAppealByType(final String value) {
        final BailAppealEnum[] bailAppealArray = BailAppealEnum.values();
        for (final BailAppealEnum bailAppeal : bailAppealArray) {
            if (bailAppeal.getDescription().equals(value)) {
                return bailAppeal;
            }
        }
        return null;
    }
}
