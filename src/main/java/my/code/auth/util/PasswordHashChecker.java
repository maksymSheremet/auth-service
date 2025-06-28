package my.code.auth.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class PasswordHashChecker {
    public static void main(String[] args) {
        BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        String password = "Admin123!";
        String hashedPassword = passwordEncoder.encode(password);
        System.out.println(hashedPassword);
    }
}
