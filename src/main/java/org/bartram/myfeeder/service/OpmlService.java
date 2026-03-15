package org.bartram.myfeeder.service;

import org.bartram.myfeeder.model.Feed;
import org.bartram.myfeeder.model.Folder;
import org.bartram.myfeeder.parser.OpmlFeed;
import org.bartram.myfeeder.parser.OpmlParseException;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.InputStream;
import java.io.StringWriter;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class OpmlService {

    public List<OpmlFeed> parseOpml(InputStream inputStream) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

            Document doc = factory.newDocumentBuilder().parse(inputStream);
            Element body = findBodyElement(doc);

            List<OpmlFeed> feeds = new ArrayList<>();
            NodeList children = body.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                if (children.item(i) instanceof Element outline) {
                    if (outline.hasAttribute("xmlUrl")) {
                        feeds.add(outlineToFeed(outline, null));
                    } else {
                        collectFeedsRecursively(outline, outline.getAttribute("text"), feeds);
                    }
                }
            }
            return feeds;
        } catch (OpmlParseException e) {
            throw e;
        } catch (Exception e) {
            throw new OpmlParseException("Failed to parse OPML: " + e.getMessage(), e);
        }
    }

    public String generateOpml(List<Feed> feeds, List<Folder> folders) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            Document doc = factory.newDocumentBuilder().newDocument();

            Element opml = doc.createElement("opml");
            opml.setAttribute("version", "2.0");
            doc.appendChild(opml);

            Element head = doc.createElement("head");
            opml.appendChild(head);
            Element title = doc.createElement("title");
            title.setTextContent("myfeeder subscriptions");
            head.appendChild(title);
            Element dateCreated = doc.createElement("dateCreated");
            dateCreated.setTextContent(ZonedDateTime.now().format(
                    DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US)));
            head.appendChild(dateCreated);

            Element body = doc.createElement("body");
            opml.appendChild(body);

            Map<Long, List<Feed>> feedsByFolder = feeds.stream()
                    .filter(f -> f.getFolderId() != null)
                    .collect(Collectors.groupingBy(Feed::getFolderId));

            // Folder outlines with their feeds
            for (Folder folder : folders) {
                List<Feed> folderFeeds = feedsByFolder.getOrDefault(folder.getId(), List.of());
                if (folderFeeds.isEmpty()) continue;

                Element folderOutline = doc.createElement("outline");
                folderOutline.setAttribute("text", folder.getName());
                folderOutline.setAttribute("title", folder.getName());
                body.appendChild(folderOutline);

                for (Feed feed : folderFeeds) {
                    folderOutline.appendChild(createFeedOutline(doc, feed));
                }
            }

            // Uncategorized feeds at top level
            feeds.stream()
                    .filter(f -> f.getFolderId() == null)
                    .forEach(feed -> body.appendChild(createFeedOutline(doc, feed)));

            return transformToString(doc);
        } catch (Exception e) {
            throw new OpmlParseException("Failed to generate OPML: " + e.getMessage(), e);
        }
    }

    private Element findBodyElement(Document doc) {
        NodeList bodyList = doc.getElementsByTagName("body");
        if (bodyList.getLength() == 0) {
            throw new OpmlParseException("Invalid OPML: missing <body> element");
        }
        return (Element) bodyList.item(0);
    }

    private void collectFeedsRecursively(Element parent, String folderName, List<OpmlFeed> feeds) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element outline) {
                if (outline.hasAttribute("xmlUrl")) {
                    feeds.add(outlineToFeed(outline, folderName));
                } else {
                    collectFeedsRecursively(outline, folderName, feeds);
                }
            }
        }
    }

    private OpmlFeed outlineToFeed(Element outline, String folderName) {
        String title = outline.getAttribute("title");
        if (title.isEmpty()) {
            title = outline.getAttribute("text");
        }
        return new OpmlFeed(
                title,
                outline.getAttribute("xmlUrl"),
                outline.getAttribute("htmlUrl"),
                folderName
        );
    }

    private Element createFeedOutline(Document doc, Feed feed) {
        Element outline = doc.createElement("outline");
        outline.setAttribute("type", "rss");
        outline.setAttribute("text", feed.getTitle() != null ? feed.getTitle() : "");
        outline.setAttribute("title", feed.getTitle() != null ? feed.getTitle() : "");
        outline.setAttribute("xmlUrl", feed.getUrl());
        if (feed.getSiteUrl() != null) {
            outline.setAttribute("htmlUrl", feed.getSiteUrl());
        }
        return outline;
    }

    private String transformToString(Document doc) throws Exception {
        TransformerFactory tf = TransformerFactory.newInstance();
        tf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(doc), new StreamResult(writer));
        return writer.toString();
    }
}
