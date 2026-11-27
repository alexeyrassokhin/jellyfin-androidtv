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
