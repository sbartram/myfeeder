package org.bartram.myfeeder.controller;

import lombok.RequiredArgsConstructor;
import org.bartram.myfeeder.model.Folder;
import org.bartram.myfeeder.service.FolderService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/folders")
@RequiredArgsConstructor
public class FolderController {
    private final FolderService folderService;

    @GetMapping
    public List<Folder> listFolders() { return folderService.findAll(); }

    @PostMapping
    public ResponseEntity<Folder> createFolder(@RequestBody CreateFolderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(folderService.create(request.name()));
    }

    @PutMapping("/{id}")
    public Folder renameFolder(@PathVariable Long id, @RequestBody RenameFolderRequest request) {
        return folderService.rename(id, request.name());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFolder(@PathVariable Long id) { folderService.delete(id); }

    @PutMapping("/order")
    public List<Folder> reorderFolders(@RequestBody ReorderFoldersRequest request) {
        return folderService.reorder(request.folderIds());
    }
}
