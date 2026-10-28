package com.devhub.repository;

import com.devhub.task.Task;

import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface TaskRepository extends MongoRepository<Task, String> {
    List<Task> findByProjectId(String projectId);
    List<Task> findByAssignedDeveloperId(String developerId);
}
