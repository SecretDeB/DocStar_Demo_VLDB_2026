package com.docstar.service;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.stereotype.Service;

import com.docstar.middleware.SearchingClientParallel;
import com.docstar.model.Client;
import com.docstar.model.dto.DocumentDTO;
import com.docstar.repository.ClientRepository;

import jakarta.annotation.PostConstruct;

@Service
public class DocumentService {

    @Autowired
    ClientRepository cr;

    @Autowired
    DBOCache dboCache;

    private static boolean initialSearch = true;

    // Pool of clients that STAY CONNECTED
    private final List<SearchingClientParallel> clientPool = new ArrayList<>();
    private final int POOL_SIZE = 10;
    private final AtomicInteger roundRobin = new AtomicInteger(0);

    private Map<String, SearchingClientParallel> clientMap = new LinkedHashMap<>();

    @PostConstruct
    public void initializeClientPool() {
        for (int i = 0; i < POOL_SIZE; i++) {
            try {
                SearchingClientParallel client = new SearchingClientParallel();

                client.initialization();

                clientPool.add(client);
                System.out.println("Client " + (i + 1) + " connected and ready");

            } catch (Exception e) {
                e.printStackTrace();
                System.err.println("Failed to initialize client " + i);
            }
        }
    }

    public Map<String, Object> checkAccessAndGetFileIDs(String userId, String keyword) {

        Object result = null;
        System.out.println("UserID" + userId);
        System.out.println("Keyword:" + keyword);
        Map<String, Object> responseBody = new LinkedHashMap<>();
        String sessionId = UUID.randomUUID().toString();
        clientMap.put(sessionId, clientPool.get(roundRobin.getAndUpdate(i -> (i + 1) % POOL_SIZE)));
        SearchingClientParallel client = clientMap.get(sessionId);
        client.setSessionId(sessionId);

        try {

            result = client.runSearch(keyword, userId, dboCache.getVerification(), initialSearch);

            List<Long> fileIDs = (ArrayList<Long>) result;

            Map<Long, DocumentDTO> documents = new LinkedHashMap<>();

            for (int i = 0; i < fileIDs.size(); i++) {
                Long fileID = fileIDs.get(i);
                documents
                        .put(fileID,
                                new DocumentDTO(fileID, "Doc_" + fileID + ".txt", false, null, false, null));
            }

            responseBody.put("documents", documents);
            responseBody.put("sessionID", sessionId);

            return responseBody;
        } catch (IOException | NoSuchAlgorithmException | ClassNotFoundException | InstantiationException
                | IllegalAccessException | InterruptedException e) {
            e.printStackTrace();
            responseBody.put("ERROR", e.getMessage());
            return responseBody;
        }

    }

    public String getFile(String sessionId, Long fileID) {

        String result = null;

        try {
            SearchingClientParallel client = clientMap.get(sessionId);
            client.cleanUpPhaseData(2);
            client.setFetchCount(1);
            client.cleanUpPhaseData(3);
            client.setPhase2Result(new ArrayList<Long>(List.of(fileID)));
            result = client.phase3();
            client.cleanUpPhaseData(3);
            System.out.println("Completed fetching.");
        } catch (IOException | NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return result;
    }
}
