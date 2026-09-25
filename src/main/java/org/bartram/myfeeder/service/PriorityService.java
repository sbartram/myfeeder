package org.bartram.myfeeder.service;

import lombok.RequiredArgsConstructor;
import org.bartram.myfeeder.model.Article;
import org.bartram.myfeeder.repository.ArticleRepository;
import org.bartram.myfeeder.repository.InterestScoreQueries;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PriorityService {

    private final InterestScoreQueries interestScoreQueries;
    private final ArticleRepository articleRepository;

    /**
     * One Priority page of up to {@code limit} rows, after {@code cursor} when it is non-null.
     *
     * <p>A cursor that names no article is a 404 ({@link NotFoundException}), not the 400 that
     * {@code findFiltered} uses: the page query alone would return an empty list that looks the same as
     * "end of list", and the client needs to tell "restart from page 1" apart from that.
     */
    public List<Article> page(Long cursor, int limit) {
        if (cursor == null) {
            return interestScoreQueries.priorityFirstPage(limit);
        }
        if (!articleRepository.existsById(cursor)) {
            throw new NotFoundException("Article not found: " + cursor);
        }
        return interestScoreQueries.priorityPageAfter(cursor, limit);
    }
}
