package com.docstar.model.dto;

import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UploadRequest {
    private List<String> fileIds;
    private List<String> selectedKeywords;
    private Map<String, List<String>> clientKeywordsMap;
}
