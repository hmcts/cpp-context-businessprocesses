package uk.gov.moj.cpp.businessprocesses.pojo;


import uk.gov.moj.cpp.businessprocesses.defendant.TaskDefendant;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@SuppressWarnings({"squid:S1161", "squid:S2384"})
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WorkflowCustomTaskType extends WorkflowTaskType {

    @JsonProperty("due")
    private LocalDateTime due;

    @JsonProperty("candidateGroups")
    private String candidateGroups;

    @JsonProperty("followUp")
    private LocalDate followUp;

    @JsonProperty("custodyTimeLimit")
    private LocalDate custodyTimeLimit;

    @JsonProperty("isUrgent")
    private Boolean isUrgent;

    @JsonProperty("caseURN")
    private String caseURN;

    @JsonProperty("hearingDate")
    private LocalDate hearingDate;

    @JsonProperty("hearingType")
    private String hearingType;

    @JsonProperty("assignee")
    private String assignee;

    @JsonProperty("region")
    private String region;

    @JsonProperty("placement")
    private String placement;

    @JsonProperty("taskType")
    private String taskType;

    @JsonProperty("businessUnitCodes")
    private String businessUnitCodes;

    @JsonProperty("courtCodes")
    private String courtCodes;

    @JsonProperty("defendants")
    private List<TaskDefendant> defendants;

    @JsonProperty("comment")
    private String comment;

    public String getTaskType() {
        return taskType;
    }

    public LocalDateTime getDue() {
        return due;
    }

    public String getCandidateGroups() {
        return candidateGroups;
    }

    public LocalDate getFollowUp() {
        return followUp;
    }

    public LocalDate getCustodyTimeLimit() {
        return custodyTimeLimit;
    }

    public Boolean getUrgent() {
        return isUrgent;
    }

    public String getCaseURN() {
        return caseURN;
    }

    public LocalDate getHearingDate() {
        return hearingDate;
    }

    public String getHearingType() {
        return hearingType;
    }

    public String getAssignee() {
        return assignee;
    }

    public String getRegion() {
        return region;
    }

    public String getPlacement() {
        return placement;
    }

    public String getBusinessUnitCodes() {
        return businessUnitCodes;
    }

    public String getCourtCodes() {
        return courtCodes;
    }

    public List<TaskDefendant> getDefendants() {
        return defendants;
    }

    public String getComment() {
        return comment;
    }

    @Override
    public String toString() {
        return "WorkflowCustomTaskType{" +
                "due=" + due +
                ", candidateGroups='" + candidateGroups + '\'' +
                ", followUp=" + followUp +
                ", custodyTimeLimit=" + custodyTimeLimit +
                ", isUrgent=" + isUrgent +
                ", caseURN='" + caseURN + '\'' +
                ", hearingDate=" + hearingDate +
                ", hearingType='" + hearingType + '\'' +
                ", assignee='" + assignee + '\'' +
                ", region='" + region + '\'' +
                ", placement='" + placement + '\'' +
                ", taskName='" + getTaskName() + '\'' +
                ", taskType='" + taskType + '\'' +
                ", displayName='" + getDisplayName() + '\'' +
                ", isDeletable=" + getDeletable() +
                ", isDeferrable=" + getDeferrable() +
                ", businessUnitCodes='" + businessUnitCodes + '\'' +
                ", courtCodes='" + courtCodes + '\'' +
                ", organisationId='" + getOrganisationId() + '\'' +
                ", defendants=" + defendants +
                ", comment='" + comment + '\'' +
                '}';
    }
}