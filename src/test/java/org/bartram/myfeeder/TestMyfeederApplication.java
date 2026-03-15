package org.bartram.myfeeder;

import org.springframework.boot.SpringApplication;

public class TestMyfeederApplication {

	public static void main(String[] args) {
		SpringApplication.from(MyfeederApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
