package com.ron.passly.service;

import com.ron.passly.repository.UserEncryptionKeyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class EncryptionService {

    private final UserEncryptionKeyRepository userEncryptionKeyRepository;

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/CBC/PKCS5Padding";
    private static final int KEY_LENGTH = 256;
    private static final int IV_LENGTH = 16;
    private static final int SALT_LENGTH = 32;
    private static final int PBKDF2_ITERATIONS = 100000;

    public String generateRandomKey() {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance(ALGORITHM);

            keyGen.init(KEY_LENGTH);

            SecretKey secretKey = keyGen.generateKey();

            return Base64.getEncoder().encodeToString(secretKey.getEncoded());
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate random key: " + e.getMessage());
        }

    }

    public String generateSalt() {

        try {
            SecureRandom random = new SecureRandom();

            byte[] salt = new byte[SALT_LENGTH];

            random.nextBytes(salt);

            return Base64.getEncoder().encodeToString(salt);

        }  catch (Exception e) {
            throw new RuntimeException("Failed to generate salt: " + e.getMessage());
        }
    }

    public String deriveKeyFromPassword(String password, String salt) {

        try {

            byte[] saltBytes = Base64.getDecoder().decode(salt);

            PBEKeySpec spec = new PBEKeySpec(
                    password.toCharArray(),
                    saltBytes,
                    PBKDF2_ITERATIONS,
                    KEY_LENGTH
            );

            SecretKeyFactory keyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");

            SecretKey derivedKey = keyFactory.generateSecret(spec);

            return  Base64.getEncoder().encodeToString(derivedKey.getEncoded());

        } catch (Exception e) {
            throw new RuntimeException("Failed to derive key from password: " + e.getMessage());
        }
    }

    public String encrypt(String plainText, String key) {
        try {

            byte[] keyByes = Base64.getDecoder().decode(key);
            SecretKey secretKey = new SecretKeySpec(keyByes, ALGORITHM);

            byte[] iv = new byte[IV_LENGTH];
            new  SecureRandom().nextBytes(iv);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec);

            byte[] encryptedBytes = cipher.doFinal(plainText.getBytes());

            byte[] encryptedWithIv = new byte[IV_LENGTH + encryptedBytes.length];
            System.arraycopy(iv, 0, encryptedWithIv, 0, IV_LENGTH);
            System.arraycopy(encryptedBytes, 0, encryptedWithIv, IV_LENGTH, encryptedBytes.length);

            return Base64.getEncoder().encodeToString(encryptedWithIv);

        } catch (Exception e) {
            throw new RuntimeException("Failed to encrypt text: " + e.getMessage());
        }

    }

    public String decrypt(String encryptedText, String key) {

        try {
            byte[] encryptedWithIv = Base64.getDecoder().decode(encryptedText);

            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(encryptedWithIv, 0, iv, 0, IV_LENGTH);

            byte[] encryptedBytes = new byte[encryptedWithIv.length - IV_LENGTH];
            System.arraycopy(encryptedWithIv, IV_LENGTH, encryptedBytes, 0, encryptedBytes.length);

            byte[] keyBytes = Base64.getDecoder().decode(key);
            SecretKey secretKey = new SecretKeySpec(keyBytes, ALGORITHM);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec);

            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);

            return new String(decryptedBytes);

        } catch (Exception e) {
            throw new RuntimeException("Failed to decrypt text: " + e.getMessage());
        }
    }

}


