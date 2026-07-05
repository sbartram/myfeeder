package org.bartram.myfeeder.service;

import lombok.RequiredArgsConstructor;
import org.bartram.myfeeder.config.MyfeederProperties;
import org.bartram.myfeeder.model.Feed;
import org.bartram.myfeeder.parser.FeedParser;
import org.bartram.myfeeder.parser.ParsedFeed;
import org.bartram.myfeeder.repository.FeedRepository;
import org.bartram.myfeeder.scheduler.FeedPollingScheduler;
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
    private final FeedPollingScheduler feedPollingScheduler;

    public List<Feed> findAll() {
        return feedRepository.findAll();
    }

    public Optional<Feed> findById(Long id) {
        return feedRepository.findById(id);
    }

    public Feed subscribe(String feedUrl, Long folderId) {
        String rawContent = feedFetcher.fetch(feedUrl).body();
        ParsedFeed parsed = feedParser.parse(rawContent);

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
        feedPollingScheduler.registerFeed(saved);
        return saved;
    }

    public Feed update(Long id, Feed updates) {
        Feed feed = feedRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Feed not found: " + id));

        if (updates.getTitle() != null) {
            feed.setTitle(updates.getTitle());
        }
        if (updates.getPollIntervalMinutes() > 0) {
            feed.setPollIntervalMinutes(updates.getPollIntervalMinutes());
        }

        Feed saved = feedRepository.save(feed);
        feedPollingScheduler.registerFeed(saved);
        return saved;
    }

    public void delete(Long id) {
        feedPollingScheduler.cancelFeed(id);
        feedRepository.deleteById(id);
    }
}
