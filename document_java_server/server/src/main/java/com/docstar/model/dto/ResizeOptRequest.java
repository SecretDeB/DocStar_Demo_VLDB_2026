package com.docstar.model.dto;

import java.io.Serializable;

public class ResizeOptRequest implements Serializable {
    public int replaceIndex; // Where the current row starts (startingPos)
    public long[] replaceRowData; // The shares to overwrite the current row (filled up)

    public int insertIndex; // Where the NEW row goes (startingPos + optCol)
    public long[] insertRowData; // The shares for the new overflow row

    public ResizeOptRequest(int rIdx, long[] rData, int iIdx, long[] iData) {
        this.replaceIndex = rIdx;
        this.replaceRowData = rData;
        this.insertIndex = iIdx;
        this.insertRowData = iData;
    }
}
