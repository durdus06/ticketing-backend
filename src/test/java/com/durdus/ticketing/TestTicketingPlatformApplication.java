package com.durdus.ticketing;

import org.springframework.boot.SpringApplication;

public class TestTicketingPlatformApplication {

	public static void main(String[] args) {
		SpringApplication.from(TicketingPlatformApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
