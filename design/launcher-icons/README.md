# MIRL launcher icons

This directory contains 40 manually selected 1024×1024 masters for the MIRL
launcher-icon picker and QA contact sheets showing square, squircle and circle
masks plus the actual 48 px fallback size.

All alternatives were generated in image-edit mode from the current clean MIRL
house scene (`ic_launcher_house_scene.webp`), not from the obsolete circuit-house
play-store artwork. Every prompt fixed these identity details:

- one small front-facing dark house;
- triangular roof and chimney on the left;
- door on the left and one glowing four-pane window on the right;
- no text, badge, border, cats, watermark or UI;
- house kept inside the adaptive-icon safe composition while scenery can bleed.

The style-specific prompts changed composition, material, lighting and environment,
not only color. Oversized or unclear generations were rejected or reframed. In
particular, sunset and nebula were raised and enlarged for mask safety; monochrome
and noir had the moon moved closer; dieselpunk was rebuilt as a matte house directly
inside a dense factory district without wet reflections; paper/retro had its house
enlarged for legibility at launcher size without sacrificing the surrounding scene.
The second collection adds 19 original MIRL interpretations based on style families
identified in the neighboring CRMgram icon catalog: faceted gems, holographic glass,
kawaii pastel, pixel art, constructivism, comic/halftone print, engraved ice,
celestial linework, abstract energy, prism optics and porcelain minimalism. The
CRMgram cats and paper planes themselves were not copied.

Run `tools/generate_launcher_icons.py` from the repository root with Pillow
available to rebuild:

- 432 px adaptive scene layers;
- Android 13 monochrome house layer;
- regular and round legacy mipmaps for mdpi through xxxhdpi;
- 320 px picker previews;
- `launcher-icons-mask-contact-sheet.png`.

The `sources` directory is the project-owned final copy. The ImageGen service's
original output files are intentionally not modified or deleted.

To deliberately refresh selected project sources from their reviewed ImageGen
masters, pass their ids to the generator, for example:

```powershell
python tools/generate_launcher_icons.py paper_retro
```
