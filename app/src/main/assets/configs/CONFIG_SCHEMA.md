# Anamol Camera — GCam 8.5 Style XML Config Schema v8.5

Camera Configs control the **base computational photography pipeline** (HDR+ merge strength, tone curve, black level, RGB gains, AWB bias, luma/chroma denoising, sharpness, night sight, portrait bokeh, and per-mode overrides). Filter Packs stack cleanly on top of the active Camera Config.

## Root Element
`<gcam-config version="8.5">`

## Sections & Tags
- `<meta>`: `id` (required), `name` (required), `icon`, `author`, `deviceProfile`, `description`
- `<hdrplus>`: `hdrPlusStrength` (0..1.5), `toneMappingGamma` (0.6..1.6), `exposureCompensation` (-2.0..2.0), `shadows` (-1..1), `highlights` (-1..1), `blackLevel` (-0.2..0.2), `nightSightStrength` (0..1.5)
- `<color>`: `contrast` (0.5..1.8), `saturation` (0..2.0), `vibrance` (-0.5..1.0), `skinToneWarmth` (-0.5..0.5)
- `<awb>`: `awbBias` (`AUTO | NATURAL | DAYLIGHT | WARM_TUNGSTEN | COOL_SHADE`), `temperatureOffset` (-1..1), `tintOffset` (-0.5..0.5), `rgbGainR` (0.6..1.4), `rgbGainG` (0.6..1.4), `rgbGainB` (0.6..1.4)
- `<detail>`: `sharpness` (0..1.5), `lumaNoiseReduction` (0..1.0), `chromaNoiseReduction` (0..1.0)
- `<lens>`: `portraitBlurStrength` (0..1.0), `vignette` (0..1.0), `grain` (0..1.0)
- `<overrides>`: `<mode target="PHOTO|PORTRAIT|NIGHT|VIDEO|FRONT">` with optional `exposureCompensation`, `hdrPlusStrength`, `nightSightStrength`, `contrast`, `saturation`, `sharpness`, `awbBias`

Out-of-range numbers clamp safely with warnings; unknown tags generate non-fatal warnings and are ignored.
