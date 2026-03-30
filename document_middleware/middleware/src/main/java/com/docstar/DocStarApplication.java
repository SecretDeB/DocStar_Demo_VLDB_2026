package com.docstar;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DocStarApplication {

	public static void main(String[] args) {
		System.out.println("System memory in GB: " + (Runtime.getRuntime().maxMemory() / (1024L * 1024L * 1024L)));
		SpringApplication.run(DocStarApplication.class, args);
	}
}
