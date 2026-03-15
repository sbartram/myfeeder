package org.bartram.myfeeder.repository;

import org.bartram.myfeeder.model.Feed;
import org.springframework.data.repository.ListCrudRepository;

public interface FeedRepository extends ListCrudRepository<Feed, Long> {
}
