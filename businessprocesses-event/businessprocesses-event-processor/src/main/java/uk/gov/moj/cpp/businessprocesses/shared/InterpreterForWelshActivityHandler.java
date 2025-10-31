package uk.gov.moj.cpp.businessprocesses.shared;

import static uk.gov.moj.cpp.businessprocesses.shared.Constants.BPMN_PROCESS_BOOK_INTERPRETER_WELSH_APPLICATION;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.BPMN_PROCESS_BOOK_INTERPRETER_WELSH_CASE;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.SYSTEM_USER_NAME;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.TASK_NAME_BOOK_WELSH_INTERPRETER_FOR_APPLICATION;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.WELSH;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.CASE_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.CASE_URN;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.DEFENDANTS;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.HEARING_DATE;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.HEARING_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.LAST_UPDATED_BY_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.LAST_UPDATED_BY_NAME;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.PROSECUTION_CASE;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.WELSH_LANGUAGE_UNIT_WORK_QUEUE;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.WORK_QUEUE;

import uk.gov.justice.core.courts.Hearing;
import uk.gov.justice.core.courts.ProsecutionCase;
import uk.gov.justice.services.core.dispatcher.SystemUserProvider;
import uk.gov.moj.cpp.businessprocesses.pojo.DefendantHearingLanguageNeeds;
import uk.gov.moj.cpp.businessprocesses.service.HearingService;
import uk.gov.moj.cpp.businessprocesses.service.ProgressionService;
import uk.gov.moj.cpp.businessprocesses.service.ReferenceDataService;
import uk.gov.moj.cpp.businessprocesses.service.TaskTypeService;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.inject.Inject;
import javax.json.JsonArray;
import javax.json.JsonObject;

import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.task.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InterpreterForWelshActivityHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(InterpreterForWelshActivityHandler.class);

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    @Inject
    private RuntimeService runtimeService;

    @Inject
    private TaskTypeService taskTypeService;

    @Inject
    private SystemUserProvider systemUserProvider;

    @Inject
    private ProgressionService progressionService;

    @Inject
    private ReferenceDataService referenceDataService;

    @Inject
    private TaskService taskService;

    @Inject
    private HearingService hearingService;

    public void handleWelshInterpreterForApplicationInitiated(final String hearingId) {
        LOGGER.info("Received request to create welsh interpreter activity for hearingId = {}", hearingId);
        final Hearing hearing = hearingService.getHearing(hearingId);
        if (isWelshCourtWithApplication(hearing)) {
            final String hearingDate = hearing.getHearingDays().get(0).getSittingDay().format(DATE_TIME_FORMATTER);
            processLinkedApplication(hearing, hearingId, hearingDate);
        }
    }

    public void handleWelshInterpreterForCaseInitiated(final String hearingId) {
        final Hearing hearing = hearingService.getHearing(hearingId);
        if (hearing != null && isWelshCourtWithProsecutionCases(hearing)) {
            final String hearingDate = hearing.getHearingDays().get(0).getSittingDay().format(DATE_TIME_FORMATTER);
            processProsecutionCases(hearing, hearingId, hearingDate);
        }
    }

    public void handleWelshInterpreterForHearingUpdated(final String hearingId) {
        final Hearing hearing = hearingService.getHearing(hearingId);

        if (!isWelshCourt(hearing)) {
            return;
        }

        final String hearingDate = hearing.getHearingDays().get(0).getSittingDay().format(DATE_TIME_FORMATTER);

        if (nonEmpty(hearing.getCourtApplications())) {
            processLinkedApplication(hearing, hearingId, hearingDate);
        } else if (nonEmpty(hearing.getProsecutionCases())) {
            processProsecutionCases(hearing, hearingId, hearingDate);
        }
    }

    private boolean isWelshCourtWithProsecutionCases(final Hearing hearing) {
        return nonEmpty(hearing.getProsecutionCases()) &&
                isWelshCourt(hearing);
    }

    private Boolean isWelshCourt(final Hearing hearing) {
        return referenceDataService.isWelshCourt(hearing.getCourtCentre().getId().toString());
    }

    private boolean isWelshCourtWithApplication(final Hearing hearing) {
        return nonEmpty(hearing.getCourtApplications()) &&
                isWelshCourt(hearing);
    }

    private void processLinkedApplication(final Hearing hearing, final String hearingId, final String hearingDate) {
        if (hearing.getCourtApplications() != null) {
            hearing.getCourtApplications().forEach(courtApplication -> {
                final UUID applicationId = courtApplication.getId();
                courtApplication.getCourtApplicationCases().forEach(courtApplicationCase -> {
                    final UUID caseId = courtApplicationCase.getProsecutionCaseId();
                    final String caseUrn = courtApplicationCase.getProsecutionCaseIdentifier().getCaseURN();
                    handleDefendantLanguageNeedsAndCreateTask(hearingId, hearingDate, caseId.toString(), applicationId.toString(), caseUrn, false);
                });
            });
        }
    }


    private void processProsecutionCases(final Hearing hearing, final String hearingId, final String hearingDate) {
        for (final ProsecutionCase prosecutionCase : hearing.getProsecutionCases()) {
            if (nonEmpty(prosecutionCase.getDefendants())) {
                handleProsecutionCase(hearingId, hearingDate, prosecutionCase);
            }
        }
    }

    private void handleProsecutionCase(final String hearingId, final String hearingDate, final ProsecutionCase prosecutionCase) {
        final String caseId = prosecutionCase.getId().toString();
        final String caseUrn = prosecutionCase.getProsecutionCaseIdentifier().getCaseURN();
        handleDefendantLanguageNeedsAndCreateTask(hearingId, hearingDate, caseId, null, caseUrn, true);
    }

    public static <T> boolean nonEmpty(final List<T> list) {
        return list != null && !list.isEmpty();
    }

    private void handleDefendantLanguageNeedsAndCreateTask(final String hearingId, final String hearingDate,
                                                           final String caseId, final String applicationId, final String urn, boolean isCaseActivity) {
        final JsonObject prosecutionCase = getProsecutionCaseDetails(caseId);

        if (prosecutionCaseHasDefendants(prosecutionCase)) {
            final JsonArray defendants = prosecutionCase.getJsonArray(DEFENDANTS);
            final Optional<DefendantHearingLanguageNeeds> defendantHearingLanguageNeeds = getWelshLanguageNeeds(defendants);

            defendantHearingLanguageNeeds.ifPresent(dhln -> {
                if (dhln.interpreterLanguageNeeds() != null && dhln.interpreterLanguageNeeds().toLowerCase().contains(WELSH)) {
                    if (isCaseActivity) {
                        createWelshInterpreterCaseTask(caseId, urn, hearingId, hearingDate);
                    } else {
                        createWelshInterpreterForApplication(caseId, applicationId, urn, hearingId, hearingDate);
                    }
                }
            });
        }
    }

    private boolean prosecutionCaseHasDefendants(final JsonObject prosecutionCase) {
        return prosecutionCase != null && prosecutionCase.containsKey(DEFENDANTS);
    }

    private JsonObject getProsecutionCaseDetails(final String caseId) {
        final JsonObject jsonObject = progressionService.getProsecutionCase(caseId);
        return jsonObject.getJsonObject(PROSECUTION_CASE);
    }

    private Optional<DefendantHearingLanguageNeeds> getWelshLanguageNeeds(final JsonArray defendants) {
        return defendants.stream()
                .map(defendant -> {
                    JsonObject personDefendant = ((JsonObject) defendant).getJsonObject("personDefendant");
                    JsonObject personDetails = personDefendant.getJsonObject("personDetails");
                    String interpreterLanguageNeeds = extractLanguageNeeds(personDetails, "interpreterLanguageNeeds");
                    String hearingLanguageNeeds = extractLanguageNeeds(personDetails, "hearingLanguageNeeds");
                    return new DefendantHearingLanguageNeeds(interpreterLanguageNeeds, hearingLanguageNeeds);
                })
                .filter(this::isWelshLanguageNeeded)
                .findFirst();
    }

    private String extractLanguageNeeds(final JsonObject personDetails, final String key) {
        return personDetails.containsKey(key) ? personDetails.getString(key) : null;
    }

    private boolean isWelshLanguageNeeded(final DefendantHearingLanguageNeeds dhln) {
        return (dhln.interpreterLanguageNeeds() != null && dhln.interpreterLanguageNeeds().toLowerCase().contains(WELSH)) ||
                (dhln.hearingLanguageNeeds() != null && dhln.hearingLanguageNeeds().toLowerCase().contains(WELSH));
    }

    private void createWelshInterpreterCaseTask(final String caseId, final String caseURN,
                                                final String hearingId, final String hearingDate) {
        if (isActivityAlreadyExist(hearingId, BPMN_PROCESS_BOOK_INTERPRETER_WELSH_CASE)) {
            return;
        }

        final Map<String, Object> processVariables = prepareProcessVariablesForACase(caseId, caseURN, hearingId, hearingDate);
        startProcessInstance(hearingId, BPMN_PROCESS_BOOK_INTERPRETER_WELSH_CASE, processVariables);
    }

    private Map<String, Object> prepareProcessVariablesForACase(final String caseId, final String caseURN,
                                                                final String hearingId, final String hearingDate) {
        final Map<String, Object> processVariables = taskTypeService.getTaskVariablesFromRefData(
                Constants.TASK_NAME_BOOK_WELSH_INTERPRETER_FOR_CASE, caseId, hearingDate, null
        );
        processVariables.put(CASE_URN, caseURN);
        processVariables.put(HEARING_ID, hearingId);
        processVariables.put(HEARING_DATE, hearingDate);
        processVariables.put(CASE_ID, caseId);

        final String userId = systemUserProvider.getContextSystemUserId().map(UUID::toString).orElse(null);
        processVariables.put(LAST_UPDATED_BY_ID, userId);
        processVariables.put(LAST_UPDATED_BY_NAME, SYSTEM_USER_NAME);
        processVariables.put(WORK_QUEUE, WELSH_LANGUAGE_UNIT_WORK_QUEUE);

        return processVariables;
    }

    private void startProcessInstance(final String hearingId, final String processKey,
                                      final Map<String, Object> processVariables) {
        runtimeService.startProcessInstanceByKey(processKey, hearingId, processVariables);
        LOGGER.info("Activity created for hearingId: {} with processKey {}", hearingId, processKey);
    }


    private void createWelshInterpreterForApplication(final String caseId, final String applicationId, final String caseURN,
                                                      final String hearingId, final String hearingDate) {
        if (isActivityAlreadyExist(hearingId, BPMN_PROCESS_BOOK_INTERPRETER_WELSH_APPLICATION)) {
            return;
        }

        final Map<String, Object> processVariables = prepareApplicationProcessVariables(caseId, applicationId, caseURN, hearingId, hearingDate);
        startProcessInstance(hearingId, BPMN_PROCESS_BOOK_INTERPRETER_WELSH_APPLICATION, processVariables);
    }

    private Map<String, Object> prepareApplicationProcessVariables(final String caseId, final String applicationId, final String caseURN,
                                                                   final String hearingId, final String hearingDate) {
        final Map<String, Object> processVariables = taskTypeService.getTaskVariablesFromRefData(
                TASK_NAME_BOOK_WELSH_INTERPRETER_FOR_APPLICATION, applicationId, hearingDate, null
        );
        processVariables.put(CASE_URN, caseURN);
        processVariables.put(HEARING_ID, hearingId);
        processVariables.put(HEARING_DATE, hearingDate);
        processVariables.put(CASE_ID, caseId);

        final String userId = systemUserProvider.getContextSystemUserId().map(UUID::toString).orElse(null);
        processVariables.put(LAST_UPDATED_BY_ID, userId);
        processVariables.put(LAST_UPDATED_BY_NAME, SYSTEM_USER_NAME);
        processVariables.put(WORK_QUEUE, WELSH_LANGUAGE_UNIT_WORK_QUEUE);

        return processVariables;
    }

    private Boolean isActivityAlreadyExist(final String hearingId, final String processDefinitionKey) {
        final List<Task> tasks = getActiveTasks(hearingId, processDefinitionKey);
        final boolean isExist = !tasks.isEmpty();

        if (isExist) {
            logActivityExistence(tasks.get(0), hearingId, processDefinitionKey);
        }

        return isExist;
    }

    private List<Task> getActiveTasks(final String hearingId, final String processDefinitionKey) {
        return taskService.createTaskQuery()
                .processDefinitionKey(processDefinitionKey)
                .processInstanceBusinessKey(hearingId)
                .active()
                .list();
    }

    private void logActivityExistence(final Task task, final String hearingId, final String processDefinitionKey) {
        LOGGER.info("Activity is already created with id: {} and hearingId: {} for processKey {}",
                task.getTenantId(), hearingId, processDefinitionKey);
    }
}
