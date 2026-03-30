package com.docstar.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.docstar.model.Admin;

import java.util.Optional;

public interface AdminRepository extends JpaRepository<Admin, Long> {
    Optional<Admin> findByUsername(String username);

    Optional<Admin> findByEmail(String email);
}
