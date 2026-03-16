package org.bartram.myfeeder.controller;
import org.bartram.myfeeder.model.Board;
import org.bartram.myfeeder.service.BoardService;
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

@WebMvcTest(BoardController.class)
class BoardControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private BoardService boardService;

    @Test
    void shouldListBoards() throws Exception {
        Board board = new Board(); board.setId(1L); board.setName("Must Read"); board.setCreatedAt(Instant.now());
        when(boardService.findAll()).thenReturn(List.of(board));
        mockMvc.perform(get("/api/boards")).andExpect(status().isOk()).andExpect(jsonPath("$[0].name").value("Must Read"));
    }

    @Test
    void shouldCreateBoard() throws Exception {
        Board board = new Board(); board.setId(1L); board.setName("Research"); board.setCreatedAt(Instant.now());
        when(boardService.create("Research", "Research articles")).thenReturn(board);
        mockMvc.perform(post("/api/boards").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Research\",\"description\":\"Research articles\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.name").value("Research"));
    }

    @Test
    void shouldDeleteBoard() throws Exception {
        mockMvc.perform(delete("/api/boards/1")).andExpect(status().isNoContent());
    }

    @Test
    void shouldGetOrCreateBoardByName() throws Exception {
        Board board = new Board(); board.setId(1L); board.setName("Read Later"); board.setCreatedAt(Instant.now());
        when(boardService.getOrCreateByName("Read Later")).thenReturn(board);

        mockMvc.perform(post("/api/boards/by-name")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Read Later\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Read Later"));
    }
}
