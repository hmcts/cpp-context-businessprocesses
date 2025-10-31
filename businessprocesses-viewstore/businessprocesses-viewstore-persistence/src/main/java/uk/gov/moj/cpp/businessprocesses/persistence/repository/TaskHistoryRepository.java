package uk.gov.moj.cpp.businessprocesses.persistence.repository;

import uk.gov.moj.cpp.businessprocesses.persistence.entity.TaskHistoryEntity;

import java.util.List;
import java.util.UUID;

import org.apache.deltaspike.data.api.EntityRepository;
import org.apache.deltaspike.data.api.Query;
import org.apache.deltaspike.data.api.QueryParam;
import org.apache.deltaspike.data.api.Repository;

@Repository
public interface TaskHistoryRepository extends EntityRepository<TaskHistoryEntity, UUID> {

    List<TaskHistoryEntity> findById(UUID id);

    @Query(value = "FROM TaskHistoryEntity th where th.task.taskId=:taskId order by eventDate")
    List<TaskHistoryEntity> findByTaskId(@QueryParam("taskId") UUID taskId);
}