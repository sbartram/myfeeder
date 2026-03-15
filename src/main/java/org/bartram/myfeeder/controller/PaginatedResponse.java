package org.bartram.myfeeder.controller;
import java.util.List;

public record PaginatedResponse<T>(List<T> articles, Long nextCursor) {}
