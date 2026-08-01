package org.bartram.myfeeder.service;

import lombok.RequiredArgsConstructor;
import org.bartram.myfeeder.config.MyfeederProperties;
import org.bartram.myfeeder.controller.FeedUpdateRequest;
import org.bartram.myfeeder.event.FeedDeletedEvent;
import org.bartram.myfeeder.event.FeedSavedEvent;
import org.bartram.myfeeder.model.Feed;
import org.bartram.myfeeder.parser.FeedParser;
import org.bartram.myfeeder.parser.ParsedFeed;
import org.bartram.myfeeder.repository.FeedRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class FeedService {

    private final FeedRepository feedRepository;
    private final FeedParser feedParser;
    private final FeedFetcher feedFetcher;
    private final MyfeederProperties properties;
    private final ApplicationEventPublisher eventPublisher;

    public List<Feed> findAll() {
        return feedRepository.findAll();
    }

    public Optional<Feed> findById(Long id) {
        return feedRepository.findById(id);
    }

    public Feed subscribe(String feedUrl, Long folderId) {
        FetchResult fetched = feedFetcher.fetch(feedUrl);
        ParsedFeed parsed = feedParser.parse(fetched.body(), fetched.contentType());

        var feed = new Feed();
        feed.setUrl(feedUrl);
        feed.setTitle(parsed.title());
        feed.setDescription(parsed.description());
        feed.setSiteUrl(parsed.siteUrl());
        feed.setFeedType(parsed.feedType());
        feed.setPollIntervalMinutes(properties.getPolling().getDefaultIntervalMinutes());
        feed.setCreatedAt(Instant.now());
        feed.setFolderId(folderId);

        Feed saved = feedRepository.save(feed);
        eventPublisher.publishEvent(new FeedSavedEvent(saved));
        return saved;
    }

    public Feed update(Long id, FeedUpdateRequest updates) {
        Feed feed = feedRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Feed not found: " + id));

        if (updates.title() != null) {
            feed.setTitle(updates.title());
        }
        if (updates.pollIntervalMinutes() != null) {
            if (updates.pollIntervalMinutes() < 1) {
                throw new IllegalArgumentException("pollIntervalMinutes must be >= 1");
            }
            feed.setPollIntervalMinutes(updates.pollIntervalMinutes());
        }

        Feed saved = feedRepository.save(feed);
        eventPublisher.publishEvent(new FeedSavedEvent(saved));
        return saved;
    }

    public void delete(Long id) {
        feedRepository.deleteById(id);
        eventPublisher.publishEvent(new FeedDeletedEvent(id));
    }
}
