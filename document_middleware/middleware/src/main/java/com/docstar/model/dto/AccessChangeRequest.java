package com.docstar.model.dto;

import java.util.List;

import lombok.Data;

@Data
public class AccessChangeRequest {
    Long clientId;
    List<String> keywords;
}
