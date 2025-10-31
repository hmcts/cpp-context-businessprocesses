package uk.gov.moj.cpp.businessprocesses.pojo;

import java.util.Objects;

public class DefendantDetails {
    private final String name;
    private final String language;
    private final String urn;

    public DefendantDetails(final String name, final String language, final String urn) {
        this.name = name;
        this.language = language;
        this.urn = urn;
    }

    public String getName() {
        return name;
    }

    public String getLanguage() {
        return language;
    }

    public String getUrn() {
        return urn;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DefendantDetails)) {
            return false;
        }
        final DefendantDetails that = (DefendantDetails) o;
        return Objects.equals(name, that.name) && Objects.equals(language, that.language) && Objects.equals(urn, that.urn);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, language, urn);
    }
}
