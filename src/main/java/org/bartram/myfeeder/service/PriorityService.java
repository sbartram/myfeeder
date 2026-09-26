package org.bartram.myfeeder.service;

import lombok.RequiredArgsConstructor;
import org.bartram.myfeeder.repository.ArticleRepository;
import org.bartram.myfeeder.repository.InterestScoreQueries;
import org.bartram.myfeeder.repository.InterestScoreQueries.PriorityRow;
import org.bartram.myfeeder.repository.InterestScoreQueries.SortKey;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PriorityService {

    private final InterestScoreQueries interestScoreQueries;
    private final ArticleRepository articleRepository;

    /**
     * One Priority page of up to {@code limit} rows, after the row served with {@code after} when it is
     * non-null.
     *
     * <p>A cursor whose article no longer exists is a 404 ({@link NotFoundException}), not the 400 that
     * {@code findFiltered} uses: the page query alone would return a page that looks the same as a normal
     * continuation, and the client needs a "restart from page 1" signal instead (R4).
     */
    public List<PriorityRow> page(SortKey after, int limit) {
        if (after == null) {
            return interestScoreQueries.priorityFirstPage(limit);
        }
        if (!articleRepository.existsById(after.id())) {
            throw new NotFoundException("Article not found: " + after.id());
        }
        return interestScoreQueries.priorityPageAfter(after, limit);
    }
}
