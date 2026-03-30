package com.docstar.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.docstar.model.Client;

@Repository
public interface ClientRepository extends JpaRepository<Client, Long> {
    Optional<Client> findByUsername(String username);

    List<Client> findByIdNotIn(List<Long> ids);

    List<Client> findByIdIn(List<Long> ids);

    List<Client> findByIdNot(Long id);

    @Query("""
            SELECT COUNT(*) FROM Client
            """)
    int getUserCount();

    Optional<Client> findByEmail(String email);
}
