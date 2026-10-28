package com.devhub.repository;

import com.devhub.auth.RegistrationChallenge;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface RegistrationChallengeRepository extends MongoRepository<RegistrationChallenge, String> { }
