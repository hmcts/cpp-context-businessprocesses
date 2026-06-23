package uk.gov.moj.cpp.businessprocesses.persistence.repository;

import uk.gov.moj.cpp.businessprocesses.persistence.entity.TaskHistoryEntity;

import java.util.List;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@ApplicationScoped
public class TaskHistoryRepository {

    @PersistenceContext(unitName = "businessprocesses-persistence-unit")
    EntityManager entityManager;

    public TaskHistoryEntity save(final TaskHistoryEntity taskHistoryEntity) {
        return entityManager.merge(taskHistoryEntity);
    }

    public List<TaskHistoryEntity> findById(final UUID id) {
        return entityManager.createQuery(
                        "SELECT th FROM TaskHistoryEntity th WHERE th.id = :id", TaskHistoryEntity.class)
                .setParameter("id", id)
                .getResultList();
    }

    public List<TaskHistoryEntity> findByTaskId(final UUID taskId) {
        return entityManager.createQuery(
                        "SELECT th FROM TaskHistoryEntity th WHERE th.task.taskId = :taskId ORDER BY th.eventDate",
                        TaskHistoryEntity.class)
                .setParameter("taskId", taskId)
                .getResultList();
    }
}
