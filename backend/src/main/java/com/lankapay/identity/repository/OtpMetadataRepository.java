package com.lankapay.identity.repository;

import com.lankapay.identity.domain.OtpMetadata;
import com.lankapay.identity.domain.OtpPurpose;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OtpMetadataRepository extends JpaRepository<OtpMetadata, Long> {

    Optional<OtpMetadata> findByUserIdAndPurpose(Long userId, OtpPurpose purpose);
}
