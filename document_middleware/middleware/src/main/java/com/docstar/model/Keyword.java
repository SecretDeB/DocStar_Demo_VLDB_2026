package com.docstar.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;

@Data
@Entity
public class Keyword {
    @Id
    private String keyword;
    private String originalKeyword;
}
