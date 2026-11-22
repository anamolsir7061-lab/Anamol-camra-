# Anamol Camera — XML Filter-Pack Schema v1.0

Filters in Anamol Camera are defined as portable `.xml` documents that can be imported, validated, stacked, live-edited, and exported.

## Root Element
`<lensmind-filter version="1.0">`

### `<meta>` (Required)
- `<id>`: Unique lowercase slug (`[a-z0-9_-]+`, 2–48 chars).
- `<name>`: Display name (1–32 chars).
- `<icon>`: Emoji or icon token shown in the filter carousel (e.g. `🎬`, `🌿`, `🕶️`).
- `<category>`: One of `COLOR_GRADE`, `VINTAGE`, `BW`, `CINEMATIC`, `NEON`, `GLITCH`, `BEAUTY`, `AR_LENS`, `NATURE`, `DUOTONE`.
- `<author>`: Creator name (optional, defaults to `Community`).
- `<description>`: Short filter note (optional).

### `<adjustments>` (Required)
All numeric parameters are validated strictly within bounds:
- `<brightness>`: `-1.0` to `1.0` (default `0.0`)
- `<contrast>`: `0.2` to `2.5` (default `1.0`)
- `<saturation>`: `0.0` to `2.5` (default `1.0`)
- `<hue>`: `-180.0` to `180.0` degrees (default `0.0`)
- `<temperature>`: `-1.0` (cool/blue) to `1.0` (warm/amber) (default `0.0`)
- `<greenery>`: `-1.0` to `1.0` foliage & emerald channel boost (default `0.0`)
- `<blur>`: `0.0` to `1.0` soft bokeh/bloom blur (default `0.0`)
- `<grain>`: `0.0` to `1.0` organic 35mm film grain (default `0.0`)
- `<vignette>`: `0.0` to `1.0` radial edge falloff (default `0.0`)
- `<skinSmooth>`: `0.0` to `1.0` beauty softness (default `0.0`)
- `<highlights>`: `-1.0` to `1.0` highlight recovery/boost (default `0.0`)
- `<shadows>`: `-1.0` to `1.0` shadow lift/crush (default `0.0`)
- `<glitch>`: `0.0` to `1.0` RGB chromatic split + scanline shift (default `0.0`)
- `<duotonePrimary>`: Optional `#RRGGBB` shadow tint hex
- `<duotoneSecondary>`: Optional `#RRGGBB` highlight tint hex
- `<blendMode>`: `NORMAL`, `MULTIPLY`, `SCREEN`, `OVERLAY`, `SOFT_LIGHT`
- `<lutPreset>`: `NONE`, `TEAL_ORANGE`, `KODAK_WARM`, `FUJI_PRO`, `CYBER_MAGENTA`, `NOIR_SILVER`

### `<ar-overlay>` (Optional)
Face-anchored AR effects powered by ML Kit face landmarks:
- `type`: `NONE`, `NEON_VISOR`, `SUNGLASSES`, `CAT_EARS`, `CROWN`, `HEART_BLUSH`, `HALO`, `CYBER_HUD`
- `anchor`: `EYES`, `FOREHEAD`, `CHEEKS`, `FULL_FACE`
- `scale`: `0.5` to `2.0` (default `1.0`)
- `offsetY`: `-0.5` to `0.5` normalized vertical offset (default `0.0`)
- `tint`: `#RRGGBB` hex color (default `#00E5FF`)
- `animation`: `NONE`, `PULSE`, `FLOAT`, `SPARKLE`
