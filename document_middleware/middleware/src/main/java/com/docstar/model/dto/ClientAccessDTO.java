package com.docstar.model.dto;

import java.util.Set;

import com.docstar.model.Client;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ClientAccessDTO {
    private Client client;
    private Set<String> keywords;
}
