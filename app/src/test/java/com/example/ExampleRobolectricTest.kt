package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.example.camera.FilterBitmapProcessor
import com.example.camera.LightAdaptationEngine
import com.example.data.CameraConfigXmlCodec
import com.example.data.FilterXmlCodec
import com.example.model.ActiveFilterLayer
import com.example.model.CameraFilter
import com.example.model.CameraMode
import com.example.model.ConfigXmlValidationResult
import com.example.model.FilterAdjustments
import com.example.model.FilterCategory
import com.example.model.LightAdaptationState
import com.example.model.ProManualParams
import com.example.model.XmlValidationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `app name matches Anamol Camera`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Anamol Camera", appName)
    }

    @Test
    fun `all bundled sample XML filters validate cleanly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val files = context.assets.list("filters")?.filter { it.endsWith(".xml") }.orEmpty()
        assertTrue("Expected at least 12 bundled XML filters, found ${files.size}", files.size >= 12)
        for (name in files) {
            val xml = context.assets.open("filters/$name").bufferedReader().use { it.readText() }
            val result = FilterXmlCodec.parseAndValidate(xml, isBuiltIn = true)
            assertTrue("Filter $name failed validation: $result", result is XmlValidationResult.Valid)
        }
    }

    @Test
    fun `all bundled GCam 8_5 XML camera configs validate cleanly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val files = context.assets.list("configs")?.filter { it.endsWith(".xml") }.orEmpty()
        assertTrue("Expected at least 8 bundled GCam 8.5 configs, found ${files.size}", files.size >= 8)
        for (name in files) {
            val xml = context.assets.open("configs/$name").bufferedReader().use { it.readText() }
            val result = CameraConfigXmlCodec.parseAndValidate(xml, isBuiltIn = true)
            assertTrue("Config $name failed validation: $result", result is ConfigXmlValidationResult.Valid)
        }
    }

    @Test
    fun `gcam config xml codec catches missing meta id and clamps out of range floats with warnings`() {
        val missingIdXml = """
            <gcam-config version="8.5">
                <meta><name>Broken Config</name></meta>
                <hdrplus><hdrPlusStrength>1.0</hdrPlusStrength></hdrplus>
            </gcam-config>
        """.trimIndent()
        val invalid = CameraConfigXmlCodec.parseAndValidate(missingIdXml)
        assertTrue(invalid is ConfigXmlValidationResult.Invalid)
        assertTrue((invalid as ConfigXmlValidationResult.Invalid).errors.any { it.contains("id", ignoreCase = true) })

        val warningXml = """
            <gcam-config version="8.5">
                <meta><id>safe_clamp</id><name>Clamped Config</name></meta>
                <hdrplus><hdrPlusStrength>9.5</hdrPlusStrength></hdrplus>
                <unknownTag>123</unknownTag>
            </gcam-config>
        """.trimIndent()
        val clamped = CameraConfigXmlCodec.parseAndValidate(warningXml)
        assertTrue(clamped is ConfigXmlValidationResult.Valid)
        val valid = clamped as ConfigXmlValidationResult.Valid
        assertEquals(1.5f, valid.config.params.hdrPlusStrength, 0.001f)
        assertTrue(valid.warnings.any { it.contains("clamped", ignoreCase = true) })
        assertTrue(valid.warnings.any { it.contains("unknownTag", ignoreCase = true) })
    }

    @Test
    fun `gcam config stacks cleanly beneath filter layers and pro params`() {
        val cfgXml = """
            <gcam-config version="8.5">
                <meta><id>vivid_test</id><name>Vivid Test</name></meta>
                <color><contrast>1.20</contrast><saturation>1.25</saturation></color>
            </gcam-config>
        """.trimIndent()
        val cfg = (CameraConfigXmlCodec.parseAndValidate(cfgXml) as ConfigXmlValidationResult.Valid).config
        val filter = CameraFilter(
            id = "warm_layer",
            name = "Warm",
            icon = "☀️",
            category = FilterCategory.COLOR_GRADE,
            adjustments = FilterAdjustments(temperature = 0.25f)
        )
        val merged = FilterBitmapProcessor.mergeAdjustments(
            layers = listOf(ActiveFilterLayer(filter, 1f)),
            pro = ProManualParams(),
            light = LightAdaptationState(),
            activeConfig = cfg,
            cameraMode = CameraMode.PHOTO
        )
        assertTrue("Expected GCam contrast to carry through", merged.contrast >= 1.18f)
        assertTrue("Expected filter temperature to stack on top", merged.temperature >= 0.20f)
    }

    @Test
    fun `xml validator catches out-of-range contrast and missing meta id`() {
        val badXml = """
            <lensmind-filter version="1.0">
                <meta><name>Bad</name></meta>
                <adjustments><contrast>99.0</contrast></adjustments>
            </lensmind-filter>
        """.trimIndent()
        val res = FilterXmlCodec.parseAndValidate(badXml)
        assertTrue(res is XmlValidationResult.Invalid)
        val errors = (res as XmlValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("contrast", ignoreCase = true) })
        assertTrue(errors.any { it.contains("id", ignoreCase = true) })
    }

    @Test
    fun `light adaptation boosts dark frames and protects bright frames`() {
        val engine = LightAdaptationEngine()
        val darkBmp = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(10, 10, 10))
        }
        repeat(15) { engine.analyzeFrame(darkBmp, enabled = true, sensitivity = 1.0f) }
        val darkState = engine.analyzeFrame(darkBmp, enabled = true, sensitivity = 1.0f)
        assertTrue("Expected low light boost", darkState.lowLightBoostActive)
        assertTrue("Expected positive brightness offset", darkState.adaptiveBrightnessOffset > 0f)
        darkBmp.recycle()

        val brightEngine = LightAdaptationEngine()
        val brightBmp = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(245, 245, 245))
        }
        repeat(15) { brightEngine.analyzeFrame(brightBmp, enabled = true, sensitivity = 1.0f) }
        val brightState = brightEngine.analyzeFrame(brightBmp, enabled = true, sensitivity = 1.0f)
        assertTrue("Expected bright highlight guard", brightState.brightLightProtectActive)
        assertTrue("Expected negative brightness offset", brightState.adaptiveBrightnessOffset < 0f)
        brightBmp.recycle()
    }
}
