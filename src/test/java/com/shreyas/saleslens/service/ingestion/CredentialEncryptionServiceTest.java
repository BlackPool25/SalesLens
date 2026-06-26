package com.shreyas.saleslens.service.ingestion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link CredentialEncryptionService}.
 * <p>
 * Uses direct constructor invocation with dummy key/salt values
 * (no Spring context required). The salt "deadbeefdeadbeef" is
 * 8 bytes (16 hex chars), meeting the minimum requirement.
 */
@ExtendWith(MockitoExtension.class)
class CredentialEncryptionServiceTest {

    private static final String TEST_KEY = "change-me-in-production";
    private static final String TEST_SALT = "deadbeefdeadbeef";

    private final CredentialEncryptionService service = new CredentialEncryptionService(TEST_KEY, TEST_SALT);

    // ---------------------------------------------------------------
    // 1. Happy roundtrip
    // ---------------------------------------------------------------

    @Test
    void encryptDecrypt_happyRoundtrip_returnsOriginalPlaintext() {
        String original = "secret";
        String encrypted = service.encrypt(original);
        String decrypted = service.decrypt(encrypted);
        assertEquals(original, decrypted);
    }

    // ---------------------------------------------------------------
    // 2. IV randomness — same input produces different ciphertext
    // ---------------------------------------------------------------

    @Test
    void encrypt_sameInput_producesDifferentCiphertext() {
        String plaintext = "same";
        String cipher1 = service.encrypt(plaintext);
        String cipher2 = service.encrypt(plaintext);
        assertNotEquals(cipher1, cipher2, "Each encryption must produce different ciphertext (random IV)");
    }

    // ---------------------------------------------------------------
    // 3. Null input — throws IllegalArgumentException
    // ---------------------------------------------------------------

    @Test
    void encrypt_nullInput_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> service.encrypt(null));
    }

    @Test
    void decrypt_nullInput_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> service.decrypt(null));
    }

    // ---------------------------------------------------------------
    // 4. Empty input — throws IllegalArgumentException
    // ---------------------------------------------------------------

    @Test
    void encrypt_emptyInput_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> service.encrypt(""));
    }

    @Test
    void decrypt_emptyInput_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> service.decrypt(""));
    }

    // ---------------------------------------------------------------
    // 5. Unicode characters — roundtrip with special chars, emoji,
    //    SQL-relevant characters
    // ---------------------------------------------------------------

    @Test
    void encryptDecrypt_unicodeInput_roundtripsSuccessfully() {
        String input = "héllo wörld 日本 中文 👋 🔒 ' OR 1=1; --";
        String encrypted = service.encrypt(input);
        String decrypted = service.decrypt(encrypted);
        assertEquals(input, decrypted);
    }

    @Test
    void encryptDecrypt_sqlInjectionChars_roundtripsSuccessfully() {
        String input = "' OR '1'='1'; DROP TABLE users; --";
        String encrypted = service.encrypt(input);
        String decrypted = service.decrypt(encrypted);
        assertEquals(input, decrypted);
    }

    // ---------------------------------------------------------------
    // 6. Long input — 4096 characters roundtrip
    // ---------------------------------------------------------------

    @Test
    void encryptDecrypt_longInput_roundtripsSuccessfully() {
        String input = "a".repeat(4096);
        String encrypted = service.encrypt(input);
        String decrypted = service.decrypt(encrypted);
        assertEquals(input, decrypted);
    }

    @Test
    void encryptDecrypt_longUnicodeInput_roundtripsSuccessfully() {
        String input = "⭐".repeat(2048);  // 4096 bytes (each star is 3 bytes in UTF-8)
        String encrypted = service.encrypt(input);
        String decrypted = service.decrypt(encrypted);
        assertEquals(input, decrypted);
    }

}
