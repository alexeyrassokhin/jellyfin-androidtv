# Experimental manual video zoom (Android TV)

This branch adds an experimental manual zoom to the existing Jellyfin Android TV playback menu.

## Features
- **Zoom +5% / Zoom -5%**, from **100% to 160%**.
- Presets **125%**, **135%**, **150%**, and **Reset zoom**.
- Manual zoom is saved locally per Jellyfin media item ID, so the selected factor can be restored for the same movie or episode.
- Existing Fit, Auto Crop, and Stretch choices remain available.
- The implementation targets the Media3 video surface rather than enlarging the whole player overlay.

## Testing on NVIDIA Shield
In the pull request, download **build-artifacts** from the **App / Build** GitHub Actions run. Install the debug APK on Shield (package `org.jellyfin.androidtv.debug`, alongside the official Jellyfin), log into the server, and play a 3840×2160 video with black bars encoded into its video frames.

Open the player **Zoom** menu, select 135%, and verify that the picture gets bigger. Also test:
1. Normal 16:9 video, plus 2.39:1 content with baked-in letterboxing.
2. Seek, pause/resume, next episode, and app restart.
3. Normal and ASS subtitles, HUD visibility, audio pass-through.
4. 4K HDR and Dolby Vision where supported.

## Limitations
- **This is experimental code, not yet tested on NVIDIA Shield.** Scaling `SurfaceView` or `TextureView` may behave differently across Android versions and hardware decode paths.
- At 135%, the video is enlarged but **image content is cropped at the left and right**; this is intentional when fitting a 2.39:1 movie to a 16:9 screen.
- This is manual adjustment, **not automatic black-bar detection**.
- The default zoom is used for items without a saved manual zoom value; saved values live only on this device.
- If the video surface ignores transforms, the next iteration should use a verified Media3/SurfaceControl approach with device diagnostics before changing rendering paths.

## Expected geometry for an embedded letterbox

For a 2.39:1 movie embedded in a 16:9 (3840×2160) video frame, removing the
vertical black bars by manual zoom takes approximately
`(2.39 / (16 / 9)) × 100 = 134.4%`. The **135%** preset is therefore a
useful first check. This fills the height but **crops image on the left and
right**; it does not preserve the full widescreen composition.

Use the test file the user observed failing with the stock **Auto Crop** mode
on NVIDIA Shield. Confirm the manual mode works independently of aspect-ratio
metadata, then test HDR and subtitle behavior.

## Auto detect black bars and series-wide preferences

- Select **Auto detect black bars** (in Russian: **Автоопределение чёрных полос**) in the playback zoom menu. Existing Fit/Auto Crop/Stretch and manual zoom algorithms are unchanged. The new mode samples a 160×90 copy of the current SurfaceView buffer via Android PixelCopy every 2.5 seconds while playing. Three consistent measurements are required. Unreliable/dark frames keep the last valid crop. It fills the viewport using a uniform scale (maximum 2×); filling a wider or narrower movie necessarily loses some image content.
- The selected zoom mode and manual percentage are now stored per movie or per series ID (all seasons/episodes sharing that ID). If the server does not supply a series ID the preference falls back to the individual item. Auto mode stores the mode, not the measured crop, and resets detection on a new stream. Legacy per-item manual values remain readable until a new explicit selection is made. Preferences are local to the app/device.
- This detector is deliberately conservative; dark, uneven, subtitled, nonblack or asymmetric borders may be rejected. SurfaceView capture may be unavailable on some HDR/protected/hardware playback paths. A failed copy does not change the last valid crop. No automatic detection work is performed in the old modes.
- Unit tests cover normal frames, encoded letterbox/pillarbox, native wide frames, dark scenes, and asymmetric borders. Device validation remains required: wait for a few samples, test a 3840×2160 letterboxed film, a 16:9 film, an aspect ratio change, subtitles, HDR and audio passthrough; then reopen another episode in the same series.

## Shield edge-smearing fix (second rendering implementation)

Physical testing showed severe horizontal and vertical edge replication with the original View scale transform. Both manual and detected zoom now share VideoSurfaceZoom, which lays out a larger centered video surface without setScaleX/setScaleY and restores the original layout for stock modes. The Fit content frame remains unscaled, and auto-detection uses that frame's dimensions (rather than the enlarged surface) to avoid a feedback loop. This is a candidate fix; Shield testing is still necessary. Compare Fit, manual 125/135%, and automatic zoom in the same scene, then switch back to Fit and verify clean edges and HDR. If artifacts persist, capture device playback format, UI resolution/output resolution, and adb logcat. Do not disable HDR or switch the entire player to TextureView as a default workaround.
