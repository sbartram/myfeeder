package org.bartram.myfeeder.service;

import lombok.RequiredArgsConstructor;
import org.bartram.myfeeder.model.Feed;
import org.bartram.myfeeder.model.Folder;
import org.bartram.myfeeder.repository.FeedRepository;
import org.bartram.myfeeder.repository.FolderRepository;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FolderService {
    private final FolderRepository folderRepository;
    private final FeedRepository feedRepository;

    public List<Folder> findAll() {
        return folderRepository.findAllByOrderByDisplayOrderAsc();
    }

    public Folder create(String name) {
        Folder folder = new Folder();
        folder.setName(name);
        folder.setDisplayOrder((int) folderRepository.count());
        folder.setCreatedAt(Instant.now());
        return folderRepository.save(folder);
    }

    public Folder rename(Long id, String name) {
        Folder folder = folderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Folder not found: " + id));
        folder.setName(name);
        return folderRepository.save(folder);
    }

    public void delete(Long id) {
        // DB foreign key has ON DELETE SET NULL, so feeds are automatically uncategorized
        folderRepository.deleteById(id);
    }

    public Feed moveFeedToFolder(Long feedId, Long folderId) {
        Feed feed = feedRepository.findById(feedId)
                .orElseThrow(() -> new IllegalArgumentException("Feed not found: " + feedId));
        if (folderId != null) {
            folderRepository.findById(folderId)
                    .orElseThrow(() -> new IllegalArgumentException("Folder not found: " + folderId));
        }
        feed.setFolderId(folderId);
        return feedRepository.save(feed);
    }
}
