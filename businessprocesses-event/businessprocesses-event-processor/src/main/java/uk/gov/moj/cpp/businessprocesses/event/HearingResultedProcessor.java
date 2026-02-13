package uk.gov.moj.cpp.businessprocesses.event;

import static java.lang.Boolean.TRUE;
import static java.util.Objects.nonNull;
import static org.apache.commons.collections.CollectionUtils.isNotEmpty;
import static uk.gov.justice.services.core.annotation.Component.EVENT_PROCESSOR;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.BPMN_PROCESS_LIST_BAIL_APPEAL_HEARING_PROCESS;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.BPMN_PROCESS_LIST_MURDER_CASE_FOR_BAIL_HEARING_PROCESS;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.BPMN_PROCESS_UPDATE_ACCESS_TO_SENSITIVE_CASE_PROCESS;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.NEXT_HEARING_CROWN_COURT;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.SYSTEM_USER_NAME;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.TASK_NAME_LIST_BAIL_APPEAL_HEARING;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.TASK_NAME_LIST_MURDER_CASE_FOR_BAIL_HEARING;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.TASK_NAME_UPDATE_ACCESS_TO_SENSITIVE_CASE;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.CASE_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.CASE_URN;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.COURT_CODES;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.CUSTODY_TIME_LIMIT;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.HEARING_DATE;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.HEARING_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.HEARING_TYPE;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.LAST_UPDATED_BY_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.LAST_UPDATED_BY_NAME;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.REGION;

import uk.gov.justice.core.courts.Hearing;
import uk.gov.justice.core.courts.JudicialResultPrompt;
import uk.gov.justice.core.courts.ProsecutionCase;
import uk.gov.justice.services.common.converter.JsonObjectToObjectConverter;
import uk.gov.justice.services.core.annotation.Handles;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.core.dispatcher.SystemUserProvider;
import uk.gov.justice.services.core.featurecontrol.FeatureControlGuard;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.moj.cpp.businessprocesses.contants.BailAppealEnum;
import uk.gov.moj.cpp.businessprocesses.contants.SeriousBailHearingEnum;
import uk.gov.moj.cpp.businessprocesses.event.summonsapplication.SummonsApplicationHandler;
import uk.gov.moj.cpp.businessprocesses.pojo.StartApplicationWorkflowRequest;
import uk.gov.moj.cpp.businessprocesses.refdata.query.api.Resultdefinition;
import uk.gov.moj.cpp.businessprocesses.service.ReferenceDataService;
import uk.gov.moj.cpp.businessprocesses.service.TaskTypeService;
import uk.gov.moj.cpp.businessprocesses.service.UserGroupsService;
import uk.gov.moj.cpp.businessprocesses.shared.HearingHelper;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.inject.Inject;
import javax.json.JsonObject;

import org.camunda.bpm.engine.RuntimeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings({"squid:UnusedPrivateMethod"})
@ServiceComponent(EVENT_PROCESSOR)
public class HearingResultedProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(HearingResultedProcessor.class);

    @Inject
    private JsonObjectToObjectConverter jsonObjectToObjectConverter;

    @Inject
    private ReferenceDataService referenceDataService;

    @Inject
    private UserGroupsService userGroupsService;

    @Inject
    private RuntimeService runtimeService;

    @Inject
    private TaskTypeService taskTypeService;

    @Inject
    private SystemUserProvider systemUserProvider;

    @Inject
    private FeatureControlGuard featureControlGuard;

    @Inject
    private SummonsApplicationHandler summonsApplicationTaskHandler;

    @SuppressWarnings("pmd:NullAssignment")
    @Handles("public.events.hearing.hearing-resulted")
    public void handleHearingResultedProcessor(final JsonEnvelope jsonEnvelope) {
        LOGGER.info("public.events.hearing.hearing-resulted with payload {} ", jsonEnvelope);
        if (featureControlGuard.isFeatureEnabled("camunda-hearing-hearing-resulted")) {
            handleHearingResulted(jsonEnvelope);
            summonsApplicationTaskHandler.handleSummonsApplicationResulted(jsonEnvelope);
        }
    }

    public void handleHearingResulted(final JsonEnvelope jsonEnvelope) {

        LOGGER.info("Received the event from HEARING that triggers workflows based on the bail related result");

        final Hearing hearing = jsonObjectToObjectConverter.convert(jsonEnvelope.payloadAsJsonObject().getJsonObject("hearing"), Hearing.class);
        final Map<String, String> taskMap = new HashMap<>();

        final JsonObject eventPayload = jsonEnvelope.payloadAsJsonObject();
        final JsonObject courtHearing = eventPayload.getJsonObject("hearing");
        final String hearingDate = ((JsonObject) courtHearing.getJsonArray("hearingDays").get(0)).getString("sittingDay");

        if (isNotEmpty(hearing.getProsecutionCases())) {
            hearing.getProsecutionCases().forEach(prosecutionCase -> prosecutionCase.getDefendants().stream()
                    .flatMap(defendant -> defendant.getOffences().stream())
                    .filter(offence -> isNotEmpty(offence.getJudicialResults()))
                    .flatMap(offence -> offence.getJudicialResults().stream())
                    .forEach(judicialResult -> {
                        LOGGER.debug("List bail appeal hearing invoked for judicial result type id {} and judicialResultId {}", judicialResult.getJudicialResultTypeId(), judicialResult.getJudicialResultId());
                        final Resultdefinition resultDefinition = referenceDataService.getResultDefinition(judicialResult.getJudicialResultTypeId().toString());
                        if (nonNull(resultDefinition) && (BailAppealEnum.getBailAppealByType(resultDefinition.getShortCode()) != null) || SeriousBailHearingEnum.getSeriousBailHearingByType(resultDefinition.getShortCode()) != null) {
                            taskMap.put(resultDefinition.getShortCode(), judicialResult.getJudicialResultId().toString());
                        } else if (nonNull(judicialResult.getNextHearing()) && resultDefinition.getShortCode().equals(NEXT_HEARING_CROWN_COURT) && TRUE.equals(userGroupsService.getCaseSensitive(prosecutionCase.getId().toString()))) {
                            final Optional<JudicialResultPrompt> location = getLocation(prosecutionCase);
                            if (featureControlGuard.isFeatureEnabled(TASK_NAME_UPDATE_ACCESS_TO_SENSITIVE_CASE)) {
                                startSensitiveCaseTransferWorkFlow(prosecutionCase.getId().toString(), prosecutionCase.getProsecutionCaseIdentifier().getCaseURN(), hearing, location.map(JudicialResultPrompt::getValue).orElse(null), hearingDate);
                            }
                        }
                    }));
        }

        taskMap.forEach((shortCode, resultId) -> {
            LOGGER.info("Tasks are iterating with  key {} and value {} for hearingType {} and hearing Id {}", shortCode, resultId, hearing.getType().getDescription(), hearing.getId());
            createNewTask(hearing, shortCode, "Bail Application", hearingDate);
        });
    }

    private void createNewTask(final Hearing hearing, final String shortCode, String type, final String hearingDate) {
        if (BailAppealEnum.getBailAppealByType(shortCode) != null && featureControlGuard.isFeatureEnabled(TASK_NAME_LIST_BAIL_APPEAL_HEARING)) {
            createBailAppealTask(hearing, BPMN_PROCESS_LIST_BAIL_APPEAL_HEARING_PROCESS, type, TASK_NAME_LIST_BAIL_APPEAL_HEARING, hearingDate);
        }
        if (SeriousBailHearingEnum.getSeriousBailHearingByType(shortCode) != null && featureControlGuard.isFeatureEnabled(TASK_NAME_LIST_MURDER_CASE_FOR_BAIL_HEARING)) {
            createBailAppealTask(hearing, BPMN_PROCESS_LIST_MURDER_CASE_FOR_BAIL_HEARING_PROCESS, type, TASK_NAME_LIST_MURDER_CASE_FOR_BAIL_HEARING, hearingDate);
        }
    }


    @SuppressWarnings("pmd:NullAssignment")
    private void createBailAppealTask(final Hearing hearing, final String listBailAppealBpmn, final String hearingType, final String taskName, final String hearingDate) {
        hearing.getProsecutionCases().forEach(prosecutionCase -> {
            String custodyTimeLimit = HearingHelper.getCustodyTimeLimit(hearing);
            final Optional<JudicialResultPrompt> locationOpt = getLocation(prosecutionCase);
            final String location = locationOpt.map(JudicialResultPrompt::getValue).orElse(null);
            final String courtCodes = getCourtCode(location, hearing);
            StartApplicationWorkflowRequest request = new StartApplicationWorkflowRequest.Builder()
                    .withProcessKey(listBailAppealBpmn)
                    .withCaseURN(prosecutionCase.getProsecutionCaseIdentifier().getCaseURN())
                    .withHearingType(hearingType)
                    .withHearingId(hearing.getId().toString())
                    .withLocation(location)
                    .withCaseId(prosecutionCase.getId())
                    .withTaskName(taskName)
                    .withHearingDate(hearingDate)
                    .withCourtCodes(courtCodes)
                    .withCustodyTimeLimit(custodyTimeLimit)
                    .build();
            startApplicationWorkFlow(request);
        });
    }

    private Optional<JudicialResultPrompt> getLocation(final ProsecutionCase prosecutionCase) {
        return prosecutionCase.getDefendants().stream()
                .flatMap(defendant -> defendant.getOffences().stream())
                .filter(offence -> offence != null && offence.getJudicialResults() != null)
                .flatMap(offence -> offence.getJudicialResults().stream())
                .filter(judicialResult -> judicialResult.getJudicialResultPrompts() != null)
                .flatMap(judicialResult -> judicialResult.getJudicialResultPrompts().stream())
                .filter(judicialResultPrompt -> judicialResultPrompt.getPromptReference() != null && "crownCourtName".equals(judicialResultPrompt.getPromptReference()))
                .findFirst();
    }

    private String getCourtCode(final String location, final Hearing hearing) {
        if (location != null) {
            JsonObject courtCentreDetailsJson = referenceDataService.retrieveCourtCentreDetailsByCourtRoomName(location);
            return ReferenceDataService.getCourtCentreOuCode(courtCentreDetailsJson);
        }

        if (hearing != null && hearing.getCourtCentre() != null) {
            return hearing.getCourtCentre().getCode();
        }

        return null;
    }

    private void startApplicationWorkFlow(final StartApplicationWorkflowRequest request) {
        final Map<String, Object> processVariables = taskTypeService.getTaskVariablesFromRefData(request.getTaskName(), request.getCaseId().toString());
        processVariables.put(CASE_URN, request.getCaseURN());
        processVariables.put(HEARING_TYPE, request.getHearingType());
        processVariables.put(HEARING_ID, request.getHearingId());
        processVariables.put(HEARING_DATE, request.getHearingDate());
        processVariables.put(CUSTODY_TIME_LIMIT, request.getCustodyTimeLimit());
        final String userId = systemUserProvider.getContextSystemUserId().map(UUID::toString).orElse(null);
        processVariables.put(LAST_UPDATED_BY_ID, userId);
        processVariables.put(LAST_UPDATED_BY_NAME, SYSTEM_USER_NAME);

        if(nonNull(request.getCourtCodes())) {
            processVariables.put(COURT_CODES, request.getCourtCodes());
        }

        runtimeService.startProcessInstanceByKey(request.getProcessKey(), request.getCaseURN(), processVariables);

        LOGGER.info("Create Bail Application process for hearing {} started with processKey  {}", request.getHearingType(), request.getProcessKey());
    }

    private void startSensitiveCaseTransferWorkFlow(final String caseId, final String caseURN, final Hearing hearing, final String location, final String hearingDate) {
        String hearingId = hearing.getId().toString();

        final Map<String, Object> processVariables = taskTypeService.getTaskVariablesFromRefData(TASK_NAME_UPDATE_ACCESS_TO_SENSITIVE_CASE, caseId);
        processVariables.put(CASE_ID, caseId);
        processVariables.put(CASE_URN, caseURN);
        processVariables.put(HEARING_ID, hearingId);
        processVariables.put(REGION, location);
        processVariables.put(HEARING_DATE, hearingDate);

        if (hearing.getCourtCentre() != null) {
            processVariables.put(COURT_CODES, hearing.getCourtCentre().getCode());
        }

        final String userId = systemUserProvider.getContextSystemUserId().map(UUID::toString).orElse(null);
        processVariables.put(LAST_UPDATED_BY_ID, userId);
        processVariables.put(LAST_UPDATED_BY_NAME, SYSTEM_USER_NAME);

        runtimeService.startProcessInstanceByKey(BPMN_PROCESS_UPDATE_ACCESS_TO_SENSITIVE_CASE_PROCESS, caseId, processVariables);

        LOGGER.info("Update Access to Sensitive Case for case with hearing id {} started.", hearingId);
    }
}