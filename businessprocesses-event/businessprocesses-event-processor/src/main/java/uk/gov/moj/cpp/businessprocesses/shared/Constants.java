package uk.gov.moj.cpp.businessprocesses.shared;

/**
 * Common constants used by the Event Processors. For variables/names of fields that are used
 * specifically within in BPMN processes, please see {@link ProcessVariableConstants}.
 */
public class Constants {

    public static final String BPMN_PROCESS_APPLICATION_RECEIVED = "application-received";
    public static final String BPMN_PROCESS_BCM_WELSH_LANGUAGE_TRANSLATION_REQUEST = "bcm_welsh_language_translation_request";
    public static final String BPMN_PROCESS_CASE_RESULTS = "case-results";
    public static final String BPMN_PROCESS_CORRESPONDENCE_FAILURE_IDENTIFIED = "correspondence_failure_identified";
    public static final String BPMN_PROCESS_COTR_CUSTOM_TASK_PROCESS = "cotr_custom_task_process";
    public static final String BPMN_PROCESS_DELETE_FINANCIAL_MEANS_INFORMATION = "delete-financial-means-information";
    public static final String BPMN_PROCESS_HEARING_LISTED = "hearing-listed";
    public static final String BPMN_PROCESS_HEARING_VACATED = "hearing-vacated";
    public static final String BPMN_PROCESS_LIST_BAIL_APPEAL_HEARING_PROCESS = "list_bail_appeal_hearing_process";
    public static final String BPMN_PROCESS_BOOK_INTERPRETER_WELSH_CASE = "book_interpreter_welsh_case";
    public static final String BPMN_PROCESS_BOOK_INTERPRETER_WELSH_APPLICATION = "book_interpreter_welsh_application";
    public static final String BPMN_PROCESS_LIST_MURDER_CASE_FOR_BAIL_HEARING_PROCESS = "list_murder_case_for_bail_hearing_process";
    public static final String BPMN_PROCESS_OPA_CUSTOM_TASK_PROCESS = "opa_custom_task_process";
    public static final String BPMN_PROCESS_SJP_CASE_HEARING_DECISION_SAVED = "sjp-case-hearing-decision-saved";
    public static final String BPMN_PROCESS_SPI_ERROR_IDENTIFIED = "spi_error_identified";
    public static final String BPMN_PROCESS_TRIAGE_INCOMING_DOCUMENT_IDENTIFIED = "triage_incoming_document_identified";
    public static final String BPMN_PROCESS_UPDATE_ACCESS_TO_SENSITIVE_CASE_PROCESS = "update_access_to_sensitive_case_process";
    public static final String BPMN_PROCESS_WELSH_TRANSLATION_PROCESS = "welsh-translation-process";

    public static final String TASK_NAME_BOOK_INTERPRETER_APPLICATION = "Book_interpreter_application";
    public static final String TASK_NAME_BOOK_INTERPRETER_CASE = "Book_interpreter_case";
    public static final String TASK_NAME_CANCEL_INTERPRETER_APPLICATION = "Cancel_interpreter_application";
    public static final String TASK_NAME_CANCEL_INTERPRETER_CASE = "Cancel_interpreter_case";
    public static final String TASK_NAME_CORRESPONDENCE_FAILURE = "Correspondence_failure";
    public static final String TASK_NAME_COTR_CUSTOM_ACTIVITY = "cotr_custom_activity";
    public static final String TASK_NAME_LIST_BAIL_APPEAL_HEARING = "list_bail_appeal_hearing";
    public static final String TASK_NAME_BOOK_WELSH_INTERPRETER_FOR_CASE = "Book_interpreter_welsh_case";
    public static final String TASK_NAME_BOOK_WELSH_INTERPRETER_FOR_APPLICATION = "Book_interpreter_welsh_application";
    public static final String TASK_NAME_LIST_MURDER_CASE_FOR_BAIL_HEARING = "list_murder_case_for_bail_hearing";
    public static final String TASK_NAME_OPA_CUSTOM_ACTIVITY = "opa_custom_activity";
    public static final String TASK_NAME_PROCESS_APPLICATION = "Process_Application";
    public static final String TASK_NAME_REFER_SJS_CASE_FOR_HEARING = "Refer_SJS_case_for_hearing";
    public static final String TASK_NAME_REMOVE_DDJ_FROM_HEARING = "Remove_DDJ_from_hearing";
    public static final String TASK_NAME_REQUEST_WLU_TO_TRANSLATE_WELSH_DOCUMENT = "Request_WLU_to_translate_welsh_document";
    public static final String TASK_NAME_SEND_DOCUMENTS_TO_PRISON = "Send_documents_to_prison";
    public static final String TASK_NAME_SPI_ERROR = "SPI_error";
    public static final String TASK_NAME_TRANSLATE_TO_WELSH = "translate_to_welsh";
    public static final String TASK_NAME_TRIAGE_INCOMING_DOCUMENT = "Triage_incoming_document";
    public static final String TASK_NAME_UPDATE_ACCESS_TO_SENSITIVE_CASE = "update_access_to_sensitive_case";
    public static final String PROCESS_NEW_SUMMONS_APPLICATION = "process_new_summons_application";
    public static final String TASK_NEW_PROCESS_NEW_SUMMONS_APPLICATION = "process_summons_application";

    public static final String ACTION = "action";
    public static final String APPLICATION_DETAILS = "applicationDetails";
    public static final String APPLICATION_ID = "applicationId";
    public static final String APPLICATION_REFERENCE = "applicationReference";
    public static final String CASE_DETAILS = "caseDetails";
    public static final String CASE_ID = "caseId";
    public static final String CASE_JURISDICTION_TYPE = "latestHearingJurisdictionType";
    public static final String CASE_STATUS = "caseStatus";
    public static final String CASE_URNS = "caseUrns";
    public static final String CASEURN = "caseUrn";
    public static final String CHANGE_AUTHOR = "changeAuthor";
    public static final String CHANGE_AUTHOR_ID = "changeAuthorId";
    public static final String COMPLETED_DATE = "completedDate";
    public static final String COURT_HEARING_LOCATION = "courtHearingLocation";
    public static final String DATE_OF_HEARING = "dateOfHearing";
    public static final String TIME_OF_HEARING = "timeOfHearing";
    public static final String COURT_ID = "courtId";
    public static final String CROWN_COURT_ADMIN_WORK_QUEUE_ID = "5cd9bd67-1f08-315b-9608-8eb7ebb7ea2f";
    public static final String CROWN_JURISDICTION_TYPE = "CROWN";
    public static final String DEFENCE = "DEFENCE";
    public static final String DEFENDANT_DETAILS = "defendantDetails";
    public static final String DEFENDANT = "defendant";
    public static final String DEFENDANTS = "defendants";
    public static final String DEFENDANT_FIRST_NAME = "defendantFirstName";
    public static final String DEFENDANT_ID = "defendantId";
    public static final String DEFENDANT_LAST_NAME = "defendantLastName";
    public static final String DEFENDANT_NAME = "defendantName";
    public static final String DEFENDANT_REMAND_STATUS = "defendantRemandStatus";
    public static final String DELETED_DATE = "deletedDate";
    public static final String DELETION_REASON = "deletionReason";
    public static final String DESCRIPTION = "description";
    public static final String DOCUMENT_META_DATA = "documentMetaData";
    public static final String GENERIC_TASK_VARIABLES_JSON_STRING = "genericTaskVariablesJsonString";
    public static final String HAS_INTERPRETER = "hasInterpreter";
    public static final String HEARING_DATE_TIME = "hearingDateTime";
    public static final String HEARING_ID = "hearingId";
    public static final String ID = "id";
    public static final String HEARINGS_AT_A_GLANCE = "hearingsAtAGlance";

    public static final String INTERPRETER_LANGUAGE_NEEDS = "interpreterLanguageNeeds";
    public static final String HEARING_LANGUAGE_NEEDS = "hearingLanguageNeeds";
    public static final String INITIAL_HEARING = "initialHearing";
    public static final String IS_WELSH = "isWelsh";
    public static final String JURISDICTION_TYPE = "jurisdictionType";
    public static final String MAGISTRATES_JURISDICTION_TYPE = "MAGISTRATES";
    public static final String MASTER_DEFENDANT_ID = "masterDefendantId";
    public static final String MATERIAL_ID = "materialId";
    public static final String NEXT_HEARING_CROWN_COURT = "NHCCS";
    public static final String NOW_DOCUMENT_NOTIFICATION_SUPPRESSED = "nowDocumentNotificationSuppressed";
    public static final String OBJECT = "object";
    public static final String PERMISSION_ID = "permissionId";
    public static final String PERMISSIONS = "permissions";
    public static final String PLACE_HOLDER = "-";
    public static final String PREFIX_SEND_DOCUMENTS_TO_PRISON = "sendDocumentsToPrison_";
    public static final String PRISON_ORGANISATION_NAME = "prisonOrganisationName";
    public static final String PROMPT_REFERENCE = "promptReference";
    public static final String PROSECUTION_CASE_IDENTIFIER = "prosecutionCaseIdentifier";
    public static final String PROSECUTOR = "PROSECUTOR";
    public static final String PROSECUTOR_CASE_REFERENCE = "prosecutorCaseReference";
    public static final String REFERENCE = "reference";
    public static final String SUBJECT = "subject";
    public static final String SYSTEM_USER_NAME = "SYSTEM";
    public static final String TARGET = "target";
    public static final String TASK_VARIABLES_JSON_STRING = "taskVariablesJsonString";
    public static final String TEMPLATE_NAME = "templateName";
    public static final String TYPE = "type";
    public static final String VACATED_TRIAL_REASON_ID = "vacatedTrialReasonId";
    public static final String VALUE = "value";
    public static final String WORK_FLOW_TASK_TYPES = "workflowWorkQueueMappings";
    public static final String WELSH = "welsh";

    private Constants() {
    }
}
