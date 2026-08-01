package org.bartram.myfeeder.service;

import lombok.RequiredArgsConstructor;
import net.dankito.readability4j.Readability4J;
import org.bartram.myfeeder.model.Article;
import org.bartram.myfeeder.parser.FeedParseException;
import org.bartram.myfeeder.repository.ArticleRepository;
import org.jsoup.Jsoup;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;

/**
 * Reader view: fetches an article's original page and extracts its readable content,
 * so dark or content-less pages can be rendered with the app's own styling.
 * Fetches go through FeedFetcher to keep the SSRF guard, size cap, and User-Agent.
 */
@Service
@RequiredArgsConstructor
public class ArticleExtractionService {

    private final ArticleRepository articleRepository;
    private final FeedFetcher feedFetcher;

    /**
     * Returns the readable content of the article's original page, fetching and extracting
     * on first request and serving the cached copy afterwards.
     *
     * @throws NotFoundException        if the article doesn't exist (404)
     * @throws IllegalArgumentException if the article has no URL (400)
     * @throws FeedFetchException       if the page fetch fails (422)
     * @throws FeedParseException       if no readable content can be extracted (422)
     */
    public ExtractedContent extract(Long articleId) {
        Article article = articleRepository.findById(articleId)
                .orElseThrow(() -> new NotFoundException("Article not found: " + articleId));
        if (article.getUrl() == null || article.getUrl().isBlank()) {
            throw new IllegalArgumentException("Article " + articleId + " has no URL to extract from");
        }
        String cached = articleRepository.findExtractedContent(articleId);
        if (cached != null) {
            return new ExtractedContent(article.getTitle(), cached);
        }

        FetchResult fetched = feedFetcher.fetch(article.getUrl());
        String html = decode(fetched, article.getUrl());
        net.dankito.readability4j.Article extracted;
        try {
            extracted = new Readability4J(article.getUrl(), html).parse();
        } catch (Exception e) {
            throw new FeedParseException("Failed to extract readable content from " + article.getUrl(), e);
        }
        String contentHtml = extracted.getContent();
        String textContent = extracted.getTextContent();
        if (contentHtml == null || textContent == null || textContent.isBlank()) {
            throw new FeedParseException("No readable content found at " + article.getUrl());
        }
        articleRepository.saveExtractedContent(articleId, contentHtml);
        String title = extracted.getTitle();
        return new ExtractedContent(
                title != null && !title.isBlank() ? title : article.getTitle(), contentHtml);
    }

    /** Decodes page bytes: header charset when declared, else jsoup's BOM/meta-tag detection. */
    private String decode(FetchResult fetched, String url) {
        try (var in = new ByteArrayInputStream(fetched.body())) {
            return Jsoup.parse(in, headerCharsetName(fetched.contentType()), url).outerHtml();
        } catch (IOException e) {
            throw new FeedParseException("Failed to read page content from " + url, e);
        }
    }

    private String headerCharsetName(String contentType) {
        if (contentType == null) return null;
        try {
            Charset charset = MediaType.parseMediaType(contentType).getCharset();
            return charset != null ? charset.name() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
