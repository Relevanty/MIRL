# MIRL focus audio selected by the user

The 28 recordings described by `scripts/focus_selected_audio_sources.tsv` were selected by
the MIRL user from their local Downloads folder on 2026-08-30. Exact SHA-256 hashes are part
of that manifest, so duplicate browser downloads are ignored and a different recording cannot
silently replace an approved one.

Every source page points to Pixabay and every row is used under the
[Pixabay Content License](https://pixabay.com/service/license-summary/). Pixabay's
[FAQ](https://pixabay.com/service/faq/) explicitly permits incorporating content into a game
or application. The original recordings are not redistributed as a standalone library: MIRL
normalizes and converts them into 60-second, seamless OGG focus loops bundled inside the APK.

The manifest retains the original creator, title, source page, file name and source hash for
each item. `scripts/prepare_focus_audio.ps1 -SelectedSourceRoot <folder>` verifies every hash
before processing and writes a second build-time audit file to
`app/build/focus-audio-sources/SELECTED_SOURCE_HASHES.tsv`.

Exact duplicate downloads were intentionally excluded. Removing a sound from MIRL never
deletes the user's original file.
