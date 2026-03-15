package org.bartram.myfeeder.controller;

import org.bartram.myfeeder.model.IntegrationConfig;
import org.bartram.myfeeder.model.IntegrationType;
import org.bartram.myfeeder.repository.IntegrationConfigRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
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
class IntegrationConfigControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private IntegrationConfigRepository configRepository;

    @Test
    void shouldListIntegrations() throws Exception {
        var config = new IntegrationConfig();
        config.setType(IntegrationType.RAINDROP);
        config.setEnabled(true);
        when(configRepository.findAll()).thenReturn(List.of(config));

        mockMvc.perform(get("/api/integrations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("RAINDROP"));
    }

    @Test
    void shouldUpsertRaindropConfig() throws Exception {
        when(configRepository.findByType(IntegrationType.RAINDROP)).thenReturn(Optional.empty());
        when(configRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        mockMvc.perform(put("/api/integrations/raindrop")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiToken\":\"my-token\",\"collectionId\":12345}"))
                .andExpect(status().isOk());

        verify(configRepository).save(any(IntegrationConfig.class));
    }

    @Test
    void shouldDeleteRaindropConfig() throws Exception {
        mockMvc.perform(delete("/api/integrations/raindrop"))
                .andExpect(status().isNoContent());

        verify(configRepository).deleteByType(IntegrationType.RAINDROP);
    }
}
