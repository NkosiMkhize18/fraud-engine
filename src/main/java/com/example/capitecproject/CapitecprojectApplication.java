package com.example.capitecproject;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CapitecprojectApplication {

	public static void main(String[] args) {
		SpringApplication.run(CapitecprojectApplication.class, args);
	}

}
