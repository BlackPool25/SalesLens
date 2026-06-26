package com.shreyas.saleslens.service.ingestion;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Service;

/**
 * AES-256 encryption service for sensitive credential data.
 * Uses Spring Security Crypto's {@link Encryptors#text(CharSequence, CharSequence)}
 * with PBKDF2 key derivation and a random 16-byte initialization vector per encryption.
 * <p>
 * Each call to {@link #encrypt(String)} produces a different hex-encoded ciphertext
 * for the same plaintext due to the random IV, defeating known-plaintext attacks.
 * <p>
 * Configuration expects:
 * <ul>
 *   <li>{@code saleslens.encryption.key} — the encryption passphrase (any string)</li>
 *   <li>{@code saleslens.encryption.salt} — hex-encoded salt, minimum 8 bytes (16 hex chars)</li>
 * </ul>
 */
@Service
@Slf4j
public class CredentialEncryptionService {

    private final TextEncryptor encryptor;

    public CredentialEncryptionService(
            @Value("${saleslens.encryption.key}") String key,
            @Value("${saleslens.encryption.salt}") String salt) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Encryption key must not be null or blank");
        }
        if (salt == null || salt.isBlank()) {
            throw new IllegalArgumentException("Encryption salt must not be null or blank");
        }
        this.encryptor = Encryptors.text(key, salt);
    }

    /**
     * Encrypts plaintext using AES-256 with a random IV.
     *
     * @param plainText the text to encrypt (must not be null or empty)
     * @return hex-encoded ciphertext
     * @throws IllegalArgumentException if plainText is null or empty
     */
    public String encrypt(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            throw new IllegalArgumentException("plainText must not be null or empty");
        }
        return encryptor.encrypt(plainText);
    }

    /**
     * Decrypts a hex-encoded ciphertext back to plaintext.
     *
     * @param encryptedText the hex-encoded ciphertext to decrypt (must not be null or empty)
     * @return the original plaintext
     * @throws IllegalArgumentException if encryptedText is null or empty
     */
    public String decrypt(String encryptedText) {
        if (encryptedText == null || encryptedText.isEmpty()) {
            throw new IllegalArgumentException("encryptedText must not be null or empty");
        }
        return encryptor.decrypt(encryptedText);
    }
}
