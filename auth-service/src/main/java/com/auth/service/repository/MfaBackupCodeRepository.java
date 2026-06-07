package com.auth.service.repository;

import com.auth.service.entity.MfaBackupCode;
import com.auth.service.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface MfaBackupCodeRepository extends JpaRepository<MfaBackupCode, Long> {
    List<MfaBackupCode> findByUserAndUsedFalse(User user);

    @Modifying
    @Query("DELETE FROM MfaBackupCode c WHERE c.user = :user")
    void deleteAllByUser(User user);
}
