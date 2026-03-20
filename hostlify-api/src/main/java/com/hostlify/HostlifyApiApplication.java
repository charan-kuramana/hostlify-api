package com.hostlify;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class HostlifyApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(HostlifyApiApplication.class, args);
	}

}
