package com.olgav.invoicesecurity.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AesGcmConverterTest {
    @Test
    void encryptsAndDecryptsAndDetectsTampering() {
        String previous = System.getProperty("AES_GCM_KEY");
        System.setProperty("AES_GCM_KEY", "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
        try {
            AesGcmConverter converter = new AesGcmConverter();
            String clearText = "{\"accountNumber\":\"sensitive\"}";
            byte[] encrypted = converter.convertToDatabaseColumn(clearText);

            assertNotEquals(clearText, new String(encrypted, java.nio.charset.StandardCharsets.UTF_8));
            assertEquals(clearText, converter.convertToEntityAttribute(encrypted));

            encrypted[encrypted.length - 1] ^= 1;
            assertThrows(IllegalStateException.class, () -> converter.convertToEntityAttribute(encrypted));
        } finally {
            if (previous == null) System.clearProperty("AES_GCM_KEY");
            else System.setProperty("AES_GCM_KEY", previous);
        }
    }

}
