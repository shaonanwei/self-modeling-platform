package com.selfmodeling;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.selfmodeling.mapper")
public class SelfModelingApplication {
    public static void main(String[] args) {
        SpringApplication.run(SelfModelingApplication.class, args);
    }
}
