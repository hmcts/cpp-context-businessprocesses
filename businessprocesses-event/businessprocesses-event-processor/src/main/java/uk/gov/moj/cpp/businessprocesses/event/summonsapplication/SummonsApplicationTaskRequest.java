package uk.gov.moj.cpp.businessprocesses.event.summonsapplication;

import java.util.UUID;

public class SummonsApplicationTaskRequest {
    private String processKey;
    private String taskName;
    private String applicationReference;
    private UUID applicationId;
    private String hearingId;
    private String hearingDate;
    private String courtName;
    private String courtCode;

    private SummonsApplicationTaskRequest() {}

    public static class Builder {
        private String processKey;
        private String taskName;
        private String applicationReference;
        private UUID applicationId;
        private String hearingId;
        private String hearingDate;
        private String courtName;
        private String courtCode;

        public SummonsApplicationTaskRequest.Builder withTaskName(String taskName) {
            this.taskName = taskName;
            return this;
        }

        public SummonsApplicationTaskRequest.Builder withApplicationReference(String applicationReference) {
            this.applicationReference = applicationReference;
            return this;
        }

        public SummonsApplicationTaskRequest.Builder withHearingId(String hearingId) {
            this.hearingId = hearingId;
            return this;
        }

        public SummonsApplicationTaskRequest.Builder withCourtName(String courtName) {
            this.courtName = courtName;
            return this;
        }

        public SummonsApplicationTaskRequest.Builder withApplicationId(UUID applicationId) {
            this.applicationId = applicationId;
            return this;
        }

        public SummonsApplicationTaskRequest.Builder withHearingDate(String hearingDate) {
            this.hearingDate = hearingDate;
            return this;
        }

        public SummonsApplicationTaskRequest.Builder withProcessKey(String processKey) {
            this.processKey = processKey;
            return this;
        }

        public SummonsApplicationTaskRequest.Builder withCourtCode(String courtCode) {
            this.courtCode = courtCode;
            return this;
        }

        public SummonsApplicationTaskRequest build() {
            SummonsApplicationTaskRequest request = new SummonsApplicationTaskRequest();
            request.processKey = this.processKey;
            request.taskName = this.taskName;
            request.applicationReference = this.applicationReference;
            request.applicationId = this.applicationId;
            request.hearingId = this.hearingId;
            request.hearingDate = this.hearingDate;
            request.courtName = this.courtName;
            request.courtCode = this.courtCode;
            return request;
        }
    }

    public String getTaskName() {
        return taskName;
    }

    public String getApplicationReference() {
        return applicationReference;
    }

    public String getHearingId() {
        return hearingId;
    }

    public String getCourtName() {
        return courtName;
    }

    public UUID getApplicationId() {
        return applicationId;
    }

    public String getHearingDate() {
        return hearingDate;
    }

    public String getProcessKey() {
        return  processKey;
    }

    public String getCourtCode() {
        return courtCode;
    }

}
