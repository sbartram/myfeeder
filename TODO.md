# TODO

## General

- [ ] add support for "magazine view" in the article list pane
- [ ] allow custom names for feeds (some defaults are very long) or truncate them
- [ ] share via email
- [ ] "read later" tag?
- [ ] add notes to an article
- [ ] dropbox and google drive
- [X] create helm chart to deploy redis, postgres, and apps
- [X] option to mark articles older than _X_ days as read
- [X] add a setting to hide read articles in the article list pane with the default enabled
- [X] do not deploy postgres in the helm chart - use pg.bartram.org
- [X] update the frontend libraries
- [X] get rid of the description text in the article list
- [X] the article list pane in "title view" mode should only show the title plus truncated text, stripping out html tags
- [X] change the presets in the "mark read" dialog to 1, 3, 7, and 14 days
- [X] copy link to clipboard
- [X] sort setting for feeds (latest, oldest)
- [X] hide empty feeds in feed nav

## Feed list

- [X] change the color or highlighting of feed groups to make them more obvious
- [X] when the unread count reaches zero in a feed, then hide it in the feed list
- [X] need a way to unsubscribe from a feed
- [X] need a way to force refresh a feed

## Article List

- [ ] Add an option to show unread or all for the currently selected feed
- [X] After "mark all read" is pressed and processed, automatically move to the next feed with unread articles

## Article Reader

- [ ] youtube feeds are not showing the video description
- [ ] links like this one - https://theeurotvplace.com/2025/12/euro-tv-premieres-in-january-2026-land-of-sin-sophie-cross-stayer-more/ - are not displaying correctly
- [ ] this link displays all the text (formatting is so-so) but doesn't display the image - https://relix.com/news/detail/jerry-garcias-tiger-guitar-among-historic-instruments-sold-at-jim-irsay-collection-auction-opening/
- [ ] suppress twitter and instagram links like on this page? https://relix.com/news/detail/marc-maron-to-headline-benefit-comedy-show-for-divided-sky-foundation-in-los-angeles/
- [X] "Copy Link" does not appear to be working
- [X] when an article is selected and then marked as read, it should not be removed from the article list while still selected
- [X] links inside an article content should be opened in a new tab
- [X] add a feature to mark an article as unread after reading

## Dropbox and Google Drive

- [ ] auto export saved articles (boards?, read later?) to PDF or HTML
- [ ] auto export feed list to OPML
- [ ] export history of saved articles

## Testing

- raindrop
- boards
- read later


## Done

- [X] add a "read later" action to the reader which should add to a "read later" board
- [X] add resilience4j config from vidtag
- [X] add a setting to allow feeds with no unread articles to be hidden
- [X] in the article reading pane, add icons for 'star', 'board', 'raindrop', etc
