package com.docstar.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.docstar.model.Keyword;

@Repository
public interface KeywordRepository extends JpaRepository<Keyword, String> {

}