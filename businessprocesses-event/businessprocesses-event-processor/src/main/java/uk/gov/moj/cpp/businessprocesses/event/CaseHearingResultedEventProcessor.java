package uk.gov.moj.cpp.businessprocesses.event;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static uk.gov.justice.services.core.annotation.Component.EVENT_PROCESSOR;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.BPMN_PROCESS_CASE_RESULTS;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.CASE_STATUS;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.CROWN_COURT_ADMIN_WORK_QUEUE_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.CROWN_JURISDICTION_TYPE;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.JURISDICTION_TYPE;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.PRISON_ORGANISATION_NAME;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.PROMPT_REFERENCE;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.SYSTEM_USER_NAME;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.TASK_NAME_SEND_DOCUMENTS_TO_PRISON;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.VALUE;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.CASE_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.CASE_URN;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.COURT_CODES;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.CUSTODY_TIME_LIMIT;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.DEFENDANT_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.DEFENDANT_NAME;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.DEFENDANT_REMAND_STATUS;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.EXECUTE_INSTANTLY;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.HAS_CUSTODIAL_RESULTS;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.HEARING_DATE;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.HEARING_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.JURISDICTION;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.LAST_UPDATED_BY_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.LAST_UPDATED_BY_NAME;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.NOTE;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.PRISON_NAME;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.WORK_QUEUE;

import uk.gov.justice.core.courts.Hearing;
import uk.gov.justice.services.common.converter.JsonObjectToObjectConverter;
import uk.gov.justice.services.core.annotation.Handles;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.core.dispatcher.SystemUserProvider;
import uk.gov.justice.services.core.featurecontrol.FeatureControlGuard;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.moj.cpp.businessprocesses.pojo.CaseHearingResultedDefendantDetails;
import uk.gov.moj.cpp.businessprocesses.service.TaskTypeService;
import uk.gov.moj.cpp.businessprocesses.shared.HearingHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.json.JsonArray;
import javax.json.JsonObject;

import org.camunda.bpm.engine.RuntimeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ServiceComponent(EVENT_PROCESSOR)
public class CaseHearingResultedEventProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(CaseHearingResultedEventProcessor.class);

    private static final Set<String> CJS_RESULT_CODE_FOR_SENDING_DOCUMENTS_TO_PRISON = Stream.of("1002", "1007", "1024", "1081", "1088", "1507", "3132", "4046", "4049", "4051")
            .collect(Collectors.toSet());

    @Inject
    private RuntimeService runtimeService;

    @Inject
    private TaskTypeService taskTypeService;

    @Inject
    private SystemUserProvider systemUserProvider;

    @Inject
    private FeatureControlGuard featureControlGuard;

    @Inject
    private JsonObjectToObjectConverter jsonObjectToObjectConverter;

    @Handles("public.progression.hearing-resulted")
    public void handleHearingResultedProcessor(final JsonEnvelope jsonEnvelope) {
        if (featureControlGuard.isFeatureEnabled("camunda-progression-hearing-resulted")) {
            handleHearingResulted(jsonEnvelope);
        }
    }

    public void handleHearingResulted(final JsonEnvelope jsonEnvelope) {

        final JsonObject eventPayload = jsonEnvelope.payloadAsJsonObject();
        final Hearing hearing = jsonObjectToObjectConverter.convert(eventPayload.getJsonObject("hearing"), Hearing.class);
        final JsonObject courtHearing = eventPayload.getJsonObject("hearing");
        final String hearingId = courtHearing.getString("id");
        final String jurisdiction = courtHearing.getString(JURISDICTION_TYPE);
        final String courtCodes = getCourtCodes(courtHearing);
        final String custodyTimeLimit = HearingHelper.getCustodyTimeLimit(hearing);

        LOGGER.info("Received event 'public.progression.hearing-resulted' for hearingId: {}", hearingId);

        final String hearingDate = ((JsonObject) courtHearing.getJsonArray("hearingDays").get(0)).getString("sittingDay");
        final JsonArray prosecutionCases = courtHearing.containsKey("prosecutionCases") ? courtHearing.getJsonArray("prosecutionCases") : null;

        if (prosecutionCases != null && !prosecutionCases.isEmpty()) {
            extractCaseDetailsAndStartProcessForCaseHearingResulted(hearingId, hearingDate, jurisdiction, prosecutionCases, courtCodes, custodyTimeLimit);
        }

    }

    private void extractCaseDetailsAndStartProcessForCaseHearingResulted(final String hearingId, final String hearingDate, final String jurisdiction, final JsonArray prosecutionCases, final String courtCodes, final String custodyTimeLimit) {
        prosecutionCases.stream()
                .filter(Objects::nonNull)
                .filter(jsonValue -> ((JsonObject) jsonValue).containsKey(CASE_STATUS))
                .forEach(prosecutionCase -> startProcessForCaseHearingResulted(hearingId, hearingDate, jurisdiction, (JsonObject) prosecutionCase, courtCodes, custodyTimeLimit));
    }

    private void startProcessForCaseHearingResulted(final String hearingId, final String hearingDate, final String jurisdiction, final JsonObject prosecutionCase, final String courtCodes, final String custodyTimeLimit) {

        final String caseId = prosecutionCase.getString("id");
        final boolean executeInstantly = false;
        final JsonArray defendants = prosecutionCase.getJsonArray("defendants");

        final List<CaseHearingResultedDefendantDetails> caseHearingResultedDefendantDetailsList = getDefendantDetailsWhichHasCjsResultCodeForSendingDocumentsToPrison(defendants);
        final boolean hasCustodialResults = !caseHearingResultedDefendantDetailsList.isEmpty();

        final Map<String, Object> processVariables = taskTypeService.getTaskVariablesFromRefData(TASK_NAME_SEND_DOCUMENTS_TO_PRISON, caseId, hearingDate, null);

        final String userId = systemUserProvider.getContextSystemUserId().map(UUID::toString).orElse(null);

        if (hasCustodialResults) {
            final JsonObject prosecutionCaseIdentifier = prosecutionCase.getJsonObject("prosecutionCaseIdentifier");
            final String urn = prosecutionCaseIdentifier.containsKey("caseURN") ? prosecutionCaseIdentifier.getString("caseURN") : prosecutionCaseIdentifier.getString("prosecutionAuthorityReference");
            final Map<String, Object> sendDocumentsToPrisonProcessVariables = getDocumentsToPrisonProcessVariables(jurisdiction, urn, caseHearingResultedDefendantDetailsList, processVariables);
            processVariables.putAll(sendDocumentsToPrisonProcessVariables);
        }

        processVariables.put(HEARING_ID, hearingId);
        processVariables.put(CASE_ID, caseId);
        processVariables.put(HEARING_DATE, hearingDate);
        processVariables.put(JURISDICTION, jurisdiction);
        processVariables.put(EXECUTE_INSTANTLY, executeInstantly);
        processVariables.put(HAS_CUSTODIAL_RESULTS, hasCustodialResults);
        processVariables.put(LAST_UPDATED_BY_ID, userId);
        processVariables.put(LAST_UPDATED_BY_NAME, SYSTEM_USER_NAME);

        if(courtCodes != null) {
            processVariables.put(COURT_CODES, courtCodes);
        }

        processVariables.put(CUSTODY_TIME_LIMIT, custodyTimeLimit);

        final String caseIdHearingIdUUID = buildCaseHearingId(hearingId, caseId);
        runtimeService.startProcessInstanceByKey(BPMN_PROCESS_CASE_RESULTS, caseIdHearingIdUUID, processVariables);
        LOGGER.info("New Case Resulted Process Started for caseId {},  hearingId {}, businessKey {} ", caseId, hearingId, caseIdHearingIdUUID);
    }

    /**
     * @param hearingId
     * @param caseId    Hearing may contain multiple cases, To make Each Case Unique We are
     *                  combining hearingId and CaseId, Returned Value we are using as an
     *                  businessKey inside camunda while creating the task
     * @return
     */
    private String buildCaseHearingId(final String hearingId, final String caseId) {
        return UUID.nameUUIDFromBytes((hearingId + caseId).getBytes()).toString();
    }

    private Map<String, Object> getDocumentsToPrisonProcessVariables(final String jurisdiction, final String urn, final List<CaseHearingResultedDefendantDetails> caseHearingResultedDefendantDetailsList, final Map<String, Object> processVariables) {
        processVariables.put(CASE_URN, urn);
        final CaseHearingResultedDefendantDetails caseHearingResultedDefendantDetails = caseHearingResultedDefendantDetailsList.get(0);
        processVariables.put(DEFENDANT_ID, caseHearingResultedDefendantDetails.getId());
        processVariables.put(DEFENDANT_NAME, caseHearingResultedDefendantDetails.getName());
        processVariables.put(DEFENDANT_REMAND_STATUS, caseHearingResultedDefendantDetails.getRemandStatus());
        processVariables.put(NOTE, getNotesFromCaseHearingResultedDefendantDetails(caseHearingResultedDefendantDetailsList));
        processVariables.put(PRISON_NAME, caseHearingResultedDefendantDetails.getPrisonName());

        if (CROWN_JURISDICTION_TYPE.equals(jurisdiction)) {
            processVariables.put(WORK_QUEUE, CROWN_COURT_ADMIN_WORK_QUEUE_ID);
        }
        return processVariables;
    }

    private List<CaseHearingResultedDefendantDetails> getDefendantDetailsWhichHasCjsResultCodeForSendingDocumentsToPrison(final JsonArray defendants) {
        final List<CaseHearingResultedDefendantDetails> caseHearingResultedDefendantDetailsList = new ArrayList<>();

        defendants.getValuesAs(JsonObject.class)
                .forEach(defendant -> {
                    final JsonObject judicialResult = getCustodialResultsInOffencesForSendingDocumentsToPrison(defendant.getJsonArray("offences"));
                    if (nonNull(judicialResult)) {
                        final String defendantId = defendant.getString("id");
                        final JsonObject personDefendant = defendant.containsKey("personDefendant") ? defendant.getJsonObject("personDefendant") : null;
                        String defendantName = EMPTY;
                        String remandStatus = EMPTY;
                        if (personDefendant != null) {
                            defendantName = getNameFromPersonDefendant(personDefendant);
                            remandStatus = personDefendant.containsKey("bailStatus") ? personDefendant.getJsonObject("bailStatus").getString("description") : EMPTY;
                        }
                        final String prisonName = extractPrisonName(judicialResult.getJsonArray("judicialResultPrompts"));
                        caseHearingResultedDefendantDetailsList.add(new CaseHearingResultedDefendantDetails(defendantName, defendantId, remandStatus, prisonName));
                    }
                });
        return caseHearingResultedDefendantDetailsList;
    }

    private String extractPrisonName(final JsonArray judicialResultPrompts) {
        if (isNull(judicialResultPrompts)) {
            return EMPTY;
        }
        return judicialResultPrompts.getValuesAs(JsonObject.class).stream()
                .filter(CaseHearingResultedEventProcessor::isPrisonOrganisationName)
                .findAny().map(jsonObject -> jsonObject.getString(VALUE)).orElse(EMPTY);
    }

    private static boolean isPrisonOrganisationName(final JsonObject jsonObject) {
        return jsonObject.containsKey(PROMPT_REFERENCE) && jsonObject.getString(PROMPT_REFERENCE).equals(PRISON_ORGANISATION_NAME);
    }

    private JsonObject getCustodialResultsInOffencesForSendingDocumentsToPrison(final JsonArray offences) {

        for (int offenceCount = 0; offenceCount < offences.size(); offenceCount++) {
            final JsonObject offence = offences.getJsonObject(offenceCount);
            final JsonArray judicialResults = offence.containsKey("judicialResults") ? offence.getJsonArray("judicialResults") : null;
            final JsonObject judicialResult = getCustodialResultsInJudicialResultsForSendingDocumentsToPrison(judicialResults);
            if (nonNull(judicialResult)) {
                return judicialResult;
            }
        }
        return null;
    }

    private JsonObject getCustodialResultsInJudicialResultsForSendingDocumentsToPrison(final JsonArray judicialResults) {
        if (isNull(judicialResults)) {
            return null;
        }

        for (int judicialResultsCount = 0; judicialResultsCount < judicialResults.size(); judicialResultsCount++) {
            final JsonObject judicialResult = judicialResults.getJsonObject(judicialResultsCount);
            final String cjsCode = judicialResult.containsKey("cjsCode") ? judicialResult.getString("cjsCode") : EMPTY;
            if (CJS_RESULT_CODE_FOR_SENDING_DOCUMENTS_TO_PRISON.contains(cjsCode)) {
                return judicialResult;
            }
        }
        return null;
    }

    private String getNameFromPersonDefendant(final JsonObject personDefendant) {
        return personDefendant.getJsonObject("personDetails").getString("firstName") + " " + personDefendant.getJsonObject("personDetails").getString("lastName");
    }

    private String getNotesFromCaseHearingResultedDefendantDetails(final List<CaseHearingResultedDefendantDetails> caseHearingResultedDefendantDetailsList) {
        return caseHearingResultedDefendantDetailsList.stream()
                .map(CaseHearingResultedDefendantDetails::getName)
                .collect(Collectors.joining(",", "[", "]"));
    }

    private String getCourtCodes(JsonObject courtHearing) {
        if (courtHearing == null) {
            return null;
        }
        JsonObject courtCentre = courtHearing.getJsonObject("courtCentre");
        return courtCentre != null && courtCentre.containsKey("code") ? courtCentre.getString("code") : null;
    }
}
