package uk.gov.moj.cpp.businessprocesses.pojo;

import java.util.Objects;

public record CaseHearingResultedDefendantDetails(String name, String id, String remandStatus, String prisonName,
                                                  String courtCode) {

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
