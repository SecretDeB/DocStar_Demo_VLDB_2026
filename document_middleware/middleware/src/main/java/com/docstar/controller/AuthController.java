package com.docstar.controller;

import org.springframework.web.bind.annotation.RestController;

import com.docstar.model.Client;
import com.docstar.model.dto.ClientDTO;
import com.docstar.service.AuthService;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    AuthService authService;

    @PostMapping("/google")
    public ResponseEntity<ClientDTO> googleLogin(@RequestBody Map<String, String> user) {
        ClientDTO clientDTO = authService.validateUserByEmail(user.get("email"));
        if (clientDTO.getError() != null && clientDTO.getError().equals("USER_NOT_FOUND")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } else {
            return ResponseEntity.ok(clientDTO);
        }
    }

    @PostMapping("/login")
    public ResponseEntity<ClientDTO> loginUser(@RequestBody ClientDTO user) {
        ClientDTO clientDTO = authService.validateUser(user.getUsername(), user.getPassword());
        if (clientDTO.getError() != null && (clientDTO.getError().equals("USER_NOT_FOUND")
                || clientDTO.getError().equals("PASSWORD_INCORRECT"))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(clientDTO);
        } else {
            return ResponseEntity.ok(clientDTO);
        }
    }

    @PostMapping("/signup")
    public ResponseEntity<ClientDTO> signUpUser(@RequestBody Client newClient) {
        ClientDTO client = authService.signUpUser(newClient);
        int status = 200;
        if (client.getError() != null) {
            if (client.getError().equals("USERNAME_ALREADY_EXISTS")
                    || client.getError().equals("EMAIL_ALREADY_EXISTS")) {
                status = 409;
            } else if (client.getError().equals("UNKNOWN_ERROR")) {
                status = 500;
            }
        }
        return ResponseEntity.status(status).body(client);
    }

}
