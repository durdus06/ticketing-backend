package com.durdus.ticketing;

import org.springframework.boot.SpringApplication;

public class TestTicketingBackendApplication {

	public static void main(String[] args) {
		SpringApplication.from(TicketingBackendApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
