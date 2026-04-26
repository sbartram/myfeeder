package org.bartram.myfeeder.integration;

import java.util.List;

public interface RaindropApiClient {

    /**
     * Lists the user's root collections.
     *
     * @throws RaindropNotConfiguredException when the deployment token is not set
     */
    List<RaindropCollection> listCollections();

    /**
     * Creates a bookmark in the given collection.
     *
     * @throws RaindropNotConfiguredException when the deployment token is not set
     */
    void createBookmark(Long collectionId, String url, String title);
}
