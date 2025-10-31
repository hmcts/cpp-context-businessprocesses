package uk.gov.moj.cpp.businessprocesses.pojo;

import java.time.LocalDate;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class WorkQueue {
    @JsonProperty("id")
    protected UUID id;

    @JsonProperty("seqNum")
    protected Integer seqNum;

    @JsonProperty("validFrom")
    protected LocalDate validFrom;

    @JsonProperty("validTo")
    protected LocalDate validTo;

    @JsonProperty("queueName")
    protected String queueName;

    @JsonProperty("crownFlag")
    protected Boolean crownFlag;

    @JsonProperty("magistratesFlag")
    protected Boolean magistratesFlag;

    public UUID getId() {
        return id;
    }

    public Integer getSeqNum() {
        return seqNum;
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public LocalDate getValidTo() {
        return validTo;
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
