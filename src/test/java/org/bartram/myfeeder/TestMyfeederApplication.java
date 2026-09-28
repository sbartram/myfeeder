package org.bartram.myfeeder;

import org.springframework.boot.SpringApplication;

public class TestMyfeederApplication {

	// bootTestRun loads the test application.yaml; this profile's overlay restores main's live settings.
	public static final String DEV_PROFILE = "dev";

	public static void main(String[] args) {
		SpringApplication.from(MyfeederApplication::main).with(TestcontainersConfiguration.class)
				.withAdditionalProfiles(DEV_PROFILE).run(args);
	}

}
