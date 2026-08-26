"""Build Android launcher-icon resources from the manually selected MIRL artwork.

The 1024 px selected masters remain in design/launcher-icons/sources. Android gets
smaller WebP derivatives, legacy density fallbacks, adaptive XML and a mask QA
contact sheet. Run from the repository root.
"""

from __future__ import annotations

from pathlib import Path
import sys
from typing import Final

from PIL import Image, ImageDraw, ImageFilter, ImageFont


ROOT: Final = Path(__file__).resolve().parents[1]
GENERATED: Final = Path(
    r"C:\Users\hepys\.codex\generated_images\01a03067-59cd-7bd3-afbd-dd5c61bfbcbc"
)
RES: Final = ROOT / "app" / "src" / "main" / "res"
DESIGN: Final = ROOT / "design" / "launcher-icons"

SEED_ICONS: Final[dict[str, Path]] = {
    "standard": RES / "drawable" / "ic_launcher_house_scene.webp",
    "classic": GENERATED / "exec-5d4cb78e-b37a-41d3-95ec-78864a612603.png",
    "amoled": GENERATED / "exec-eb0a8637-dfd5-42be-bfd7-0d23950ee59d.png",
    "monochrome": GENERATED / "exec-5942e6dd-0675-4723-b030-ceda6cbe9f2d.png",
    "graphite": GENERATED / "exec-417fda47-7d61-4d6e-86dc-4493f6e76400.png",
    "cyberpunk": GENERATED / "exec-1a3bd14b-6e03-47a1-b82d-2ffe7cbf122d.png",
    "neon_city": GENERATED / "exec-3b7df9f6-d5fc-461e-ac8f-faea1807a82c.png",
    "synthwave": GENERATED / "exec-efb49dc2-ee93-4199-97e6-13706c1d450c.png",
    "vaporwave": GENERATED / "exec-8dec72ec-39b9-460c-92e5-8c35059cba86.png",
    "dieselpunk": GENERATED / "exec-743bb60d-93e2-454e-9813-1de6dac8d56f.png",
    "brass": GENERATED / "exec-2f330551-55b1-44d1-8464-d1c8f7025ec7.png",
    "post_punk": GENERATED / "exec-5b1c7881-3624-44c2-92e1-4569377ec7ab.png",
    "noir": GENERATED / "exec-7253004c-0ba7-4ec4-a13b-e8f7cca748c5.png",
    "forest": GENERATED / "exec-dcc6f48f-5b48-4c86-b858-91d8723d7542.png",
    "aurora": GENERATED / "exec-bff09250-7645-49ac-abd7-5982f95363ff.png",
    "ocean": GENERATED / "exec-60f4ec91-9868-4529-ba84-b77975d08221.png",
    "sunset": GENERATED / "exec-3bd0b270-b4d7-460a-8787-37e39004d1db.png",
    "lavender": GENERATED / "exec-a25437fb-05a6-4f7e-8ffc-209e3c298266.png",
    "paper_retro": GENERATED / "exec-173e8943-bb10-4ebb-b209-a3b5907184e5.png",
    "nebula": GENERATED / "exec-7699d3db-27dd-4cb9-8681-997fbb642d33.png",
    "matrix_terminal": GENERATED / "exec-de913907-0369-455d-b5eb-fc9ac042a04d.png",
    "amethyst_facet": GENERATED / "exec-76c998aa-b496-4d87-b05a-6ed3a523cc86.png",
    "obsidian_facet": GENERATED / "exec-b6f6dbed-c0f8-45a6-b1ff-68c198fc06cd.png",
    "sapphire_prism": GENERATED / "exec-a4c5df2a-b5df-4981-87ce-b6df851a9cf3.png",
    "emerald_facet": GENERATED / "exec-f2fd91f8-f68e-449a-86b9-d917b2a4d96c.png",
    "coral_polygon": GENERATED / "exec-78d8c300-064c-41eb-99a7-91f3cbfbecf2.png",
    "holographic_glass": GENERATED / "exec-4b3de3bd-17b5-41a0-a900-a32500ccbf02.png",
    "kawaii_pink": GENERATED / "exec-6012411c-cdcf-4469-8a16-4e81b8d91803.png",
    "pixel_cozy": GENERATED / "exec-ad47b7ec-ecfd-48ab-b0ba-733f7521bbb7.png",
    "constructivist": GENERATED / "exec-03edf32b-fa68-4343-a207-18e3338548e5.png",
    "comic_pop": GENERATED / "exec-6ab9a1cd-4da2-4e8e-b97e-633740b0f759.png",
    "cyan_grunge": GENERATED / "exec-b7fb2501-b210-40e9-a395-5813856d094e.png",
    "yellow_halftone": GENERATED / "exec-d7a5c092-f010-40d4-bea9-30bc51edc05b.png",
    "ice_engraved": GENERATED / "exec-a9c6cf64-4d0b-4ca6-9502-b3fa383e05c8.png",
    "celestial_gold": GENERATED / "exec-7c346771-2420-4db3-8360-67415a809625.png",
    "electric_blue": GENERATED / "exec-d3695e88-8c5b-414e-927d-ecf7443ca060.png",
    "ultraviolet_plasma": GENERATED / "exec-4248118e-e0d9-4877-8845-3315cf871568.png",
    "rainbow_prism": GENERATED / "exec-80c283d2-233f-4a0e-904a-7d9e97b9b8c6.png",
    "gold_wireframe": GENERATED / "exec-b4f1f1e8-08b1-40fc-8b0c-46856f6aefd2.png",
    "porcelain_minimal": GENERATED / "exec-9b882697-5992-4ea0-bbed-3a4e8ad298ee.png",
}

NEW_ICON_IDS: Final = (
    "stained_glass", "ceramic_mosaic", "cloisonne_enamel", "terrazzo",
    "carved_wood", "wood_marquetry", "satin_embroidery", "knitted_wool",
    "felt_applique", "claymation", "linocut", "risograph", "cyanotype",
    "copper_engraving", "scratchboard", "watercolor_wash", "gouache",
    "dry_pastel", "charcoal_drawing", "sumi_e", "art_nouveau", "art_deco",
    "ukiyo_e", "lubok", "medieval_miniature", "rococo", "impressionist",
    "pointillist", "cubist", "surrealist", "solarpunk_greenhouse",
    "bioluminescent_mushrooms", "snow_globe", "adobe_desert",
    "volcanic_basalt", "autumn_collage", "underwater_reef",
    "floating_islands", "cloud_kingdom", "bonsai_garden",
    "architectural_blueprint", "xray_house", "thermal_vision", "crt_screen",
    "digital_glitch", "voxel_isometric", "liquid_chrome",
    "engineering_exploded", "retro_space_poster", "quantum_field",
    "crayon_drawing", "chalkboard", "sticker_pack", "toy_blocks",
    "gingerbread", "gummy_glass", "inflatable_sculpture", "plush_toy",
    "shadow_theatre", "paper_diorama",
)

# The selected composites live in the repository, so regenerating Android
# resources never depends on the ImageGen cache.
SEED_ICONS.update({
    icon_id: DESIGN / "proposed-new60" / f"{icon_id}.webp"
    for icon_id in NEW_ICON_IDS
})

BACKGROUND_COLORS: Final[dict[str, str]] = {
    "standard": "#061A42",
    "classic": "#04183F",
    "amoled": "#000000",
    "monochrome": "#111111",
    "graphite": "#777777",
    "cyberpunk": "#071638",
    "neon_city": "#101459",
    "synthwave": "#24085B",
    "vaporwave": "#8A75C6",
    "dieselpunk": "#2A1A10",
    "brass": "#063C42",
    "post_punk": "#EEE6D4",
    "noir": "#050505",
    "forest": "#061E18",
    "aurora": "#08256A",
    "ocean": "#07527A",
    "sunset": "#C23D57",
    "lavender": "#3155B1",
    "paper_retro": "#F0D9A8",
    "nebula": "#071A55",
    "matrix_terminal": "#001107",
    "amethyst_facet": "#16083D",
    "obsidian_facet": "#050609",
    "sapphire_prism": "#061B5D",
    "emerald_facet": "#031B10",
    "coral_polygon": "#FF8C68",
    "holographic_glass": "#F3F7FA",
    "kawaii_pink": "#F88CB4",
    "pixel_cozy": "#17184F",
    "constructivist": "#EEE0BE",
    "comic_pop": "#16A8B4",
    "cyan_grunge": "#08BFC0",
    "yellow_halftone": "#FFD400",
    "ice_engraved": "#77C8EC",
    "celestial_gold": "#06182D",
    "electric_blue": "#021238",
    "ultraviolet_plasma": "#210024",
    "rainbow_prism": "#000000",
    "gold_wireframe": "#050505",
    "porcelain_minimal": "#F4F1E9",
    "stained_glass": "#071A3F",
    "ceramic_mosaic": "#176F91",
    "cloisonne_enamel": "#052E37",
    "terrazzo": "#F2EAD7",
    "carved_wood": "#50301A",
    "wood_marquetry": "#2A1B14",
    "satin_embroidery": "#082A59",
    "knitted_wool": "#4D0F1B",
    "felt_applique": "#15335E",
    "claymation": "#1762B3",
    "linocut": "#E8D7B4",
    "risograph": "#F4E7C5",
    "cyanotype": "#174E84",
    "copper_engraving": "#8A4A21",
    "scratchboard": "#080808",
    "watercolor_wash": "#E7DDBE",
    "gouache": "#29474E",
    "dry_pastel": "#382064",
    "charcoal_drawing": "#BDB3A1",
    "sumi_e": "#E9E0CF",
    "art_nouveau": "#083B51",
    "art_deco": "#090B0E",
    "ukiyo_e": "#EBDCB8",
    "lubok": "#E7B42F",
    "medieval_miniature": "#153D70",
    "rococo": "#F3E6D9",
    "impressionist": "#153A76",
    "pointillist": "#050505",
    "cubist": "#8A8173",
    "surrealist": "#E5A99E",
    "solarpunk_greenhouse": "#F2E9C7",
    "bioluminescent_mushrooms": "#081443",
    "snow_globe": "#08265C",
    "adobe_desert": "#E97929",
    "volcanic_basalt": "#0C0C0D",
    "autumn_collage": "#234C31",
    "underwater_reef": "#063A72",
    "floating_islands": "#43A9E4",
    "cloud_kingdom": "#B9B4F1",
    "bonsai_garden": "#181912",
    "architectural_blueprint": "#0D4C7D",
    "xray_house": "#06182E",
    "thermal_vision": "#24125D",
    "crt_screen": "#001309",
    "digital_glitch": "#05050B",
    "voxel_isometric": "#0C53C8",
    "liquid_chrome": "#05060A",
    "engineering_exploded": "#17242D",
    "retro_space_poster": "#142431",
    "quantum_field": "#06151F",
    "crayon_drawing": "#F5F0D6",
    "chalkboard": "#16452D",
    "sticker_pack": "#050505",
    "toy_blocks": "#F4E7C8",
    "gingerbread": "#A51416",
    "gummy_glass": "#251045",
    "inflatable_sculpture": "#9DE1E4",
    "plush_toy": "#EAB079",
    "shadow_theatre": "#080303",
    "paper_diorama": "#F0DDAF",
}

DENSITIES: Final = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}

ADAPTIVE_LAYER_SIZE: Final = 432
# Android guarantees that the centered 66x66 dp area of a 108x108 dp
# adaptive layer survives every OEM mask. At our 4x export scale this is 264 px.
ADAPTIVE_SAFE_ART_SIZE: Final = 264


def fit_master(path: Path) -> Image.Image:
    image = Image.open(path).convert("RGB")
    if image.size != (1024, 1024):
        image = image.resize((1024, 1024), Image.Resampling.LANCZOS)
    return image


def adaptive_background(scene: Image.Image, color: str, size: int = ADAPTIVE_LAYER_SIZE) -> Image.Image:
    """Full-bleed ambient color; clipping it cannot remove meaningful artwork."""
    # A heavily defocused copy keeps each style's lighting and palette while
    # making the duplicated central house impossible to read behind foreground.
    sample_size = max(24, size // 8)
    ambient = scene.resize((sample_size, sample_size), Image.Resampling.LANCZOS)
    ambient = ambient.filter(ImageFilter.GaussianBlur(radius=sample_size * 0.14))
    ambient = ambient.resize((size, size), Image.Resampling.BICUBIC).convert("RGB")
    tint = Image.new("RGB", (size, size), color)
    return Image.blend(ambient, tint, 0.16)


def adaptive_foreground(scene: Image.Image, size: int = ADAPTIVE_LAYER_SIZE) -> Image.Image:
    """The complete selected composition, centered inside Android's safe zone."""
    art_size = round(size * ADAPTIVE_SAFE_ART_SIZE / ADAPTIVE_LAYER_SIZE)
    art = scene.resize((art_size, art_size), Image.Resampling.LANCZOS).convert("RGBA")
    layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    offset = (size - art_size) // 2
    layer.alpha_composite(art, (offset, offset))
    return layer


def rounded_fallback(scene: Image.Image, size: int, radius_ratio: float) -> Image.Image:
    scaled = scene.resize((size, size), Image.Resampling.LANCZOS).convert("RGBA")
    mask = Image.new("L", (size, size), 0)
    radius = max(1, round(size * radius_ratio))
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, size - 1, size - 1), radius=radius, fill=255)
    scaled.putalpha(mask)
    return scaled


def circle_fallback(scene: Image.Image, size: int) -> Image.Image:
    scaled = scene.resize((size, size), Image.Resampling.LANCZOS).convert("RGBA")
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, size - 1, size - 1), fill=255)
    scaled.putalpha(mask)
    return scaled


def monochrome_house(size: int = 432) -> Image.Image:
    """A crisp house alpha mask for Android 13 themed icons."""
    scale = size / 432
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(canvas)
    ink = (0, 0, 0, 255)

    def p(value: float) -> int:
        return round(value * scale)

    # Platform, body, chimney and roof preserve the current MIRL silhouette.
    draw.polygon([(p(88), p(348)), (p(344), p(348)), (p(374), p(365)), (p(58), p(365))], fill=ink)
    draw.rectangle((p(116), p(224), p(316), p(348)), fill=ink)
    draw.rectangle((p(137), p(133), p(174), p(235)), fill=ink)
    draw.polygon([(p(82), p(230)), (p(216), p(105)), (p(350), p(230))], fill=ink)

    # Negative door and four window panes make the mark readable when recolored.
    draw.rectangle((p(145), p(267), p(190), p(348)), fill=(0, 0, 0, 0))
    for left, top, right, bottom in (
        (239, 263, 265, 289),
        (273, 263, 299, 289),
        (239, 297, 265, 323),
        (273, 297, 299, 323),
    ):
        draw.rectangle((p(left), p(top), p(right), p(bottom)), fill=(0, 0, 0, 0))
    # Fit the complete mark into the guaranteed adaptive safe zone too.
    alpha_bounds = canvas.getchannel("A").getbbox()
    if alpha_bounds is None:
        return canvas
    mark = canvas.crop(alpha_bounds)
    safe_size = round(size * ADAPTIVE_SAFE_ART_SIZE / ADAPTIVE_LAYER_SIZE)
    mark.thumbnail((safe_size, safe_size), Image.Resampling.LANCZOS)
    safe_canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    safe_canvas.alpha_composite(mark, ((size - mark.width) // 2, (size - mark.height) // 2))
    return safe_canvas


def adaptive_xml(icon_id: str, with_monochrome: bool) -> str:
    monochrome = (
        '\n    <monochrome android:drawable="@drawable/ic_launcher_monochrome_house" />'
        if with_monochrome
        else ""
    )
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
        f'    <background android:drawable="@drawable/ic_launcher_background_{icon_id}" />\n'
        f'    <foreground android:drawable="@drawable/ic_launcher_foreground_{icon_id}" />'
        f'{monochrome}\n'
        '</adaptive-icon>\n'
    )


def write_contact_sheet(masters: dict[str, Image.Image]) -> None:
    tile = 150
    label_height = 28
    gap = 12
    groups_per_row = 2
    columns = groups_per_row * 3
    rows = (len(masters) + groups_per_row - 1) // groups_per_row
    sheet = Image.new("RGB", (columns * (tile + gap) + gap, rows * (tile + label_height + gap) + gap), "#202124")
    draw = ImageDraw.Draw(sheet)
    font = ImageFont.load_default(size=15)
    for index, (icon_id, scene) in enumerate(masters.items()):
        group_column = index % groups_per_row
        row = index // groups_per_row
        y = gap + row * (tile + label_height + gap)
        previews = (
            rounded_fallback(scene, tile, 0.0),
            rounded_fallback(scene, tile, 0.22),
            circle_fallback(scene, tile),
        )
        for preview_column, preview in enumerate(previews):
            column = group_column * 3 + preview_column
            x = gap + column * (tile + gap)
            checker = Image.new("RGBA", (tile, tile), "#ECECEC")
            checker.alpha_composite(preview)
            sheet.paste(checker.convert("RGB"), (x, y))
        label_x = gap + group_column * 3 * (tile + gap)
        draw.text((label_x, y + tile + 6), icon_id, fill="white", font=font)
    sheet.save(DESIGN / "launcher-icons-mask-contact-sheet.png", optimize=True)


def write_small_size_sheet(masters: dict[str, Image.Image]) -> None:
    """Shows the actual 48 px fallback, enlarged with nearest-neighbor for review."""
    source_size = 48
    scale = 4
    tile = source_size * scale
    label_height = 28
    gap = 12
    columns = 5
    rows = (len(masters) + columns - 1) // columns
    sheet = Image.new(
        "RGB",
        (columns * (tile + gap) + gap, rows * (tile + label_height + gap) + gap),
        "#202124",
    )
    draw = ImageDraw.Draw(sheet)
    font = ImageFont.load_default(size=15)
    for index, (icon_id, scene) in enumerate(masters.items()):
        column = index % columns
        row = index // columns
        x = gap + column * (tile + gap)
        y = gap + row * (tile + label_height + gap)
        actual = rounded_fallback(scene, source_size, 0.19)
        enlarged = actual.resize((tile, tile), Image.Resampling.NEAREST)
        checker = Image.new("RGBA", (tile, tile), "#ECECEC")
        checker.alpha_composite(enlarged)
        sheet.paste(checker.convert("RGB"), (x, y))
        draw.text((x, y + tile + 6), icon_id, fill="white", font=font)
    sheet.save(DESIGN / "launcher-icons-48px-contact-sheet.png", optimize=True)


def main() -> None:
    refresh_icons = set(sys.argv[1:])
    unknown_refresh_icons = refresh_icons.difference(SEED_ICONS)
    if unknown_refresh_icons:
        raise ValueError(f"Unknown icon ids to refresh: {sorted(unknown_refresh_icons)}")

    sources_dir = DESIGN / "sources"
    missing = [
        f"{icon_id}: {path}"
        for icon_id, path in SEED_ICONS.items()
        if not (sources_dir / f"{icon_id}.webp").exists() and not path.exists()
    ]
    if missing:
        raise FileNotFoundError("Missing selected icon masters:\n" + "\n".join(missing))

    drawable_nodpi = RES / "drawable-nodpi"
    v26 = RES / "mipmap-anydpi-v26"
    v33 = RES / "mipmap-anydpi-v33"
    values = RES / "values"
    for directory in (sources_dir, drawable_nodpi, v26, v33, values):
        directory.mkdir(parents=True, exist_ok=True)

    adaptive_previews: dict[str, Image.Image] = {}
    for icon_id, seed_path in SEED_ICONS.items():
        project_source = sources_dir / f"{icon_id}.webp"
        source_path = (
            seed_path
            if icon_id in refresh_icons or not project_source.exists()
            else project_source
        )
        scene = fit_master(source_path)
        if icon_id in refresh_icons or not project_source.exists():
            scene.save(project_source, "WEBP", quality=95, method=6)
        background_layer = adaptive_background(scene, BACKGROUND_COLORS[icon_id])
        foreground_layer = adaptive_foreground(scene)
        rendered = background_layer.convert("RGBA")
        rendered.alpha_composite(foreground_layer)
        rendered = rendered.convert("RGB")
        adaptive_previews[icon_id] = rendered

        background_layer.save(
            drawable_nodpi / f"ic_launcher_background_{icon_id}.webp",
            "WEBP",
            quality=90,
            method=6,
        )
        foreground_layer.save(
            drawable_nodpi / f"ic_launcher_foreground_{icon_id}.webp",
            "WEBP",
            lossless=True,
            method=6,
        )
        rendered.resize((320, 320), Image.Resampling.LANCZOS).save(
            drawable_nodpi / f"ic_launcher_preview_{icon_id}.webp",
            "WEBP",
            quality=91,
            method=6,
        )

        for density, size in DENSITIES.items():
            directory = RES / f"mipmap-{density}"
            directory.mkdir(parents=True, exist_ok=True)
            rounded_fallback(rendered, size, 0.19).save(
                directory / f"ic_launcher_{icon_id}.webp", "WEBP", lossless=True, method=6
            )
            circle_fallback(rendered, size).save(
                directory / f"ic_launcher_{icon_id}_round.webp", "WEBP", lossless=True, method=6
            )

        (v26 / f"ic_launcher_{icon_id}.xml").write_text(adaptive_xml(icon_id, False), encoding="utf-8")
        (v26 / f"ic_launcher_{icon_id}_round.xml").write_text(adaptive_xml(icon_id, False), encoding="utf-8")
        (v33 / f"ic_launcher_{icon_id}.xml").write_text(adaptive_xml(icon_id, True), encoding="utf-8")
        (v33 / f"ic_launcher_{icon_id}_round.xml").write_text(adaptive_xml(icon_id, True), encoding="utf-8")

    monochrome_house().save(drawable_nodpi / "ic_launcher_monochrome_house.png", optimize=True)

    colors = ['<?xml version="1.0" encoding="utf-8"?>', '<resources>']
    colors.extend(
        f'    <color name="ic_launcher_{icon_id}_background">{BACKGROUND_COLORS[icon_id]}</color>'
        for icon_id in SEED_ICONS
    )
    colors.append('</resources>')
    (values / "launcher_icon_colors.xml").write_text("\n".join(colors) + "\n", encoding="utf-8")

    write_contact_sheet(adaptive_previews)
    write_small_size_sheet(adaptive_previews)
    print(f"Generated {len(SEED_ICONS)} launcher icon sets and QA contact sheet")


if __name__ == "__main__":
    main()
