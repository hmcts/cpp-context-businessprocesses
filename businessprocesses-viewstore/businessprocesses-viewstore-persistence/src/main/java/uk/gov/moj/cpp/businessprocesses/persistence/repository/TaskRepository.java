package uk.gov.moj.cpp.businessprocesses.persistence.repository;

import uk.gov.moj.cpp.businessprocesses.persistence.entity.TaskEntity;

import java.util.UUID;

import org.apache.deltaspike.data.api.EntityRepository;
import org.apache.deltaspike.data.api.Repository;

@Repository
public interface TaskRepository extends EntityRepository<TaskEntity, UUID> {
}