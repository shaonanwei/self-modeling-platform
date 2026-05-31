package com.selfmodeling;

import cn.hutool.crypto.digest.BCrypt;

public class GeneratePassword {
    public static void main(String[] args) {
        String password = "admin123";
        String hashed = BCrypt.hashpw(password);
        System.out.println("Password: " + password);
        System.out.println("Hash: " + hashed);
        System.out.println("Verification: " + BCrypt.checkpw(password, hashed));
    }
}
