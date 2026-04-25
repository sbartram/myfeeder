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
import org.springframework.web.client.RestClient;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeedPollingService {

    private final FeedRepository feedRepository;
    private final ArticleRepository articleRepository;
    private final FeedParser feedParser;
    private final RestClient.Builder restClientBuilder;

    public void pollFeed(Long feedId) {
        Feed feed = feedRepository.findById(feedId)
                .orElseThrow(() -> new IllegalArgumentException("Feed not found: " + feedId));

        try {
            String rawContent = fetchFeedContent(feed);
            if (rawContent == null) {
                // 304 Not Modified
                feed.setLastPolledAt(Instant.now());
                feedRepository.save(feed);
                return;
            }

            ParsedFeed parsed = feedParser.parse(rawContent);
            int newCount = 0;

            for (ParsedArticle parsedArticle : parsed.getArticles()) {
                if (!articleRepository.existsByFeedIdAndGuid(feed.getId(), parsedArticle.getGuid())) {
                    Article article = toArticle(parsedArticle, feed.getId());
                    articleRepository.save(article);
                    newCount++;
                }
            }

            feed.setLastPolledAt(Instant.now());
            feed.setLastSuccessfulPollAt(Instant.now());
            feed.setErrorCount(0);
            feed.setLastError(null);
            feedRepository.save(feed);

            log.info("Polled feed '{}': {} new articles", feed.getTitle(), newCount);
        } catch (Exception e) {
            feed.setLastPolledAt(Instant.now());
            feed.setErrorCount(feed.getErrorCount() + 1);
            feed.setLastError(e.getMessage());
            feedRepository.save(feed);
            log.warn("Failed to poll feed '{}': {}", feed.getTitle(), e.getMessage());
        }
    }

    private String fetchFeedContent(Feed feed) {
        RestClient client = restClientBuilder.build();
        return client.get()
                .uri(feed.getUrl())
                .headers(headers -> {
                    if (feed.getEtag() != null) {
                        headers.setIfNoneMatch(feed.getEtag());
                    }
                    if (feed.getLastModifiedHeader() != null) {
                        headers.set("If-Modified-Since", feed.getLastModifiedHeader());
                    }
                })
                .exchange((request, response) -> {
                    if (response.getStatusCode().value() == 304) {
                        return null;
                    }
                    String etag = response.getHeaders().getETag();
                    String lastModified = response.getHeaders().getFirst("Last-Modified");
                    if (etag != null) feed.setEtag(etag);
                    if (lastModified != null) feed.setLastModifiedHeader(lastModified);

                    return new String(response.getBody().readAllBytes());
                });
    }

    private Article toArticle(ParsedArticle parsed, Long feedId) {
        var article = new Article();
        article.setFeedId(feedId);
        article.setGuid(parsed.getGuid());
        article.setTitle(parsed.getTitle());
        article.setUrl(parsed.getUrl());
        article.setAuthor(parsed.getAuthor());
        article.setContent(parsed.getContent());
        article.setSummary(parsed.getSummary());
        article.setImageUrl(parsed.getImageUrl());
        article.setPublishedAt(parsed.getPublishedAt() != null ? parsed.getPublishedAt() : Instant.now());
        article.setFetchedAt(Instant.now());
        return article;
    }
}
