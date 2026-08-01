package org.bartram.myfeeder.service;

/** Readable content extracted from an article's original page (reader view). */
public record ExtractedContent(String title, String contentHtml) {
}
