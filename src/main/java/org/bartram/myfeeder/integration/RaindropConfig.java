package org.bartram.myfeeder.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RaindropConfig {
    private Long collectionId;
}
