package com.docstar.model.dto;

import java.io.ObjectOutputStream;
import java.io.Serializable;

import lombok.Data;

/**
 * A wrapper for all DBO-related messages sent from an admin client to the
 * Server.
 * The payload can be an operation code (Integer) or any other data needed for
 * the protocol.
 */
@Data
public class DBORequest implements Serializable {
    // Using a generic Object payload allows for maximum flexibility in your
    // protocol.
    private transient ObjectOutputStream dboObjectOutputStream;
    private final Object payload;
}
