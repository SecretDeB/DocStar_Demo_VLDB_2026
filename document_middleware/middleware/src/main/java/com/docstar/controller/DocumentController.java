package com.docstar.controller;

import java.io.IOException;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.docstar.model.dto.ApiResponse;
import com.docstar.service.DocumentService;

@RestController
@RequestMapping("/documents")
public class DocumentController {

    @Autowired
    DocumentService documentService;

    @GetMapping("")
    public ApiResponse<Map<String, Object>> checkAccessAndGetFileIDs(@RequestParam String userId,
            @RequestParam String keyword)
            throws IOException {
        Map<String, Object> responseBody = documentService.checkAccessAndGetFileIDs(userId, keyword);
        System.out.println("Returning from here");
        return ApiResponse.success(responseBody);
    }

    @GetMapping("/{fileID}/fetch")
    public ApiResponse<?> getFile(@PathVariable Long fileID, @RequestHeader("Session-ID") String sessionId) {
        System.out.println("Getting file with ID: " + fileID + " for session: " + sessionId);
        String fileContent = documentService.getFile(sessionId, fileID);

        return ApiResponse.success(fileContent.getBytes());
    }

    @GetMapping("/close/{sessionID}")
    public ResponseEntity<Void> closeSession(@PathVariable String sessionId) {
        documentService.releaseResources(sessionId);
        return ResponseEntity.noContent().build();
    }
}
