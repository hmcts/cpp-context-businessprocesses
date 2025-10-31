package uk.gov.moj.cpp.businessprocesses.pojo;

import java.util.UUID;

import java.util.UUID;

public class StartApplicationWorkflowRequest {
    private String processKey;
    private String caseURN;
    private String hearingType;
    private String hearingId;
    private String location;
    private UUID caseId;
    private String taskName;
    private String hearingDate;
    private String courtCodes;

    private String custodyTimeLimit;

    private StartApplicationWorkflowRequest() {}

    public static class Builder {
        private String processKey;
        private String caseURN;
        private String hearingType;
        private String hearingId;
        private String location;
        private UUID caseId;
        private String taskName;
        private String hearingDate;
        private String courtCodes;

        private String custodyTimeLimit;

        public Builder withProcessKey(String processKey) {
            this.processKey = processKey;
            return this;
        }

        public Builder withCaseURN(String caseURN) {
            this.caseURN = caseURN;
            return this;
        }

        public Builder withHearingType(String hearingType) {
            this.hearingType = hearingType;
            return this;
        }

        public Builder withHearingId(String hearingId) {
            this.hearingId = hearingId;
            return this;
        }

        public Builder withLocation(String location) {
            this.location = location;
            return this;
        }

        public Builder withCaseId(UUID caseId) {
            this.caseId = caseId;
            return this;
        }

        public Builder withTaskName(String taskName) {
            this.taskName = taskName;
            return this;
        }

        public Builder withHearingDate(String hearingDate) {
            this.hearingDate = hearingDate;
            return this;
        }

        public Builder withCourtCodes(String courtCodes) {
            this.courtCodes = courtCodes;
            return this;
        }

        public Builder withCustodyTimeLimit(String custodyTimeLimit) {
            this.custodyTimeLimit = custodyTimeLimit;
            return this;
        }

        public StartApplicationWorkflowRequest build() {
            StartApplicationWorkflowRequest request = new StartApplicationWorkflowRequest();
            request.processKey = this.processKey;
            request.caseURN = this.caseURN;
            request.hearingType = this.hearingType;
            request.hearingId = this.hearingId;
            request.location = this.location;
            request.caseId = this.caseId;
            request.taskName = this.taskName;
            request.hearingDate = this.hearingDate;
            request.courtCodes = this.courtCodes;
            request.custodyTimeLimit = this.custodyTimeLimit;
            return request;
        }
    }

    public String getProcessKey() {
        return processKey;
    }

    public String getCaseURN() {
        return caseURN;
    }
    public String getHearingType() {
        return hearingType;
    }

    public String getHearingId() {
        return hearingId;
    }
    public String getLocation() {
        return location;
    }
    public UUID getCaseId() {
        return caseId;
    }
    public String getTaskName() {
        return taskName;
    }
    public String getHearingDate() {
        return hearingDate;
    }

    public String getCourtCodes() {
        return courtCodes;
    }

    public String getCustodyTimeLimit() {
        return custodyTimeLimit;
    }

}

