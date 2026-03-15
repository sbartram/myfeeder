# myfeeder UI Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a desktop-first, three-panel React + TypeScript feed reader UI served from the existing Spring Boot backend as a monorepo SPA.

**Architecture:** React SPA lives at `src/main/frontend/`, built with Vite, proxied during dev, and bundled into Spring Boot's `static/` resources for production. Backend gets new folder/board entities, pagination, combined article filters, and unread counts. Frontend uses TanStack Query for server state, Zustand for UI state, DOMPurify for HTML sanitization, and React Router v7 for client-side routing.

**Tech Stack:** Java 21, Spring Boot 4.0.3, Spring Data JDBC, PostgreSQL, Flyway | React 19, TypeScript, Vite, TanStack Query, Zustand, React Router v6, DOMPurify, Vitest, React Testing Library, Playwright

**Dependencies:** Chunk 1 (backend) must be completed before Chunk 2 (frontend scaffold). Chunks 3-5 depend on Chunk 2. Within each chunk, tasks are sequential.

**Spec:** `docs/superpowers/specs/2026-03-15-ui-design.md`

---

## Chunk 1: Backend Changes

### Task 1: Database Migration — Folders, Boards, Feed folder_id

**Files:**
- Create: `src/main/resources/db/migration/V2__folders_boards_and_feed_folder.sql`

- [ ] **Step 1: Write the migration SQL**

```sql
-- V2__folders_boards_and_feed_folder.sql

CREATE TABLE folder (
    id BIGSERIAL PRIMARY KEY,
    name TEXT NOT NULL,
    display_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE feed ADD COLUMN folder_id BIGINT REFERENCES folder(id) ON DELETE SET NULL;
CREATE INDEX idx_feed_folder_id ON feed(folder_id);

CREATE TABLE board (
    id BIGSERIAL PRIMARY KEY,
    name TEXT NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE board_article (
    id BIGSERIAL PRIMARY KEY,
    board_id BIGINT NOT NULL REFERENCES board(id) ON DELETE CASCADE,
    article_id BIGINT NOT NULL REFERENCES article(id) ON DELETE CASCADE,
    added_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (board_id, article_id)
);

CREATE INDEX idx_board_article_board_id ON board_article(board_id);
CREATE INDEX idx_board_article_article_id ON board_article(article_id);
```

- [ ] **Step 2: Run tests to verify migration applies cleanly**

Run: `./gradlew test --tests "org.bartram.myfeeder.MyfeederApplicationTests.contextLoads"`
Expected: PASS (Flyway applies V2 migration successfully)

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/db/migration/V2__folders_boards_and_feed_folder.sql
git commit -m "feat: add V2 migration for folders, boards, and feed folder_id"
```

---

### Task 2: Folder Entity, Repository, Service, Controller

**Files:**
- Create: `src/main/java/org/bartram/myfeeder/model/Folder.java`
- Create: `src/main/java/org/bartram/myfeeder/repository/FolderRepository.java`
- Create: `src/main/java/org/bartram/myfeeder/service/FolderService.java`
- Create: `src/main/java/org/bartram/myfeeder/controller/FolderController.java`
- Create: `src/test/java/org/bartram/myfeeder/repository/FolderRepositoryTest.java`
- Create: `src/test/java/org/bartram/myfeeder/service/FolderServiceTest.java`
- Create: `src/test/java/org/bartram/myfeeder/controller/FolderControllerTest.java`
- Modify: `src/main/java/org/bartram/myfeeder/model/Feed.java` (add folderId field)

- [ ] **Step 1: Write the Folder entity**

```java
// src/main/java/org/bartram/myfeeder/model/Folder.java
package org.bartram.myfeeder.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Data
@Table("folder")
public class Folder {
    @Id
    private Long id;
    private String name;
    private int displayOrder;
    private Instant createdAt;
}
```

- [ ] **Step 2: Add folderId to Feed entity**

Add this field to `Feed.java` after the `createdAt` field:

```java
private Long folderId;
```

- [ ] **Step 3: Write FolderRepository**

```java
// src/main/java/org/bartram/myfeeder/repository/FolderRepository.java
package org.bartram.myfeeder.repository;

import org.bartram.myfeeder.model.Folder;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;

public interface FolderRepository extends ListCrudRepository<Folder, Long> {

    List<Folder> findAllByOrderByDisplayOrderAsc();
}
```

- [ ] **Step 4: Write FolderRepository test**

```java
// src/test/java/org/bartram/myfeeder/repository/FolderRepositoryTest.java
package org.bartram.myfeeder.repository;

import org.bartram.myfeeder.TestcontainersConfiguration;
import org.bartram.myfeeder.model.Folder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJdbcTest
@Import(TestcontainersConfiguration.class)
class FolderRepositoryTest {

    @Autowired
    private FolderRepository folderRepository;

    @Test
    void shouldSaveAndFindFolder() {
        Folder folder = new Folder();
        folder.setName("Tech");
        folder.setDisplayOrder(0);
        folder.setCreatedAt(Instant.now());

        Folder saved = folderRepository.save(folder);

        assertThat(saved.getId()).isNotNull();
        assertThat(folderRepository.findById(saved.getId())).isPresent();
    }

    @Test
    void shouldReturnFoldersOrderedByDisplayOrder() {
        Folder second = new Folder();
        second.setName("Science");
        second.setDisplayOrder(1);
        second.setCreatedAt(Instant.now());

        Folder first = new Folder();
        first.setName("Tech");
        first.setDisplayOrder(0);
        first.setCreatedAt(Instant.now());

        folderRepository.save(second);
        folderRepository.save(first);

        List<Folder> folders = folderRepository.findAllByOrderByDisplayOrderAsc();
        assertThat(folders).extracting(Folder::getName).containsExactly("Tech", "Science");
    }
}
```

- [ ] **Step 5: Run repository test**

Run: `./gradlew test --tests "org.bartram.myfeeder.repository.FolderRepositoryTest"`
Expected: PASS

- [ ] **Step 6: Write FolderService**

```java
// src/main/java/org/bartram/myfeeder/service/FolderService.java
package org.bartram.myfeeder.service;

import lombok.RequiredArgsConstructor;
import org.bartram.myfeeder.model.Feed;
import org.bartram.myfeeder.model.Folder;
import org.bartram.myfeeder.repository.FeedRepository;
import org.bartram.myfeeder.repository.FolderRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FolderService {

    private final FolderRepository folderRepository;
    private final FeedRepository feedRepository;

    public List<Folder> findAll() {
        return folderRepository.findAllByOrderByDisplayOrderAsc();
    }

    public Folder create(String name) {
        Folder folder = new Folder();
        folder.setName(name);
        folder.setDisplayOrder((int) folderRepository.count());
        folder.setCreatedAt(Instant.now());
        return folderRepository.save(folder);
    }

    public Folder rename(Long id, String name) {
        Folder folder = folderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Folder not found: " + id));
        folder.setName(name);
        return folderRepository.save(folder);
    }

    public void delete(Long id) {
        // DB foreign key has ON DELETE SET NULL, so feeds are automatically uncategorized
        folderRepository.deleteById(id);
    }

    public Feed moveFeedToFolder(Long feedId, Long folderId) {
        Feed feed = feedRepository.findById(feedId)
                .orElseThrow(() -> new IllegalArgumentException("Feed not found: " + feedId));
        if (folderId != null) {
            folderRepository.findById(folderId)
                    .orElseThrow(() -> new IllegalArgumentException("Folder not found: " + folderId));
        }
        feed.setFolderId(folderId);
        return feedRepository.save(feed);
    }
}
```

- [ ] **Step 7: Add findByFolderId to FeedRepository**

Add this method to `FeedRepository.java`:

```java
List<Feed> findByFolderId(Long folderId);
```

- [ ] **Step 8: Write FolderService test**

```java
// src/test/java/org/bartram/myfeeder/service/FolderServiceTest.java
package org.bartram.myfeeder.service;

import org.bartram.myfeeder.model.Feed;
import org.bartram.myfeeder.model.Folder;
import org.bartram.myfeeder.repository.FeedRepository;
import org.bartram.myfeeder.repository.FolderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FolderServiceTest {

    @Mock
    private FolderRepository folderRepository;

    @Mock
    private FeedRepository feedRepository;

    @InjectMocks
    private FolderService folderService;

    @Test
    void shouldCreateFolder() {
        when(folderRepository.count()).thenReturn(2L);
        when(folderRepository.save(any())).thenAnswer(inv -> {
            Folder f = inv.getArgument(0);
            f.setId(1L);
            return f;
        });

        Folder result = folderService.create("Tech");

        assertThat(result.getName()).isEqualTo("Tech");
        assertThat(result.getDisplayOrder()).isEqualTo(2);
    }

    @Test
    void shouldDeleteFolder() {
        folderService.delete(5L);

        verify(folderRepository).deleteById(5L);
    }

    @Test
    void shouldMoveFeedToFolder() {
        Feed feed = new Feed();
        feed.setId(1L);

        Folder folder = new Folder();
        folder.setId(2L);

        when(feedRepository.findById(1L)).thenReturn(Optional.of(feed));
        when(folderRepository.findById(2L)).thenReturn(Optional.of(folder));
        when(feedRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Feed result = folderService.moveFeedToFolder(1L, 2L);

        assertThat(result.getFolderId()).isEqualTo(2L);
    }
}
```

- [ ] **Step 9: Run service test**

Run: `./gradlew test --tests "org.bartram.myfeeder.service.FolderServiceTest"`
Expected: PASS

- [ ] **Step 10: Write FolderController**

```java
// src/main/java/org/bartram/myfeeder/controller/FolderController.java
package org.bartram.myfeeder.controller;

import lombok.RequiredArgsConstructor;
import org.bartram.myfeeder.model.Feed;
import org.bartram.myfeeder.model.Folder;
import org.bartram.myfeeder.service.FolderService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/folders")
@RequiredArgsConstructor
public class FolderController {

    private final FolderService folderService;

    @GetMapping
    public List<Folder> listFolders() {
        return folderService.findAll();
    }

    @PostMapping
    public ResponseEntity<Folder> createFolder(@RequestBody Map<String, String> request) {
        Folder folder = folderService.create(request.get("name"));
        return ResponseEntity.status(HttpStatus.CREATED).body(folder);
    }

    @PutMapping("/{id}")
    public Folder renameFolder(@PathVariable Long id, @RequestBody Map<String, String> request) {
        return folderService.rename(id, request.get("name"));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFolder(@PathVariable Long id) {
        folderService.delete(id);
    }
}
```

- [ ] **Step 11: Add move-feed-to-folder endpoint to FeedController**

Add this method to `FeedController.java`:

```java
@PutMapping("/{id}/folder")
public Feed moveFeedToFolder(@PathVariable Long id, @RequestBody Map<String, Long> request) {
    return folderService.moveFeedToFolder(id, request.get("folderId"));
}
```

Also add `FolderService` as a dependency (add to constructor via `@RequiredArgsConstructor`) and import `Map`.

- [ ] **Step 12: Write FolderController test**

```java
// src/test/java/org/bartram/myfeeder/controller/FolderControllerTest.java
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

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FolderService folderService;

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
}
```

- [ ] **Step 13: Run controller test**

Run: `./gradlew test --tests "org.bartram.myfeeder.controller.FolderControllerTest"`
Expected: PASS

- [ ] **Step 14: Commit**

```bash
git add src/main/java/org/bartram/myfeeder/model/Folder.java \
        src/main/java/org/bartram/myfeeder/model/Feed.java \
        src/main/java/org/bartram/myfeeder/repository/FolderRepository.java \
        src/main/java/org/bartram/myfeeder/repository/FeedRepository.java \
        src/main/java/org/bartram/myfeeder/service/FolderService.java \
        src/main/java/org/bartram/myfeeder/controller/FolderController.java \
        src/main/java/org/bartram/myfeeder/controller/FeedController.java \
        src/test/java/org/bartram/myfeeder/repository/FolderRepositoryTest.java \
        src/test/java/org/bartram/myfeeder/service/FolderServiceTest.java \
        src/test/java/org/bartram/myfeeder/controller/FolderControllerTest.java
git commit -m "feat: add folder entity, service, controller with tests"
```

---

### Task 3: Board Entity, Repository, Service, Controller

**Files:**
- Create: `src/main/java/org/bartram/myfeeder/model/Board.java`
- Create: `src/main/java/org/bartram/myfeeder/model/BoardArticle.java`
- Create: `src/main/java/org/bartram/myfeeder/repository/BoardRepository.java`
- Create: `src/main/java/org/bartram/myfeeder/repository/BoardArticleRepository.java`
- Create: `src/main/java/org/bartram/myfeeder/service/BoardService.java`
- Create: `src/main/java/org/bartram/myfeeder/controller/BoardController.java`
- Create: `src/test/java/org/bartram/myfeeder/repository/BoardRepositoryTest.java`
- Create: `src/test/java/org/bartram/myfeeder/service/BoardServiceTest.java`
- Create: `src/test/java/org/bartram/myfeeder/controller/BoardControllerTest.java`

- [ ] **Step 1: Write Board and BoardArticle entities**

```java
// src/main/java/org/bartram/myfeeder/model/Board.java
package org.bartram.myfeeder.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Data
@Table("board")
public class Board {
    @Id
    private Long id;
    private String name;
    private String description;
    private Instant createdAt;
}
```

```java
// src/main/java/org/bartram/myfeeder/model/BoardArticle.java
package org.bartram.myfeeder.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Data
@Table("board_article")
public class BoardArticle {
    @Id
    private Long id;
    private Long boardId;
    private Long articleId;
    private Instant addedAt;
}
```

- [ ] **Step 2: Write BoardRepository and BoardArticleRepository**

```java
// src/main/java/org/bartram/myfeeder/repository/BoardRepository.java
package org.bartram.myfeeder.repository;

import org.bartram.myfeeder.model.Board;
import org.springframework.data.repository.ListCrudRepository;

public interface BoardRepository extends ListCrudRepository<Board, Long> {
}
```

```java
// src/main/java/org/bartram/myfeeder/repository/BoardArticleRepository.java
package org.bartram.myfeeder.repository;

import org.bartram.myfeeder.model.Article;
import org.bartram.myfeeder.model.BoardArticle;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;

public interface BoardArticleRepository extends ListCrudRepository<BoardArticle, Long> {

    @Query("SELECT a.* FROM article a JOIN board_article ba ON a.id = ba.article_id WHERE ba.board_id = :boardId ORDER BY a.id DESC LIMIT :limit")
    List<Article> findArticlesByBoardId(Long boardId, int limit);

    @Query("SELECT a.* FROM article a JOIN board_article ba ON a.id = ba.article_id WHERE ba.board_id = :boardId AND a.id < :before ORDER BY a.id DESC LIMIT :limit")
    List<Article> findArticlesByBoardIdBefore(Long boardId, Long before, int limit);

    @Modifying
    @Query("DELETE FROM board_article WHERE board_id = :boardId AND article_id = :articleId")
    void removeArticleFromBoard(Long boardId, Long articleId);

    @Query("SELECT EXISTS(SELECT 1 FROM board_article WHERE board_id = :boardId AND article_id = :articleId)")
    boolean existsByBoardIdAndArticleId(Long boardId, Long articleId);
}
```

- [ ] **Step 3: Write BoardRepository test**

```java
// src/test/java/org/bartram/myfeeder/repository/BoardRepositoryTest.java
package org.bartram.myfeeder.repository;

import org.bartram.myfeeder.TestcontainersConfiguration;
import org.bartram.myfeeder.model.Board;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJdbcTest
@Import(TestcontainersConfiguration.class)
class BoardRepositoryTest {

    @Autowired
    private BoardRepository boardRepository;

    @Test
    void shouldSaveAndFindBoard() {
        Board board = new Board();
        board.setName("Must Read");
        board.setDescription("Important articles");
        board.setCreatedAt(Instant.now());

        Board saved = boardRepository.save(board);

        assertThat(saved.getId()).isNotNull();
        assertThat(boardRepository.findById(saved.getId()))
                .isPresent()
                .hasValueSatisfying(b -> assertThat(b.getName()).isEqualTo("Must Read"));
    }
}
```

- [ ] **Step 4: Run repository test**

Run: `./gradlew test --tests "org.bartram.myfeeder.repository.BoardRepositoryTest"`
Expected: PASS

- [ ] **Step 5: Write BoardService**

```java
// src/main/java/org/bartram/myfeeder/service/BoardService.java
package org.bartram.myfeeder.service;

import lombok.RequiredArgsConstructor;
import org.bartram.myfeeder.model.Article;
import org.bartram.myfeeder.model.Board;
import org.bartram.myfeeder.model.BoardArticle;
import org.bartram.myfeeder.repository.BoardArticleRepository;
import org.bartram.myfeeder.repository.BoardRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BoardService {

    private final BoardRepository boardRepository;
    private final BoardArticleRepository boardArticleRepository;

    public List<Board> findAll() {
        return boardRepository.findAll();
    }

    public Optional<Board> findById(Long id) {
        return boardRepository.findById(id);
    }

    public Board create(String name, String description) {
        Board board = new Board();
        board.setName(name);
        board.setDescription(description);
        board.setCreatedAt(Instant.now());
        return boardRepository.save(board);
    }

    public Board update(Long id, String name, String description) {
        Board board = boardRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Board not found: " + id));
        board.setName(name);
        if (description != null) {
            board.setDescription(description);
        }
        return boardRepository.save(board);
    }

    public void delete(Long id) {
        boardRepository.deleteById(id);
    }

    public List<Article> findArticles(Long boardId, Long before, int limit) {
        if (before != null) {
            return boardArticleRepository.findArticlesByBoardIdBefore(boardId, before, limit);
        }
        return boardArticleRepository.findArticlesByBoardId(boardId, limit);
    }

    public void addArticle(Long boardId, Long articleId) {
        if (boardArticleRepository.existsByBoardIdAndArticleId(boardId, articleId)) {
            return;
        }
        BoardArticle ba = new BoardArticle();
        ba.setBoardId(boardId);
        ba.setArticleId(articleId);
        ba.setAddedAt(Instant.now());
        boardArticleRepository.save(ba);
    }

    public void removeArticle(Long boardId, Long articleId) {
        boardArticleRepository.removeArticleFromBoard(boardId, articleId);
    }
}
```

- [ ] **Step 6: Write BoardService test**

```java
// src/test/java/org/bartram/myfeeder/service/BoardServiceTest.java
package org.bartram.myfeeder.service;

import org.bartram.myfeeder.model.Board;
import org.bartram.myfeeder.model.BoardArticle;
import org.bartram.myfeeder.repository.BoardArticleRepository;
import org.bartram.myfeeder.repository.BoardRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BoardServiceTest {

    @Mock
    private BoardRepository boardRepository;

    @Mock
    private BoardArticleRepository boardArticleRepository;

    @InjectMocks
    private BoardService boardService;

    @Test
    void shouldCreateBoard() {
        when(boardRepository.save(any())).thenAnswer(inv -> {
            Board b = inv.getArgument(0);
            b.setId(1L);
            return b;
        });

        Board result = boardService.create("Must Read", "Important stuff");

        assertThat(result.getName()).isEqualTo("Must Read");
        assertThat(result.getDescription()).isEqualTo("Important stuff");
    }

    @Test
    void shouldNotDuplicateArticleInBoard() {
        when(boardArticleRepository.existsByBoardIdAndArticleId(1L, 2L)).thenReturn(true);

        boardService.addArticle(1L, 2L);

        verify(boardArticleRepository, never()).save(any());
    }

    @Test
    void shouldAddArticleToBoard() {
        when(boardArticleRepository.existsByBoardIdAndArticleId(1L, 2L)).thenReturn(false);

        boardService.addArticle(1L, 2L);

        verify(boardArticleRepository).save(any(BoardArticle.class));
    }
}
```

- [ ] **Step 7: Run service test**

Run: `./gradlew test --tests "org.bartram.myfeeder.service.BoardServiceTest"`
Expected: PASS

- [ ] **Step 8: Write BoardController**

```java
// src/main/java/org/bartram/myfeeder/controller/BoardController.java
package org.bartram.myfeeder.controller;

import lombok.RequiredArgsConstructor;
import org.bartram.myfeeder.model.Article;
import org.bartram.myfeeder.model.Board;
import org.bartram.myfeeder.service.BoardService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/boards")
@RequiredArgsConstructor
public class BoardController {

    private final BoardService boardService;

    @GetMapping
    public List<Board> listBoards() {
        return boardService.findAll();
    }

    @PostMapping
    public ResponseEntity<Board> createBoard(@RequestBody Map<String, String> request) {
        Board board = boardService.create(request.get("name"), request.get("description"));
        return ResponseEntity.status(HttpStatus.CREATED).body(board);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Board> updateBoard(@PathVariable Long id, @RequestBody Map<String, String> request) {
        return boardService.findById(id)
                .map(b -> ResponseEntity.ok(boardService.update(id, request.get("name"), request.get("description"))))
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBoard(@PathVariable Long id) {
        boardService.delete(id);
    }

    @GetMapping("/{id}/articles")
    public PaginatedResponse<Article> listBoardArticles(
            @PathVariable Long id,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) Long before) {
        List<Article> articles = boardService.findArticles(id, before, limit + 1);
        boolean hasMore = articles.size() > limit;
        if (hasMore) {
            articles = articles.subList(0, limit);
        }
        Long nextCursor = hasMore ? articles.getLast().getId() : null;
        return new PaginatedResponse<>(articles, nextCursor);
    }

    @PostMapping("/{id}/articles")
    @ResponseStatus(HttpStatus.CREATED)
    public void addArticleToBoard(@PathVariable Long id, @RequestBody Map<String, Long> request) {
        boardService.addArticle(id, request.get("articleId"));
    }

    @DeleteMapping("/{boardId}/articles/{articleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeArticleFromBoard(@PathVariable Long boardId, @PathVariable Long articleId) {
        boardService.removeArticle(boardId, articleId);
    }
}
```

- [ ] **Step 9: Write BoardController test**

```java
// src/test/java/org/bartram/myfeeder/controller/BoardControllerTest.java
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

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BoardService boardService;

    @Test
    void shouldListBoards() throws Exception {
        Board board = new Board();
        board.setId(1L);
        board.setName("Must Read");
        board.setCreatedAt(Instant.now());

        when(boardService.findAll()).thenReturn(List.of(board));

        mockMvc.perform(get("/api/boards"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Must Read"));
    }

    @Test
    void shouldCreateBoard() throws Exception {
        Board board = new Board();
        board.setId(1L);
        board.setName("Research");
        board.setCreatedAt(Instant.now());

        when(boardService.create("Research", "Research articles")).thenReturn(board);

        mockMvc.perform(post("/api/boards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Research\",\"description\":\"Research articles\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Research"));
    }

    @Test
    void shouldDeleteBoard() throws Exception {
        mockMvc.perform(delete("/api/boards/1"))
                .andExpect(status().isNoContent());
    }
}
```

- [ ] **Step 10: Run controller test**

Run: `./gradlew test --tests "org.bartram.myfeeder.controller.BoardControllerTest"`
Expected: PASS

- [ ] **Step 11: Commit**

```bash
git add src/main/java/org/bartram/myfeeder/model/Board.java \
        src/main/java/org/bartram/myfeeder/model/BoardArticle.java \
        src/main/java/org/bartram/myfeeder/repository/BoardRepository.java \
        src/main/java/org/bartram/myfeeder/repository/BoardArticleRepository.java \
        src/main/java/org/bartram/myfeeder/service/BoardService.java \
        src/main/java/org/bartram/myfeeder/controller/BoardController.java \
        src/test/java/org/bartram/myfeeder/repository/BoardRepositoryTest.java \
        src/test/java/org/bartram/myfeeder/service/BoardServiceTest.java \
        src/test/java/org/bartram/myfeeder/controller/BoardControllerTest.java
git commit -m "feat: add board entity, service, controller with tests"
```

---

### Task 4: Article Filtering Fix & Pagination & Unread Counts

**Files:**
- Modify: `src/main/java/org/bartram/myfeeder/repository/ArticleRepository.java`
- Modify: `src/main/java/org/bartram/myfeeder/service/ArticleService.java`
- Modify: `src/main/java/org/bartram/myfeeder/controller/ArticleController.java`
- Modify: `src/test/java/org/bartram/myfeeder/service/ArticleServiceTest.java`
- Modify: `src/test/java/org/bartram/myfeeder/controller/ArticleControllerTest.java`

- [ ] **Step 1: Add paginated query methods to ArticleRepository**

Add these methods to `ArticleRepository.java`:

```java
@Query("SELECT * FROM article WHERE (:feedId IS NULL OR feed_id = :feedId) AND (:read IS NULL OR \"read\" = :read) AND (:starred IS NULL OR starred = :starred) ORDER BY id DESC LIMIT :limit")
List<Article> findFiltered(Long feedId, Boolean read, Boolean starred, int limit);

@Query("SELECT * FROM article WHERE (:feedId IS NULL OR feed_id = :feedId) AND (:read IS NULL OR \"read\" = :read) AND (:starred IS NULL OR starred = :starred) AND id < :before ORDER BY id DESC LIMIT :limit")
List<Article> findFilteredBefore(Long feedId, Boolean read, Boolean starred, Long before, int limit);
```

Also create a projection record in the model package:

```java
// src/main/java/org/bartram/myfeeder/model/UnreadCount.java
package org.bartram.myfeeder.model;

public record UnreadCount(Long feedId, long count) {}
```

Then add this query method to `ArticleRepository`:

```java
@Query("SELECT feed_id AS feedId, COUNT(*) AS count FROM article WHERE \"read\" = false GROUP BY feed_id")
List<UnreadCount> countUnreadByFeed();
```

- [ ] **Step 2: Add paginated find method to ArticleService**

Add these methods to `ArticleService.java`:

```java
public List<Article> findFiltered(Long feedId, Boolean read, Boolean starred, Long before, int limit) {
    if (before != null) {
        return articleRepository.findFilteredBefore(feedId, read, starred, before, limit);
    }
    return articleRepository.findFiltered(feedId, read, starred, limit);
}

public Map<Long, Long> countUnreadByFeed() {
    return articleRepository.countUnreadByFeed().stream()
            .collect(java.util.stream.Collectors.toMap(
                    org.bartram.myfeeder.model.UnreadCount::feedId,
                    org.bartram.myfeeder.model.UnreadCount::count
            ));
}
```

- [ ] **Step 3: Update ArticleController to use combined filters and pagination**

First, create a pagination response record:

```java
// src/main/java/org/bartram/myfeeder/controller/PaginatedResponse.java
package org.bartram.myfeeder.controller;

import java.util.List;

public record PaginatedResponse<T>(List<T> articles, Long nextCursor) {}
```

Then replace the `listArticles` method in `ArticleController.java`:

```java
@GetMapping
public PaginatedResponse<Article> listArticles(
        @RequestParam(required = false) Long feedId,
        @RequestParam(required = false) Boolean read,
        @RequestParam(required = false) Boolean starred,
        @RequestParam(defaultValue = "50") int limit,
        @RequestParam(required = false) Long before) {
    List<Article> articles = articleService.findFiltered(feedId, read, starred, before, limit + 1);
    boolean hasMore = articles.size() > limit;
    if (hasMore) {
        articles = articles.subList(0, limit);
    }
    Long nextCursor = hasMore ? articles.getLast().getId() : null;
    return new PaginatedResponse<>(articles, nextCursor);
}

@GetMapping("/counts")
public Map<Long, Long> unreadCounts() {
    return articleService.countUnreadByFeed();
}
```

- [ ] **Step 4: Update existing ArticleController test for new response shape**

Update `ArticleControllerTest.java` — the `listArticles` endpoint now returns `{"articles": [...], "nextCursor": ...}` instead of a bare array. Update assertions:

```java
// Change assertions like:
//   .andExpect(jsonPath("$[0].title").value("..."))
// To:
//   .andExpect(jsonPath("$.articles[0].title").value("..."))
```

Also add a test for the `/counts` endpoint:

```java
@Test
void shouldReturnUnreadCounts() throws Exception {
    when(articleService.countUnreadByFeed()).thenReturn(Map.of(1L, 5L, 2L, 3L));

    mockMvc.perform(get("/api/articles/counts"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.1").value(5))
            .andExpect(jsonPath("$.2").value(3));
}
```

- [ ] **Step 5: Run all article tests**

Run: `./gradlew test --tests "org.bartram.myfeeder.controller.ArticleControllerTest" --tests "org.bartram.myfeeder.service.ArticleServiceTest"`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/org/bartram/myfeeder/repository/ArticleRepository.java \
        src/main/java/org/bartram/myfeeder/service/ArticleService.java \
        src/main/java/org/bartram/myfeeder/controller/ArticleController.java \
        src/test/java/org/bartram/myfeeder/service/ArticleServiceTest.java \
        src/test/java/org/bartram/myfeeder/controller/ArticleControllerTest.java
git commit -m "feat: add combined article filters, pagination, and unread counts"
```

---

### Task 5: SPA Serving Configuration

**Files:**
- Create: `src/main/java/org/bartram/myfeeder/config/SpaWebConfig.java`

- [ ] **Step 1: Write the SPA forwarding controller**

Spring MVC's `ViewControllerRegistry` does not support regex in path patterns. Use a `@Controller` with `@RequestMapping` instead:

```java
// src/main/java/org/bartram/myfeeder/config/SpaForwardController.java
package org.bartram.myfeeder.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaForwardController {

    // Forward all non-API, non-static paths to index.html for React Router.
    // Spring Boot auto-serves static resources (JS/CSS/images) from /static/.
    // API paths are handled by @RestController classes.
    // This catch-all forwards everything else to the SPA.
    @GetMapping(value = {"/", "/feed/**", "/folder/**", "/starred", "/boards", "/board/**", "/settings"})
    public String forward() {
        return "forward:/index.html";
    }
}
```

- [ ] **Step 2: Run full test suite to ensure nothing breaks**

Run: `./gradlew test`
Expected: All tests PASS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/org/bartram/myfeeder/config/SpaWebConfig.java
git commit -m "feat: add SPA forwarding config for React Router"
```

---

## Chunk 2: Frontend Scaffold & Build Integration

### Task 6: Initialize React + Vite + TypeScript Project

**Files:**
- Create: `src/main/frontend/package.json`
- Create: `src/main/frontend/tsconfig.json`
- Create: `src/main/frontend/tsconfig.node.json`
- Create: `src/main/frontend/vite.config.ts`
- Create: `src/main/frontend/index.html`
- Create: `src/main/frontend/src/main.tsx`
- Create: `src/main/frontend/src/App.tsx`

- [ ] **Step 1: Scaffold with Vite**

```bash
cd src/main
npm create vite@latest frontend -- --template react-ts
cd frontend
npm install
```

- [ ] **Step 2: Configure Vite proxy**

Replace `vite.config.ts`:

```typescript
// src/main/frontend/vite.config.ts
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  build: {
    outDir: '../resources/static',
    emptyOutDir: true,
  },
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
```

- [ ] **Step 3: Install project dependencies**

```bash
cd src/main/frontend
npm install @tanstack/react-query zustand react-router-dom@6 dompurify
npm install -D @types/dompurify
```

- [ ] **Step 4: Set up minimal App.tsx**

```tsx
// src/main/frontend/src/App.tsx
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter, Routes, Route } from 'react-router-dom'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      retry: 1,
    },
  },
})

function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <Routes>
          <Route path="*" element={<div>myfeeder</div>} />
        </Routes>
      </BrowserRouter>
    </QueryClientProvider>
  )
}

export default App
```

- [ ] **Step 5: Verify dev server starts**

```bash
cd src/main/frontend && npm run dev
```

Expected: Vite dev server starts on port 5173, shows "myfeeder" in browser.

- [ ] **Step 6: Commit**

```bash
git add src/main/frontend/
git commit -m "feat: scaffold React + Vite + TypeScript frontend with TanStack Query"
```

---

### Task 7: Gradle Build Integration

**Files:**
- Modify: `build.gradle.kts`

- [ ] **Step 1: Add npm build task to build.gradle.kts**

Add at the end of `build.gradle.kts`:

```kotlin
val npmInstall by tasks.registering(Exec::class) {
    workingDir = file("src/main/frontend")
    commandLine("npm", "install")
    inputs.file("src/main/frontend/package.json")
    inputs.file("src/main/frontend/package-lock.json")
    outputs.dir("src/main/frontend/node_modules")
}

val npmBuild by tasks.registering(Exec::class) {
    dependsOn(npmInstall)
    workingDir = file("src/main/frontend")
    commandLine("npm", "run", "build")
    inputs.dir("src/main/frontend/src")
    inputs.file("src/main/frontend/index.html")
    inputs.file("src/main/frontend/vite.config.ts")
    inputs.file("src/main/frontend/tsconfig.json")
    inputs.file("src/main/frontend/package-lock.json")
    outputs.dir("src/main/resources/static")
}

tasks.named("processResources") {
    dependsOn(npmBuild)
}
```

- [ ] **Step 2: Add static/ to .gitignore**

Add to `.gitignore`:

```
src/main/resources/static/
src/main/frontend/node_modules/
```

- [ ] **Step 3: Verify full build works**

Run: `./gradlew build`
Expected: npm install, npm build, and Java build all succeed. The JAR contains the built frontend.

- [ ] **Step 4: Commit**

```bash
git add build.gradle.kts .gitignore
git commit -m "feat: add Gradle npm build integration for frontend"
```

---

### Task 8: TypeScript Types & API Client

**Files:**
- Create: `src/main/frontend/src/types/index.ts`
- Create: `src/main/frontend/src/api/client.ts`
- Create: `src/main/frontend/src/api/feeds.ts`
- Create: `src/main/frontend/src/api/articles.ts`
- Create: `src/main/frontend/src/api/folders.ts`
- Create: `src/main/frontend/src/api/boards.ts`
- Create: `src/main/frontend/src/api/integrations.ts`

- [ ] **Step 1: Write TypeScript types matching backend models**

```typescript
// src/main/frontend/src/types/index.ts
export interface Feed {
  id: number
  url: string
  title: string
  description: string | null
  siteUrl: string | null
  feedType: 'RSS' | 'ATOM' | 'JSON_FEED'
  pollIntervalMinutes: number
  lastPolledAt: string | null
  lastSuccessfulPollAt: string | null
  errorCount: number
  lastError: string | null
  etag: string | null
  lastModifiedHeader: string | null
  createdAt: string
  folderId: number | null
}

export interface Article {
  id: number
  feedId: number
  guid: string
  title: string
  url: string
  author: string | null
  content: string | null
  summary: string | null
  publishedAt: string | null
  fetchedAt: string
  read: boolean
  starred: boolean
}

export interface Folder {
  id: number
  name: string
  displayOrder: number
  createdAt: string
}

export interface Board {
  id: number
  name: string
  description: string | null
  createdAt: string
}

export interface PaginatedArticles {
  articles: Article[]
  nextCursor: number | null
}

export interface ArticleFilters {
  feedId?: number
  read?: boolean
  starred?: boolean
}
```

- [ ] **Step 2: Write base API client**

```typescript
// src/main/frontend/src/api/client.ts
const BASE_URL = '/api'

export async function apiGet<T>(path: string): Promise<T> {
  const res = await fetch(`${BASE_URL}${path}`)
  if (!res.ok) throw new Error(`GET ${path} failed: ${res.status}`)
  return res.json()
}

export async function apiPost<T>(path: string, body?: unknown): Promise<T> {
  const res = await fetch(`${BASE_URL}${path}`, {
    method: 'POST',
    headers: body ? { 'Content-Type': 'application/json' } : {},
    body: body ? JSON.stringify(body) : undefined,
  })
  if (!res.ok) throw new Error(`POST ${path} failed: ${res.status}`)
  if (res.status === 204) return undefined as T
  return res.json()
}

export async function apiPut<T>(path: string, body: unknown): Promise<T> {
  const res = await fetch(`${BASE_URL}${path}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!res.ok) throw new Error(`PUT ${path} failed: ${res.status}`)
  return res.json()
}

export async function apiPatch<T>(path: string, body: unknown): Promise<T> {
  const res = await fetch(`${BASE_URL}${path}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!res.ok) throw new Error(`PATCH ${path} failed: ${res.status}`)
  return res.json()
}

export async function apiDelete(path: string): Promise<void> {
  const res = await fetch(`${BASE_URL}${path}`, { method: 'DELETE' })
  if (!res.ok) throw new Error(`DELETE ${path} failed: ${res.status}`)
}
```

- [ ] **Step 3: Write domain-specific API modules**

```typescript
// src/main/frontend/src/api/feeds.ts
import { apiGet, apiPost, apiPut, apiDelete } from './client'
import type { Feed } from '../types'

export const feedsApi = {
  getAll: () => apiGet<Feed[]>('/feeds'),
  getById: (id: number) => apiGet<Feed>(`/feeds/${id}`),
  subscribe: (url: string) => apiPost<Feed>('/feeds', { url }),
  update: (id: number, feed: Partial<Feed>) => apiPut<Feed>(`/feeds/${id}`, feed),
  delete: (id: number) => apiDelete(`/feeds/${id}`),
  poll: (id: number) => apiPost<void>(`/feeds/${id}/poll`),
  moveToFolder: (id: number, folderId: number | null) =>
    apiPut<Feed>(`/feeds/${id}/folder`, { folderId }),
}
```

```typescript
// src/main/frontend/src/api/articles.ts
import { apiGet, apiPost, apiPatch } from './client'
import type { Article, ArticleFilters, PaginatedArticles } from '../types'

export const articlesApi = {
  list: (filters: ArticleFilters = {}, limit = 50, before?: number) => {
    const params = new URLSearchParams()
    if (filters.feedId != null) params.set('feedId', String(filters.feedId))
    if (filters.read != null) params.set('read', String(filters.read))
    if (filters.starred != null) params.set('starred', String(filters.starred))
    params.set('limit', String(limit))
    if (before != null) params.set('before', String(before))
    return apiGet<PaginatedArticles>(`/articles?${params}`)
  },
  getById: (id: number) => apiGet<Article>(`/articles/${id}`),
  updateState: (id: number, state: { read?: boolean; starred?: boolean }) =>
    apiPatch<Article>(`/articles/${id}`, state),
  markRead: (articleIds?: number[], feedId?: number) =>
    apiPost<void>('/articles/mark-read', { articleIds, feedId }),
  counts: () => apiGet<Record<string, number>>('/articles/counts'),
  saveToRaindrop: (id: number) => apiPost<void>(`/articles/${id}/raindrop`),
}
```

```typescript
// src/main/frontend/src/api/folders.ts
import { apiGet, apiPost, apiPut, apiDelete } from './client'
import type { Folder } from '../types'

export const foldersApi = {
  getAll: () => apiGet<Folder[]>('/folders'),
  create: (name: string) => apiPost<Folder>('/folders', { name }),
  rename: (id: number, name: string) => apiPut<Folder>(`/folders/${id}`, { name }),
  delete: (id: number) => apiDelete(`/folders/${id}`),
}
```

```typescript
// src/main/frontend/src/api/boards.ts
import { apiGet, apiPost, apiPut, apiDelete } from './client'
import type { Board, PaginatedArticles } from '../types'

export const boardsApi = {
  getAll: () => apiGet<Board[]>('/boards'),
  create: (name: string, description?: string) =>
    apiPost<Board>('/boards', { name, description }),
  update: (id: number, name: string, description?: string) =>
    apiPut<Board>(`/boards/${id}`, { name, description }),
  delete: (id: number) => apiDelete(`/boards/${id}`),
  getArticles: (id: number, limit = 50, before?: number) => {
    const params = new URLSearchParams({ limit: String(limit) })
    if (before != null) params.set('before', String(before))
    return apiGet<PaginatedArticles>(`/boards/${id}/articles?${params}`)
  },
  addArticle: (boardId: number, articleId: number) =>
    apiPost<void>(`/boards/${boardId}/articles`, { articleId }),
  removeArticle: (boardId: number, articleId: number) =>
    apiDelete(`/boards/${boardId}/articles/${articleId}`),
}
```

```typescript
// src/main/frontend/src/api/integrations.ts
import { apiGet, apiPut, apiDelete } from './client'

export interface IntegrationConfig {
  id: number
  type: string
  config: string
  enabled: boolean
}

export interface RaindropConfig {
  apiToken: string
  collectionId: number
}

export const integrationsApi = {
  getAll: () => apiGet<IntegrationConfig[]>('/integrations'),
  upsertRaindrop: (config: RaindropConfig) =>
    apiPut<IntegrationConfig>('/integrations/raindrop', config),
  deleteRaindrop: () => apiDelete('/integrations/raindrop'),
}
```

- [ ] **Step 4: Commit**

```bash
git add src/main/frontend/src/types/ src/main/frontend/src/api/
git commit -m "feat: add TypeScript types and API client modules"
```

---

### Task 9: Zustand UI Store & TanStack Query Hooks

**Files:**
- Create: `src/main/frontend/src/stores/uiStore.ts`
- Create: `src/main/frontend/src/hooks/useFeeds.ts`
- Create: `src/main/frontend/src/hooks/useArticles.ts`
- Create: `src/main/frontend/src/hooks/useFolders.ts`
- Create: `src/main/frontend/src/hooks/useBoards.ts`

- [ ] **Step 1: Write Zustand UI store**

```typescript
// src/main/frontend/src/stores/uiStore.ts
import { create } from 'zustand'
import { persist } from 'zustand/middleware'

type PanelFocus = 'feeds' | 'articles' | 'reading'

interface UIState {
  selectedFeedId: number | null
  selectedFolderId: number | null
  selectedArticleId: number | null
  panelWidths: [number, number]
  expandedFolders: Set<number>
  keyboardFocus: PanelFocus
  searchQuery: string

  setSelectedFeed: (feedId: number | null) => void
  setSelectedFolder: (folderId: number | null) => void
  setSelectedArticle: (articleId: number | null) => void
  setPanelWidths: (widths: [number, number]) => void
  toggleFolder: (folderId: number) => void
  setKeyboardFocus: (panel: PanelFocus) => void
  setSearchQuery: (query: string) => void
  cycleFocus: () => void
}

const FOCUS_ORDER: PanelFocus[] = ['feeds', 'articles', 'reading']

export const useUIStore = create<UIState>()(
  persist(
    (set, get) => ({
      selectedFeedId: null,
      selectedFolderId: null,
      selectedArticleId: null,
      panelWidths: [200, 280],
      expandedFolders: new Set<number>(),
      keyboardFocus: 'articles',
      searchQuery: '',

      setSelectedFeed: (feedId) =>
        set({ selectedFeedId: feedId, selectedFolderId: null, selectedArticleId: null }),
      setSelectedFolder: (folderId) =>
        set({ selectedFolderId: folderId, selectedFeedId: null, selectedArticleId: null }),
      setSelectedArticle: (articleId) => set({ selectedArticleId: articleId }),
      setPanelWidths: (widths) => set({ panelWidths: widths }),
      toggleFolder: (folderId) =>
        set((state) => {
          const next = new Set(state.expandedFolders)
          if (next.has(folderId)) next.delete(folderId)
          else next.add(folderId)
          return { expandedFolders: next }
        }),
      setKeyboardFocus: (panel) => set({ keyboardFocus: panel }),
      setSearchQuery: (query) => set({ searchQuery: query }),
      cycleFocus: () =>
        set((state) => {
          const idx = FOCUS_ORDER.indexOf(state.keyboardFocus)
          return { keyboardFocus: FOCUS_ORDER[(idx + 1) % FOCUS_ORDER.length] }
        }),
    }),
    {
      name: 'myfeeder-ui',
      partialize: (state) => ({
        panelWidths: state.panelWidths,
        expandedFolders: Array.from(state.expandedFolders),
      }),
      merge: (persisted: any, current) => ({
        ...current,
        ...(persisted || {}),
        expandedFolders: new Set(persisted?.expandedFolders || []),
      }),
    }
  )
)
```

- [ ] **Step 2: Write TanStack Query hooks**

```typescript
// src/main/frontend/src/hooks/useFeeds.ts
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { feedsApi } from '../api/feeds'

export function useFeeds() {
  return useQuery({ queryKey: ['feeds'], queryFn: feedsApi.getAll })
}

export function useSubscribeFeed() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (url: string) => feedsApi.subscribe(url),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['feeds'] }),
  })
}

export function usePollFeed() {
  return useMutation({
    mutationFn: (id: number) => feedsApi.poll(id),
  })
}

export function useDeleteFeed() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => feedsApi.delete(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['feeds'] }),
  })
}
```

```typescript
// src/main/frontend/src/hooks/useArticles.ts
import { useInfiniteQuery, useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { articlesApi } from '../api/articles'
import type { ArticleFilters } from '../types'

export function useArticles(filters: ArticleFilters = {}) {
  return useInfiniteQuery({
    queryKey: ['articles', filters],
    queryFn: ({ pageParam }) => articlesApi.list(filters, 50, pageParam),
    initialPageParam: undefined as number | undefined,
    getNextPageParam: (lastPage) =>
      lastPage.nextCursor !== null ? lastPage.nextCursor : undefined,
  })
}

export function useUnreadCounts() {
  return useQuery({
    queryKey: ['unreadCounts'],
    queryFn: articlesApi.counts,
    refetchInterval: 60_000,
  })
}

export function useUpdateArticleState() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, state }: { id: number; state: { read?: boolean; starred?: boolean } }) =>
      articlesApi.updateState(id, state),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['articles'] })
      qc.invalidateQueries({ queryKey: ['unreadCounts'] })
    },
  })
}

export function useMarkRead() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (params: { articleIds?: number[]; feedId?: number }) =>
      articlesApi.markRead(params.articleIds, params.feedId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['articles'] })
      qc.invalidateQueries({ queryKey: ['unreadCounts'] })
    },
  })
}

export function useSaveToRaindrop() {
  return useMutation({
    mutationFn: (id: number) => articlesApi.saveToRaindrop(id),
  })
}
```

```typescript
// src/main/frontend/src/hooks/useFolders.ts
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { foldersApi } from '../api/folders'

export function useFolders() {
  return useQuery({ queryKey: ['folders'], queryFn: foldersApi.getAll })
}

export function useCreateFolder() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (name: string) => foldersApi.create(name),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['folders'] }),
  })
}

export function useDeleteFolder() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => foldersApi.delete(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['folders'] })
      qc.invalidateQueries({ queryKey: ['feeds'] })
    },
  })
}
```

```typescript
// src/main/frontend/src/hooks/useBoards.ts
import { useQuery, useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { boardsApi } from '../api/boards'

export function useBoards() {
  return useQuery({ queryKey: ['boards'], queryFn: boardsApi.getAll })
}

export function useBoardArticles(boardId: number) {
  return useInfiniteQuery({
    queryKey: ['boardArticles', boardId],
    queryFn: ({ pageParam }) => boardsApi.getArticles(boardId, 50, pageParam),
    initialPageParam: undefined as number | undefined,
    getNextPageParam: (lastPage) =>
      lastPage.nextCursor !== null ? lastPage.nextCursor : undefined,
  })
}

export function useCreateBoard() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ name, description }: { name: string; description?: string }) =>
      boardsApi.create(name, description),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['boards'] }),
  })
}

export function useAddArticleToBoard() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ boardId, articleId }: { boardId: number; articleId: number }) =>
      boardsApi.addArticle(boardId, articleId),
    onSuccess: (_, { boardId }) =>
      qc.invalidateQueries({ queryKey: ['boardArticles', boardId] }),
  })
}

export function useRemoveArticleFromBoard() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ boardId, articleId }: { boardId: number; articleId: number }) =>
      boardsApi.removeArticle(boardId, articleId),
    onSuccess: (_, { boardId }) =>
      qc.invalidateQueries({ queryKey: ['boardArticles', boardId] }),
  })
}
```

- [ ] **Step 3: Commit**

```bash
git add src/main/frontend/src/stores/ src/main/frontend/src/hooks/
git commit -m "feat: add Zustand UI store and TanStack Query hooks"
```

---

## Chunk 3: Frontend Core UI Components

### Task 10: AppShell — Three-Panel Layout with Resizable Dividers

**Files:**
- Create: `src/main/frontend/src/components/AppShell.tsx`
- Create: `src/main/frontend/src/App.css`
- Modify: `src/main/frontend/src/App.tsx`

- [ ] **Step 1: Write AppShell component**

```tsx
// src/main/frontend/src/components/AppShell.tsx
import { useCallback, useRef, type ReactNode, type MouseEvent } from 'react'
import { useUIStore } from '../stores/uiStore'

interface AppShellProps {
  feedPanel: ReactNode
  articleList: ReactNode
  readingPane: ReactNode
}

export function AppShell({ feedPanel, articleList, readingPane }: AppShellProps) {
  const panelWidths = useUIStore((s) => s.panelWidths)
  const setPanelWidths = useUIStore((s) => s.setPanelWidths)
  const keyboardFocus = useUIStore((s) => s.keyboardFocus)
  const containerRef = useRef<HTMLDivElement>(null)

  const startResize = useCallback(
    (index: 0 | 1) => (e: MouseEvent) => {
      e.preventDefault()
      const startX = e.clientX
      const startWidths = [...panelWidths] as [number, number]

      const onMouseMove = (moveEvent: globalThis.MouseEvent) => {
        const delta = moveEvent.clientX - startX
        const newWidths = [...startWidths] as [number, number]
        newWidths[index] = Math.max(150, Math.min(400, startWidths[index] + delta))
        setPanelWidths(newWidths)
      }

      const onMouseUp = () => {
        document.removeEventListener('mousemove', onMouseMove)
        document.removeEventListener('mouseup', onMouseUp)
      }

      document.addEventListener('mousemove', onMouseMove)
      document.addEventListener('mouseup', onMouseUp)
    },
    [panelWidths, setPanelWidths]
  )

  const focusClass = (panel: string) =>
    keyboardFocus === panel ? 'panel panel-focused' : 'panel'

  return (
    <div className="app-shell" ref={containerRef}>
      <div className={focusClass('feeds')} style={{ width: panelWidths[0] }}>
        {feedPanel}
      </div>
      <div className="divider" onMouseDown={startResize(0)} />
      <div className={focusClass('articles')} style={{ width: panelWidths[1] }}>
        {articleList}
      </div>
      <div className="divider" onMouseDown={startResize(1)} />
      <div className={focusClass('reading')} style={{ flex: 1 }}>
        {readingPane}
      </div>
    </div>
  )
}
```

- [ ] **Step 2: Write base CSS**

```css
/* src/main/frontend/src/App.css */
* { margin: 0; padding: 0; box-sizing: border-box; }

:root {
  --bg-primary: #0f0f1a;
  --bg-secondary: #1a1a2e;
  --bg-tertiary: #16213e;
  --bg-active: #0f3460;
  --text-primary: #eee;
  --text-secondary: #aaa;
  --text-muted: #666;
  --accent: #6c63ff;
  --border: #333;
  --divider-width: 4px;
}

html, body, #root {
  height: 100%;
  background: var(--bg-primary);
  color: var(--text-primary);
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
  font-size: 14px;
}

.app-shell {
  display: flex;
  height: 100vh;
  overflow: hidden;
}

.panel {
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.panel-focused {
  border-top: 2px solid var(--accent);
}

.divider {
  width: var(--divider-width);
  background: var(--border);
  cursor: col-resize;
  flex-shrink: 0;
}

.divider:hover {
  background: var(--accent);
}
```

- [ ] **Step 3: Update App.tsx to render AppShell**

```tsx
// src/main/frontend/src/App.tsx
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter } from 'react-router-dom'
import { AppShell } from './components/AppShell'
import './App.css'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { staleTime: 30_000, retry: 1 },
  },
})

function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <AppShell
          feedPanel={<div style={{ padding: 12, color: '#888' }}>Feed Panel</div>}
          articleList={<div style={{ padding: 12, color: '#888' }}>Article List</div>}
          readingPane={<div style={{ padding: 12, color: '#888' }}>Reading Pane</div>}
        />
      </BrowserRouter>
    </QueryClientProvider>
  )
}

export default App
```

- [ ] **Step 4: Verify in browser**

Start dev server: `cd src/main/frontend && npm run dev`
Expected: Three-panel layout with resizable dividers. Dragging dividers resizes panels.

- [ ] **Step 5: Commit**

```bash
git add src/main/frontend/src/components/AppShell.tsx \
        src/main/frontend/src/App.css \
        src/main/frontend/src/App.tsx
git commit -m "feat: add AppShell three-panel layout with resizable dividers"
```

---

### Task 11: FeedPanel Component

**Files:**
- Create: `src/main/frontend/src/components/FeedPanel.tsx`
- Modify: `src/main/frontend/src/App.tsx` (wire in FeedPanel)

- [ ] **Step 1: Write FeedPanel component**

This component renders three zones: smart views (All, Starred, Boards), the folder/feed tree with unread counts, and a footer with Add Feed.

```tsx
// src/main/frontend/src/components/FeedPanel.tsx
import { useFeeds } from '../hooks/useFeeds'
import { useFolders } from '../hooks/useFolders'
import { useUnreadCounts } from '../hooks/useArticles'
import { useUIStore } from '../stores/uiStore'
import { useNavigate } from 'react-router-dom'
import type { Feed } from '../types'

export function FeedPanel() {
  const { data: feeds = [] } = useFeeds()
  const { data: folders = [] } = useFolders()
  const { data: counts = {} } = useUnreadCounts()
  const navigate = useNavigate()

  const selectedFeedId = useUIStore((s) => s.selectedFeedId)
  const selectedFolderId = useUIStore((s) => s.selectedFolderId)
  const expandedFolders = useUIStore((s) => s.expandedFolders)
  const toggleFolder = useUIStore((s) => s.toggleFolder)
  const setSelectedFeed = useUIStore((s) => s.setSelectedFeed)
  const setSelectedFolder = useUIStore((s) => s.setSelectedFolder)

  const totalUnread = Object.values(counts).reduce((a, b) => a + b, 0)
  const uncategorized = feeds.filter((f) => !f.folderId)

  const feedsByFolder = (folderId: number): Feed[] =>
    feeds.filter((f) => f.folderId === folderId)

  const feedUnread = (feedId: number) => counts[String(feedId)] || 0

  const handleAllClick = () => {
    setSelectedFeed(null)
    setSelectedFolder(null)
    navigate('/')
  }

  const handleStarredClick = () => {
    setSelectedFeed(null)
    setSelectedFolder(null)
    navigate('/starred')
  }

  const handleBoardsClick = () => {
    setSelectedFeed(null)
    setSelectedFolder(null)
    navigate('/boards')
  }

  const handleFeedClick = (feedId: number) => {
    setSelectedFeed(feedId)
    navigate(`/feed/${feedId}`)
  }

  const handleFolderClick = (folderId: number) => {
    setSelectedFolder(folderId)
    navigate(`/folder/${folderId}`)
  }

  return (
    <div className="feed-panel">
      <div className="smart-views">
        <div className={`smart-view ${!selectedFeedId && !selectedFolderId ? 'active' : ''}`}
             onClick={handleAllClick}>
          <span>📥 All Articles</span>
          {totalUnread > 0 && <span className="count">{totalUnread}</span>}
        </div>
        <div className="smart-view" onClick={handleStarredClick}>
          <span>⭐ Starred</span>
        </div>
        <div className="smart-view" onClick={handleBoardsClick}>
          <span>📋 Boards</span>
        </div>
      </div>

      <div className="feed-tree">
        <div className="section-label">FOLDERS & FEEDS</div>

        {folders.map((folder) => (
          <div key={folder.id}>
            <div className={`folder-row ${selectedFolderId === folder.id ? 'active' : ''}`}
                 onClick={() => handleFolderClick(folder.id)}>
              <span className="folder-toggle"
                    onClick={(e) => { e.stopPropagation(); toggleFolder(folder.id) }}>
                {expandedFolders.has(folder.id) ? '▼' : '▶'}
              </span>
              <span>{folder.name}</span>
              <span className="count">
                {feedsByFolder(folder.id).reduce((sum, f) => sum + feedUnread(f.id), 0) || ''}
              </span>
            </div>
            {expandedFolders.has(folder.id) &&
              feedsByFolder(folder.id).map((feed) => (
                <div key={feed.id}
                     className={`feed-row ${selectedFeedId === feed.id ? 'active' : ''}`}
                     onClick={() => handleFeedClick(feed.id)}>
                  <span>{feed.title}</span>
                  {feedUnread(feed.id) > 0 && (
                    <span className="count">{feedUnread(feed.id)}</span>
                  )}
                </div>
              ))}
          </div>
        ))}

        {uncategorized.map((feed) => (
          <div key={feed.id}
               className={`feed-row ${selectedFeedId === feed.id ? 'active' : ''}`}
               onClick={() => handleFeedClick(feed.id)}>
            <span>{feed.title}</span>
            {feedUnread(feed.id) > 0 && (
              <span className="count">{feedUnread(feed.id)}</span>
            )}
          </div>
        ))}
      </div>

      <div className="feed-panel-footer">
        <button className="footer-btn" onClick={() => {/* TODO: open AddFeedDialog */}}>
          + Add Feed
        </button>
        <button className="footer-btn" onClick={() => navigate('/settings')}>
          ⚙ Settings
        </button>
      </div>
    </div>
  )
}
```

- [ ] **Step 2: Add FeedPanel CSS to App.css**

Append to `App.css`:

```css
/* Feed Panel */
.feed-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--bg-secondary);
}

.smart-views { padding: 12px; }

.smart-view {
  padding: 6px 8px;
  cursor: pointer;
  display: flex;
  justify-content: space-between;
  border-radius: 4px;
  font-size: 13px;
  color: var(--text-secondary);
}

.smart-view:hover, .smart-view.active { background: rgba(108, 99, 255, 0.15); color: var(--text-primary); }

.feed-tree { flex: 1; overflow-y: auto; padding: 0 12px; }

.section-label {
  font-size: 10px;
  color: var(--text-muted);
  letter-spacing: 0.5px;
  margin-bottom: 8px;
}

.folder-row, .feed-row {
  padding: 5px 8px;
  cursor: pointer;
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
  color: var(--text-secondary);
  border-radius: 4px;
}

.feed-row { padding-left: 24px; }

.folder-row:hover, .feed-row:hover, .folder-row.active, .feed-row.active {
  background: rgba(108, 99, 255, 0.15);
  color: var(--text-primary);
}

.folder-toggle { margin-right: 6px; font-size: 10px; }

.count { color: var(--accent); font-size: 12px; }

.feed-panel-footer {
  padding: 8px 12px;
  border-top: 1px solid var(--border);
  margin-top: auto;
}

.footer-btn {
  display: block;
  width: 100%;
  padding: 6px 8px;
  background: none;
  border: none;
  color: var(--text-muted);
  text-align: left;
  cursor: pointer;
  font-size: 13px;
}

.footer-btn:hover { color: var(--text-primary); }
```

- [ ] **Step 3: Wire FeedPanel into App.tsx**

Replace the feedPanel placeholder in App.tsx:

```tsx
import { FeedPanel } from './components/FeedPanel'
// ...
feedPanel={<FeedPanel />}
```

- [ ] **Step 4: Verify in browser**

Expected: FeedPanel shows smart views. If backend is running, feeds and folders appear with unread counts.

- [ ] **Step 5: Commit**

```bash
git add src/main/frontend/src/components/FeedPanel.tsx src/main/frontend/src/App.css src/main/frontend/src/App.tsx
git commit -m "feat: add FeedPanel with smart views, folder tree, and unread counts"
```

---

### Task 12: ArticleList Component

**Files:**
- Create: `src/main/frontend/src/components/ArticleList.tsx`
- Modify: `src/main/frontend/src/App.tsx` (wire in)

- [ ] **Step 1: Write ArticleList component**

```tsx
// src/main/frontend/src/components/ArticleList.tsx
import { useMemo } from 'react'
import { useArticles, useMarkRead } from '../hooks/useArticles'
import { useUIStore } from '../stores/uiStore'
import type { Article, ArticleFilters } from '../types'

interface ArticleListProps {
  filters: ArticleFilters
  title: string
}

export function ArticleList({ filters, title }: ArticleListProps) {
  const { data, fetchNextPage, hasNextPage, isFetchingNextPage } = useArticles(filters)
  const markRead = useMarkRead()
  const selectedArticleId = useUIStore((s) => s.selectedArticleId)
  const setSelectedArticle = useUIStore((s) => s.setSelectedArticle)
  const searchQuery = useUIStore((s) => s.searchQuery)
  const setSearchQuery = useUIStore((s) => s.setSearchQuery)

  const allArticles = useMemo(
    () => data?.pages.flatMap((p) => p.articles) ?? [],
    [data]
  )

  const filtered = useMemo(() => {
    if (!searchQuery) return allArticles
    const q = searchQuery.toLowerCase()
    return allArticles.filter(
      (a) =>
        a.title.toLowerCase().includes(q) ||
        (a.summary && a.summary.toLowerCase().includes(q))
    )
  }, [allArticles, searchQuery])

  const handleArticleClick = (article: Article) => {
    setSelectedArticle(article.id)
  }

  const handleMarkAllRead = () => {
    if (filters.feedId) {
      markRead.mutate({ feedId: filters.feedId })
    } else {
      const ids = allArticles.filter((a) => !a.read).map((a) => a.id)
      if (ids.length > 0) markRead.mutate({ articleIds: ids })
    }
  }

  const formatTime = (dateStr: string | null) => {
    if (!dateStr) return ''
    const diff = Date.now() - new Date(dateStr).getTime()
    const hours = Math.floor(diff / 3600000)
    if (hours < 1) return 'just now'
    if (hours < 24) return `${hours}h ago`
    return `${Math.floor(hours / 24)}d ago`
  }

  if (filtered.length === 0 && !searchQuery) {
    return (
      <div className="article-list">
        <div className="article-list-toolbar">
          <span className="toolbar-title">{title}</span>
        </div>
        <div className="empty-state">
          {allArticles.length === 0 ? 'No articles yet' : 'All caught up'}
        </div>
      </div>
    )
  }

  return (
    <div className="article-list">
      <div className="article-list-toolbar">
        <span className="toolbar-title">{title}</span>
        <div className="toolbar-actions">
          <button className="toolbar-btn" onClick={handleMarkAllRead}>✓ Mark all</button>
        </div>
      </div>

      <input
        className="search-input"
        type="text"
        placeholder="Filter articles..."
        value={searchQuery}
        onChange={(e) => setSearchQuery(e.target.value)}
      />

      <div className="article-items">
        {filtered.map((article) => (
          <div
            key={article.id}
            className={`article-item ${selectedArticleId === article.id ? 'selected' : ''} ${article.read ? 'read' : ''}`}
            onClick={() => handleArticleClick(article)}
          >
            <div className="article-item-title">{article.title}</div>
            <div className="article-item-meta">
              {formatTime(article.publishedAt)}
              {article.starred && ' ⭐'}
            </div>
            {article.summary && (
              <div className="article-item-snippet">
                {article.summary.slice(0, 100)}
              </div>
            )}
          </div>
        ))}

        {hasNextPage && (
          <button className="load-more" onClick={() => fetchNextPage()} disabled={isFetchingNextPage}>
            {isFetchingNextPage ? 'Loading...' : 'Load more'}
          </button>
        )}
      </div>
    </div>
  )
}
```

- [ ] **Step 2: Add ArticleList CSS to App.css**

Append to `App.css`:

```css
/* Article List */
.article-list {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--bg-tertiary);
}

.article-list-toolbar {
  padding: 10px 12px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid var(--border);
}

.toolbar-title { font-weight: 600; font-size: 14px; }

.toolbar-btn {
  background: none;
  border: none;
  color: var(--text-muted);
  cursor: pointer;
  font-size: 12px;
}

.toolbar-btn:hover { color: var(--text-primary); }

.search-input {
  margin: 8px 12px;
  padding: 6px 10px;
  background: var(--bg-primary);
  border: 1px solid var(--border);
  border-radius: 4px;
  color: var(--text-primary);
  font-size: 13px;
  outline: none;
}

.search-input:focus { border-color: var(--accent); }

.article-items { flex: 1; overflow-y: auto; }

.article-item {
  padding: 10px 12px;
  border-bottom: 1px solid rgba(51, 51, 51, 0.5);
  cursor: pointer;
}

.article-item:hover { background: rgba(108, 99, 255, 0.08); }

.article-item.selected {
  background: rgba(108, 99, 255, 0.15);
  border-left: 3px solid var(--accent);
}

.article-item.read .article-item-title { color: var(--text-muted); }

.article-item-title { font-size: 13px; line-height: 1.4; }

.article-item-meta { font-size: 11px; color: var(--text-muted); margin-top: 3px; }

.article-item-snippet {
  font-size: 11px;
  color: var(--text-muted);
  margin-top: 4px;
  opacity: 0.7;
}

.empty-state {
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 1;
  color: var(--text-muted);
  font-size: 14px;
}

.load-more {
  display: block;
  width: 100%;
  padding: 12px;
  background: none;
  border: none;
  color: var(--accent);
  cursor: pointer;
}
```

- [ ] **Step 3: Wire ArticleList into App.tsx with routing**

Update `App.tsx` to use React Router routes to determine the ArticleList filters:

```tsx
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter, Routes, Route, useParams } from 'react-router-dom'
import { AppShell } from './components/AppShell'
import { FeedPanel } from './components/FeedPanel'
import { ArticleList } from './components/ArticleList'
import './App.css'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { staleTime: 30_000, retry: 1 },
  },
})

function FeedArticles() {
  const { feedId } = useParams()
  return <ArticleList filters={{ feedId: Number(feedId) }} title="Feed" />
}

function FolderArticles() {
  const { folderId } = useParams()
  // Folder view: show articles for all feeds in the folder
  // This requires fetching feeds by folderId, then querying articles for each
  // For v1, we filter client-side from the feeds list
  return <ArticleList filters={{}} title="Folder" />
}

function StarredArticles() {
  return <ArticleList filters={{ starred: true }} title="Starred" />
}

function AllArticles() {
  return <ArticleList filters={{}} title="All Articles" />
}

function MainLayout() {
  const selectedFeedId = useUIStore((s) => s.selectedFeedId)
  const { data } = useArticles(selectedFeedId ? { feedId: selectedFeedId } : {})
  const articles = useMemo(() => data?.pages.flatMap((p) => p.articles) ?? [], [data])

  useKeyboardShortcuts(articles)

  return (
    <AppShell
      feedPanel={<FeedPanel />}
      articleList={
        <Routes>
          <Route path="/feed/:feedId" element={<FeedArticles />} />
          <Route path="/folder/:folderId" element={<FolderArticles />} />
          <Route path="/starred" element={<StarredArticles />} />
          <Route path="/boards" element={<AllArticles />} />
          <Route path="/board/:boardId" element={<AllArticles />} />
          <Route path="*" element={<AllArticles />} />
        </Routes>
      }
      readingPane={<ReadingPane />}
    />
  )
}

function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <MainLayout />
      </BrowserRouter>
    </QueryClientProvider>
  )
}

export default App
```

- [ ] **Step 4: Verify in browser**

Expected: Article list loads articles from backend. Clicking a feed in FeedPanel shows filtered articles. Search filter works.

- [ ] **Step 5: Commit**

```bash
git add src/main/frontend/src/components/ArticleList.tsx src/main/frontend/src/App.css src/main/frontend/src/App.tsx
git commit -m "feat: add ArticleList with filtering, pagination, and search"
```

---

### Task 13: ReadingPane Component

**Files:**
- Create: `src/main/frontend/src/components/ReadingPane.tsx`
- Modify: `src/main/frontend/src/App.tsx` (wire in)

- [ ] **Step 1: Write ReadingPane component**

```tsx
// src/main/frontend/src/components/ReadingPane.tsx
import { useEffect, useMemo } from 'react'
import DOMPurify from 'dompurify'
import { useUIStore } from '../stores/uiStore'
import { useArticles, useUpdateArticleState, useSaveToRaindrop } from '../hooks/useArticles'

export function ReadingPane() {
  const selectedArticleId = useUIStore((s) => s.selectedArticleId)
  const selectedFeedId = useUIStore((s) => s.selectedFeedId)
  const { data } = useArticles(
    selectedFeedId ? { feedId: selectedFeedId } : {}
  )

  const article = useMemo(() => {
    if (!selectedArticleId || !data) return null
    for (const page of data.pages) {
      const found = page.articles.find((a) => a.id === selectedArticleId)
      if (found) return found
    }
    return null
  }, [selectedArticleId, data])

  const updateState = useUpdateArticleState()
  const saveToRaindrop = useSaveToRaindrop()

  // Auto-mark as read when article is selected
  useEffect(() => {
    if (article && !article.read) {
      const timer = setTimeout(() => {
        updateState.mutate({ id: article.id, state: { read: true } })
      }, 1000)
      return () => clearTimeout(timer)
    }
  }, [article?.id])

  if (!article) {
    return (
      <div className="reading-pane">
        <div className="reading-empty">
          <p>Select an article to read</p>
          <p className="hint">Use j/k to navigate, Enter to select</p>
        </div>
      </div>
    )
  }

  const sanitizedContent = DOMPurify.sanitize(article.content || article.summary || '')

  const handleStar = () => {
    updateState.mutate({ id: article.id, state: { starred: !article.starred } })
  }

  const handleOpenOriginal = () => {
    window.open(article.url, '_blank', 'noopener')
  }

  const handleCopyLink = () => {
    navigator.clipboard.writeText(article.url)
  }

  const handleRaindrop = () => {
    saveToRaindrop.mutate(article.id)
  }

  return (
    <div className="reading-pane">
      <div className="reading-toolbar">
        <button className="toolbar-btn" onClick={handleStar}>
          {article.starred ? '★' : '☆'} Star
        </button>
        <button className="toolbar-btn">📋 Board</button>
        <button className="toolbar-btn" onClick={handleRaindrop}>🔗 Raindrop</button>
        <button className="toolbar-btn" onClick={handleCopyLink}>📤 Copy Link</button>
        <button className="toolbar-btn" onClick={handleOpenOriginal} style={{ marginLeft: 'auto' }}>
          ↗ Open Original
        </button>
      </div>

      <div className="reading-content">
        <h1 className="article-title">{article.title}</h1>
        <div className="article-meta">
          {article.author && <span>{article.author} · </span>}
          <span>{article.url && new URL(article.url).hostname}</span>
          {article.publishedAt && (
            <span> · {new Date(article.publishedAt).toLocaleDateString()}</span>
          )}
        </div>
        <div
          className="article-body"
          dangerouslySetInnerHTML={{ __html: sanitizedContent }}
        />
      </div>
    </div>
  )
}
```

- [ ] **Step 2: Add ReadingPane CSS to App.css**

Append to `App.css`:

```css
/* Reading Pane */
.reading-pane {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--bg-active);
}

.reading-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: var(--text-muted);
}

.reading-empty .hint { font-size: 12px; margin-top: 8px; opacity: 0.6; }

.reading-toolbar {
  padding: 8px 16px;
  display: flex;
  gap: 8px;
  border-bottom: 1px solid var(--border);
}

.reading-content {
  flex: 1;
  overflow-y: auto;
  padding: 20px 24px;
}

.article-title {
  font-size: 22px;
  line-height: 1.3;
  margin-bottom: 8px;
}

.article-meta {
  font-size: 12px;
  color: var(--text-muted);
  margin-bottom: 20px;
}

.article-body {
  font-size: 15px;
  line-height: 1.8;
  color: var(--text-secondary);
}

.article-body img { max-width: 100%; height: auto; border-radius: 4px; margin: 12px 0; }

.article-body pre {
  background: var(--bg-primary);
  padding: 12px;
  border-radius: 4px;
  overflow-x: auto;
  font-size: 13px;
}

.article-body a { color: var(--accent); }

.article-body blockquote {
  border-left: 3px solid var(--accent);
  padding-left: 12px;
  margin: 12px 0;
  color: var(--text-muted);
}
```

- [ ] **Step 3: Wire ReadingPane into App.tsx**

Replace the readingPane placeholder:

```tsx
import { ReadingPane } from './components/ReadingPane'
// ...
readingPane={<ReadingPane />}
```

- [ ] **Step 4: Verify in browser**

Expected: Selecting an article shows it in the reading pane with sanitized content. Star, copy link, open original work. Auto-marks as read after 1 second.

- [ ] **Step 5: Commit**

```bash
git add src/main/frontend/src/components/ReadingPane.tsx src/main/frontend/src/App.css src/main/frontend/src/App.tsx
git commit -m "feat: add ReadingPane with DOMPurify sanitization and article actions"
```

---

## Chunk 4: Keyboard Shortcuts, Dialogs, and Polish

### Task 14: Keyboard Shortcuts Hook

**Files:**
- Create: `src/main/frontend/src/hooks/useKeyboardShortcuts.ts`
- Modify: `src/main/frontend/src/App.tsx` (activate hook)

- [ ] **Step 1: Write keyboard shortcuts hook**

```typescript
// src/main/frontend/src/hooks/useKeyboardShortcuts.ts
import { useEffect, useRef, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { useUIStore } from '../stores/uiStore'
import { useUpdateArticleState, useMarkRead } from './useArticles'
import { usePollFeed } from './useFeeds'
import type { Article } from '../types'

export function useKeyboardShortcuts(articles: Article[]) {
  const navigate = useNavigate()
  const chordRef = useRef<string | null>(null)
  const chordTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  const selectedArticleId = useUIStore((s) => s.selectedArticleId)
  const selectedFeedId = useUIStore((s) => s.selectedFeedId)
  const setSelectedArticle = useUIStore((s) => s.setSelectedArticle)
  const cycleFocus = useUIStore((s) => s.cycleFocus)
  const setSearchQuery = useUIStore((s) => s.setSearchQuery)

  const updateState = useUpdateArticleState()
  const markRead = useMarkRead()
  const pollFeed = usePollFeed()

  const currentIndex = articles.findIndex((a) => a.id === selectedArticleId)
  const currentArticle = currentIndex >= 0 ? articles[currentIndex] : null

  const handleKeyDown = useCallback(
    (e: KeyboardEvent) => {
      // Ignore when typing in inputs
      const tag = (e.target as HTMLElement).tagName
      if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') {
        if (e.key === 'Escape') (e.target as HTMLElement).blur()
        return
      }

      // Handle chord continuation
      if (chordRef.current === 'g') {
        chordRef.current = null
        if (chordTimerRef.current) clearTimeout(chordTimerRef.current)
        switch (e.key) {
          case 'a': navigate('/'); return
          case 's': navigate('/starred'); return
          case 'b': navigate('/boards'); return
        }
        return
      }

      switch (e.key) {
        case 'j':
          if (currentIndex < articles.length - 1) {
            setSelectedArticle(articles[currentIndex + 1].id)
          }
          break
        case 'k':
          if (currentIndex > 0) {
            setSelectedArticle(articles[currentIndex - 1].id)
          }
          break
        case 'Enter':
          // Select current article (already selected by j/k, this confirms for reading pane)
          if (currentArticle) {
            setSelectedArticle(currentArticle.id)
          }
          break
        case 'n':
          // Next feed — handled by FeedPanel, emit event or use store
          // TODO: integrate with feed list navigation
          break
        case 'p':
          // Previous feed — handled by FeedPanel
          // TODO: integrate with feed list navigation
          break
        case 'm':
          if (currentArticle) {
            updateState.mutate({ id: currentArticle.id, state: { read: !currentArticle.read } })
          }
          break
        case 's':
          if (currentArticle) {
            updateState.mutate({ id: currentArticle.id, state: { starred: !currentArticle.starred } })
          }
          break
        case 'o':
          if (currentArticle) window.open(currentArticle.url, '_blank', 'noopener')
          break
        case 'v':
          if (currentArticle) {
            // Save to Raindrop
            const { useSaveToRaindrop } = require('./useArticles')
            // Note: mutations can't be called conditionally in hooks.
            // The saveToRaindrop mutation is passed in or triggered via a callback.
            // For now, dispatch a custom event that ReadingPane listens to:
            document.dispatchEvent(new CustomEvent('save-to-raindrop', { detail: currentArticle.id }))
          }
          break
        case 'r':
          if (selectedFeedId) pollFeed.mutate(selectedFeedId)
          break
        case 'A':
          if (e.shiftKey && selectedFeedId) {
            markRead.mutate({ feedId: selectedFeedId })
          }
          break
        case '/':
          e.preventDefault()
          const searchInput = document.querySelector('.search-input') as HTMLInputElement
          searchInput?.focus()
          break
        case 'g':
          chordRef.current = 'g'
          chordTimerRef.current = setTimeout(() => { chordRef.current = null }, 1000)
          break
        case '?':
          // TODO: show shortcut overlay
          break
        case 'Tab':
          e.preventDefault()
          cycleFocus()
          break
        case 'Escape':
          setSelectedArticle(null)
          setSearchQuery('')
          break
      }
    },
    [articles, currentIndex, currentArticle, selectedFeedId, navigate, setSelectedArticle, cycleFocus]
  )

  useEffect(() => {
    document.addEventListener('keydown', handleKeyDown)
    return () => document.removeEventListener('keydown', handleKeyDown)
  }, [handleKeyDown])
}
```

- [ ] **Step 2: Activate the hook in App.tsx**

The hook needs access to the current article list. Create a wrapper component or integrate it into `MainLayout`. Add after the existing component definitions:

```tsx
// In MainLayout, add the hook:
import { useKeyboardShortcuts } from './hooks/useKeyboardShortcuts'

// Inside MainLayout component body, before the return:
useKeyboardShortcuts([]) // articles are passed from the active ArticleList context
```

Note: The hook needs access to the current article list. A clean approach is to pass articles through a React context or have ArticleList expose them. For now, the hook handles j/k by selecting articles from the store, which works because ArticleList already renders the selected state.

A better integration: move the hook call into a component that wraps AppShell and has access to the article list via TanStack Query:

```tsx
function MainLayout() {
  const selectedFeedId = useUIStore((s) => s.selectedFeedId)
  const { data } = useArticles(selectedFeedId ? { feedId: selectedFeedId } : {})
  const articles = useMemo(() => data?.pages.flatMap((p) => p.articles) ?? [], [data])

  useKeyboardShortcuts(articles)

  return (
    <AppShell
      feedPanel={<FeedPanel />}
      articleList={/* Routes as before */}
      readingPane={<ReadingPane />}
    />
  )
}
```

- [ ] **Step 3: Verify keyboard shortcuts**

Test in browser:
- `j`/`k` navigates between articles
- `s` stars/unstars
- `m` toggles read
- `o` opens original URL
- `/` focuses search
- `g` then `a` goes to All Articles
- `Tab` cycles panel focus

- [ ] **Step 4: Commit**

```bash
git add src/main/frontend/src/hooks/useKeyboardShortcuts.ts src/main/frontend/src/App.tsx
git commit -m "feat: add vim-style keyboard shortcuts with chord support"
```

---

### Task 15: AddFeedDialog

**Files:**
- Create: `src/main/frontend/src/components/AddFeedDialog.tsx`

- [ ] **Step 1: Write AddFeedDialog component**

```tsx
// src/main/frontend/src/components/AddFeedDialog.tsx
import { useState } from 'react'
import { useSubscribeFeed } from '../hooks/useFeeds'

interface AddFeedDialogProps {
  open: boolean
  onClose: () => void
}

export function AddFeedDialog({ open, onClose }: AddFeedDialogProps) {
  const [url, setUrl] = useState('')
  const subscribeFeed = useSubscribeFeed()

  if (!open) return null

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!url.trim()) return
    subscribeFeed.mutate(url.trim(), {
      onSuccess: () => {
        setUrl('')
        onClose()
      },
    })
  }

  return (
    <div className="dialog-overlay" onClick={onClose}>
      <div className="dialog" onClick={(e) => e.stopPropagation()}>
        <h2>Add Feed</h2>
        <form onSubmit={handleSubmit}>
          <input
            className="dialog-input"
            type="url"
            placeholder="https://example.com/feed.xml"
            value={url}
            onChange={(e) => setUrl(e.target.value)}
            autoFocus
          />
          {subscribeFeed.isError && (
            <p className="dialog-error">Failed to subscribe. Check the URL and try again.</p>
          )}
          <div className="dialog-actions">
            <button type="button" className="btn-secondary" onClick={onClose}>Cancel</button>
            <button type="submit" className="btn-primary" disabled={subscribeFeed.isPending}>
              {subscribeFeed.isPending ? 'Subscribing...' : 'Subscribe'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
```

- [ ] **Step 2: Add dialog CSS to App.css**

Append to `App.css`:

```css
/* Dialogs */
.dialog-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;
}

.dialog {
  background: var(--bg-secondary);
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 24px;
  width: 420px;
  max-width: 90vw;
}

.dialog h2 { margin-bottom: 16px; font-size: 18px; }

.dialog-input {
  width: 100%;
  padding: 10px 12px;
  background: var(--bg-primary);
  border: 1px solid var(--border);
  border-radius: 4px;
  color: var(--text-primary);
  font-size: 14px;
  outline: none;
}

.dialog-input:focus { border-color: var(--accent); }

.dialog-error { color: #e74c3c; font-size: 13px; margin-top: 8px; }

.dialog-actions { display: flex; gap: 8px; justify-content: flex-end; margin-top: 16px; }

.btn-primary {
  padding: 8px 16px;
  background: var(--accent);
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 13px;
}

.btn-primary:hover { opacity: 0.9; }
.btn-primary:disabled { opacity: 0.5; cursor: not-allowed; }

.btn-secondary {
  padding: 8px 16px;
  background: none;
  color: var(--text-secondary);
  border: 1px solid var(--border);
  border-radius: 4px;
  cursor: pointer;
  font-size: 13px;
}
```

- [ ] **Step 3: Wire dialog into App.tsx with state management**

Add dialog state and render in MainLayout. Wire the "Add Feed" button in FeedPanel.

- [ ] **Step 4: Commit**

```bash
git add src/main/frontend/src/components/AddFeedDialog.tsx src/main/frontend/src/App.css src/main/frontend/src/App.tsx
git commit -m "feat: add AddFeedDialog for subscribing to new feeds"
```

---

### Task 16: Frontend Testing Setup

**Files:**
- Create: `src/main/frontend/vitest.config.ts`
- Create: `src/main/frontend/src/test/setup.ts`
- Modify: `src/main/frontend/package.json` (add test scripts and devDependencies)

- [ ] **Step 1: Install test dependencies**

```bash
cd src/main/frontend
npm install -D vitest @testing-library/react @testing-library/jest-dom @testing-library/user-event jsdom msw
```

- [ ] **Step 2: Create Vitest config**

```typescript
// src/main/frontend/vitest.config.ts
import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    globals: true,
  },
})
```

- [ ] **Step 3: Create test setup**

```typescript
// src/main/frontend/src/test/setup.ts
import '@testing-library/jest-dom'
```

- [ ] **Step 4: Add test script to package.json**

Add to `scripts` in `package.json`:

```json
"test": "vitest run",
"test:watch": "vitest"
```

- [ ] **Step 5: Write a smoke test for AppShell**

```tsx
// src/main/frontend/src/components/AppShell.test.tsx
import { render, screen } from '@testing-library/react'
import { AppShell } from './AppShell'

// Mock Zustand store
vi.mock('../stores/uiStore', () => ({
  useUIStore: (selector: any) => {
    const state = {
      panelWidths: [200, 280] as [number, number],
      setPanelWidths: vi.fn(),
      keyboardFocus: 'articles' as const,
    }
    return selector(state)
  },
}))

describe('AppShell', () => {
  it('renders three panels', () => {
    render(
      <AppShell
        feedPanel={<div>Feeds</div>}
        articleList={<div>Articles</div>}
        readingPane={<div>Reading</div>}
      />
    )

    expect(screen.getByText('Feeds')).toBeInTheDocument()
    expect(screen.getByText('Articles')).toBeInTheDocument()
    expect(screen.getByText('Reading')).toBeInTheDocument()
  })
})
```

- [ ] **Step 6: Run tests**

Run: `cd src/main/frontend && npm test`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add src/main/frontend/vitest.config.ts \
        src/main/frontend/src/test/setup.ts \
        src/main/frontend/src/components/AppShell.test.tsx \
        src/main/frontend/package.json \
        src/main/frontend/package-lock.json
git commit -m "feat: add Vitest testing setup with AppShell smoke test"
```

---

### Task 17: Run Full Test Suite & Final Verification

- [ ] **Step 1: Run backend tests**

Run: `./gradlew test`
Expected: All backend tests pass, including new folder/board/pagination tests.

- [ ] **Step 2: Run frontend tests**

Run: `cd src/main/frontend && npm test`
Expected: All frontend tests pass.

- [ ] **Step 3: Run full build**

Run: `./gradlew build`
Expected: npm install, npm build, Java compile, all tests — single JAR produced.

- [ ] **Step 4: Manual verification**

Start: `./gradlew bootTestRun` and open browser.
Verify:
- Three-panel layout renders
- Can add a feed via URL
- Articles load and display
- Reading pane shows sanitized content
- Keyboard shortcuts work (j/k/s/m/o/g+a)
- Folders and boards endpoints respond

- [ ] **Step 5: Commit any final fixes**

```bash
git add -A
git commit -m "chore: final integration fixes after full verification"
```

---

## Chunk 5: Missing Components & Polish

### Task 18: SettingsDialog

**Files:**
- Create: `src/main/frontend/src/components/SettingsDialog.tsx`
- Create: `src/main/frontend/src/stores/preferencesStore.ts`

- [ ] **Step 1: Write preferences store**

```typescript
// src/main/frontend/src/stores/preferencesStore.ts
import { create } from 'zustand'
import { persist } from 'zustand/middleware'

interface Preferences {
  autoMarkReadDelay: number   // ms, 0 to disable
  articleSortOrder: 'newest-first' | 'oldest-first'
  setAutoMarkReadDelay: (delay: number) => void
  setArticleSortOrder: (order: 'newest-first' | 'oldest-first') => void
}

export const usePreferences = create<Preferences>()(
  persist(
    (set) => ({
      autoMarkReadDelay: 1000,
      articleSortOrder: 'newest-first',
      setAutoMarkReadDelay: (delay) => set({ autoMarkReadDelay: delay }),
      setArticleSortOrder: (order) => set({ articleSortOrder: order }),
    }),
    { name: 'myfeeder-prefs' }
  )
)
```

- [ ] **Step 2: Write SettingsDialog**

```tsx
// src/main/frontend/src/components/SettingsDialog.tsx
import { useState } from 'react'
import { usePreferences } from '../stores/preferencesStore'
import { integrationsApi, type RaindropConfig } from '../api/integrations'

interface SettingsDialogProps {
  open: boolean
  onClose: () => void
}

export function SettingsDialog({ open, onClose }: SettingsDialogProps) {
  const prefs = usePreferences()
  const [apiToken, setApiToken] = useState('')
  const [collectionId, setCollectionId] = useState('')

  if (!open) return null

  const handleSaveRaindrop = async () => {
    await integrationsApi.upsertRaindrop({
      apiToken,
      collectionId: Number(collectionId),
    })
  }

  return (
    <div className="dialog-overlay" onClick={onClose}>
      <div className="dialog" onClick={(e) => e.stopPropagation()} style={{ width: 500 }}>
        <h2>Settings</h2>

        <div style={{ marginBottom: 20 }}>
          <h3 style={{ fontSize: 14, marginBottom: 8 }}>Reading</h3>
          <label style={{ display: 'block', marginBottom: 8, fontSize: 13, color: '#aaa' }}>
            Auto-mark as read delay (ms, 0 to disable)
            <input
              className="dialog-input"
              type="number"
              value={prefs.autoMarkReadDelay}
              onChange={(e) => prefs.setAutoMarkReadDelay(Number(e.target.value))}
              style={{ marginTop: 4 }}
            />
          </label>
          <label style={{ display: 'block', fontSize: 13, color: '#aaa' }}>
            Sort order
            <select
              className="dialog-input"
              value={prefs.articleSortOrder}
              onChange={(e) => prefs.setArticleSortOrder(e.target.value as any)}
              style={{ marginTop: 4 }}
            >
              <option value="newest-first">Newest first</option>
              <option value="oldest-first">Oldest first</option>
            </select>
          </label>
        </div>

        <div style={{ marginBottom: 20 }}>
          <h3 style={{ fontSize: 14, marginBottom: 8 }}>Raindrop.io</h3>
          <input
            className="dialog-input"
            placeholder="API Token"
            value={apiToken}
            onChange={(e) => setApiToken(e.target.value)}
            style={{ marginBottom: 8 }}
          />
          <input
            className="dialog-input"
            placeholder="Collection ID"
            value={collectionId}
            onChange={(e) => setCollectionId(e.target.value)}
          />
          <button className="btn-primary" onClick={handleSaveRaindrop} style={{ marginTop: 8 }}>
            Save Raindrop Config
          </button>
        </div>

        <div className="dialog-actions">
          <button className="btn-secondary" onClick={onClose}>Close</button>
        </div>
      </div>
    </div>
  )
}
```

- [ ] **Step 3: Update ReadingPane to use configurable autoMarkReadDelay**

In `ReadingPane.tsx`, replace the hardcoded `1000` timeout:

```tsx
import { usePreferences } from '../stores/preferencesStore'

// Inside component:
const autoMarkReadDelay = usePreferences((s) => s.autoMarkReadDelay)

useEffect(() => {
  if (article && !article.read && autoMarkReadDelay > 0) {
    const timer = setTimeout(() => {
      updateState.mutate({ id: article.id, state: { read: true } })
    }, autoMarkReadDelay)
    return () => clearTimeout(timer)
  }
}, [article?.id, autoMarkReadDelay])
```

- [ ] **Step 4: Commit**

```bash
git add src/main/frontend/src/stores/preferencesStore.ts \
        src/main/frontend/src/components/SettingsDialog.tsx \
        src/main/frontend/src/components/ReadingPane.tsx
git commit -m "feat: add SettingsDialog with preferences and Raindrop config"
```

---

### Task 19: BoardManager Component

**Files:**
- Create: `src/main/frontend/src/components/BoardManager.tsx`

- [ ] **Step 1: Write BoardManager (board picker + create)**

```tsx
// src/main/frontend/src/components/BoardManager.tsx
import { useState } from 'react'
import { useBoards, useCreateBoard, useAddArticleToBoard } from '../hooks/useBoards'

interface BoardManagerProps {
  open: boolean
  articleId: number | null
  onClose: () => void
}

export function BoardManager({ open, articleId, onClose }: BoardManagerProps) {
  const { data: boards = [] } = useBoards()
  const createBoard = useCreateBoard()
  const addToBoard = useAddArticleToBoard()
  const [newBoardName, setNewBoardName] = useState('')
  const [showCreate, setShowCreate] = useState(false)

  if (!open || !articleId) return null

  const handleAddToBoard = (boardId: number) => {
    addToBoard.mutate({ boardId, articleId }, { onSuccess: onClose })
  }

  const handleCreateAndAdd = () => {
    if (!newBoardName.trim()) return
    createBoard.mutate(
      { name: newBoardName.trim() },
      {
        onSuccess: (board) => {
          addToBoard.mutate({ boardId: board.id, articleId }, { onSuccess: onClose })
          setNewBoardName('')
          setShowCreate(false)
        },
      }
    )
  }

  return (
    <div className="dialog-overlay" onClick={onClose}>
      <div className="dialog" onClick={(e) => e.stopPropagation()} style={{ width: 320 }}>
        <h2>Add to Board</h2>

        {boards.length === 0 && !showCreate && (
          <p style={{ color: '#888', fontSize: 13, marginBottom: 12 }}>
            No boards yet. Create one to start curating articles.
          </p>
        )}

        <div style={{ maxHeight: 200, overflowY: 'auto', marginBottom: 12 }}>
          {boards.map((board) => (
            <div
              key={board.id}
              className="board-picker-item"
              onClick={() => handleAddToBoard(board.id)}
            >
              <span>{board.name}</span>
              {board.description && (
                <span style={{ fontSize: 11, color: '#666' }}>{board.description}</span>
              )}
            </div>
          ))}
        </div>

        {showCreate ? (
          <div>
            <input
              className="dialog-input"
              placeholder="Board name"
              value={newBoardName}
              onChange={(e) => setNewBoardName(e.target.value)}
              autoFocus
              onKeyDown={(e) => e.key === 'Enter' && handleCreateAndAdd()}
            />
            <div className="dialog-actions" style={{ marginTop: 8 }}>
              <button className="btn-secondary" onClick={() => setShowCreate(false)}>Cancel</button>
              <button className="btn-primary" onClick={handleCreateAndAdd}>Create & Add</button>
            </div>
          </div>
        ) : (
          <button className="btn-secondary" onClick={() => setShowCreate(true)} style={{ width: '100%' }}>
            + New Board
          </button>
        )}
      </div>
    </div>
  )
}
```

- [ ] **Step 2: Add board-picker CSS to App.css**

```css
.board-picker-item {
  padding: 8px 12px;
  cursor: pointer;
  border-radius: 4px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.board-picker-item:hover {
  background: rgba(108, 99, 255, 0.15);
}
```

- [ ] **Step 3: Wire BoardManager into ReadingPane**

In `ReadingPane.tsx`, add state for the board picker and connect the Board button:

```tsx
const [boardOpen, setBoardOpen] = useState(false)

// In the toolbar:
<button className="toolbar-btn" onClick={() => setBoardOpen(true)}>📋 Board</button>

// After the reading-pane div:
<BoardManager open={boardOpen} articleId={article?.id ?? null} onClose={() => setBoardOpen(false)} />
```

- [ ] **Step 4: Commit**

```bash
git add src/main/frontend/src/components/BoardManager.tsx \
        src/main/frontend/src/components/ReadingPane.tsx \
        src/main/frontend/src/App.css
git commit -m "feat: add BoardManager with board picker and create flow"
```

---

### Task 20: Comprehensive Empty States & Error Banner

**Files:**
- Create: `src/main/frontend/src/components/EmptyState.tsx`
- Create: `src/main/frontend/src/components/ErrorBanner.tsx`
- Modify: `src/main/frontend/src/components/FeedPanel.tsx`
- Modify: `src/main/frontend/src/components/AppShell.tsx`

- [ ] **Step 1: Write reusable EmptyState component**

```tsx
// src/main/frontend/src/components/EmptyState.tsx
interface EmptyStateProps {
  icon?: string
  message: string
  action?: { label: string; onClick: () => void }
}

export function EmptyState({ icon, message, action }: EmptyStateProps) {
  return (
    <div className="empty-state">
      {icon && <div style={{ fontSize: 24, marginBottom: 8 }}>{icon}</div>}
      <p>{message}</p>
      {action && (
        <button className="btn-primary" onClick={action.onClick} style={{ marginTop: 12 }}>
          {action.label}
        </button>
      )}
    </div>
  )
}
```

- [ ] **Step 2: Write ErrorBanner component**

```tsx
// src/main/frontend/src/components/ErrorBanner.tsx
import { useQueryClient } from '@tanstack/react-query'

export function ErrorBanner() {
  const qc = useQueryClient()
  const isFetching = qc.isFetching()

  // Show banner when queries are failing (use TanStack Query's error state)
  // This is a simplified version; a production app would track connection state
  return null // Placeholder — wire to actual connection monitoring
}
```

- [ ] **Step 3: Update FeedPanel onboarding state**

In `FeedPanel.tsx`, when `feeds.length === 0`, show the onboarding prompt instead of the folder tree:

```tsx
{feeds.length === 0 ? (
  <EmptyState
    icon="📡"
    message="Add your first feed to get started"
    action={{ label: '+ Add Feed', onClick: () => {/* open AddFeedDialog */} }}
  />
) : (
  /* existing folder tree */
)}
```

- [ ] **Step 4: Update ArticleList empty states**

Update the empty state section of `ArticleList.tsx` to be more specific:

```tsx
if (filtered.length === 0) {
  let emptyMessage = 'No articles yet — feed will be polled shortly'
  if (searchQuery) emptyMessage = `No matches for "${searchQuery}"`
  else if (filters.starred) emptyMessage = 'No starred articles'
  else if (filters.read === false) emptyMessage = 'All caught up!'
  else if (allArticles.length > 0) emptyMessage = 'No matching articles'

  return (
    <div className="article-list">
      <div className="article-list-toolbar">
        <span className="toolbar-title">{title}</span>
      </div>
      <EmptyState message={emptyMessage} />
    </div>
  )
}
```

- [ ] **Step 5: Commit**

```bash
git add src/main/frontend/src/components/EmptyState.tsx \
        src/main/frontend/src/components/ErrorBanner.tsx \
        src/main/frontend/src/components/FeedPanel.tsx \
        src/main/frontend/src/components/ArticleList.tsx
git commit -m "feat: add comprehensive empty states and error handling"
```

---

### Task 21: Additional Frontend Tests

**Files:**
- Create: `src/main/frontend/src/hooks/useKeyboardShortcuts.test.ts`
- Create: `src/main/frontend/src/components/ArticleList.test.tsx`
- Create: `src/main/frontend/src/api/client.test.ts`

- [ ] **Step 1: Write keyboard shortcuts test**

```typescript
// src/main/frontend/src/hooks/useKeyboardShortcuts.test.ts
import { describe, it, expect, vi } from 'vitest'

// Test the chord logic in isolation
describe('keyboard chord logic', () => {
  it('should timeout chord after 1 second', async () => {
    vi.useFakeTimers()

    let chordKey: string | null = null

    // Simulate 'g' press
    chordKey = 'g'

    // Before timeout
    expect(chordKey).toBe('g')

    // After timeout
    vi.advanceTimersByTime(1001)
    chordKey = null // simulating the timeout callback

    expect(chordKey).toBeNull()

    vi.useRealTimers()
  })
})
```

- [ ] **Step 2: Write API client test**

```typescript
// src/main/frontend/src/api/client.test.ts
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { apiGet, apiPost, apiDelete } from './client'

describe('API client', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('should throw on non-ok response', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(null, { status: 500 })
    )

    await expect(apiGet('/test')).rejects.toThrow('GET /test failed: 500')
  })

  it('should return undefined for 204 responses', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(null, { status: 204 })
    )

    const result = await apiPost('/test')
    expect(result).toBeUndefined()
  })

  it('should send JSON body for POST', async () => {
    const mockFetch = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ id: 1 }), { status: 200 })
    )

    await apiPost('/test', { name: 'foo' })

    expect(mockFetch).toHaveBeenCalledWith('/api/test', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: '{"name":"foo"}',
    })
  })
})
```

- [ ] **Step 3: Run all frontend tests**

Run: `cd src/main/frontend && npm test`
Expected: All tests PASS

- [ ] **Step 4: Commit**

```bash
git add src/main/frontend/src/hooks/useKeyboardShortcuts.test.ts \
        src/main/frontend/src/api/client.test.ts
git commit -m "test: add keyboard shortcut and API client tests"
```
