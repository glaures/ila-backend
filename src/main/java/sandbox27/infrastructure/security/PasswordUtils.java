package sandbox27.infrastructure.security;

import lombok.NonNull;
import org.mindrot.jbcrypt.BCrypt;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PasswordUtils {

    private static final int LOG_ROUNDS = 12;
    private static final SecureRandom random = new SecureRandom();

    private static final String UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String LOWERCASE = "abcdefghijklmnopqrstuvwxyz";
    private static final String DIGITS = "0123456789";

    private static final String SPECIAL_CHARS = "!@#$%&*+";

    public static String hashPassword(@NonNull String plainPassword) {
        return BCrypt.hashpw(plainPassword, BCrypt.gensalt(LOG_ROUNDS));
    }

    public static boolean verifyPassword(String plainPassword, String passwordHash) {
        if (plainPassword == null || passwordHash == null) {
            return false;
        }
        try {
            return BCrypt.checkpw(plainPassword, passwordHash);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static boolean isBCryptHash(String hash) {
        if (hash == null) {
            return false;
        }
        return hash.matches("^\\$2[ayb]\\$\\d{2}\\$.{53}$");
    }

    public static String generateRandomPassword() {
        List<Character> passwordChars = new ArrayList<>();

        // Anzahl der Sonderzeichen zufällig zwischen 1 und 2 wählen
        int specialCharCount = random.nextInt(2) + 1; // 1 oder 2
        int alphanumericCount = 8 - specialCharCount;  // 6 oder 7

        // Mindestens je ein Zeichen aus jeder Kategorie (außer Special)
        passwordChars.add(getRandomChar(UPPERCASE));
        passwordChars.add(getRandomChar(LOWERCASE));
        passwordChars.add(getRandomChar(DIGITS));

        // Restliche alphanumerische Zeichen auffüllen
        String allAlphanumeric = UPPERCASE + LOWERCASE + DIGITS;
        for (int i = 3; i < alphanumericCount; i++) {
            passwordChars.add(getRandomChar(allAlphanumeric));
        }

        // Sonderzeichen hinzufügen
        for (int i = 0; i < specialCharCount; i++) {
            passwordChars.add(getRandomChar(SPECIAL_CHARS));
        }

        // Alle Zeichen zufällig mischen
        Collections.shuffle(passwordChars, random);

        // In String umwandeln
        StringBuilder password = new StringBuilder();
        for (Character c : passwordChars) {
            password.append(c);
        }

        return password.toString();
    }

    private static char getRandomChar(String source) {
        int index = random.nextInt(source.length());
        return source.charAt(index);
    }
}