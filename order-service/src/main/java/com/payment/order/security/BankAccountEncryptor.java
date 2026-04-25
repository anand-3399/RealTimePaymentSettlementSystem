package com.payment.order.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Converter
@Component
public class BankAccountEncryptor implements AttributeConverter<String, String> {

    private static final String AES = "AES";
    
    // In production, this MUST be loaded from a secure vault
    private static final String SECRET = "RTPS_Project_Super_Secret_Key_32"; 

    private final SecretKeySpec key;

    public BankAccountEncryptor() {
        this.key = new SecretKeySpec(SECRET.getBytes(), AES);
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) return null;
        try {
            Cipher cipher = Cipher.getInstance(AES);
            cipher.init(Cipher.ENCRYPT_MODE, key);
            return Base64.getEncoder().encodeToString(cipher.doFinal(attribute.getBytes()));
        } catch (Exception e) {
            throw new RuntimeException("Encryption error: " + e.getMessage());
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        try {
            Cipher cipher = Cipher.getInstance(AES);
            cipher.init(Cipher.DECRYPT_MODE, key);
            return new String(cipher.doFinal(Base64.getDecoder().decode(dbData)));
        } catch (Exception e) {
            throw new RuntimeException("Decryption error: " + e.getMessage());
        }
    }
}
