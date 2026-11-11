package com.codemove;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@org.springframework.scheduling.annotation.EnableScheduling
public class CodemoveApplication {

	public static void main(String[] args) {
		SpringApplication.run(CodemoveApplication.class, args);
	}

}
