package uk.gov.moj.cpp.businessprocesses.service;

import static java.lang.String.format;
import static java.util.Arrays.stream;
import static java.util.Objects.nonNull;
import static java.util.regex.Pattern.compile;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.CANDIDATE_GROUPS;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.DEEP_LINK;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.DUE_DATE;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.IS_DEFERRABLE;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.IS_DELETABLE;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.TASK_NAME;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.TASK_TYPE_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.WORK_QUEUE;

import uk.gov.moj.cpp.businessprocesses.pojo.WorkflowTaskType;
import uk.gov.moj.cpp.businessprocesses.shared.WorkFlowTaskTypeNotFoundException;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class TaskTypeService {

    private static final String REGEX = ",";

    private static final Logger LOGGER = LoggerFactory.getLogger(TaskTypeService.class);

    @Inject
    private ReferenceDataService referenceDataService;

    @Inject
    private DueDateCalculationService dueDateCalculationService;

    /**
     * Retrieves basic task information from reference data.
     *
     * @param taskType    - The task type to retrieve the information of.
     * @param resourceId  - A resource ID if required, that will be appended to the deeplink.
     * @param hearingDate - The hearingDate if required, that will be used for the due date
     *                    calculation.
     * @param caseUrn     - A case urn value if required, that will be appended to the deeplink.
     * @return A map of task variables from reference data.
     */
    @SuppressWarnings({"squid:S3776"})
    public Map<String, Object> getTaskVariablesFromRefData(final String taskType, final String resourceId, final String hearingDate, final String caseUrn) {
        LOGGER.info("Looking up Task information from reference data for taskType: {}", taskType);

        final HashMap<String, Object> variables = new HashMap<>();
        final Optional<WorkflowTaskType> optionalWorkflowTaskType = referenceDataService.getWorkflowTaskType(taskType);
        if (nonNull(optionalWorkflowTaskType) && optionalWorkflowTaskType.isPresent()) {
            final WorkflowTaskType workflowTaskType = optionalWorkflowTaskType.get();
            variables.put(IS_DELETABLE, workflowTaskType.getDeletable());
            variables.put(IS_DEFERRABLE, workflowTaskType.getDeferrable());
            variables.put(TASK_NAME, workflowTaskType.getDisplayName());

            if (nonNull(workflowTaskType.getId())) {
                variables.put(TASK_TYPE_ID, workflowTaskType.getId());
            }
            if (nonNull(workflowTaskType.getTaskGroup())) {
                variables.put(CANDIDATE_GROUPS, stream(workflowTaskType.getTaskGroup().split(REGEX)).map(String::trim).toList());
            }
            if (isNotEmpty(workflowTaskType.getDeepLink()) && isNotEmpty((resourceId))) {
                if (compile("%[s]").matcher(workflowTaskType.getDeepLink()).results().count() == 2) {
                    variables.put(DEEP_LINK, String.format(workflowTaskType.getDeepLink(), resourceId, caseUrn));
                } else {
                    variables.put(DEEP_LINK, workflowTaskType.getDeepLink().concat(resourceId));
                }
            }
            if (nonNull(workflowTaskType.getWorkQueueId())) {
                variables.put(WORK_QUEUE, workflowTaskType.getWorkQueueId());
            }
            if (nonNull(workflowTaskType.getDueDateCalc())) {
                String dueDate = (hearingDate == null) ? dueDateCalculationService.calculateDueDate(workflowTaskType.getDueDateCalc())
                        : dueDateCalculationService.calculateDueDate(workflowTaskType.getDueDateCalc(), hearingDate);
                variables.put(DUE_DATE, dueDate);
            } else {
                variables.put(DUE_DATE, EMPTY);
            }
        } else {
            throw new WorkFlowTaskTypeNotFoundException(format("No WorkFlowTaskType with task type '%s' found in referencedata context", taskType));
        }
        return variables;
    }

    /**
     * See {@link #getTaskVariablesFromRefData}, but when the due date calculation doesn't require
     * hearing date.
     *
     * @param taskType   - The task type to retrieve the information of. * @param resourceId  - A
     * @param resourceId - A resource ID if required, that will be appended to the deeplink.
     * @return
     */
    public Map<String, Object> getTaskVariablesFromRefData(final String taskType, final String resourceId) {
        return getTaskVariablesFromRefData(taskType, resourceId, null, null);
    }

    /**
     * See {@link #getTaskVariablesFromRefData}, but when the BPMN process contains multiple tasks
     * so a prefix is added to the variable names to differentiate between each task.
     *
     * @param taskType    - The task type to retrieve the information of. * @param resourceId  - A
     * @param resourceId  - A resource ID if required, that will be appended to the deeplink.
     * @param prefix      - The variable name prefix to distinguish variables belonging to this
     *                    task.
     * @param hearingDate - The hearingDate if required, that will be used for the due date *
     *                    calculation.
     * @return
     */
    public Map<String, Object> getTaskVariablesFromRefDataWithPrefix(final String taskType, final String resourceId, final String prefix, final String hearingDate) {
        final HashMap<String, Object> variables = new HashMap<>();
        final Map<String, Object> referenceData = getTaskVariablesFromRefData(taskType, resourceId, hearingDate, null);

        final Iterator referenceDataIterator = referenceData.entrySet().iterator();

        while (referenceDataIterator.hasNext()) {
            final Map.Entry entry = (Map.Entry) referenceDataIterator.next();
            final Object key = entry.getKey();
            final Object value = entry.getValue();
            if(WORK_QUEUE.equals(key)){
                variables.put(key.toString(), value);
                continue;
            }
            variables.put(prefix + key.toString(), value);
        }
        return variables;
    }

    /**
     * See {@link #getTaskVariablesFromRefData}, but when the BPMN process contains multiple tasks
     * so a prefix is added to the variable names to differentiate between each task, but the task
     * doesn't require a hearingDate for the due date calculation.
     *
     * @param taskType   - The task type to retrieve the information of. * @param resourceId  - A
     * @param resourceId - A resource ID if required, that will be appended to the deeplink.
     * @param prefix     - The variable name prefix to distinguish variables belonging to this
     *                   task.
     * @return
     */
    public Map<String, Object> getTaskVariablesFromRefDataWithPrefix(final String taskType, final String resourceId, final String prefix) {
        return getTaskVariablesFromRefDataWithPrefix(taskType, resourceId, prefix, null);
    }
}
