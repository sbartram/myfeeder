# Initial Design

I want to build an application which mirrors the features of Feedly (feedly.com).

## Server Requirements

- A scheduled service to retrieve updates (since the last check) from configured feeds
- per-user feed list
- provide REST endpoints to expose feed articles or updates to the UI

## User Interface Requirements

- The UI needs to be prototyped before implementation
- A web-based UI is a definite requirement
- mobile app UIs may be created initially or later
- provide options for implementation including React and React-native
- A user interface similar to feedly which allows custom feed grouping
- integrations with external applications including raindrop.io, google drive, and dropbox
