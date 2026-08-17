package com.habitforge;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.habitforge.modules.**.mapper")
public class HabitForgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(HabitForgeApplication.class, args);
    }
}
