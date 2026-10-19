package com.example.capitecproject;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CapitecProjectApplication {

	public static void main(String[] args) {
		SpringApplication.run(CapitecProjectApplication.class, args);
	}

}
