package uk.gov.moj.cpp.businessprocesses.pojo;

import java.util.Objects;

public class CaseHearingResultedDefendantDetails {
    private final String name;
    private final String id;
    private final String remandStatus;
    private final String prisonName;

    public CaseHearingResultedDefendantDetails(final String name, final String id, final String remandStatus, final String prisonName) {
        this.name = name;
        this.id = id;
        this.remandStatus = remandStatus;
        this.prisonName = prisonName;
    }

    public String getName() {
        return name;
    }

    public String getId() {
        return id;
    }

    public String getRemandStatus() {
        return remandStatus;
    }

    public String getPrisonName() {
        return prisonName;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CaseHearingResultedDefendantDetails)) {
            return false;
        }
        final CaseHearingResultedDefendantDetails that = (CaseHearingResultedDefendantDetails) o;
        return Objects.equals(name, that.name) && Objects.equals(id, that.id) && Objects.equals(remandStatus, that.remandStatus) && Objects.equals(prisonName, that.prisonName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, id, remandStatus, prisonName);
    }
}
