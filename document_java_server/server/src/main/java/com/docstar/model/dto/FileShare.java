package com.docstar.model.dto;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class FileShare implements Serializable {
    private int serverNumber;
    private long[] fileShare;
}
