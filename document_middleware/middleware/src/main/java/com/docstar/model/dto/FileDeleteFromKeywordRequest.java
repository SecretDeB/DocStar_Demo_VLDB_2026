package com.docstar.model.dto;

import lombok.Data;

@Data
public class FileDeleteFromKeywordRequest {
    private String keyword;
    private long fileId;
}
