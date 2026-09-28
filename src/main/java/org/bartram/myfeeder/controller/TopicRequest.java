package org.bartram.myfeeder.controller;

/** Boxed weight: a missing weight on POST means the +20 default; on PUT it is required. */
public record TopicRequest(String name, String description, Integer weight) {}
