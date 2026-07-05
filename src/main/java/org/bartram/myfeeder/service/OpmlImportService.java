package org.bartram.myfeeder.service;

import lombok.RequiredArgsConstructor;
import org.bartram.myfeeder.config.MyfeederProperties;
import org.bartram.myfeeder.event.FeedSavedEvent;
import org.bartram.myfeeder.model.Feed;
import org.bartram.myfeeder.model.Folder;
import org.bartram.myfeeder.parser.OpmlFeed;
import org.bartram.myfeeder.repository.FeedRepository;
import org.bartram.myfeeder.repository.FolderRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.bartram.myfeeder.model.FeedType;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OpmlImportService {

    private final OpmlService opmlService;
    private final FeedRepository feedRepository;
    private final FolderRepository folderRepository;
    private final FolderService folderService;
    private final ApplicationEventPublisher eventPublisher;
    private final MyfeederProperties properties;

    @Transactional
    public OpmlImportResult importOpml(InputStream inputStream) {
        List<OpmlFeed> opmlFeeds = opmlService.parseOpml(inputStream);

        Map<String, Feed> existingByUrl = feedRepository.findAll().stream()
                .collect(Collectors.toMap(Feed::getUrl, Function.identity(), (a, b) -> a));
        Map<String, Folder> existingFolders = folderRepository.findAll().stream()
                .collect(Collectors.toMap(f -> f.getName().toLowerCase(), Function.identity()));

        int created = 0;
        int updated = 0;

        for (OpmlFeed opmlFeed : opmlFeeds) {
            Long folderId = resolveFolder(opmlFeed.folderName(), existingFolders);
            Feed existing = existingByUrl.get(opmlFeed.xmlUrl());

            if (existing != null) {
                existing.setTitle(opmlFeed.title());
                existing.setFolderId(folderId);
                feedRepository.save(existing);
                updated++;
            } else {
                Feed feed = new Feed();
                feed.setUrl(opmlFeed.xmlUrl());
                feed.setTitle(opmlFeed.title());
                feed.setSiteUrl(opmlFeed.htmlUrl() != null && !opmlFeed.htmlUrl().isEmpty()
                        ? opmlFeed.htmlUrl() : null);
                feed.setFolderId(folderId);
                feed.setFeedType(FeedType.RSS);
                feed.setPollIntervalMinutes(properties.getPolling().getDefaultIntervalMinutes());
                feed.setCreatedAt(Instant.now());
                Feed saved = feedRepository.save(feed);
                eventPublisher.publishEvent(new FeedSavedEvent(saved));
                created++;
            }
        }

        return new OpmlImportResult(created, updated, opmlFeeds.size());
    }

    private Long resolveFolder(String folderName, Map<String, Folder> existingFolders) {
        if (folderName == null || folderName.isBlank()) {
            return null;
        }

        Folder existing = existingFolders.get(folderName.toLowerCase());
        if (existing != null) {
            return existing.getId();
        }

        Folder created = folderService.create(folderName);
        existingFolders.put(folderName.toLowerCase(), created);
        return created.getId();
    }
}
