package utilities;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

public final class PasswordSecurity {

    public static final int MINIMUM_LENGTH = 4;
    private static final String PREFIX = "pbkdf2";
    private static final int ITERATIONS = 210_000;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BITS = 256;
    private static final char[] TEMPORARY_ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordSecurity() {}

    public static String hash(String password) {
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("La contraseña no puede estar vacía");
        }

        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] derived = derive(password.toCharArray(), salt, ITERATIONS);
        return PREFIX + "$" + ITERATIONS + "$"
                + Base64.getEncoder().encodeToString(salt) + "$"
                + Base64.getEncoder().encodeToString(derived);
    }

    public static boolean verify(String password, String storedValue) {
        if (password == null || storedValue == null) {
            return false;
        }
        if (!isHash(storedValue)) {
            return MessageDigest.isEqual(
                    password.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    storedValue.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        try {
            String[] parts = storedValue.split("\\$", -1);
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            byte[] actual = derive(password.toCharArray(), salt, iterations);
            return MessageDigest.isEqual(expected, actual);
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static boolean needsUpgrade(String storedValue) {
        return !isHash(storedValue);
    }

    public static String generateTemporaryPassword() {
        StringBuilder value = new StringBuilder("LENC-");
        for (int i = 0; i < 10; i++) {
            value.append(TEMPORARY_ALPHABET[RANDOM.nextInt(TEMPORARY_ALPHABET.length)]);
        }
        return value.toString();
    }

    private static boolean isHash(String storedValue) {
        return storedValue != null
                && storedValue.startsWith(PREFIX + "$")
                && storedValue.split("\\$", -1).length == 4;
    }

    private static byte[] derive(char[] password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, HASH_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec)
                    .getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo proteger la contraseña", e);
        } finally {
            spec.clearPassword();
        }
    }
}
