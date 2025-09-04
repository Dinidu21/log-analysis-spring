package com.dinidu.loglens.repository;

import com.dinidu.loglens.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByProviderId(String providerId);

    Optional<User> findByEmailAndProvider(String email, User.Provider provider);

    boolean existsByEmail(String email);

    boolean existsByProviderId(String providerId);
}