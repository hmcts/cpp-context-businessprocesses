package uk.gov.moj.cpp.businessprocesses.pojo;


import uk.gov.moj.cpp.businessprocesses.defendant.TaskDefendant;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

@SuppressWarnings("squid:S1161")
public class WorkflowCOTRTaskType extends WorkflowTaskType {

    @JsonProperty("cotrId")
    private String cotrId;

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

    @JsonProperty("organisationId")
    private String organisationId;

    @JsonProperty("defendants")
    private List<TaskDefendant> defendants;

    @JsonProperty("comment")
    private String comment;

    public String getCotrId() {
        return cotrId;
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

    public String getOrganisationId() {
        return organisationId;
    }

    public List<TaskDefendant> getDefendants() {
        return defendants;
    }

    public String getComment() {
        return comment;
    }

    public static final class WorkflowCOTRTaskTypeBuilder {
        private String cotrId;
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

        private WorkflowCOTRTaskTypeBuilder() {
        }

        public static WorkflowCOTRTaskTypeBuilder aWorkflowCOTRTaskType() {
            return new WorkflowCOTRTaskTypeBuilder();
        }

        public WorkflowCOTRTaskTypeBuilder withCotrId(String cotrId) {
            this.cotrId = cotrId;
            return this;
        }

        public WorkflowCOTRTaskTypeBuilder withDue(LocalDateTime due) {
            this.due = due;
            return this;
        }

        public WorkflowCOTRTaskTypeBuilder withCandidateGroups(String candidateGroups) {
            this.candidateGroups = candidateGroups;
            return this;
        }

        public WorkflowCOTRTaskTypeBuilder withTaskCreationDate(LocalDate taskCreationDate) {
            this.taskCreationDate = taskCreationDate;
            return this;
        }

        public WorkflowCOTRTaskTypeBuilder withCustodyTimeLimit(LocalDate custodyTimeLimit) {
            this.custodyTimeLimit = custodyTimeLimit;
            return this;
        }

        public WorkflowCOTRTaskTypeBuilder withCaseURN(String caseURN) {
            this.caseURN = caseURN;
            return this;
        }

        public WorkflowCOTRTaskTypeBuilder withHearingDate(LocalDate hearingDate) {
            this.hearingDate = hearingDate;
            return this;
        }

        public WorkflowCOTRTaskTypeBuilder withAssignee(String assignee) {
            this.assignee = assignee;
            return this;
        }

        public WorkflowCOTRTaskTypeBuilder withCourtCodes(String courtCodes) {
            this.courtCodes = courtCodes;
            return this;
        }

        public WorkflowCOTRTaskTypeBuilder withOrganisationId(String organisationId) {
            this.organisationId = organisationId;
            return this;
        }

        public WorkflowCOTRTaskTypeBuilder withDefendants(List<TaskDefendant> defendants) {
            this.defendants = defendants;
            return this;
        }

        public WorkflowCOTRTaskTypeBuilder withComment(String comment) {
            this.comment = comment;
            return this;
        }

        public WorkflowCOTRTaskTypeBuilder withId(UUID id) {
            this.id = id;
            return this;
        }

        public WorkflowCOTRTaskTypeBuilder withSeqNum(Integer seqNum) {
            this.seqNum = seqNum;
            return this;
        }

        public WorkflowCOTRTaskTypeBuilder withTaskName(String taskName) {
            this.taskName = taskName;
            return this;
        }

        public WorkflowCOTRTaskTypeBuilder withDisplayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public WorkflowCOTRTaskTypeBuilder withTaskGroup(String taskGroup) {
            this.taskGroup = taskGroup;
            return this;
        }

        public WorkflowCOTRTaskTypeBuilder withIsDeletable(Boolean isDeletable) {
            this.isDeletable = isDeletable;
            return this;
        }

        public WorkflowCOTRTaskTypeBuilder withIsDeferrable(Boolean isDeferrable) {
            this.isDeferrable = isDeferrable;
            return this;
        }

        public WorkflowCOTRTaskTypeBuilder withDuration(Integer duration) {
            this.duration = duration;
            return this;
        }

        public WorkflowCOTRTaskTypeBuilder withFollowUpInterval(Integer followUpInterval) {
            this.followUpInterval = followUpInterval;
            return this;
        }

        public WorkflowCOTRTaskTypeBuilder withValidFrom(LocalDate validFrom) {
            this.validFrom = validFrom;
            return this;
        }

        public WorkflowCOTRTaskTypeBuilder withValidTo(LocalDate validTo) {
            this.validTo = validTo;
            return this;
        }

        public WorkflowCOTRTaskType build() {
            final WorkflowCOTRTaskType workflowCOTRTaskType = new WorkflowCOTRTaskType();
            workflowCOTRTaskType.caseURN = this.caseURN;
            workflowCOTRTaskType.validTo = this.validTo;
            workflowCOTRTaskType.comment = this.comment;
            workflowCOTRTaskType.taskName = this.taskName;
            workflowCOTRTaskType.duration = this.duration;
            workflowCOTRTaskType.displayName = this.displayName;
            workflowCOTRTaskType.candidateGroups = this.candidateGroups;
            workflowCOTRTaskType.custodyTimeLimit = this.custodyTimeLimit;
            workflowCOTRTaskType.isDeletable = this.isDeletable;
            workflowCOTRTaskType.taskCreationDate = this.taskCreationDate;
            workflowCOTRTaskType.courtCodes = this.courtCodes;
            workflowCOTRTaskType.cotrId = this.cotrId;
            workflowCOTRTaskType.assignee = this.assignee;
            workflowCOTRTaskType.id = this.id;
            workflowCOTRTaskType.taskGroup = this.taskGroup;
            workflowCOTRTaskType.validFrom = this.validFrom;
            workflowCOTRTaskType.followUpInterval = this.followUpInterval;
            workflowCOTRTaskType.seqNum = this.seqNum;
            workflowCOTRTaskType.organisationId = this.organisationId;
            workflowCOTRTaskType.due = this.due;
            workflowCOTRTaskType.defendants = this.defendants;
            workflowCOTRTaskType.hearingDate = this.hearingDate;
            workflowCOTRTaskType.isDeferrable = this.isDeferrable;
            return workflowCOTRTaskType;
        }
    }
}