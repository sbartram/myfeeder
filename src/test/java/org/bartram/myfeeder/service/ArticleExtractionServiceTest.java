package org.bartram.myfeeder.service;

import org.bartram.myfeeder.model.Article;
import org.bartram.myfeeder.parser.FeedParseException;
import org.bartram.myfeeder.repository.ArticleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArticleExtractionServiceTest {

    @Mock private ArticleRepository articleRepository;
    @Mock private FeedFetcher feedFetcher;
    @InjectMocks private ArticleExtractionService service;

    @Test
    void extractsReadableContentAndCachesIt() {
        when(articleRepository.findById(5L))
                .thenReturn(Optional.of(article(5L, "https://example.com/post")));
        when(articleRepository.findExtractedContent(5L)).thenReturn(null);
        when(feedFetcher.fetch("https://example.com/post")).thenReturn(new FetchResult(
                loadPage("/pages/dark-article.html"), "text/html; charset=UTF-8", null, null, false));

        ExtractedContent result = service.extract(5L);

        assertThat(result.contentHtml()).contains("quantum widget shipments");
        assertThat(result.contentHtml()).doesNotContain("<script");
        assertThat(result.title()).contains("Quantum Widget Report");
        verify(articleRepository).saveExtractedContent(5L, result.contentHtml());
    }

    @Test
    void returnsCachedContentWithoutRefetching() {
        when(articleRepository.findById(5L))
                .thenReturn(Optional.of(article(5L, "https://example.com/post")));
        when(articleRepository.findExtractedContent(5L)).thenReturn("<p>cached</p>");

        ExtractedContent result = service.extract(5L);

        assertThat(result.contentHtml()).isEqualTo("<p>cached</p>");
        verifyNoInteractions(feedFetcher);
    }

    @Test
    void throwsNotFoundForMissingArticle() {
        when(articleRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.extract(99L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void throwsBadRequestWhenArticleHasNoUrl() {
        when(articleRepository.findById(5L)).thenReturn(Optional.of(article(5L, null)));

        assertThatThrownBy(() -> service.extract(5L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no URL");
    }

    @Test
    void throwsParseExceptionWhenNothingExtractable() {
        when(articleRepository.findById(5L))
                .thenReturn(Optional.of(article(5L, "https://example.com/post")));
        when(articleRepository.findExtractedContent(5L)).thenReturn(null);
        when(feedFetcher.fetch("https://example.com/post")).thenReturn(new FetchResult(
                "<html><body></body></html>".getBytes(StandardCharsets.UTF_8),
                "text/html", null, null, false));

        assertThatThrownBy(() -> service.extract(5L))
                .isInstanceOf(FeedParseException.class);
    }

    private Article article(Long id, String url) {
        var a = new Article();
        a.setId(id);
        a.setTitle("Feed Title");
        a.setUrl(url);
        return a;
    }

    private byte[] loadPage(String path) {
        try (var is = getClass().getResourceAsStream(path)) {
            return is.readAllBytes();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
