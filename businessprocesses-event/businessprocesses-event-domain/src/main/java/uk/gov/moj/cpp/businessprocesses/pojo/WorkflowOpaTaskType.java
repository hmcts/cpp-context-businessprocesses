package uk.gov.moj.cpp.businessprocesses.pojo;


import uk.gov.moj.cpp.businessprocesses.defendant.TaskDefendant;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

public class WorkflowOpaTaskType extends WorkflowTaskType {

    @JsonProperty("opaId")
    private String opaId;

    @JsonProperty("due")
    private LocalDateTime due;

    @JsonProperty("candidateGroups")
    private String candidateGroups;

    @JsonProperty("taskCreationDate")
    private LocalDate taskCreationDate;

    @JsonProperty("custodyTimeLimit")
    private LocalDate custodyTimeLimit;

    @JsonProperty("caseURN")
    private String caseURN;

    @JsonProperty("hearingDate")
    private LocalDate hearingDate;

    @JsonProperty("assignee")
    private String assignee;

    @JsonProperty("courtCodes")
    private String courtCodes;


    @JsonProperty("defendants")
    private List<TaskDefendant> defendants;

    @JsonProperty("comment")
    private String comment;

    public String getOpaId() {
        return opaId;
    }

    public LocalDateTime getDue() {
        return due;
    }

    public String getCandidateGroups() {
        return candidateGroups;
    }

    public LocalDate getTaskCreationDate() {
        return taskCreationDate;
    }

    public LocalDate getCustodyTimeLimit() {
        return custodyTimeLimit;
    }

    public String getCaseURN() {
        return caseURN;
    }

    public LocalDate getHearingDate() {
        return hearingDate;
    }

    public String getAssignee() {
        return assignee;
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

    public static final class WorkflowOpaTaskTypeBuilder {
        private String opaId;
        private LocalDateTime due;
        private String candidateGroups;
        private LocalDate taskCreationDate;
        private LocalDate custodyTimeLimit;
        private String caseURN;
        private LocalDate hearingDate;
        private String assignee;
        private String courtCodes;
        private String organisationId;
        private List<TaskDefendant> defendants;
        private String comment;
        private UUID id;
        private Integer seqNum;
        private String taskName;
        private String displayName;
        private String taskGroup;
        private Boolean isDeletable;
        private Boolean isDeferrable;
        private Integer duration;
        private Integer followUpInterval;
        private LocalDate validFrom;
        private LocalDate validTo;

        private WorkflowOpaTaskTypeBuilder() {
        }

        public static WorkflowOpaTaskTypeBuilder aWorkflowOpaTaskType() {
            return new WorkflowOpaTaskTypeBuilder();
        }

        public WorkflowOpaTaskTypeBuilder withOpaId(String opaId) {
            this.opaId = opaId;
            return this;
        }

        public WorkflowOpaTaskTypeBuilder withDue(LocalDateTime due) {
            this.due = due;
            return this;
        }

        public WorkflowOpaTaskTypeBuilder withCandidateGroups(String candidateGroups) {
            this.candidateGroups = candidateGroups;
            return this;
        }

        public WorkflowOpaTaskTypeBuilder withTaskCreationDate(LocalDate taskCreationDate) {
            this.taskCreationDate = taskCreationDate;
            return this;
        }

        public WorkflowOpaTaskTypeBuilder withCustodyTimeLimit(LocalDate custodyTimeLimit) {
            this.custodyTimeLimit = custodyTimeLimit;
            return this;
        }

        public WorkflowOpaTaskTypeBuilder withCaseURN(String caseURN) {
            this.caseURN = caseURN;
            return this;
        }

        public WorkflowOpaTaskTypeBuilder withHearingDate(LocalDate hearingDate) {
            this.hearingDate = hearingDate;
            return this;
        }

        public WorkflowOpaTaskTypeBuilder withAssignee(String assignee) {
            this.assignee = assignee;
            return this;
        }

        public WorkflowOpaTaskTypeBuilder withCourtCodes(String courtCodes) {
            this.courtCodes = courtCodes;
            return this;
        }

        public WorkflowOpaTaskTypeBuilder withOrganisationId(String organisationId) {
            this.organisationId = organisationId;
            return this;
        }

        public WorkflowOpaTaskTypeBuilder withDefendants(List<TaskDefendant> defendants) {
            this.defendants = defendants;
            return this;
        }

        public WorkflowOpaTaskTypeBuilder withComment(String comment) {
            this.comment = comment;
            return this;
        }

        public WorkflowOpaTaskTypeBuilder withId(UUID id) {
            this.id = id;
            return this;
        }

        public WorkflowOpaTaskTypeBuilder withSeqNum(Integer seqNum) {
            this.seqNum = seqNum;
            return this;
        }

        public WorkflowOpaTaskTypeBuilder withTaskName(String taskName) {
            this.taskName = taskName;
            return this;
        }

        public WorkflowOpaTaskTypeBuilder withDisplayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public WorkflowOpaTaskTypeBuilder withTaskGroup(String taskGroup) {
            this.taskGroup = taskGroup;
            return this;
        }

        public WorkflowOpaTaskTypeBuilder withIsDeletable(Boolean isDeletable) {
            this.isDeletable = isDeletable;
            return this;
        }

        public WorkflowOpaTaskTypeBuilder withIsDeferrable(Boolean isDeferrable) {
            this.isDeferrable = isDeferrable;
            return this;
        }

        public WorkflowOpaTaskTypeBuilder withDuration(Integer duration) {
            this.duration = duration;
            return this;
        }

        public WorkflowOpaTaskTypeBuilder withFollowUpInterval(Integer followUpInterval) {
            this.followUpInterval = followUpInterval;
            return this;
        }

        public WorkflowOpaTaskTypeBuilder withValidFrom(LocalDate validFrom) {
            this.validFrom = validFrom;
            return this;
        }

        public WorkflowOpaTaskTypeBuilder withValidTo(LocalDate validTo) {
            this.validTo = validTo;
            return this;
        }

        public WorkflowOpaTaskType build() {
            final WorkflowOpaTaskType workflowOpaTaskType = new WorkflowOpaTaskType();
            workflowOpaTaskType.caseURN = this.caseURN;
            workflowOpaTaskType.validTo = this.validTo;
            workflowOpaTaskType.comment = this.comment;
            workflowOpaTaskType.taskName = this.taskName;
            workflowOpaTaskType.duration = this.duration;
            workflowOpaTaskType.displayName = this.displayName;
            workflowOpaTaskType.candidateGroups = this.candidateGroups;
            workflowOpaTaskType.custodyTimeLimit = this.custodyTimeLimit;
            workflowOpaTaskType.isDeletable = this.isDeletable;
            workflowOpaTaskType.taskCreationDate = this.taskCreationDate;
            workflowOpaTaskType.courtCodes = this.courtCodes;
            workflowOpaTaskType.opaId = this.opaId;
            workflowOpaTaskType.assignee = this.assignee;
            workflowOpaTaskType.id = this.id;
            workflowOpaTaskType.taskGroup = this.taskGroup;
            workflowOpaTaskType.validFrom = this.validFrom;
            workflowOpaTaskType.followUpInterval = this.followUpInterval;
            workflowOpaTaskType.seqNum = this.seqNum;
            workflowOpaTaskType.organisationId = this.organisationId;
            workflowOpaTaskType.due = this.due;
            workflowOpaTaskType.defendants = this.defendants;
            workflowOpaTaskType.hearingDate = this.hearingDate;
            workflowOpaTaskType.isDeferrable = this.isDeferrable;
            return workflowOpaTaskType;
        }
    }
}