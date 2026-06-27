package com.example.knowledge.repository;

import com.example.knowledge.entity.SyncStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 同步状态 Repository
 */
@Repository
public interface SyncStatusRepository extends JpaRepository<SyncStatus, Long> {

    /**
     * 根据知识源名称查找同步状态
     */
    Optional<SyncStatus> findBySourceName(String sourceName);
}
