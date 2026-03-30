package com.docstar.model.dto;

import java.io.ObjectOutputStream;
import java.io.Serializable;

public class ClientRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private String sessionId;
    private Object payload;
    private String requestType;
    private transient ObjectOutputStream clientObjectOutputStream;

    // Constructor
    public ClientRequest() {
    }

    public ClientRequest(String sessionId, Object payload) {
        this.sessionId = sessionId;
        this.payload = payload;
    }

    // Getters and Setters
    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public Object getPayload() {
        return payload;
    }

    public void setPayload(Object payload) {
        this.payload = payload;
    }

    public String getRequestType() {
        return requestType;
    }

    public void setRequestType(String requestType) {
        this.requestType = requestType;
    }

    public ObjectOutputStream getClientObjectOutputStream() {
        return clientObjectOutputStream;
    }

    public void setClientObjectOutputStream(ObjectOutputStream clientObjectOutputStream) {
        this.clientObjectOutputStream = clientObjectOutputStream;
    }
}