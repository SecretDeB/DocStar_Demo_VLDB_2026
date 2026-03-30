package com.docstar.service;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.docstar.middleware.DBO;
import com.docstar.middleware.DBOParallel;
import com.docstar.repository.ClientRepository;
import com.docstar.repository.DocumentRepository;

import jakarta.annotation.PostConstruct;

@Service
public class DBOCache {

    @Autowired
    private DBOParallel dbo;

    @Autowired
    private ClientRepository cr;

    @Autowired
    DocumentRepository documentRepository;

    // 1. Thread-safe maps and sets
    private final Map<String, Set<String>> clientAccessMap = new ConcurrentHashMap<>();

    // If exact insertion order matters for your cryptography, use this instead:
    // private final Map<String, Integer> keywords = Collections.synchronizedMap(new
    // LinkedHashMap<>());
    private final Map<String, Integer> keywords = new ConcurrentHashMap<>();

    private int documentCount;
    private int clientCount;
    private int keywordCount;

    private int optCol;
    private int optRow;
    private boolean verification = false;

    public Map<String, Set<String>> getClientAccessMap() {
        return clientAccessMap;
    }

    public int getDocumentCount() {
        return documentCount;
    }

    public void setDocumentCount(int documentCount) {
        this.documentCount = documentCount;
    }

    public int getClientCount() {
        return clientCount;
    }

    public void setClientCount(int clientCount) {
        this.clientCount = clientCount;
    }

    public int getOptCol() {
        return optCol;
    }

    public void setOptCol(int optCol) {
        this.optCol = optCol;
    }

    public int getOptRow() {
        return optRow;
    }

    public void setOptRow(int optRow) {
        this.optRow = optRow;
    }

    public boolean getVerification() {
        return verification;
    }

    public void setVerification(boolean verification) {
        this.verification = verification;
    }

    public int getKeywordCount() {
        return keywordCount;
    }

    public void setKeywordCount(int keywordCount) {
        this.keywordCount = keywordCount;
    }

    public Map<String, Integer> getKeywords() {
        return keywords;
    }

    public void addKeyword(String keyword) {
        // Atomic thread-safe increment
        keywords.merge(keyword, 1, Integer::sum);
    }

    public void addKeywords(List<String> newKeywords) {
        for (String keyword : newKeywords) {
            addKeyword(keyword);
        }
    }

    public void removeKeyword(String keyword) {
        // Thread-safe iteration and null-safety
        clientAccessMap.values().forEach(accessSet -> {
            if (accessSet != null) {
                accessSet.remove(keyword);
            }
        });
        keywords.remove(keyword);
    }

    @PostConstruct
    public void init() throws IOException, ClassNotFoundException {
        System.out.println("Loading user access cache...");

        keywords.clear();

        // Populate keywords
        keywords.putAll(dbo.getAllKeywords());

        Map<String, Integer> stats = dbo.getStats();
        stats.forEach((stat, value) -> System.out.println(stat + ": " + value));

        documentCount = stats.getOrDefault("documents", 0);
        clientCount = stats.getOrDefault("clients", 0);

        if (documentCount == 0 && documentRepository.count() != 0) {
            documentRepository.deleteAllDocuments();
        }

        int dbClientCount = (int) cr.count();

        if (dbClientCount != clientCount) {
            System.out.println("Number of clients: " + clientCount);

            while (clientCount < dbClientCount) {
                try {
                    dbo.addNewClient();
                    clientCount++; // Thread-safe increment
                } catch (NoSuchAlgorithmException e) {
                    e.printStackTrace();
                }
            }
        }

        // Initialize client access map with thread-safe Sets!
        Map<String, Set<String>> rawAccessMap = dbo.getClientAccessMap(new ArrayList<>(keywords.keySet()));
        rawAccessMap.forEach((client, accessList) -> {
            // Convert standard sets to Concurrent Sets
            Set<String> concurrentSet = ConcurrentHashMap.newKeySet();
            concurrentSet.addAll(accessList);
            clientAccessMap.put(client, concurrentSet);
        });
    }

    public boolean hasAccess(String userId, String keyword) {
        Set<String> accessSet = clientAccessMap.get(userId);
        return accessSet != null && accessSet.contains(keyword);
    }

    public Set<String> getUserKeywords(String userId) {
        return clientAccessMap.getOrDefault(userId, Collections.emptySet());
    }

    public void grantAccess(Long clientId, String keyword) {
        clientAccessMap.computeIfAbsent(clientId.toString(), k -> ConcurrentHashMap.newKeySet()).add(keyword);
    }

    public void grantAccess(Long clientId, List<String> newKeywords) {
        clientAccessMap.computeIfAbsent(clientId.toString(), k -> ConcurrentHashMap.newKeySet()).addAll(newKeywords);
    }

    public void revokeAccess(Long clientId, String keyword) {
        Set<String> accessSet = clientAccessMap.get(clientId.toString());
        if (accessSet != null) {
            accessSet.remove(keyword);
        }
    }

    public void revokeAccess(Long clientId, List<String> keywordsToRemove) {
        Set<String> accessSet = clientAccessMap.get(clientId.toString());
        if (accessSet != null) {
            accessSet.removeAll(keywordsToRemove);
        }
    }

    public Map<String, Integer> getStats() {
        return Map.of(
                "documents", documentCount,
                "clients", clientCount,
                "keywords", keywords.size(),
                "verification", verification ? 1 : 0);
    }
}