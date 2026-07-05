package org.bartram.myfeeder.controller;

import lombok.RequiredArgsConstructor;
import org.bartram.myfeeder.service.FeedService;
import org.bartram.myfeeder.service.FolderService;
import org.bartram.myfeeder.service.OpmlImportResult;
import org.bartram.myfeeder.service.OpmlImportService;
import org.bartram.myfeeder.service.OpmlService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/opml")
@RequiredArgsConstructor
public class OpmlController {

    private final OpmlImportService opmlImportService;
    private final OpmlService opmlService;
    private final FeedService feedService;
    private final FolderService folderService;

    @PostMapping("/import")
    public OpmlImportResult importOpml(@RequestParam("file") MultipartFile file) throws IOException {
        return opmlImportService.importOpml(file.getInputStream());
    }

    @GetMapping("/export")
    public ResponseEntity<String> exportOpml() {
        String opml = opmlService.generateOpml(feedService.findAll(), folderService.findAll());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"myfeeder-export.opml\"")
                .body(opml);
    }
}
