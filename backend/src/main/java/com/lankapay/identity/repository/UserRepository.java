package com.lankapay.identity.repository;

import com.lankapay.identity.domain.User;
import com.lankapay.identity.domain.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    Optional<User> findByPhone(String phone);

    Optional<User> findByNicNumber(String nicNumber);

    boolean existsByPhone(String phone);

    boolean existsByNicNumber(String nicNumber);

    /** Used by admin search – never return deleted users by default. */
    Optional<User> findByPhoneAndStatusNot(String phone, UserStatus status);
}
