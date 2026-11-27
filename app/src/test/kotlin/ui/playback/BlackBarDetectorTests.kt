package org.jellyfin.androidtv.ui.playback

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe

class BlackBarDetectorTests : FunSpec({
	val w = 160
	val h = 90
	fun frame(top: Int = 0, bottom: Int = top, left: Int = 0, right: Int = left, color: Int = 0xff909090.toInt()) =
		IntArray(w * h) { i ->
			val x = i % w
			val y = i / w
			if (y < top || y >= h - bottom || x < left || x >= w - right) 0xff000000.toInt() else color
		}

	test("detects encoded horizontal bars and fills a 16:9 screen") {
		val bounds = BlackBarDetector.detect(frame(top = 12), w, h)!!
		bounds.horizontalBar shouldBe 12
		bounds.verticalBar shouldBe 0
		BlackBarDetector.fillScale(bounds, w, h, 1920, 1080, 1920, 1080) shouldBe (90f / 66f plusOrMinus 0.001f)
	}

	test("does not zoom a normal 16:9 frame") {
		val bounds = BlackBarDetector.detect(frame(), w, h)!!
		bounds.horizontalBar shouldBe 0
		bounds.verticalBar shouldBe 0
		BlackBarDetector.fillScale(bounds, w, h, 1920, 1080, 1920, 1080) shouldBe 1f
	}

	test("native widescreen with no embedded bars still fills the viewport") {
		val bounds = BlackBarDetector.detect(frame(), w, h)!!
		BlackBarDetector.fillScale(bounds, w, h, 1920, 1080, 1920, 804) shouldBe (1080f / 804f plusOrMinus 0.001f)
	}

	test("detects vertical pillarbox bars") {
		val bounds = BlackBarDetector.detect(frame(left = 20), w, h)!!
		bounds.horizontalBar shouldBe 0
		bounds.verticalBar shouldBe 20
		BlackBarDetector.fillScale(bounds, w, h, 1920, 1080, 1920, 1080) shouldBe (160f / 120f plusOrMinus 0.001f)
	}

	test("ignores completely dark and very dark frames") {
		BlackBarDetector.detect(frame(color = 0xff000000.toInt()), w, h) shouldBe null
		BlackBarDetector.detect(frame(top = 12, color = 0xff101010.toInt()), w, h) shouldBe null
	}

	test("rejects strongly asymmetric edges") {
		BlackBarDetector.detect(frame(top = 12, bottom = 0), w, h) shouldBe null
	}
})
