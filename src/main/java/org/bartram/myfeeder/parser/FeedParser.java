package org.bartram.myfeeder.parser;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.rometools.modules.mediarss.MediaEntryModule;
import com.rometools.modules.mediarss.types.MediaContent;
import com.rometools.modules.mediarss.types.Thumbnail;
import com.rometools.rome.feed.synd.SyndContent;
import com.rometools.rome.feed.synd.SyndEnclosure;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.feed.synd.SyndPerson;
import com.rometools.rome.io.SyndFeedInput;
import org.bartram.myfeeder.model.FeedType;
import org.springframework.stereotype.Component;

import java.io.StringReader;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class FeedParser {

    private static final Pattern IMG_SRC_PATTERN =
            Pattern.compile("<img[^>]+src\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);

    private final ObjectMapper objectMapper = new ObjectMapper();

    public ParsedFeed parse(String rawContent) {
        FeedType type = detectFeedType(rawContent);
        return switch (type) {
            case RSS, ATOM -> parseWithRome(rawContent, type);
            case JSON_FEED -> parseJsonFeed(rawContent);
        };
    }

    public FeedType detectFeedType(String rawContent) {
        String trimmed = rawContent.trim();
        if (trimmed.startsWith("{")) {
            return FeedType.JSON_FEED;
        }
        if (trimmed.contains("<feed") && trimmed.contains("http://www.w3.org/2005/Atom")) {
            return FeedType.ATOM;
        }
        return FeedType.RSS;
    }

    private ParsedFeed parseWithRome(String rawContent, FeedType type) {
        try {
            SyndFeedInput input = new SyndFeedInput();
            SyndFeed syndFeed = input.build(new StringReader(rawContent));

            List<ParsedArticle> articles = syndFeed.getEntries().stream()
                    .map(this::toArticle)
                    .toList();

            return ParsedFeed.builder()
                    .title(syndFeed.getTitle())
                    .description(syndFeed.getDescription())
                    .siteUrl(syndFeed.getLink())
                    .feedType(type)
                    .articles(articles)
                    .build();
        } catch (Exception e) {
            throw new FeedParseException("Failed to parse " + type + " feed", e);
        }
    }

    private ParsedArticle toArticle(SyndEntry entry) {
        String content = Optional.ofNullable(entry.getContents())
                .filter(c -> !c.isEmpty())
                .map(c -> c.get(0))
                .map(SyndContent::getValue)
                .orElse(null);

        String summary = Optional.ofNullable(entry.getDescription())
                .map(SyndContent::getValue)
                .orElse(null);

        String author = Optional.ofNullable(entry.getAuthors())
                .filter(a -> !a.isEmpty())
                .map(a -> a.get(0))
                .map(SyndPerson::getName)
                .orElse(entry.getAuthor());

        Instant publishedAt = Optional.ofNullable(entry.getPublishedDate())
                .or(() -> Optional.ofNullable(entry.getUpdatedDate()))
                .map(Date::toInstant)
                .orElse(null);

        String guid = entry.getUri() != null ? entry.getUri() : entry.getLink();

        String imageUrl = extractImageUrl(entry, content, summary);

        return ParsedArticle.builder()
                .guid(guid)
                .title(entry.getTitle())
                .url(entry.getLink())
                .author(author)
                .content(content)
                .summary(summary)
                .imageUrl(imageUrl)
                .publishedAt(publishedAt)
                .build();
    }

    private String extractImageUrl(SyndEntry entry, String content, String summary) {
        MediaEntryModule media = (MediaEntryModule) entry.getModule(MediaEntryModule.URI);
        if (media != null) {
            for (MediaContent mc : media.getMediaContents()) {
                String url = mediaContentImageUrl(mc);
                if (url != null) return url;
            }
            if (media.getMetadata() != null && media.getMetadata().getThumbnail() != null) {
                for (Thumbnail t : media.getMetadata().getThumbnail()) {
                    if (t.getUrl() != null) return t.getUrl().toString();
                }
            }
        }

        for (SyndEnclosure enc : entry.getEnclosures()) {
            String mime = enc.getType();
            if (mime != null && mime.toLowerCase().startsWith("image/") && enc.getUrl() != null) {
                return enc.getUrl();
            }
        }

        String fromContent = firstImgInHtml(content);
        if (fromContent != null) return fromContent;
        return firstImgInHtml(summary);
    }

    private String mediaContentImageUrl(MediaContent mc) {
        if (mc.getReference() == null) return null;
        String medium = mc.getMedium();
        String type = mc.getType();
        boolean isImage = "image".equalsIgnoreCase(medium)
                || (type != null && type.toLowerCase().startsWith("image/"))
                || (medium == null && type == null);
        if (!isImage) return null;
        return mc.getReference().toString();
    }

    private String firstImgInHtml(String html) {
        if (html == null || html.isEmpty()) return null;
        Matcher m = IMG_SRC_PATTERN.matcher(html);
        return m.find() ? m.group(1) : null;
    }

    private ParsedFeed parseJsonFeed(String rawContent) {
        try {
            JsonNode root = objectMapper.readTree(rawContent);
            List<ParsedArticle> articles = new ArrayList<>();

            JsonNode items = root.get("items");
            if (items != null && items.isArray()) {
                for (JsonNode item : items) {
                    articles.add(parseJsonItem(item));
                }
            }

            return ParsedFeed.builder()
                    .title(textOrNull(root, "title"))
                    .description(textOrNull(root, "description"))
                    .siteUrl(textOrNull(root, "home_page_url"))
                    .feedType(FeedType.JSON_FEED)
                    .articles(articles)
                    .build();
        } catch (Exception e) {
            throw new FeedParseException("Failed to parse JSON Feed", e);
        }
    }

    private ParsedArticle parseJsonItem(JsonNode item) {
        String author = null;
        JsonNode authors = item.get("authors");
        if (authors != null && authors.isArray() && !authors.isEmpty()) {
            author = textOrNull(authors.get(0), "name");
        }

        Instant publishedAt = null;
        String dateStr = textOrNull(item, "date_published");
        if (dateStr != null) {
            publishedAt = Instant.parse(dateStr);
        }

        String content = textOrNull(item, "content_html");
        String summary = textOrNull(item, "summary");
        String imageUrl = Optional.ofNullable(textOrNull(item, "image"))
                .or(() -> Optional.ofNullable(textOrNull(item, "banner_image")))
                .orElseGet(() -> {
                    String fromContent = firstImgInHtml(content);
                    return fromContent != null ? fromContent : firstImgInHtml(summary);
                });

        return ParsedArticle.builder()
                .guid(textOrNull(item, "id"))
                .title(textOrNull(item, "title"))
                .url(textOrNull(item, "url"))
                .author(author)
                .content(content)
                .summary(summary)
                .imageUrl(imageUrl)
                .publishedAt(publishedAt)
                .build();
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode child = node.get(field);
        return (child != null && !child.isNull()) ? child.asText() : null;
    }
}
