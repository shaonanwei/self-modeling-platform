package com.selfmodeling;

import cn.hutool.crypto.digest.BCrypt;

public class PasswordGenerator {
    public static void main(String[] args) {
        String password = "admin123";
        String hashedPassword = BCrypt.hashpw(password);
        System.out.println("密码: " + password);
        System.out.println("哈希值: " + hashedPassword);
        
        // 验证一下
        boolean matches = BCrypt.checkpw(password, hashedPassword);
        System.out.println("验证结果: " + matches);
    }
}
