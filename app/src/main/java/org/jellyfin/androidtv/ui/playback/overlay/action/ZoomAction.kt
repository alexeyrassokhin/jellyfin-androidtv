package org.jellyfin.androidtv.ui.playback.overlay.action

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.PopupMenu
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.preference.constant.ZoomMode
import org.jellyfin.androidtv.ui.playback.PlaybackController
import org.jellyfin.androidtv.ui.playback.overlay.CustomPlaybackTransportControlGlue
import org.jellyfin.androidtv.ui.playback.overlay.VideoPlayerAdapter
import org.jellyfin.androidtv.util.popupMenu

class ZoomAction(
	context: Context,
	customPlaybackTransportControlGlue: CustomPlaybackTransportControlGlue,
) : CustomAction(context, customPlaybackTransportControlGlue) {
	private var popup: PopupMenu? = null

	init {
		initializeWithIcon(R.drawable.ic_aspect_ratio)
	}

	override fun handleClickAction(
		playbackController: PlaybackController,
		videoPlayerAdapter: VideoPlayerAdapter,
		context: Context,
		view: View,
	) {
		videoPlayerAdapter.leanbackOverlayFragment.setFading(false)
		dismissPopup()
		val currentManualZoom = playbackController.manualZoomPercent
		popup = popupMenu(context, view, Gravity.END) {
			item(context.getString(R.string.lbl_fit)) {
				playbackController.setZoom(ZoomMode.FIT)
			}.apply {
				isChecked = playbackController.zoomMode == ZoomMode.FIT
			}

			item(context.getString(R.string.lbl_auto_crop)) {
				playbackController.setZoom(ZoomMode.AUTO_CROP)
			}.apply {
				isChecked = playbackController.zoomMode == ZoomMode.AUTO_CROP
			}

			item(context.getString(R.string.lbl_stretch)) {
				playbackController.setZoom(ZoomMode.STRETCH)
			}.apply {
				isChecked = playbackController.zoomMode == ZoomMode.STRETCH
			}

			item(context.getString(R.string.lbl_detect_black_bars)) {
				playbackController.setZoom(ZoomMode.DETECT_BLACK_BARS)
			}.apply {
				isChecked = playbackController.zoomMode == ZoomMode.DETECT_BLACK_BARS
			}

			item(context.getString(R.string.lbl_zoom_increase, currentManualZoom)) {
				playbackController.setManualZoomPercent(currentManualZoom + 5)
			}
			item(context.getString(R.string.lbl_zoom_decrease, currentManualZoom)) {
				playbackController.setManualZoomPercent(currentManualZoom - 5)
			}
			item(context.getString(R.string.lbl_zoom_reset)) {
				playbackController.setZoom(ZoomMode.FIT)
			}
			for (percent in listOf(125, 135, 150)) {
				item(context.getString(R.string.lbl_zoom_preset, percent)) {
					playbackController.setManualZoomPercent(percent)
				}.apply {
					isChecked = playbackController.zoomMode == ZoomMode.MANUAL &&
						currentManualZoom == percent
				}
			}
		}
		popup?.menu?.setGroupCheckable(0, true, true)
		popup?.setOnDismissListener {
			videoPlayerAdapter.leanbackOverlayFragment.setFading(true)
			popup = null
		}
		popup?.show()
	}

	fun dismissPopup() {
		popup?.dismiss()
	}
}
