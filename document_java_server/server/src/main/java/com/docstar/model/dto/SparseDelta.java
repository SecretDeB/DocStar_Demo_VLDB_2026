package com.docstar.model.dto;

import java.io.Serializable;

/**
 * Represents a sparse delta for optInv updates.
 * Instead of sending 245M values (mostly zeros), we send only non-zero
 * positions.
 * 
 * Example:
 * Full array: [0, 0, 0, ..., 5, 17, ..., 10001, 0, 0, ...] (1.96 GB)
 * Sparse: positions=[700112, 700113, ..., 700140] (144 bytes)
 * values=[5, 17, ..., 10001]
 * 
 */
public class SparseDelta implements Serializable {
    private static final long serialVersionUID = 1L;

    // Positions where values changed (typically ~9 positions)
    private int[] positions;

    // Secret-shared values at those positions (typically ~9 values)
    private long[] values;

    // Label to identify which server this is for (1, 2, or 3)
    private int serverLabel;

    /**
     * Constructor for sparse delta
     * 
     * @param positions   Array of indices where values are non-zero
     * @param values      Array of values at those positions
     * @param serverLabel Server identifier (1, 2, or 3)
     */
    public SparseDelta(int[] positions, long[] values, int serverLabel) {
        if (positions.length != values.length) {
            throw new IllegalArgumentException(
                    "Positions and values arrays must have same length");
        }
        this.positions = positions;
        this.values = values;
        this.serverLabel = serverLabel;
    }

    public int[] getPositions() {
        return positions;
    }

    public long[] getValues() {
        return values;
    }

    public int getServerLabel() {
        return serverLabel;
    }

    public int size() {
        return positions.length;
    }

    public boolean isEmpty() {
        return positions.length == 0;
    }

    /**
     * Calculate memory size of this sparse delta
     * 
     * @return Size in bytes
     */
    public long getMemorySize() {
        // int[] positions: 4 bytes per int
        // long[] values: 8 bytes per long
        // serverLabel: 4 bytes
        // object overhead: ~16 bytes
        return positions.length * 4 + values.length * 8 + 20;
    }

    @Override
    public String toString() {
        return String.format(
                "SparseDelta[size=%d, server=%d, memorySize=%d bytes]",
                positions.length, serverLabel, getMemorySize());
    }
}