package com.docstar.controller;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import com.docstar.model.Client;
import com.docstar.model.Document;
import com.docstar.model.dto.AccessChangeRequest;
import com.docstar.model.dto.ClientAccessDTO;
import com.docstar.model.dto.FileDeleteFromKeywordRequest;
import com.docstar.model.dto.UploadRequest;
import com.docstar.service.DBOCache;
import com.docstar.service.DBOService;

@Controller
@RequestMapping("/dbo")
public class DBOController {

    @Autowired
    private DBOService dboService;

    @Autowired
    private DBOCache dboCache;

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Integer>> getStats() {
        return ResponseEntity.ok(dboCache.getStats());
    }

    @GetMapping("/clients")
    public ResponseEntity<Map<Long, ClientAccessDTO>> getAllClients() {
        return ResponseEntity.ok(dboService.getAllClients());
    }

    @GetMapping("/client-access")
    public ResponseEntity<Map<Long, ClientAccessDTO>> getAllClientsAccess() {
        return ResponseEntity.ok(dboService.getAllClientsAccess());
    }

    @PostMapping("/update-client")
    public ResponseEntity<?> updateClient(@RequestBody Client client) {
        try {
            String result = dboService.updateClient(client);
            if (result.equals("SUCCESS"))
                return ResponseEntity.ok("Client updated successfully");
            else
                return ResponseEntity.status(500).body("Error updating client!");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }

    @GetMapping("/keywords")
    public ResponseEntity<LinkedHashMap<String, Integer>> getAllKeywords() {
        LinkedHashMap<String, Integer> keywords = dboService.getAllKeywords(); // userAccessCache.getKeywords();
        if (keywords == null) {
            ResponseEntity.internalServerError();
        }
        return ResponseEntity.ok(keywords);
    }

    @PostMapping("/grant")
    public ResponseEntity<String> grantAccess(
            @RequestBody AccessChangeRequest accessChangeRequest) {

        String result = dboService.grantAccess(accessChangeRequest.getClientId(),
                accessChangeRequest.getKeywords());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/revoke")
    public ResponseEntity<String> revokeAccess(
            @RequestBody AccessChangeRequest accessChangeRequest) {
        System.out.println("Revoking...");
        String result = dboService.revokeAccess(accessChangeRequest.getClientId(),
                accessChangeRequest.getKeywords());
        return ResponseEntity.ok(result);

    }

    @DeleteMapping("/delete-keyword")
    public ResponseEntity<String> deleteKeyword(
            @RequestBody String keyword) {
        if (dboService.deleteKeyword(keyword)) {
            dboCache.removeKeyword(keyword);
            return ResponseEntity.ok("Keyword deleted successfully!");
        }
        return ResponseEntity.badRequest().body("An error occured while deleting keyword.");
    }

    @DeleteMapping("/delete-file/keyword/{keyword}/file/{fileId}")
    public ResponseEntity<String> deleteFileFromKeyword(
            @PathVariable String keyword, @PathVariable long fileId) {
        if (dboService.deleteFileFromKeyword(keyword, fileId)) {
            return ResponseEntity.ok("Keyword deleted successfully!");
        }
        return ResponseEntity.badRequest().body("An error occured while deleting keyword.");
    }

    @PostMapping("/add-file-to-keyword")
    public ResponseEntity<String> addFileToKeyword(
            @RequestBody Map<String, String> addFileRequest) {
        if (dboService.addFileToKeyword(addFileRequest.get("keyword"), addFileRequest.get("fileId"))) {
            return ResponseEntity.ok("Keyword deleted successfully!");
        }
        return ResponseEntity.badRequest().body("An error occured while deleting keyword.");
    }

    @GetMapping("/get-all-files")
    public ResponseEntity<Map<Long, Document>> getAllFiles() {
        Map<Long, Document> fileMap = dboService.getAllFiles();
        return ResponseEntity.ok(fileMap);
    }

    @GetMapping("/search-files")
    public ResponseEntity<?> searchFilesByName(
            @RequestParam String query,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize) {
        // Search files by name/ID pattern
        List<Document> matchingFileNames = new ArrayList<>();

        Map<Long, Document> files = dboService.getAllFiles();
        for (Map.Entry<Long, Document> entry : files.entrySet()) {
            if (entry.getValue().getName().toLowerCase().contains(query.toLowerCase())) {
                matchingFileNames.add(entry.getValue());
            }
        }

        // Paginate results
        int start = (page - 1) * pageSize;
        int end = Math.min(start + pageSize, matchingFileNames.size());

        Map<String, Object> response = new HashMap<>();
        response.put("totalResults", matchingFileNames.size());
        response.put("page", page);
        response.put("pageSize", pageSize);
        response.put("results", matchingFileNames.subList(start, end));

        return ResponseEntity.ok(response);
    }

    @GetMapping("/get-file/{fileId}")
    public ResponseEntity<Map<String, Object>> getFile(@PathVariable int fileId) {
        Map<String, Object> data = dboService.getFile(fileId);
        return ResponseEntity.ok(data);
    }

    @PostMapping("/add-keyword")
    public ResponseEntity<Map<String, Object>> addKeyword(
            @RequestBody Map<String, String> addKeywordRequest) {
        try {
            Boolean result = dboService.addNewKeyword(addKeywordRequest.get("keyword"));

            if ("KEYWORD_EXISTS".equals(result)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Keyword already exists"));
            }

            return ResponseEntity.ok(Map.of("message", "Keyword added successfully"));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    private static final int MAX_INDIVIDUAL_FILES = 50;
    private static final long MAX_ZIP_SIZE = 1024 * 1024 * 1024; // 1GB

    @PostMapping("/upload/extract")
    public ResponseEntity<?> extractKeywords(@RequestParam("files") MultipartFile[] files) {
        try {
            // ✅ Check threshold
            if (files.length > MAX_INDIVIDUAL_FILES) {
                return ResponseEntity.status(413).body(Map.of(
                        "error", "TOO_MANY_FILES",
                        "message",
                        "Please upload files as a ZIP archive. Maximum " + MAX_INDIVIDUAL_FILES
                                + " individual files allowed.",
                        "threshold", MAX_INDIVIDUAL_FILES,
                        "received", files.length));
            }

            Map<String, Object> result = dboService.extractKeywordsFromFiles(files);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/upload/extract-zip")
    public ResponseEntity<?> extractKeywordsFromZip(@RequestParam("zipFile") MultipartFile zipFile) {
        try {
            // ✅ Validate ZIP size
            if (zipFile.getSize() > MAX_ZIP_SIZE) {
                return ResponseEntity.status(413).body(Map.of(
                        "error", "ZIP_TOO_LARGE",
                        "message", "ZIP file exceeds maximum size of " + (MAX_ZIP_SIZE / 1024 / 1024) + "MB"));
            }

            // ✅ Validate ZIP extension
            String filename = zipFile.getOriginalFilename();
            if (filename == null || !filename.toLowerCase().endsWith(".zip")) {
                return ResponseEntity.status(400).body(Map.of(
                        "error", "INVALID_FILE_TYPE",
                        "message", "Please upload a ZIP file"));
            }

            System.out.println("Extracting ZIP file: " + filename);

            // ✅ Extract ZIP and process
            MultipartFile[] extractedFiles = extractZipFile(zipFile);

            if (extractedFiles.length == 0) {
                return ResponseEntity.status(400).body(Map.of(
                        "error", "EMPTY_ZIP",
                        "message", "No valid .txt files found in ZIP archive"));
            }

            System.out.println("Extracted " + extractedFiles.length + " files from ZIP");

            Map<String, Object> result = dboService.extractKeywordsFromFiles(extractedFiles);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Extract .txt files from ZIP archive and convert to MultipartFile array
     */
    private MultipartFile[] extractZipFile(MultipartFile zipFile) throws IOException {
        List<MultipartFile> files = new ArrayList<>();

        try (ZipInputStream zis = new ZipInputStream(zipFile.getInputStream())) {
            ZipEntry entry;

            while ((entry = zis.getNextEntry()) != null) {
                // Skip directories
                if (entry.isDirectory()) {
                    continue;
                }

                String fileName = entry.getName();

                // Skip hidden files and system files
                if (fileName.startsWith(".") ||
                        fileName.startsWith("__MACOSX") ||
                        fileName.contains("/.")) {
                    System.out.println("Skipping system file: " + fileName);
                    continue;
                }

                // Only process .txt files
                if (!fileName.toLowerCase().endsWith(".txt")) {
                    System.out.println("Skipping non-txt file: " + fileName);
                    continue;
                }

                // Read file content
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int len;
                while ((len = zis.read(buffer)) > 0) {
                    baos.write(buffer, 0, len);
                }

                byte[] content = baos.toByteArray();

                // Skip empty files
                if (content.length == 0) {
                    System.out.println("Skipping empty file: " + fileName);
                    continue;
                }

                // Get just the filename without path
                String simpleFileName = fileName;
                if (fileName.contains("/")) {
                    simpleFileName = fileName.substring(fileName.lastIndexOf("/") + 1);
                }

                // Create MultipartFile from extracted content
                MultipartFile file = new InMemoryMultipartFile(
                        simpleFileName,
                        simpleFileName,
                        "text/plain",
                        content);

                files.add(file);
                // System.out.println("✓ Extracted: " + simpleFileName + " (" + content.length +
                // " bytes)");

                zis.closeEntry();
            }
        }

        return files.toArray(new MultipartFile[0]);
    }

    // ============================================================================
    // CUSTOM MULTIPARTFILE IMPLEMENTATION
    // ============================================================================

    /**
     * In-memory MultipartFile implementation for extracted ZIP files
     */
    private static class InMemoryMultipartFile implements MultipartFile {
        private final String name;
        private final String originalFilename;
        private final String contentType;
        private final byte[] content;

        public InMemoryMultipartFile(String name, String originalFilename,
                String contentType, byte[] content) {
            this.name = name;
            this.originalFilename = originalFilename;
            this.contentType = contentType;
            this.content = content;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getOriginalFilename() {
            return originalFilename;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            return content.length == 0;
        }

        @Override
        public long getSize() {
            return content.length;
        }

        @Override
        public byte[] getBytes() {
            return content;
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(content);
        }

        @Override
        public void transferTo(File dest) throws IOException {
            Files.write(dest.toPath(), content);
        }
    }

    @PostMapping("/upload/stats")
    public ResponseEntity<?> getUploadStats(@RequestBody UploadRequest request) {
        try {
            Map<String, Object> stats = dboService.calculateUploadStats(
                    request.getFileIds(),
                    request.getSelectedKeywords(),
                    request.getClientKeywordsMap());
            System.out.println("Selected keywords: " + request.getSelectedKeywords());
            System.out.println("Stats: " + stats);
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Finalize upload with user permissions
     */
    @PostMapping("/upload/finalize")
    public ResponseEntity<?> finalizeUpload(@RequestBody UploadRequest request) {
        try {
            Map<String, Object> result = dboService.finalizeUploadWithPermissions(
                    request.getFileIds(),
                    request.getSelectedKeywords(),
                    request.getClientKeywordsMap());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/set-verification/{verification}")
    public ResponseEntity<String> setVerification(@PathVariable boolean verification) {
        if (dboService.setVerification(verification)) {
            System.out.println("Verification set to " + verification);
            return ResponseEntity.ok("Verification set to " + verification);
        }
        return ResponseEntity.internalServerError().body("Verification could not be toggled");
    }

    @PostMapping("/reset-servers")
    public ResponseEntity<String> resetServers() {
        dboService.resetServers();
        return ResponseEntity.ok("Servers reset successfully");
    }

}
