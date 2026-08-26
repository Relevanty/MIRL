"""Compose the 60 proposed MIRL launcher icons with one fixed house geometry.

Image generation is deliberately limited to the surrounding artwork.  This
script owns the MIRL mark, so every result has the exact same house bounds,
baseline, door, chimney and four-pane window before visual review.
"""

from __future__ import annotations

from pathlib import Path
from typing import Final

from PIL import Image, ImageDraw, ImageFilter, ImageFont


ROOT: Final = Path(__file__).resolve().parents[1]
GENERATED: Final = Path(
    r"C:\Users\hepys\.codex\generated_images\01a03067-59cd-7bd3-afbd-dd5c61bfbcbc"
)
OUT: Final = ROOT / "design" / "launcher-icons" / "proposed-new60"

ENVIRONMENTS: Final[dict[str, str]] = {
    "stained_glass": "exec-c0435718-19de-4cd1-8b0a-522eff1453eb.png",
    "ceramic_mosaic": "exec-1ec86048-ac2f-46e8-af38-961150cb34ac.png",
    "cloisonne_enamel": "exec-6360af08-ac79-48af-925f-d10c01623e9d.png",
    "terrazzo": "exec-cdda7ff2-9389-4197-890e-4c55346ec2da.png",
    "carved_wood": "exec-9834f791-eac9-4c8c-9512-d47b2861da62.png",
    "wood_marquetry": "exec-b0cc5b81-2c93-4b62-b5ca-55aed5548651.png",
    "satin_embroidery": "exec-dffc87e9-c07b-4bc9-a969-3ec6cae5aaa1.png",
    "knitted_wool": "exec-acd4e3a6-9c3a-4ffb-9743-b3bc6a62c95f.png",
    "felt_applique": "exec-f355bbe9-993b-4fbb-a04c-5d87e1995b0b.png",
    "claymation": "exec-bfaaebfe-e788-4d09-ab50-f181174a1997.png",
    "linocut": "exec-100d22ca-fbf7-4627-b7ae-5849aa27fa6e.png",
    "risograph": "exec-a45355d5-fc44-41e5-b510-21b2871582a2.png",
    "cyanotype": "exec-91f71c22-6139-48b9-9f35-6bec789bd45a.png",
    "copper_engraving": "exec-71328abe-3b94-45fb-a695-238517ec2c5e.png",
    "scratchboard": "exec-0afb5539-4490-416c-a477-96d70ce65dca.png",
    "watercolor_wash": "exec-34718cd6-21ae-43a6-b241-3059c6feb117.png",
    "gouache": "exec-2b041065-2498-437d-9760-f95b8b807d02.png",
    "dry_pastel": "exec-28816e40-b4b5-4681-b91e-02d18592a6da.png",
    "charcoal_drawing": "exec-d38606eb-11e6-400e-a6c0-233f9e6f4699.png",
    "sumi_e": "exec-a0252efb-44bd-44e0-a782-5de93997660e.png",
    "art_nouveau": "exec-25967e31-839c-43a3-9b57-b8397fb5bf78.png",
    "art_deco": "exec-fc8334f7-e0f0-4d70-9a38-3af6693fef42.png",
    "ukiyo_e": "exec-1626bbab-88ea-4299-ac58-40d1402f3af6.png",
    "lubok": "exec-52057931-b453-4b0e-95d8-7f302c8aa465.png",
    "medieval_miniature": "exec-a74a6e99-ff48-4dd5-a24c-8334ec916839.png",
    "rococo": "exec-fc3ea001-9912-49ab-9159-076feaae6661.png",
    "impressionist": "exec-e4b7f13b-dedb-477e-9fa0-52d25065bbd0.png",
    "pointillist": "exec-bc5d286e-366b-4348-a2f6-1a52c769b8fd.png",
    "cubist": "exec-f071664c-748a-4f88-a0a0-5e52b4881052.png",
    "surrealist": "exec-33a17ae3-bd44-4868-b1ee-efc4a4d3ec3c.png",
    "solarpunk_greenhouse": "exec-c42e4ceb-2f60-4320-88eb-aa3167cf0bbc.png",
    "bioluminescent_mushrooms": "exec-861208f0-e405-47ca-9e82-8b172df98a00.png",
    "snow_globe": "exec-8518e2e2-1d83-4348-abcc-36cf1098f4b5.png",
    "adobe_desert": "exec-234f5e46-bc3d-4901-9e05-27744fd9e7b6.png",
    "volcanic_basalt": "exec-5f792c29-5ad6-4343-aa4f-7b488eaca172.png",
    "autumn_collage": "exec-03c02e9f-de5b-4349-bd97-154a71150c13.png",
    "underwater_reef": "exec-8b2a1025-4b0d-4a97-8b8f-66809824ab09.png",
    "floating_islands": "exec-3b962b9e-a6c7-4c30-8081-494196f78070.png",
    "cloud_kingdom": "exec-c6a29b14-e5b8-44ca-94e2-6bd87a5655f1.png",
    "bonsai_garden": "exec-1b240759-e5a8-4c69-858e-5fb898b2d390.png",
    "architectural_blueprint": "exec-c371238a-94c0-4dcc-919d-b89e338e2e19.png",
    "xray_house": "exec-a82fa44b-d37d-414a-aff6-3ab10497789d.png",
    "thermal_vision": "exec-55134082-6d26-4edb-a665-27485dcfebcd.png",
    "crt_screen": "exec-085ffbb9-b365-422b-8083-413947d9fe67.png",
    "digital_glitch": "exec-549d0a73-2a6c-4e53-b789-791ab7a8e856.png",
    "voxel_isometric": "exec-862f34d2-ecaf-45c1-8ae1-0961308ed2e8.png",
    "liquid_chrome": "exec-f1ddad4e-6555-46ab-b456-6a549511f5ed.png",
    "engineering_exploded": "exec-bf426dc0-c702-47ae-8102-deb08ed6e742.png",
    "retro_space_poster": "exec-545f33c8-a3d8-454d-b010-43d90af6260c.png",
    "quantum_field": "exec-d5f6ad62-5feb-49f1-b6ff-0febcd93ac57.png",
    "crayon_drawing": "exec-3d03c247-c419-49af-aceb-4c4914955c6f.png",
    "chalkboard": "exec-3b7539be-ddb6-4938-831b-272cb8aaded9.png",
    "sticker_pack": "exec-4f35a510-0549-487c-91cf-b28f0352432d.png",
    "toy_blocks": "exec-b2779472-969d-4ca5-9de9-fe0f13dda579.png",
    "gingerbread": "exec-741d7f10-bd0a-445e-b7a3-d517dd3ed880.png",
    "gummy_glass": "exec-dec77d70-ce03-4e82-8971-141ca3bf2868.png",
    "inflatable_sculpture": "exec-418fafb8-3ec5-42ef-88cf-3858453e2dca.png",
    "plush_toy": "exec-0bb71f1a-702f-4ad4-aac3-12db4debc368.png",
    "shadow_theatre": "exec-2ad00f01-0a2f-4b0d-8ed2-618c4ef5639c.png",
    "paper_diorama": "exec-73ee6f38-9468-452b-af18-59ba771dec35.png",
}

# body, roof, outline, door, window. Geometry never changes between entries.
PALETTES: Final[dict[str, tuple[str, str, str, str, str]]] = {
    "stained_glass": ("#241265", "#113EAF", "#10101A", "#090817", "#FFC83D"),
    "ceramic_mosaic": ("#EEE2BF", "#123E9A", "#172332", "#0D285C", "#FFC54D"),
    "cloisonne_enamel": ("#092C61", "#006E69", "#F2B93B", "#080A10", "#FFC83D"),
    "terrazzo": ("#242424", "#30343B", "#F2E5C5", "#121212", "#FFC84A"),
    "carved_wood": ("#9D5B20", "#3B1E0E", "#F0B35B", "#241006", "#FFD267"),
    "wood_marquetry": ("#E2B66F", "#3C1D12", "#17110D", "#18100B", "#FFC84A"),
    "satin_embroidery": ("#081E5E", "#EF604F", "#F5B642", "#061238", "#FFD05A"),
    "knitted_wool": ("#D8B05A", "#F1E1BE", "#43101B", "#351018", "#FFD05A"),
    "felt_applique": ("#102B58", "#0A1F47", "#E6A52F", "#08142C", "#FFD65E"),
    "claymation": ("#EC6347", "#184C82", "#13213A", "#17233C", "#FFD454"),
    "linocut": ("#111419", "#C73720", "#F1E4C1", "#090A0B", "#FFC33A"),
    "risograph": ("#65408D", "#F02B7D", "#132C69", "#1A2451", "#FFD04D"),
    "cyanotype": ("#F2F0E8", "#0E3D83", "#FFFFFF", "#0A2B61", "#FFD04D"),
    "copper_engraving": ("#3B2116", "#B9662E", "#21110C", "#170B08", "#FFD06A"),
    "scratchboard": ("#EFE4BF", "#101010", "#F4E9C9", "#111111", "#E8A52B"),
    "watercolor_wash": ("#243B88", "#E45E65", "#5A357D", "#152452", "#FFD25A"),
    "gouache": ("#204B37", "#1946A7", "#E9D8B4", "#183226", "#FFC83D"),
    "dry_pastel": ("#38214F", "#F45172", "#6BE1D4", "#241332", "#FFD35D"),
    "charcoal_drawing": ("#282727", "#E8E1D4", "#191919", "#111111", "#FFD15A"),
    "sumi_e": ("#EEE5D5", "#1A1A19", "#111111", "#111111", "#E5A92F"),
    "art_nouveau": ("#083C70", "#0D6B48", "#EAB64B", "#06243E", "#FFD05A"),
    "art_deco": ("#080A0C", "#086C69", "#F1B842", "#030405", "#FFD05A"),
    "ukiyo_e": ("#EEE0BD", "#153C68", "#182D3C", "#182635", "#D95032"),
    "lubok": ("#F2C74E", "#C9281D", "#152519", "#102016", "#FFD965"),
    "medieval_miniature": ("#103B98", "#A92823", "#E6B83D", "#101D4E", "#FFD260"),
    "rococo": ("#F2CFC7", "#F4EFE5", "#D7A63B", "#9BB8CF", "#FFD25C"),
    "impressionist": ("#173CA1", "#E8A72E", "#5AC45F", "#11266B", "#FFD24F"),
    "pointillist": ("#122A68", "#D92A9E", "#F4D445", "#0D1B48", "#FFD44C"),
    "cubist": ("#D5CDBE", "#16191B", "#202020", "#151515", "#F0A832"),
    "surrealist": ("#4B2D68", "#25143A", "#F4C5A3", "#1C1028", "#FFD36A"),
    "solarpunk_greenhouse": ("#F0E5C8", "#158160", "#1F6E5C", "#104B3E", "#FFC84E"),
    "bioluminescent_mushrooms": ("#07163C", "#00AEEB", "#E43BD9", "#050A20", "#FFD94F"),
    "snow_globe": ("#0B2E5F", "#A7D8ED", "#E7F6FF", "#071B39", "#FFD05A"),
    "adobe_desert": ("#E9BD78", "#B7552E", "#5D2A1A", "#2E3A3D", "#FFD15C"),
    "volcanic_basalt": ("#171719", "#29292B", "#F04A24", "#080809", "#FFBA3B"),
    "autumn_collage": ("#123D2C", "#A63E21", "#E1A62D", "#0C281D", "#FFD45A"),
    "underwater_reef": ("#082A58", "#0A80B9", "#F04F4C", "#051D3D", "#FFD14F"),
    "floating_islands": ("#F2E6C9", "#1879BA", "#163D62", "#133047", "#FFD15A"),
    "cloud_kingdom": ("#A98AD9", "#4F9ADB", "#F3ECFF", "#553A76", "#FFD26A"),
    "bonsai_garden": ("#E4D8B5", "#1D211E", "#9D211A", "#101311", "#F1B43B"),
    "architectural_blueprint": ("#0D4A99", "#123E7C", "#F4FAFF", "#082E62", "#FFD04B"),
    "xray_house": ("#06182A", "#0A304B", "#A9F5FF", "#020B12", "#E7FAFF"),
    "thermal_vision": ("#27105C", "#FFB51F", "#F729A4", "#0B0618", "#FFF26A"),
    "crt_screen": ("#06120A", "#0A3318", "#39FF79", "#020704", "#D5FF53"),
    "digital_glitch": ("#07080D", "#00DCEB", "#FF218F", "#020205", "#FFD650"),
    "voxel_isometric": ("#9A5C22", "#25A934", "#17470F", "#55300E", "#FFD047"),
    "liquid_chrome": ("#212127", "#DDE4EC", "#10B8F0", "#0D0D10", "#FFCA52"),
    "engineering_exploded": ("#18232B", "#EC7B21", "#10BBD8", "#080E12", "#FFD251"),
    "retro_space_poster": ("#1E777A", "#E8D9AF", "#E36855", "#123D45", "#FFD05A"),
    "quantum_field": ("#11102D", "#4D28A7", "#22C6EC", "#070615", "#FFD452"),
    "crayon_drawing": ("#1E61B6", "#E7442E", "#15723A", "#153F74", "#FFD43B"),
    "chalkboard": ("#123E2B", "#E1C343", "#F5F1DE", "#0B281C", "#FFD25A"),
    "sticker_pack": ("#17265E", "#EF3E9B", "#FFFFFF", "#0B1538", "#FFD047"),
    "toy_blocks": ("#F0B92F", "#20A253", "#1655AF", "#CA3927", "#FFD64D"),
    "gingerbread": ("#A64E21", "#EEDFC5", "#B71F1F", "#6A2811", "#FFD75C"),
    "gummy_glass": ("#08AFCB", "#F0A31B", "#E52E8E", "#204067", "#FFD64D"),
    "inflatable_sculpture": ("#F0C62D", "#205DD2", "#EF6D59", "#164290", "#FFD950"),
    "plush_toy": ("#2D9290", "#EFE2C5", "#D5A7CF", "#1B5E5D", "#FFD45F"),
    "shadow_theatre": ("#080808", "#8D1519", "#E8A42B", "#030303", "#FFD05A"),
    "paper_diorama": ("#F0E3C5", "#17497E", "#D85E4D", "#5B3971", "#FFD054"),
}


def fit(path: Path) -> Image.Image:
    image = Image.open(path).convert("RGB")
    return image.resize((1024, 1024), Image.Resampling.LANCZOS)


def house_layer(palette: tuple[str, str, str, str, str]) -> Image.Image:
    body, roof, outline, door, window = palette
    layer = Image.new("RGBA", (1024, 1024), (0, 0, 0, 0))
    shadow = Image.new("RGBA", layer.size, (0, 0, 0, 0))
    shadow_draw = ImageDraw.Draw(shadow)
    shadow_ink = (0, 0, 0, 125)
    shadow_draw.rectangle((382, 638, 642, 830), fill=shadow_ink)
    shadow_draw.rectangle((405, 528, 448, 638), fill=shadow_ink)
    shadow_draw.polygon(((342, 658), (512, 498), (682, 658)), fill=shadow_ink)
    shadow = shadow.filter(ImageFilter.GaussianBlur(16))
    layer.alpha_composite(shadow, (10, 12))

    draw = ImageDraw.Draw(layer)
    width = 14
    draw.rectangle((382, 630, 642, 822), fill=body, outline=outline, width=width)
    draw.rectangle((405, 520, 448, 632), fill=roof, outline=outline, width=width)
    draw.polygon(
        ((342, 650), (512, 490), (682, 650)),
        fill=roof,
        outline=outline,
        width=width,
    )
    draw.line(((350, 651), (674, 651)), fill=outline, width=18)
    draw.rectangle((416, 710, 476, 822), fill=door, outline=outline, width=11)
    draw.ellipse((457, 762, 467, 772), fill=window)

    for left, top in ((535, 704), (575, 704), (535, 744), (575, 744)):
        draw.rounded_rectangle(
            (left, top, left + 30, top + 30),
            radius=3,
            fill=window,
            outline=outline,
            width=6,
        )
    draw.rectangle((346, 818, 678, 836), fill=outline)
    return layer


def compose(icon_id: str) -> Image.Image:
    scene = fit(GENERATED / ENVIRONMENTS[icon_id]).convert("RGBA")
    scene.alpha_composite(house_layer(PALETTES[icon_id]))
    return scene.convert("RGB")


def write_sheet(icons: dict[str, Image.Image]) -> None:
    actual = 48
    scale = 4
    tile = actual * scale
    label_height = 26
    gap = 10
    columns = 6
    rows = (len(icons) + columns - 1) // columns
    sheet = Image.new(
        "RGB",
        (columns * (tile + gap) + gap, rows * (tile + label_height + gap) + gap),
        "#202124",
    )
    draw = ImageDraw.Draw(sheet)
    font = ImageFont.load_default(size=13)
    for index, (icon_id, icon) in enumerate(icons.items()):
        x = gap + (index % columns) * (tile + gap)
        y = gap + (index // columns) * (tile + label_height + gap)
        small = icon.resize((actual, actual), Image.Resampling.LANCZOS)
        preview = small.resize((tile, tile), Image.Resampling.NEAREST)
        sheet.paste(preview, (x, y))
        draw.text((x, y + tile + 5), icon_id, fill="white", font=font)
    sheet.save(OUT / "new60-48px-contact-sheet.png", optimize=True)


def main() -> None:
    missing = [name for name in ENVIRONMENTS.values() if not (GENERATED / name).exists()]
    if missing:
        raise FileNotFoundError("Missing generated environments: " + ", ".join(missing))
    if set(ENVIRONMENTS) != set(PALETTES):
        raise ValueError("Environment and palette ids differ")

    OUT.mkdir(parents=True, exist_ok=True)
    icons: dict[str, Image.Image] = {}
    for icon_id in ENVIRONMENTS:
        icon = compose(icon_id)
        icons[icon_id] = icon
        icon.save(OUT / f"{icon_id}.webp", "WEBP", quality=95, method=6)
    write_sheet(icons)
    print(f"Composed {len(icons)} fixed-house icon previews")


if __name__ == "__main__":
    main()
