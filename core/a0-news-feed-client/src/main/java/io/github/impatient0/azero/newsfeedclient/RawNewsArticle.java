package io.github.impatient0.azero.newsfeedclient;

/**
 * Represents a raw news article as loaded from a data source.
 * <p>
 * This is an immutable data carrier record, containing the essential information
 * for a single news item before any analysis is performed.
 *
 * @param timestamp The time the article was published, in milliseconds since the Unix epoch.
 * @param content  The headline or primary text content of the article.
 */
public record RawNewsArticle(
    long timestamp,
    String content
) {}