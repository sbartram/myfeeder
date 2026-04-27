package org.bartram.myfeeder.service;

import lombok.RequiredArgsConstructor;
import org.bartram.myfeeder.model.Feed;
import org.bartram.myfeeder.model.Folder;
import org.bartram.myfeeder.repository.FeedRepository;
import org.bartram.myfeeder.repository.FolderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

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

    @Transactional
    public List<Folder> reorder(List<Long> orderedFolderIds) {
        if (orderedFolderIds == null) {
            throw new IllegalArgumentException("folderIds must be provided");
        }
        Set<Long> requestedIds = new HashSet<>(orderedFolderIds);
        if (requestedIds.size() != orderedFolderIds.size()) {
            throw new IllegalArgumentException("folderIds must not contain duplicates");
        }
        List<Folder> existing = folderRepository.findAll();
        Set<Long> existingIds = existing.stream().map(Folder::getId).collect(Collectors.toSet());
        if (!requestedIds.equals(existingIds)) {
            throw new IllegalArgumentException("folderIds must match the current set of folders");
        }
        Map<Long, Folder> byId = existing.stream().collect(Collectors.toMap(Folder::getId, Function.identity()));
        for (int i = 0; i < orderedFolderIds.size(); i++) {
            Folder f = byId.get(orderedFolderIds.get(i));
            f.setDisplayOrder(i);
            folderRepository.save(f);
        }
        return folderRepository.findAllByOrderByDisplayOrderAsc();
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
