package org.bartram.myfeeder.service;

import lombok.RequiredArgsConstructor;
import org.bartram.myfeeder.config.MyfeederProperties;
import org.bartram.myfeeder.model.Feed;
import org.bartram.myfeeder.model.Folder;
import org.bartram.myfeeder.parser.OpmlFeed;
import org.bartram.myfeeder.repository.FeedRepository;
import org.bartram.myfeeder.repository.FolderRepository;
import org.bartram.myfeeder.scheduler.FeedPollingScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import org.bartram.myfeeder.model.FeedType;

import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
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
    private final FeedPollingScheduler feedPollingScheduler;
    private final MyfeederProperties properties;

    @Transactional
    public OpmlImportResult importOpml(InputStream inputStream) {
        List<OpmlFeed> opmlFeeds = opmlService.parseOpml(inputStream);

        Map<String, Feed> existingByUrl = feedRepository.findAll().stream()
                .collect(Collectors.toMap(Feed::getUrl, Function.identity()));
        Map<String, Folder> existingFolders = folderRepository.findAll().stream()
                .collect(Collectors.toMap(f -> f.getName().toLowerCase(), Function.identity()));

        int created = 0;
        int updated = 0;
        List<Feed> newFeedsToRegister = new ArrayList<>();

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
                newFeedsToRegister.add(saved);
                created++;
            }
        }

        // Register new feeds with scheduler after transaction commits
        if (!newFeedsToRegister.isEmpty()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    newFeedsToRegister.forEach(feedPollingScheduler::registerFeed);
                }
            });
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
