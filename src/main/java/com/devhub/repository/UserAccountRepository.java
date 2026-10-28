package com.devhub.repository;

import com.devhub.auth.RegistrationMethod;
import com.devhub.auth.RegistrationRole;
import com.devhub.auth.UserAccount;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface UserAccountRepository extends MongoRepository<UserAccount, String> {
    boolean existsByEmail(String email);
    boolean existsByUsername(String username);
    java.util.Optional<UserAccount> findByEmail(String email);
    java.util.List<UserAccount> findByUsernameIn(java.util.Collection<String> usernames);
    java.util.List<UserAccount> findByOrganizationId(String organizationId);
    boolean existsByOrganizationIdAndRole(String organizationId, RegistrationRole role);
    Optional<UserAccount> findByEmailOrUsername(String email, String username);
    Optional<UserAccount> findByRegistrationMethodAndProviderSubject(RegistrationMethod method, String providerSubject);
}
