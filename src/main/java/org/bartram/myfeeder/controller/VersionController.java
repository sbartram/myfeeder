package org.bartram.myfeeder.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/version")
public class VersionController {
    private final BuildProperties buildProperties;

    public VersionController(@Autowired(required = false) BuildProperties buildProperties) {
        this.buildProperties = buildProperties;
    }

    @GetMapping
    public Map<String, String> getVersion() {
        if (buildProperties == null) {
            return Map.of("version", "dev", "buildTime", Instant.EPOCH.toString());
        }
        return Map.of(
                "version", buildProperties.getVersion(),
                "buildTime", buildProperties.getTime().toString()
        );
    }
}
