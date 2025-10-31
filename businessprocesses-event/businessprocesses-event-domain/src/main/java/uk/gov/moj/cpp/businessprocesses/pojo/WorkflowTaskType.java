package uk.gov.moj.cpp.businessprocesses.pojo;

import java.time.LocalDate;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class WorkflowTaskType {
    @JsonProperty("id")
    protected UUID id;

    @JsonProperty("organisationId")
    protected String organisationId;

    @JsonProperty("seqNum")
    protected Integer seqNum;

    @JsonProperty("taskName")
    protected String taskName;

    @JsonProperty("displayName")
    protected String displayName;

    @JsonProperty("taskGroup")
    protected String taskGroup;

    @JsonProperty("isDeletable")
    protected Boolean isDeletable;

    @JsonProperty("isDeferrable")
    protected Boolean isDeferrable;

    @JsonProperty("duration")
    protected Integer duration;

    @JsonProperty("followUpInterval")
    protected Integer followUpInterval;

    @JsonProperty("validFrom")
    protected LocalDate validFrom;

    @JsonProperty("validTo")
    protected LocalDate validTo;

    @JsonProperty("deepLink")
    protected String deepLink;

    @JsonProperty("workQueueId")
    protected String workQueueId;

    @JsonProperty("manualTask")
    protected Boolean manualTask;

    @JsonProperty("dueDateCalc")
    protected String dueDateCalc;

    @JsonProperty("isCustomTask")
    protected Boolean isCustomTask;

    @JsonProperty("queueName")
    protected String queueName;

    @JsonProperty("crownFlag")
    protected Boolean crownFlag;

    @JsonProperty("magistratesFlag")
    protected Boolean magistratesFlag;

    @JsonProperty("caseId")
    protected String caseId;

    @JsonProperty("note")
    protected String note;

    @JsonProperty("caseTag")
    protected String caseTag;

    public UUID getId() {
        return id;
    }

    public Integer getSeqNum() {
        return seqNum;
    }

    public String getTaskName() {
        return taskName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getTaskGroup() {
        return taskGroup;
    }

    public Boolean getDeletable() {
        return isDeletable;
    }

    public Boolean getDeferrable() {
        return isDeferrable;
    }

    public Integer getDuration() {
        return duration;
    }

    public Integer getFollowUpInterval() {
        return followUpInterval;
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public LocalDate getValidTo() {
        return validTo;
    }

    public String getOrganisationId() {
        return organisationId;
    }

    public String getDeepLink() {
        return deepLink;
    }

    public String getWorkQueueId() {
        return workQueueId;
    }

    public String getCaseId() {
        return caseId;
    }

    public String getNote() {
        return note;
    }

    public String getCaseTag() {
        return caseTag;
    }

    public Boolean getManualTask() {
        return manualTask;
    }

    public String getDueDateCalc() {
        return dueDateCalc;
    }

    public Boolean getCustomTask() {
        return isCustomTask;
    }

    public String getQueueName() {
        return queueName;
    }

    public Boolean getCrownFlag() {
        return crownFlag;
    }

    public Boolean getMagistratesFlag() {
        return magistratesFlag;
    }
}
