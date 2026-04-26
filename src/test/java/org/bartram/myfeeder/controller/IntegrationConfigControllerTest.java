package org.bartram.myfeeder.controller;

import org.bartram.myfeeder.config.MyfeederProperties;
import org.bartram.myfeeder.integration.RaindropCollection;
import org.bartram.myfeeder.integration.RaindropNotConfiguredException;
import org.bartram.myfeeder.integration.RaindropService;
import org.bartram.myfeeder.model.IntegrationConfig;
import org.bartram.myfeeder.model.IntegrationType;
import org.bartram.myfeeder.repository.IntegrationConfigRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(IntegrationConfigController.class)
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
@Import({GlobalExceptionHandler.class})
@EnableConfigurationProperties(MyfeederProperties.class)
class IntegrationConfigControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private IntegrationConfigRepository configRepository;
    @MockitoBean private RaindropService raindropService;
    @Autowired private MyfeederProperties properties;

    @Test
    void statusReturnsConfiguredFalseWhenTokenEmpty() throws Exception {
        properties.getRaindrop().setApiToken("");

        mockMvc.perform(get("/api/integrations/raindrop/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(false));
    }

    @Test
    void statusReturnsConfiguredTrueWhenTokenPresent() throws Exception {
        properties.getRaindrop().setApiToken("a-token");

        mockMvc.perform(get("/api/integrations/raindrop/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true));
    }
}
