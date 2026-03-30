package com.docstar.model.dto;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class FilesDataStructureShares implements Serializable {
    private int serverNumber;
    private long[][] filesShare;
}
