package org.bartram.myfeeder.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bartram.myfeeder.parser.OpmlParseException;
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

@Slf4j
@RestController
@RequestMapping("/api/opml")
@RequiredArgsConstructor
public class OpmlController {

    private final OpmlImportService opmlImportService;
    private final OpmlService opmlService;
    private final FeedService feedService;
    private final FolderService folderService;

    @PostMapping("/import")
    public ResponseEntity<OpmlImportResult> importOpml(@RequestParam("file") MultipartFile file) {
        try {
            OpmlImportResult result = opmlImportService.importOpml(file.getInputStream());
            return ResponseEntity.ok(result);
        } catch (OpmlParseException e) {
            log.error("OPML parse error", e);
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("OPML import error", e);
            return ResponseEntity.badRequest().build();
        }
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
