# MIRL focus ambience: sources and processing

The focus ambience library is built from downloaded recordings, not from MIRL's procedural
PCM generator. The canonical machine-readable source list is
[`scripts/focus_audio_sources.tsv`](../../../../../scripts/focus_audio_sources.tsv), and the
reproducible build pipeline is
[`scripts/prepare_focus_audio.ps1`](../../../../../scripts/prepare_focus_audio.ps1).

All source rows are declared as [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/).
Attribution is not legally required by CC0, but author, title, and source page are retained
below for provenance and future audits. Do not replace a row with a differently licensed file
without also changing the pipeline's explicit licence policy.

The user's approved Pixabay recordings are documented separately in
[`SELECTED_AUDIO_SOURCES.md`](SELECTED_AUDIO_SOURCES.md). When
`-SelectedSourceRoot` is supplied, those hash-verified recordings intentionally replace nine
matching catalogue loops and add nineteen new entries, including two real ambient tracks.
They are processed after the CC0 base so the final APK contains the approved versions.
The pipeline therefore requires `-SelectedSourceRoot` whenever the selected manifest exists;
use `-SkipSelected` only for an intentional base-only diagnostic build.

## Processing

Run from the repository root with FFmpeg and FFprobe 6.x or newer:

```powershell
pwsh -File scripts/prepare_focus_audio.ps1 `
  -FfmpegPath C:\tools\ffmpeg\bin\ffmpeg.exe `
  -FfprobePath C:\tools\ffmpeg\bin\ffprobe.exe `
  -SelectedSourceRoot C:\path\to\approved-downloads
```

The script:

1. downloads all HTTPS sources in parallel into `app/build/focus-audio-sources/downloads`;
2. records their SHA-256 hashes in `app/build/focus-audio-sources/SOURCE_HASHES.tsv`;
3. converts only the downloaded recordings to 48 kHz stereo working tracks;
4. builds the four documented real-recording mixes below;
5. makes a three-second crossfaded seam and exports an exact 60-second OGG Vorbis loop;
6. applies two-pass loudness normalization to -22 LUFS, LRA 7 and -2 dBTP;
7. verifies all 38 files and their durations with FFprobe.

Cached sources are reused. Use `-ForceDownload` to refresh them or `-Offline` to prohibit all
network access and fail if a cached source is missing. The Android app never downloads these
files: the resulting OGG files are bundled under `assets/focus/ambience` and remain fully
available offline.

Recordings flagged `speech_audit`, `dynamic_peaks`, `traffic_peaks`, or `trim_required` must be
listened to before release. The published loops must not contain intelligible private speech,
music, announcements, sirens, clipping, or startling peaks.

## Composite loops

| Output | Real source layers | Construction |
|---|---|---|
| `study/turning_pages.ogg` | `quiet_reading_room`, `turning_pages` | Reading-room bed plus page events at 8, 19, 31, 44 and 56 seconds |
| `spaces/university_archive.ogg` | `quiet_reading_room`, `turning_pages` | Quieter reading-room bed plus rarer page events at 17, 39 and 55 seconds |
| `spaces/rainy_cafe.ogg` | `rainy_cafe_cafe`, `summer_rain` | Cafe recording mixed with real terrace rain |
| `weather/rainy_night_city.ogg` | `rainy_night_city`, `summer_rain` | Paris night recording mixed with real terrace rain |

The gain values are fixed in the PowerShell pipeline. Final loudness normalization happens only
after layering, so the relative balance remains reproducible.

## Source register

| Source key | Author | Original title | Source page | License | Notes |
|---|---|---|---|---|---|
| `large_library` | xkeril | Library ambience | [Freesound 613781](https://freesound.org/people/xkeril/sounds/613781) | CC0-1.0 | Speech audit |
| `quiet_reading_room` | xkeril | Quiet library ambience | [Freesound 620683](https://freesound.org/people/xkeril/sounds/620683) | CC0-1.0 | Speech audit; also used as a mix bed |
| `pencil_on_paper` | Joseph Sardin | Pencil | [BigSoundBank 0221](https://bigsoundbank.com/pencil-s0221.html) | CC0-1.0 | Original BWF; crossfaded when extended |
| `fountain_pen` | Anthousai | writing - pen 01.wav | [Freesound 337086](https://freesound.org/people/Anthousai/sounds/337086) | CC0-1.0 | Crossfaded when extended |
| `turning_pages` | Joseph Sardin | Pages that turn 5 | [BigSoundBank 2212](https://bigsoundbank.com/pages-that-turn-5-s2212.html) | CC0-1.0 | Original BWF; used only as real event material |
| `soft_keyboard` | Joseph Sardin | Computer keyboard | [BigSoundBank 0229](https://bigsoundbank.com/computer-keyboard-s0229.html) | CC0-1.0 | Original BWF; crossfaded when extended |
| `mechanical_keyboard` | Nmb910 | Mechanical Keyboard Typing | [Freesound 234923](https://freesound.org/people/Nmb910/sounds/234923) | CC0-1.0 | Crossfaded when extended |
| `distant_lecture_hall` | okieactor | Classroom Ambience - High School Class.wav | [Freesound 417041](https://freesound.org/people/okieactor/sounds/417041) | CC0-1.0 | Speech audit |
| `morning_cafe` | Joseph Sardin | Atmosphere bar 2 | [BigSoundBank 0480](https://bigsoundbank.com/atmosphere-bar-2-s0480.html) | CC0-1.0 | Original BWF; speech audit |
| `rainy_cafe_cafe` | Joseph Sardin | Atmosphere bar 2 | [BigSoundBank 0480](https://bigsoundbank.com/atmosphere-bar-2-s0480.html) | CC0-1.0 | Real cafe layer; speech audit |
| `evening_office` | Joseph Sardin | Noisy office industry | [BigSoundBank 0502](https://bigsoundbank.com/noisy-office-industry-s0502.html) | CC0-1.0 | Original BWF; speech audit |
| `bookshop` | ilmari_freesound | 2022-09-09-bookstore-ambience-zoom-f3-binaural-okm-ii.wav | [Freesound 649440](https://freesound.org/people/ilmari_freesound/sounds/649440) | CC0-1.0 | Speech audit |
| `museum_hall` | ilmari_freesound | 2022-11-08-museum-ambience-002.wav | [Freesound 658303](https://freesound.org/people/ilmari_freesound/sounds/658303) | CC0-1.0 | Speech audit |
| `rain_on_window` | ikayuka | rain on window | [Freesound 273333](https://freesound.org/people/ikayuka/sounds/273333) | CC0-1.0 | Clean ambience |
| `rain_on_tent` | Joseph Sardin | Rain and storm in a tent | [BigSoundBank 0820](https://bigsoundbank.com/rain-and-storm-in-a-tent-s0820.html) | CC0-1.0 | Original BWF |
| `distant_thunder` | Joseph Sardin | Rain and storm 2 | [BigSoundBank 0740](https://bigsoundbank.com/rain-and-storm-2-s0740.html) | CC0-1.0 | Original BWF; dynamic-peak audit |
| `snowstorm` | JSilverSound | Howling wind | [Freesound 528328](https://freesound.org/people/JSilverSound/sounds/528328) | CC0-1.0 | Trim and peak audit |
| `summer_rain` | Joseph Sardin | Summer rain on terrace | [BigSoundBank 1019](https://bigsoundbank.com/summer-rain-on-terrace-s1019.html) | CC0-1.0 | Original BWF; also used as a mix layer |
| `rainy_night_city` | Joseph Sardin | Paris by night | [BigSoundBank 0680](https://bigsoundbank.com/paris-by-night-s0680.html) | CC0-1.0 | Original BWF; traffic-peak audit; city layer only |
| `forest_stream` | Joseph Sardin | Forest and stream 1 | [BigSoundBank 2713](https://bigsoundbank.com/forest-and-stream-1-s2713.html) | CC0-1.0 | Original BWF |
| `ocean_waves` | Joseph Sardin | Sea waves | [BigSoundBank 0266](https://bigsoundbank.com/sea-waves-s0266.html) | CC0-1.0 | Original BWF |
| `quiet_lake` | Joseph Sardin | Pond in a field | [BigSoundBank 0691](https://bigsoundbank.com/pond-in-a-field-s0691.html) | CC0-1.0 | Original BWF |
| `wind_in_pines` | Joseph Sardin | Forest wind in the trees | [BigSoundBank 0904](https://bigsoundbank.com/forest-wind-in-the-trees-s0904.html) | CC0-1.0 | Original BWF |
| `night_crickets` | Joseph Sardin | Song of nocturnal insect 2 | [BigSoundBank 0425](https://bigsoundbank.com/song-of-nocturnal-insect-2-s0425.html) | CC0-1.0 | Original BWF |
| `campfire` | Joseph Sardin | Big branching fire 1 | [BigSoundBank 0987](https://bigsoundbank.com/big-branching-fire-1-s0987.html) | CC0-1.0 | Original BWF |
| `fireplace` | Joseph Sardin | Fireplace 5 | [BigSoundBank 2857](https://bigsoundbank.com/fireplace-5-s2857.html) | CC0-1.0 | Original BWF |
| `cat_purring` | Joseph Sardin | Cat purring 3 | [BigSoundBank 1010](https://bigsoundbank.com/cat-purring-3-s1010.html) | CC0-1.0 | Original BWF; crossfaded when extended |
| `aquarium` | fonografico | aquarium.ogg | [Freesound 636123](https://freesound.org/people/fonografico/sounds/636123) | CC0-1.0 | Clean ambience |
| `ceiling_fan` | Joseph Sardin | Electric fan 1 | [BigSoundBank 0078](https://bigsoundbank.com/electric-fan-1-s0078.html) | CC0-1.0 | Original BWF |
| `vinyl_crackle` | Joseph Sardin | Vinyl disc | [BigSoundBank 2547](https://bigsoundbank.com/vinyl-disc-s2547.html) | CC0-1.0 | Original BWF; verify that no music is audible |
| `next_room` | Joseph Sardin | Paris apartment, closed window | [BigSoundBank 0080](https://bigsoundbank.com/paris-apartment-closed-window-s0080.html) | CC0-1.0 | Original BWF |
| `night_train` | Yoyodaman234 | train interior ambience 1a | [Freesound 341208](https://freesound.org/people/Yoyodaman234/sounds/341208) | CC0-1.0 | Clean ambience |
| `airplane_cabin` | Kinoton | Airliner, Jet, Interior Ambience, Idle in flight | [Freesound 438972](https://freesound.org/people/Kinoton/sounds/438972) | CC0-1.0 | Trim and speech audit |
| `car_in_rain` | Joseph Sardin | Rain on car windshield | [BigSoundBank 1295](https://bigsoundbank.com/rain-on-car-windshield-s1295.html) | CC0-1.0 | Original BWF |
| `ferry_cabin` | blaukreuz | 170722_MediumFerry_BelowDeck_F.wav | [Freesound 398850](https://freesound.org/people/blaukreuz/sounds/398850) | CC0-1.0 | Clean ambience |
| `city_tram` | melbourne.atmospheres | Melbourne Onboard Tram Interior Elizabeth Street Ambience | [Freesound 582252](https://freesound.org/people/melbourne.atmospheres/sounds/582252) | CC0-1.0 | Trim and speech audit |
| `orbital_station` | db3005 | Space Station Drone | [Freesound 686237](https://freesound.org/people/db3005/sounds/686237) | CC0-1.0 | Downloaded designed recording; no MIRL synthesis |

## Release checklist

- Compare `SOURCE_HASHES.tsv` with the hashes from the reviewed build.
- Listen to the first/last seam and at least one full loop of every output with headphones.
- Confirm all flagged speech is unintelligible and contains no personal information.
- Confirm there is no copyrighted music in cafe, office, tram, vinyl, or adjacent-room tracks.
- Confirm thunder, traffic, page turns, keyboards, and fire contain no startling peaks.
- Build the release APK and verify that all 38 OGG assets remain packaged.
