package uk.gov.moj.cpp.businessprocesses.shared;

import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.SPACE;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.APPLICATION_REFERENCE;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.CASEURN;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.DEFENDANT_DETAILS;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.DEFENDANT_FIRST_NAME;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.DEFENDANT_LAST_NAME;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.INTERPRETER_LANGUAGE_NEEDS;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.SUBJECT;

import uk.gov.moj.cpp.businessprocesses.pojo.DefendantDetails;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.json.JsonArray;
import javax.json.JsonObject;

public class NotesGenerator {

    @SuppressWarnings({"squid:S134"})
    public String getNotesFromCaseDefendantsArray(final JsonArray caseDetailsArray) {

        final Map<String, List<DefendantDetails>> defendantDetailsMap = new HashMap<>();
        for (int caseCount = 0; caseCount < caseDetailsArray.size(); caseCount++) {
            final JsonObject caseDetails = caseDetailsArray.getJsonObject(caseCount);
            final String urn = caseDetails.containsKey(CASEURN) ? caseDetails.getString(CASEURN) : EMPTY;
            final JsonArray defendantsArray = caseDetails.getJsonArray(DEFENDANT_DETAILS);
            if (!defendantsArray.isEmpty()) {
                for (int defendantCount = 0; defendantCount < defendantsArray.size(); defendantCount++) {
                    final JsonObject details = defendantsArray.getJsonObject(defendantCount);
                    final DefendantDetails defendantDetails = getDefendantDetails(details, urn);
                    addDefendantDetailsToMap(defendantDetailsMap, defendantDetails);
                }
            }
        }
        return getNotesFromMap(defendantDetailsMap);
    }

    @SuppressWarnings({"squid:S134"})
    public String getNotesFromApplicationDefendantsArray(final JsonArray applicationArray) {
        final Map<String, List<DefendantDetails>> defendantDetailsMap = new HashMap<>();
        if (!applicationArray.isEmpty()) {
            for (int applicationCount = 0; applicationCount < applicationArray.size(); applicationCount++) {
                final JsonObject applicationDetails = applicationArray.getJsonObject(applicationCount);
                final String applicationReference = applicationDetails.containsKey(APPLICATION_REFERENCE) ? applicationDetails.getString(APPLICATION_REFERENCE) : EMPTY;
                final JsonObject details = applicationDetails.getJsonObject(SUBJECT);
                if (!applicationDetails.isEmpty()) {
                    final DefendantDetails defendantDetails = getDefendantDetails(details, applicationReference);
                    addDefendantDetailsToMap(defendantDetailsMap, defendantDetails);
                }
            }
        }
        return getNotesFromMap(defendantDetailsMap);
    }

    public String buildDefendantName(final JsonObject defendantDetails) {

        String defendantName = EMPTY;

        if (defendantDetails.containsKey(DEFENDANT_FIRST_NAME)) {
            defendantName = defendantDetails.getString(DEFENDANT_FIRST_NAME);
        }

        if (defendantDetails.containsKey(DEFENDANT_LAST_NAME)) {
            defendantName = EMPTY.equals(defendantName)
                    ? defendantDetails.getString(DEFENDANT_LAST_NAME)
                    : defendantName.concat(SPACE).concat(defendantDetails.getString(DEFENDANT_LAST_NAME));
        }
        return defendantName;
    }

    private void addDefendantDetailsToMap(final Map<String, List<DefendantDetails>> defendantDetailsMap, DefendantDetails defendantDetails) {
        if (!EMPTY.equals(defendantDetails.getLanguage())) {
            if (defendantDetailsMap.get(defendantDetails.getUrn()) == null) {
                final List<DefendantDetails> defendantDetailsList = new ArrayList<>();
                defendantDetailsMap.put(defendantDetails.getUrn(), defendantDetailsList);
            }
            defendantDetailsMap.get(defendantDetails.getUrn()).add(defendantDetails);
        }
    }

    private String getNotesFromMap(final Map<String, List<DefendantDetails>> defendantDetailsMap) {
        return defendantDetailsMap.keySet().stream()
                .map(key -> getNotesFromList(defendantDetailsMap.get(key), key))
                .collect(Collectors.joining(EMPTY, EMPTY, EMPTY));
    }

    private String getNotesFromList(final List<DefendantDetails> defendantDetailsList, final String urn) {
        final String delimiter = ",";
        final String suffix = " ]";
        String prefix = "[ ";
        if (!EMPTY.equals(urn)) {
            prefix = prefix + urn + " = ";
        }
        return defendantDetailsList.stream()
                .map(n -> n.getName() + " : " + n.getLanguage())
                .collect(Collectors.joining(delimiter, prefix, suffix));
    }

    private DefendantDetails getDefendantDetails(final JsonObject defendantDetails, final String urn) {
        final String defendantName = buildDefendantName(defendantDetails);
        final String language = defendantDetails.containsKey(INTERPRETER_LANGUAGE_NEEDS) ? defendantDetails.getString(INTERPRETER_LANGUAGE_NEEDS) : EMPTY;

        return new DefendantDetails(defendantName, language, urn);
    }

}
