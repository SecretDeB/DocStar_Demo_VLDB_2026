package com.docstar.model.dto;

import java.io.Serializable;
import java.util.ArrayList;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SearchResult implements Serializable {
    String sessionId;
    int phase1Result;
    long[] phase2AddrResult;
    long[] phase2OptInvResult;
    ArrayList<Long> phase2Result;
    long[][] phase3Result;
    boolean success;
    String errorMessage;
}
