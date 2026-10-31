package com.olgav.invoicesecurity.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserSerializationTest {

    @Test
    void doesNotSerializePasswordHash() {
        User user = new User();
        user.setEmail("user@example.com");
        user.setPasswordHash("sensitive-hash");

        var json = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .valueToTree(user);

        assertTrue(json.has("email"));
        assertFalse(json.has("passwordHash"));
    }
}
