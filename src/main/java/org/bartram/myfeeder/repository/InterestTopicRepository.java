package org.bartram.myfeeder.repository;

import org.bartram.myfeeder.model.InterestTopic;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;

public interface InterestTopicRepository extends ListCrudRepository<InterestTopic, Long> {
    @Query("SELECT * FROM interest_topic ORDER BY id")
    List<InterestTopic> findAllOrdered();
}
