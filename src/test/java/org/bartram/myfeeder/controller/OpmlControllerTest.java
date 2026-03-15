package org.bartram.myfeeder.controller;

import org.bartram.myfeeder.model.Feed;
import org.bartram.myfeeder.model.Folder;
import org.bartram.myfeeder.parser.OpmlParseException;
import org.bartram.myfeeder.service.FeedService;
import org.bartram.myfeeder.service.FolderService;
import org.bartram.myfeeder.service.OpmlImportResult;
import org.bartram.myfeeder.service.OpmlImportService;
import org.bartram.myfeeder.service.OpmlService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.io.InputStream;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OpmlController.class)
class OpmlControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private OpmlImportService opmlImportService;
    @MockitoBean private OpmlService opmlService;
    @MockitoBean private FeedService feedService;
    @MockitoBean private FolderService folderService;

    @Test
    void shouldImportOpmlFile() throws Exception {
        var file = new MockMultipartFile("file", "feeds.opml",
                "application/xml", "<opml/>".getBytes());
        when(opmlImportService.importOpml(any(InputStream.class)))
                .thenReturn(new OpmlImportResult(3, 1, 4));

        mockMvc.perform(multipart("/api/opml/import").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(3))
                .andExpect(jsonPath("$.updated").value(1))
                .andExpect(jsonPath("$.total").value(4));
    }

    @Test
    void shouldReturn400ForInvalidOpml() throws Exception {
        var file = new MockMultipartFile("file", "bad.opml",
                "application/xml", "not xml".getBytes());
        when(opmlImportService.importOpml(any(InputStream.class)))
                .thenThrow(new OpmlParseException("Invalid OPML"));

        mockMvc.perform(multipart("/api/opml/import").file(file))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldExportOpml() throws Exception {
        when(feedService.findAll()).thenReturn(List.of(new Feed()));
        when(folderService.findAll()).thenReturn(List.of());
        when(opmlService.generateOpml(any(), any())).thenReturn("<opml>test</opml>");

        mockMvc.perform(get("/api/opml/export"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/xml"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"myfeeder-export.opml\""))
                .andExpect(content().string("<opml>test</opml>"));
    }
}
