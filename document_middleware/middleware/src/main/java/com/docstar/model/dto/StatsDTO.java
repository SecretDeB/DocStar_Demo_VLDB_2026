package com.docstar.model.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StatsDTO {
    private int documentCount;
    private int clientCount;

    public StatsDTO() {
    }

    public StatsDTO(int documentCount, int clientCount) {
        this.documentCount = documentCount;
        this.clientCount = clientCount;
    }

}
