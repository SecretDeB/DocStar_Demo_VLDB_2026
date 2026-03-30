package com.docstar.model.dto;

import java.io.Serializable;
import java.math.BigInteger;

public class KeywordDTO implements Serializable {
    public BigInteger keywordShare;
    public long positionShare;
    public BigInteger[] accessShares;

    public KeywordDTO(BigInteger kw, long pos, BigInteger[] access) {
        this.keywordShare = kw;
        this.positionShare = pos;
        this.accessShares = access;
    }
}
