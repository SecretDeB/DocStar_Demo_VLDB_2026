package com.docstar.service;

import java.sql.SQLException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.docstar.middleware.DBO;
import com.docstar.model.Admin;
import com.docstar.model.Client;
import com.docstar.model.dto.ClientDTO;
import com.docstar.repository.AdminRepository;
import com.docstar.repository.ClientRepository;

@Service
public class AuthService {

    @Autowired
    ClientRepository cr;

    @Autowired
    AdminRepository ar;

    @Autowired
    DBO dbo;

    @Autowired
    DBOCache dboCache;

    public ClientDTO validateUser(String username, String password) {
        Client client = cr.findByUsername(username).orElse(null);
        Admin admin = ar.findByUsername(username).orElse(null);

        if (client == null) {
            if (admin != null && admin.getPassword().equals(password)) {
                ClientDTO clientDTO = new ClientDTO(admin.getId(), admin.getUsername(), "Admin");
                clientDTO.setEmail(admin.getEmail());
                return clientDTO;
            } else {
                return new ClientDTO("USER_NOT_FOUND");
            }
        } else if (client.getPassword().equals(password)) {
            ClientDTO clientDTO = new ClientDTO(client.getId(), client.getUsername(), "Client");
            clientDTO.setEmail(client.getEmail());
            return clientDTO;
        } else {
            return new ClientDTO("INCORRECT_PASSWORD");
        }
    }

    public ClientDTO signUpUser(Client client) {

        try {
            Client newClient = cr.save(client);

            try {
                dbo.addNewClient();
                dboCache.setClientCount(dboCache.getClientCount() + 1);
            } catch (Exception ex) {
                ex.printStackTrace();
                return new ClientDTO("UNKNOWN_ERROR");
            }

            return new ClientDTO(newClient.getId(), newClient.getUsername(), "Client");
        } catch (DataIntegrityViolationException e) {
            Throwable rootCause = e.getRootCause();
            if (rootCause instanceof SQLException sqlEx && "23000".equals(sqlEx.getSQLState())
                    && sqlEx.getErrorCode() == 1062) {
                String errorMessage = sqlEx.getMessage().toLowerCase();
                if (errorMessage.contains("client_uname_unique")) {
                    return new ClientDTO("USERNAME_ALREADY_EXISTS");
                } else if (errorMessage.contains("client_email_unique")) {
                    return new ClientDTO("EMAIL_ALREADY_EXISTS");
                }
            }
            return new ClientDTO("UNKNOWN_ERROR");
        }
    }

    public ClientDTO validateUserByEmail(String email) {
        Client client = cr.findByEmail(email).orElse(null);
        if (client == null) {
            Admin admin = ar.findByEmail(email).orElse(null);
            if (admin != null) {
                return new ClientDTO(admin.getId(), admin.getUsername(), "admin");
            } else {
                return new ClientDTO("USER_NOT_FOUND");
            }
        } else {
            return new ClientDTO(client.getId(), client.getUsername(), "client");
        }
    }

}
