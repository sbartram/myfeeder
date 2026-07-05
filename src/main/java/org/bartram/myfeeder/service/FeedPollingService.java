package org.bartram.myfeeder.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bartram.myfeeder.model.Article;
import org.bartram.myfeeder.model.Feed;
import org.bartram.myfeeder.parser.FeedParser;
import org.bartram.myfeeder.parser.ParsedArticle;
import org.bartram.myfeeder.parser.ParsedFeed;
import org.bartram.myfeeder.repository.ArticleRepository;
import org.bartram.myfeeder.repository.FeedRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeedPollingService {

    private final FeedRepository feedRepository;
    private final ArticleRepository articleRepository;
    private final FeedParser feedParser;
    private final FeedFetcher feedFetcher;

    public void pollFeed(Long feedId) {
        Feed feed = feedRepository.findById(feedId)
                .orElseThrow(() -> new NotFoundException("Feed not found: " + feedId));

        try {
            FetchResult result = feedFetcher.fetch(feed.getUrl(), feed.getEtag(), feed.getLastModifiedHeader());
            feed.setLastPolledAt(Instant.now());
            if (result.notModified()) {
                feedRepository.save(feed);
                return;
            }
            if (result.etag() != null) {
                feed.setEtag(result.etag());
            }
            if (result.lastModified() != null) {
                feed.setLastModifiedHeader(result.lastModified());
            }

            ParsedFeed parsed = feedParser.parse(result.body());
            int newCount = 0;

            for (ParsedArticle parsedArticle : parsed.articles()) {
                if (!articleRepository.existsByFeedIdAndGuid(feed.getId(), parsedArticle.guid())) {
                    Article article = toArticle(parsedArticle, feed.getId());
                    articleRepository.save(article);
                    newCount++;
                }
            }

            feed.setLastSuccessfulPollAt(Instant.now());
            feed.setErrorCount(0);
            feed.setLastError(null);
            feedRepository.save(feed);

            log.info("Polled feed '{}': {} new articles", feed.getTitle(), newCount);
        } catch (Exception e) {
            feed.setLastPolledAt(Instant.now());
            feed.setErrorCount(feed.getErrorCount() + 1);
            feed.setLastError(e.toString());
            feedRepository.save(feed);
            log.warn("Failed to poll feed '{}': {}", feed.getTitle(), e.toString());
        }
    }

    private Article toArticle(ParsedArticle parsed, Long feedId) {
        var article = new Article();
        article.setFeedId(feedId);
        article.setGuid(parsed.guid());
        article.setTitle(parsed.title());
        article.setUrl(parsed.url());
        article.setAuthor(parsed.author());
        article.setContent(parsed.content());
        article.setSummary(parsed.summary());
        article.setImageUrl(parsed.imageUrl());
        article.setPublishedAt(parsed.publishedAt() != null ? parsed.publishedAt() : Instant.now());
        article.setFetchedAt(Instant.now());
        return article;
    }
}
