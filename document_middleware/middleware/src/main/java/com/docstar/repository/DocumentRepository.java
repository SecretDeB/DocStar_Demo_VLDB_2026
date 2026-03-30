package com.docstar.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.docstar.model.Document;

import jakarta.transaction.Transactional;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {

    @Modifying
    @Transactional
    @Query("DELETE FROM Document")
    void deleteAllDocuments();
}