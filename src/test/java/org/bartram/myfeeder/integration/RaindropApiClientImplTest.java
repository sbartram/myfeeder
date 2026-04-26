package org.bartram.myfeeder.integration;

import org.bartram.myfeeder.config.MyfeederProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RaindropApiClientImplTest {

    private MyfeederProperties properties;

    @BeforeEach
    void setUp() {
        properties = new MyfeederProperties();
        properties.getRaindrop().setApiBaseUrl("https://api.raindrop.io/rest/v1");
        properties.getRaindrop().setApiToken("test-token");
    }

    @Test
    void listCollectionsThrowsWhenTokenMissing() {
        properties.getRaindrop().setApiToken("");
        var client = new RaindropApiClientImpl(properties, RestClient.builder());

        assertThatThrownBy(client::listCollections)
                .isInstanceOf(RaindropNotConfiguredException.class);
    }

    @Test
    void listCollectionsCallsApiAndParsesResponse() {
        var builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        var client = new RaindropApiClientImpl(properties, builder);

        server.expect(requestTo("https://api.raindrop.io/rest/v1/collections"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer test-token"))
                .andRespond(withSuccess(
                        """
                        {
                          "items": [
                            {"_id": 100, "title": "Reading"},
                            {"_id": 200, "title": "Recipes"}
                          ]
                        }
                        """,
                        MediaType.APPLICATION_JSON));

        List<RaindropCollection> result = client.listCollections();

        assertThat(result)
                .extracting(RaindropCollection::id, RaindropCollection::title)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(100L, "Reading"),
                        org.assertj.core.groups.Tuple.tuple(200L, "Recipes"));
        server.verify();
    }
}
