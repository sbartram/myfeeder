package org.bartram.myfeeder.controller;

import lombok.RequiredArgsConstructor;
import org.bartram.myfeeder.service.InterestPreviewService;
import org.bartram.myfeeder.service.TopicPreviewResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/interest")
@RequiredArgsConstructor
public class InterestPreviewController {

    private final InterestPreviewService previewService;

    @PostMapping("/preview")
    public TopicPreviewResponse preview(@RequestBody TopicPreviewRequest request) {
        return previewService.preview(request.articleId(), request.description(), request.topicId());
    }
}
