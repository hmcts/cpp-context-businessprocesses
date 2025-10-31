package uk.gov.moj.cpp.businessprocesses.persistence.entity;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.Table;

@Entity
@Table(name = "task")
public class TaskEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "task_id", unique = true, nullable = false)
    private UUID taskId;

    @Column(name = "reference")
    private String reference;

    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "created_date", nullable = false)
    private ZonedDateTime createdDate;

    @Column(name = "due_date")
    private ZonedDateTime dueDate;

    @Column(name = "completed_date")
    private ZonedDateTime completedDate;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "workqueue")
    private UUID workqueue;

    @Column(name = "court_id")
    private UUID courtId;

    @Column(name = "jurisdiction")
    private String jurisdiction;

    @Column(name = "hearing_date")
    private ZonedDateTime hearingDate;

    @Column(name = "task_type_id")
    private UUID taskTypeId;

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER, mappedBy = "task", orphanRemoval = true)
    private List<TaskHistoryEntity> taskHistory = new ArrayList<>();

    public UUID getTaskId() {
        return taskId;
    }

    public void setTaskId(final UUID taskId) {
        this.taskId = taskId;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(final String reference) {
        this.reference = reference;
    }

    public String getType() {
        return type;
    }

    public void setType(final String type) {
        this.type = type;
    }

    public ZonedDateTime getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(final ZonedDateTime createdDate) {
        this.createdDate = createdDate;
    }

    public ZonedDateTime getDueDate() {
        return dueDate;
    }

    public void setDueDate(final ZonedDateTime dueDate) {
        this.dueDate = dueDate;
    }

    public ZonedDateTime getCompletedDate() {
        return completedDate;
    }

    public void setCompletedDate(final ZonedDateTime completedDate) {
        this.completedDate = completedDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(final String status) {
        this.status = status;
    }

    public UUID getWorkqueue() {
        return workqueue;
    }

    public void setWorkqueue(final UUID workqueue) {
        this.workqueue = workqueue;
    }

    public UUID getCourtId() {
        return courtId;
    }

    public void setCourtId(final UUID courtId) {
        this.courtId = courtId;
    }

    public String getJurisdiction() {
        return jurisdiction;
    }

    public void setJurisdiction(final String jurisdiction) {
        this.jurisdiction = jurisdiction;
    }

    public ZonedDateTime getHearingDate() {
        return hearingDate;
    }

    public void setHearingDate(final ZonedDateTime hearingDate) {
        this.hearingDate = hearingDate;
    }

    public List<TaskHistoryEntity> getTaskHistory() {
        return taskHistory;
    }

    public void setTaskHistory(final List<TaskHistoryEntity> taskHistory) {
        this.taskHistory = taskHistory;
    }

    public UUID getTaskTypeId() {
        return taskTypeId;
    }

    public void setTaskTypeId(final UUID taskTypeId) {
        this.taskTypeId = taskTypeId;
    }
}
