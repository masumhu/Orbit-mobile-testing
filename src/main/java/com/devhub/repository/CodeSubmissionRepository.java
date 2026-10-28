package com.devhub.repository;

import com.devhub.submission.CodeSubmission;

import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface CodeSubmissionRepository extends MongoRepository<CodeSubmission, String> {
    List<CodeSubmission> findByProjectId(String projectId);
    List<CodeSubmission> findByOrganizationId(String organizationId);
}
