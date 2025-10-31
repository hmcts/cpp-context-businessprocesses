package uk.gov.moj.cpp.businessprocesses.persistence.entity;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.UUID;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

@Entity
@Table(name = "task_history")
public class TaskHistoryEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "id", unique = true, nullable = false)
    private UUID id;

    @Column(name = "event_date", nullable = false)
    private ZonedDateTime eventDate;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "change_author", nullable = false)
    private String changeAuthor;

    @Column(name = "details")
    private String details;

    @ManyToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "task_id")
    private TaskEntity task;

    public UUID getId() {
        return id;
    }

    public void setId(final UUID id) {
        this.id = id;
    }

    public ZonedDateTime getEventDate() {
        return eventDate;
    }

    public void setEventDate(final ZonedDateTime eventDate) {
        this.eventDate = eventDate;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(final String eventType) {
        this.eventType = eventType;
    }

    public String getChangeAuthor() {
        return changeAuthor;
    }

    public void setChangeAuthor(final String changeAuthor) {
        this.changeAuthor = changeAuthor;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(final String details) {
        this.details = details;
    }

    public TaskEntity getTask() {
        return task;
    }

    public void setTask(final TaskEntity task) {
        this.task = task;
    }
}