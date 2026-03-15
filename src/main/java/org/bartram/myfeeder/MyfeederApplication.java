package org.bartram.myfeeder;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MyfeederApplication {

	public static void main(String[] args) {
		SpringApplication.run(MyfeederApplication.class, args);
	}

}
