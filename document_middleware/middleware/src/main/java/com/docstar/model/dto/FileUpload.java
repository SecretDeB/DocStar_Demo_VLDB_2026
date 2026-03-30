package com.docstar.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FileUpload {
    public String content;
    public String[] keywords;
    public String filename; // Optional, for tracking

    public FileUpload(String content, String[] keywords) {
        this.content = content;
        this.keywords = keywords;
    }
}
