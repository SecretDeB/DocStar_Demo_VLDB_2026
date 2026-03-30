package com.docstar.service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.Arrays;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.docstar.middleware.DBO;
import com.docstar.middleware.DBOParallel;
import com.docstar.model.Client;
import com.docstar.model.Document;
import com.docstar.model.dto.ClientAccessDTO;
import com.docstar.model.dto.FileUpload;
import com.docstar.repository.ClientRepository;
import com.docstar.repository.DocumentRepository;
import com.docstar.utility.ExtractKeywordsUtil;

@Service
public class DBOService {

    @Autowired
    ClientRepository clientRepository;

    @Autowired
    DocumentRepository documentRepository;

    @Autowired
    DBOCache dboCache;

    @Autowired
    DBOParallel dbo;

    public Map<Long, ClientAccessDTO> getAllClients() {
        List<Client> allClients = clientRepository.findAll();

        Map<String, Set<String>> allKeywords = dboCache.getClientAccessMap();
        // Batch fetch all keywords at once if possible
        // OR handle errors gracefully per client
        Map<Long, ClientAccessDTO> clients = new LinkedHashMap<>();

        for (Client client : allClients) {
            try {

                ClientAccessDTO dto = new ClientAccessDTO(
                        client,
                        allKeywords.getOrDefault(client.getId().toString(), new HashSet<>()) // Default to empty set
                );

                clients.put(client.getId(), dto);

            } catch (Exception e) {
                System.err.println("Failed to fetch keywords for client " + client.getId() + ": " + e.getMessage());

                // Include client with empty keywords rather than failing entirely
                ClientAccessDTO dto = new ClientAccessDTO(
                        client,
                        new HashSet<>());

                clients.put(client.getId(), dto);
            }
        }

        return clients;
    }

    public Map<Long, ClientAccessDTO> getAllClientsAccess() {
        List<Client> allClients = clientRepository.findAll();

        Map<String, Set<String>> allKeywords = dboCache.getClientAccessMap();
        // Batch fetch all keywords at once if possible
        // OR handle errors gracefully per client
        Map<Long, ClientAccessDTO> clients = new LinkedHashMap<>();

        for (Client client : allClients) {
            try {

                ClientAccessDTO dto = new ClientAccessDTO(
                        new Client(client.getId(), client.getUsername(), client.getName(), client.getEmail()),
                        allKeywords.getOrDefault(client.getId().toString(), new HashSet<>()) // Default to empty set
                );

                clients.put(client.getId(), dto);

            } catch (Exception e) {
                System.err.println("Failed to fetch keywords for client " + client.getId() + ": " + e.getMessage());

                // Include client with empty keywords rather than failing entirely
                ClientAccessDTO dto = new ClientAccessDTO(
                        client,
                        new HashSet<>());

                clients.put(client.getId(), dto);
            }
        }

        return clients;
    }

    public String updateClient(Client client) throws Exception {
        // 1. Update client details in database
        Client existingClient = clientRepository.findById(client.getId())
                .orElseThrow(() -> new Exception("Client not found"));

        existingClient.setName(client.getName());
        existingClient.setEmail(client.getEmail());
        existingClient.setUsername(client.getUsername());
        existingClient.setPhone(client.getPhone());
        existingClient.setAddress(client.getAddress());

        clientRepository.save(existingClient);
        return "SUCCESS";
    }

    public LinkedHashMap<String, Integer> getAllKeywords() {
        LinkedHashMap<String, Integer> keywords = dbo.getAllKeywords();
        return keywords;
    }

    public String grantAccess(Long clientId, List<String> keywords) {
        try {
            dbo.revokeGrantAccessBatch(clientId.toString(), keywords, true);
            dboCache.grantAccess(clientId, keywords);
        } catch (Exception e) {
            e.printStackTrace();
            return "ERROR";
        }
        return "SUCCESS";
    }

    public String grantAccessSingle(Long clientId, String keyword) {
        String result = "";
        try {
            result = dbo.revokeGrantAccess(clientId.toString(), keyword, true);
            if (result.equals("ACCESS_GRANTED")) {
                dboCache.grantAccess(clientId, keyword);
            }
        } catch (

        Exception e) {
            e.printStackTrace();
            return "ERROR";
        }
        return result;
    }

    public String revokeAccess(Long clientId, List<String> keywords) {
        try {
            dbo.revokeGrantAccessBatch(clientId.toString(), keywords, false);
            dboCache.revokeAccess(clientId, keywords);
        } catch (Exception e) {
            System.out.println(
                    "Error occured while revoking access for Client " + clientId + " on keywords " + keywords);
            e.printStackTrace();
            return "ERROR";
        }
        return "SUCCESS";
    }

    public String revokeAccessSingle(Long clientId, String keyword) {
        String result = "";
        try {
            result = dbo.revokeGrantAccess(clientId.toString(), keyword, false);
            if (result.equals("ACCESS_REVOKED")) {
                dboCache.revokeAccess(clientId, keyword);
            }
        } catch (Exception e) {
            System.out.println(
                    "Error occured while revoking access for Client " + clientId + " on keyword " + keyword);
            e.printStackTrace();
            return "ERROR";
        }
        return result;
    }

    public Boolean deleteKeyword(String keyword) {
        try {
            dbo.deleteKeywordOpt(keyword);
            System.out.println(dboCache.getKeywords());
            dboCache.removeKeyword(keyword);
            System.out.println(dboCache.getKeywords());
            return true;
        } catch (Exception e) {
            System.out.println(
                    "Error occured while deleting keyword " + keyword);
            e.printStackTrace();
            return false;
        }
    }

    public Boolean addFileToKeyword(String keyword, String fileId) {
        try {
            dbo.addFilesOpt(keyword, fileId);
            return true;
        } catch (Exception e) {
            System.out.println(
                    "Error occured while deleting keyword " + keyword);
            e.printStackTrace();
            return false;
        }
    }

    public Boolean deleteFileFromKeyword(String keyword, long fileId) {
        try {
            dbo.deleteFileOpt(keyword, fileId);
            return true;
        } catch (Exception e) {
            System.out.println(
                    "Error occured while deleting keyword " + keyword);
            e.printStackTrace();
            return false;
        }
    }

    public Map<Long, Document> getAllFiles() {
        try {
            int fileCount = dbo.getFileCount();
            List<Document> files = documentRepository.findAll();
            if (files.size() != fileCount) {
                System.out.println("Warning: File count mismatch! DBO reports " + fileCount + " but repository has "
                        + files.size());
            }
            Map<Long, Document> fileMap = new LinkedHashMap<>();
            for (Document file : files) {
                fileMap.put(file.getId(), file);
            }
            return fileMap;
        } catch (Exception e) {
            e.printStackTrace();
            return Collections.emptyMap();
        }
    }

    public Map<String, Object> getFile(int fileId) {
        try {
            String fileContent = dbo.getFile(fileId);

            // Stemmer for keyword extraction
            Map<String, Integer> keywords = ExtractKeywordsUtil.extractKeywordsWithCountFromContent(fileContent);
            return Map.of("content", fileContent, "keywords", keywords);
        } catch (Exception e) {
            e.printStackTrace();
            return Map.of("content", "", "keywords", Collections.emptySet());
        }
    }

    public String addFile(String fileContent, List<String> keywords) {
        try {
            String[] keywordArray = keywords.toArray(new String[0]);
            dbo.addFile(fileContent, keywordArray);

            // Refresh cache after adding file
            // (Keywords might have been added)

            return "SUCCESS";
        } catch (Exception e) {
            e.printStackTrace();
            return "FAILED: " + e.getMessage();
        }
    }

    public boolean addNewKeyword(String keyword) {
        try {
            dbo.addNewKeyword(keyword);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

    }

    public Map<String, Integer> getStats() {
        try {
            return dbo.getStats();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private Map<String, TempFileData> tempStorage = new ConcurrentHashMap<>();

    /**
     * Extract keywords from all files and return grouped list with occurrence
     * counts
     */
    public Map<String, Object> extractKeywordsFromFiles(MultipartFile[] files) throws Exception {
        System.out.println("\n=== EXTRACTING KEYWORDS FROM " + files.length + " FILES ===");
        long startTime = System.currentTimeMillis();

        // int minimumThreshold = 5;
        Set<String> allKeywords = ConcurrentHashMap.newKeySet();
        List<Map<String, Object>> fileDataList = Collections.synchronizedList(new ArrayList<>());
        Map<String, Integer> keywordOccurrences = new ConcurrentHashMap<>();

        Arrays.stream(files).parallel().forEach(file -> {
            try {
                String fileId = UUID.randomUUID().toString();
                // Reading bytes might be I/O bound, but doing it in parallel helps
                String content = new String(file.getBytes(), StandardCharsets.UTF_8);
                String filename = file.getOriginalFilename();

                // CPU Intensive Step: Extraction
                Set<String> fileKeywords = ExtractKeywordsUtil.extractKeywordsFromContent(content);

                // Atomic Updates to Shared Maps
                fileKeywords.forEach(keyword -> keywordOccurrences.merge(keyword, 1, Integer::sum) // Thread-safe
                                                                                                   // increment
                );

                allKeywords.addAll(fileKeywords);

                // Store file data
                TempFileData tempData = new TempFileData(fileId, filename, content, fileKeywords);
                tempStorage.put(fileId, tempData);

                // Add to response list
                fileDataList.add(Map.of(
                        "fileId", fileId,
                        "filename", filename != null ? filename : "unknown",
                        "keywordCount", fileKeywords.size()));

            } catch (Exception e) {
                e.printStackTrace();
                System.err.println("Error processing file " + file.getOriginalFilename() + ": " + e.getMessage());
                // Depending on requirements, you might want to throw a RuntimeException here to
                // abort
            }
        });

        // 3. Sorting & Aggregation (Single Threaded - fast enough)
        // Convert to sorted LinkedHashMap for the UI
        Map<String, Integer> keywordStats = keywordOccurrences.entrySet()
                .stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()) // Sort by count desc
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new));

        long duration = System.currentTimeMillis() - startTime;
        System.out.println("\n=== EXTRACTION COMPLETE in " + duration + "ms ===");
        System.out.println("Total unique keywords: " + allKeywords.size());

        return Map.of(
                "files", fileDataList,
                "keywords", keywordStats,
                "totalKeywords", allKeywords.size());
    }

    public Map<String, Object> calculateUploadStats(
            List<String> fileIds,
            List<String> selectedKeywords,
            Map<String, List<String>> clientKeywordsMap) {

        int validFileCount = 0;

        // 1. Calculate how many files will actually be indexed
        // A file is indexed ONLY if it contains at least one of the selected keywords
        for (String fileId : fileIds) {
            TempFileData fileData = tempStorage.get(fileId);

            if (fileData != null) {
                Set<String> fileKeywords = fileData.getKeywords();

                // Check if file has any intersection with selected keywords
                boolean hasMatch = selectedKeywords.stream()
                        .anyMatch(fileKeywords::contains);

                if (hasMatch) {
                    validFileCount++;
                }
            }
        }

        // 2. Calculate Permissions to be granted
        int permissionsCount = 0;
        for (List<String> keywords : clientKeywordsMap.values()) {
            if (keywords != null) {
                permissionsCount += keywords.size();
            }
        }

        // 3. Calculate Keywords to be indexed
        // This is simply the size of the final keyword list sent to the backend
        int keywordCount = selectedKeywords.size();

        Map<String, Object> stats = new HashMap<>();
        stats.put("validFileCount", validFileCount);
        stats.put("totalFileCount", fileIds.size());
        stats.put("skippedFileCount", fileIds.size() - validFileCount);
        stats.put("indexedKeywordCount", keywordCount);
        stats.put("permissionsCount", permissionsCount);

        return stats;
    }

    /**
     * Finalize upload with automatic permission grants
     */
    public Map<String, Object> finalizeUploadWithPermissions(
            List<String> fileIds,
            List<String> selectedKeywords,
            Map<String, List<String>> clientKeywordsMap) throws Exception {

        System.out.println("\n=== FINALIZING UPLOAD WITH PERMISSIONS ===");
        System.out.println("Files: " + fileIds.size());
        System.out.println("Selected keywords: " + selectedKeywords);
        System.out.println("Grant access to users: " + clientKeywordsMap.size());

        int successCount = 0;
        List<String> uploadedFiles = new ArrayList<>();

        Map<String, String[]> fileToKeywords = new HashMap<>();
        List<String> validFileIds = new ArrayList<>();

        // Step 1: Upload all files with their keywords
        for (String fileId : fileIds) {
            TempFileData fileData = tempStorage.get(fileId);

            if (fileData == null) {
                System.err.println("File not found: " + fileId);
                continue;
            }

            // Find intersection of file keywords and selected keywords
            Set<String> fileKeywords = fileData.getKeywords();
            List<String> intersection = selectedKeywords.stream()
                    .filter(fileKeywords::contains)
                    .collect(Collectors.toList());
            fileToKeywords.put(fileId, intersection.toArray(new String[0]));

            if (intersection.isEmpty()) {
                System.out.println("\n" + fileData.getFilename() + ":");
                System.out.println("  ⚠️ No keywords match the selected keywords - skipping file.");
                // Remove from temp storage now to clear memory
                tempStorage.remove(fileId);
                continue;
            }

            fileToKeywords.put(fileId, intersection.toArray(new String[0]));
            validFileIds.add(fileId);

            // System.out.println("\n" + fileData.getFilename() + ":");
            // System.out.println(" Indexing with: " + intersection);
        }

        List<FileUpload> files = new ArrayList<>();
        for (String fileId : validFileIds) {
            TempFileData fileData = tempStorage.get(fileId);
            FileUpload fu = new FileUpload();
            fu.setFilename(fileData.getFilename());
            fu.setContent(fileData.getContent());
            fu.setKeywords(fileToKeywords.get(fileId));
            files.add(fu);
            tempStorage.remove(fileId);
        }

        if (dbo.keywordCount == 0 && dbo.fileCount == 0) {
            System.out.println("\n=== INITIALIZING SYSTEM WITH FIRST UPLOAD ===");
            String result = dbo.bulkInitializeSystem(files);
            if (result.equals("SUCCESS")) {
                successCount = files.size();
                dboCache.addKeywords(selectedKeywords);
                dboCache.setDocumentCount(dboCache.getDocumentCount() + files.size());
            }

        } else {
            System.out.println("\n=== BULK UPLOADING FILES ===");
            String result = dbo.bulkUploadFiles(files, dboCache.getKeywords().keySet());
            if (result.equals("SUCCESS")) {
                successCount = files.size();
                dboCache.addKeywords(selectedKeywords);
                System.out.println("Current document count: " + dboCache.getDocumentCount());
                System.out.println("Adding " + files.size() + " documents.");
                dboCache.setDocumentCount(dboCache.getDocumentCount() + files.size());
            }
        }

        // Step 2: Grant permissions to selected users for selected keywords
        System.out.println("\n=== GRANTING PERMISSIONS ===");

        int permissionsGranted = 0;
        for (String userId : clientKeywordsMap.keySet()) {
            try {

                String grantResult = dbo.revokeGrantAccessBatch(userId, clientKeywordsMap.get(userId), true);

                if ("ACCESS_GRANTED".equals(grantResult)) {
                    permissionsGranted += clientKeywordsMap.get(userId).size();
                    for (String keyword : clientKeywordsMap.get(userId))
                        dboCache.grantAccess(Long.parseLong(userId), keyword);
                    System.out.println("  ✓ Granted");
                } else {
                    System.out.println("  ⚠️ " + grantResult);
                }

            } catch (Exception e) {
                System.err.println("  ✗ Error granting access: " + e.getMessage());
            }
        }

        // dboCache.init();

        System.out.println("\n=== UPLOAD COMPLETE ===");
        System.out.println("Files uploaded: " + successCount + "/" + fileIds.size());
        System.out.println("Permissions granted: " + permissionsGranted);

        return Map.of(
                "success", successCount,
                "total", fileIds.size(),
                "uploadedFiles", uploadedFiles,
                "permissionsGranted", permissionsGranted,
                "selectedKeywords", selectedKeywords);
    }

    public boolean setVerification(boolean verification) {
        dboCache.setVerification(verification);
        return true;
    }

    public void resetServers() {
        dbo.resetServers();
        try {
            dboCache.init();
            documentRepository.deleteAllDocuments();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}

class TempFileData {
    private String fileId;
    private String filename;
    private String content;
    private Set<String> keywords;

    public TempFileData(String fileId, String filename, String content, Set<String> keywords) {
        this.fileId = fileId;
        this.filename = filename;
        this.content = content;
        this.keywords = keywords;
    }

    // getters
    public String getFileId() {
        return fileId;
    }

    public String getFilename() {
        return filename;
    }

    public String getContent() {
        return content;
    }

    public Set<String> getKeywords() {
        return keywords;
    }
}
