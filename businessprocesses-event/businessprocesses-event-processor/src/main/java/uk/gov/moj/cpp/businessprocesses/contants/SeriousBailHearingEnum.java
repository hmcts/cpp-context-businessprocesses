package uk.gov.moj.cpp.businessprocesses.contants;

public enum SeriousBailHearingEnum {

    CCQB("CCQB");

    private String description;

    private SeriousBailHearingEnum(final String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public static SeriousBailHearingEnum getSeriousBailHearingByType(final String value) {
        final SeriousBailHearingEnum[] seriousBailAppealArray = SeriousBailHearingEnum.values();
        for (final SeriousBailHearingEnum seriousBailAppeal : seriousBailAppealArray) {
            if (seriousBailAppeal.getDescription().equals(value)) {
                return seriousBailAppeal;
            }
        }
        return null;
    }
}
