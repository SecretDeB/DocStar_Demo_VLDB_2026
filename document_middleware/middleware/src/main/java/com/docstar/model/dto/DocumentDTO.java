package com.docstar.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DocumentDTO {
    private Long id;
    private String title;
    private Boolean fetched;
    private String error;
    private Boolean loading;
    private byte[] file;
}
