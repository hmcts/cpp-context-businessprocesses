package uk.gov.moj.cpp.businessprocesses.service;

import static java.util.Objects.nonNull;

import javax.inject.Inject;
import javax.json.JsonObject;

public class CourtCentreService {

    @Inject
    private ReferenceDataService referenceDataService;

    public String getCourtCentreRegionByCourtCode(final String courtCode) {
        JsonObject courtCentreDetails = courtCode != null ? referenceDataService.retrieveCourtCentreDetailsByCourtRoomCode(courtCode) : null;
        String courtCentreId = ReferenceDataService.getCourtCentreId(courtCentreDetails);
        if (nonNull(courtCentreId)) {
            JsonObject courtCentre = referenceDataService.retrieveCourtCentreDetailsByCourtId(courtCentreId);
            return ReferenceDataService.getCourtCentreRegion(courtCentre);
        }
        return null;
    }
}
