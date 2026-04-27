package org.bartram.myfeeder.controller;

import org.bartram.myfeeder.model.Folder;
import org.bartram.myfeeder.service.FolderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Instant;
import java.util.List;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FolderController.class)
class FolderControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private FolderService folderService;

    @Test
    void shouldListFolders() throws Exception {
        Folder folder = new Folder();
        folder.setId(1L);
        folder.setName("Tech");
        folder.setDisplayOrder(0);
        folder.setCreatedAt(Instant.now());
        when(folderService.findAll()).thenReturn(List.of(folder));
        mockMvc.perform(get("/api/folders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Tech"));
    }

    @Test
    void shouldCreateFolder() throws Exception {
        Folder folder = new Folder();
        folder.setId(1L);
        folder.setName("Science");
        folder.setCreatedAt(Instant.now());
        when(folderService.create("Science")).thenReturn(folder);
        mockMvc.perform(post("/api/folders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Science\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Science"));
    }

    @Test
    void shouldDeleteFolder() throws Exception {
        mockMvc.perform(delete("/api/folders/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void shouldReorderFolders() throws Exception {
        Folder a = new Folder(); a.setId(1L); a.setName("Tech"); a.setDisplayOrder(1);
        Folder b = new Folder(); b.setId(2L); b.setName("News"); b.setDisplayOrder(0);
        when(folderService.reorder(List.of(2L, 1L))).thenReturn(List.of(b, a));
        mockMvc.perform(put("/api/folders/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"folderIds\":[2,1]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(2))
                .andExpect(jsonPath("$[1].id").value(1));
    }

    @Test
    void shouldReturnBadRequestWhenReorderListInvalid() throws Exception {
        when(folderService.reorder(List.of(1L)))
                .thenThrow(new IllegalArgumentException("folderIds must match the current set of folders"));
        mockMvc.perform(put("/api/folders/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"folderIds\":[1]}"))
                .andExpect(status().isBadRequest());
    }
}
