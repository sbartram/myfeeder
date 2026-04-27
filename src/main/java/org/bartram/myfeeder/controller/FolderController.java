package org.bartram.myfeeder.controller;

import lombok.RequiredArgsConstructor;
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
    public List<Folder> listFolders() { return folderService.findAll(); }

    @PostMapping
    public ResponseEntity<Folder> createFolder(@RequestBody Map<String, String> request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(folderService.create(request.get("name")));
    }

    @PutMapping("/{id}")
    public Folder renameFolder(@PathVariable Long id, @RequestBody Map<String, String> request) {
        return folderService.rename(id, request.get("name"));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFolder(@PathVariable Long id) { folderService.delete(id); }

    @PutMapping("/order")
    public List<Folder> reorderFolders(@RequestBody Map<String, List<Long>> request) {
        return folderService.reorder(request.get("folderIds"));
    }
}
