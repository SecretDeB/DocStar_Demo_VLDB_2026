package com.docstar.model.dto;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PeerMessage implements Serializable {

    private String sessionId;
    private int originServer;
    private Object payload;
}
