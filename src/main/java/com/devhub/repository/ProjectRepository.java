package com.devhub.repository;

import com.devhub.project.Project;

import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface ProjectRepository extends MongoRepository<Project, String> {
    List<Project> findByOrganizationId(String organizationId);
    List<Project> findByOrganizationIdAndProductManagerId(String organizationId, String productManagerId);
    List<Project> findByOrganizationIdAndSeniorManagerIdsContaining(String organizationId, String seniorManagerId);
}
