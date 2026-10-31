package com.olgav.invoicesecurity.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

@Converter
public class AesGcmConverter implements AttributeConverter<String, byte[]> {
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    public byte[] convertToDatabaseColumn(String value) {
        if (value == null) return null;
        try {
            byte[] iv = new byte[IV_LENGTH];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] encrypted = cipher.doFinal(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Unable to encrypt sensitive database value", e);
        }
    }

    @Override
    public String convertToEntityAttribute(byte[] value) {
        if (value == null) return null;
        if (value.length <= IV_LENGTH) {
            throw new IllegalStateException("Encrypted database value is malformed");
        }
        try {
            ByteBuffer buffer = ByteBuffer.wrap(value);
            byte[] iv = new byte[IV_LENGTH];
            buffer.get(iv);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(encrypted), java.nio.charset.StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Unable to decrypt sensitive database value", e);
        }
    }

    private static SecretKeySpec key() {
        String encoded = System.getenv("AES_GCM_KEY");
        if (encoded == null || encoded.isBlank()) {
            encoded = System.getProperty("AES_GCM_KEY");
        }
        if (encoded == null || encoded.isBlank()) {
            throw new IllegalStateException("AES_GCM_KEY must be set before storing or reading encrypted values");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("AES_GCM_KEY must be base64-encoded", e);
        }
        if (bytes.length != 16 && bytes.length != 24 && bytes.length != 32) {
            throw new IllegalStateException("AES_GCM_KEY must decode to 16, 24, or 32 bytes");
        }
        return new SecretKeySpec(bytes, "AES");
    }
}
