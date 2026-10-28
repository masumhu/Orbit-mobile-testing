package com.devhub.repository;

import com.devhub.organization.Organization;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface OrganizationRepository extends MongoRepository<Organization, String> { }
