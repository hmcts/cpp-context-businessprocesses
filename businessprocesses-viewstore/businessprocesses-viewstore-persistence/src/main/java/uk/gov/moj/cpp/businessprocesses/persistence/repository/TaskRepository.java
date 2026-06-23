package uk.gov.moj.cpp.businessprocesses.persistence.repository;

import uk.gov.moj.cpp.businessprocesses.persistence.entity.TaskEntity;

import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@ApplicationScoped
public class TaskRepository {

    @PersistenceContext(unitName = "businessprocesses-persistence-unit")
    EntityManager entityManager;

    public TaskEntity findBy(final UUID id) {
        return entityManager.find(TaskEntity.class, id);
    }

    public TaskEntity save(final TaskEntity taskEntity) {
        return entityManager.merge(taskEntity);
    }
}
