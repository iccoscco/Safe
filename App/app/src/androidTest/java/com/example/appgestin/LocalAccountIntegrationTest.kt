package com.example.appgestin

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.appgestin.controller.GstiController
import com.example.appgestin.controller.Page
import com.example.appgestin.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class LocalAccountIntegrationTest {
    private class IsolatedContext(base: Context): ContextWrapper(base) {
        private val id = UUID.randomUUID().toString()
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
            baseContext.getSharedPreferences("safe_test_${id}_$name", mode)
        override fun getFilesDir(): File = File(baseContext.cacheDir, "safe_profile_test_$id").apply { mkdirs() }
    }

    @Test fun loginAndEnrollmentNavigationRequireCorrectCredentials() {
        val ctx = IsolatedContext(InstrumentationRegistry.getInstrumentation().targetContext)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val c = GstiController(ctx, scope)
            assertEquals(Page.Login, c.state.page)
            c.login("admin", "incorrect")
            assertEquals(Page.Login, c.state.page)
            assertNotNull(c.state.error)
            c.showEnrollment()
            assertEquals(Page.Enroll, c.state.page)
            c.closeEnrollment()
            assertEquals(Page.Login, c.state.page)
            c.login("admin", "admin")
            assertEquals(Page.Home, c.state.page)
            assertTrue(c.state.preview)
            scope.cancel()
        }
    }

    @Test fun closingLocalSessionPreservesBindingPendingResponseAndProfile() {
        val ctx = IsolatedContext(InstrumentationRegistry.getInstrumentation().targetContext)
        val store = SecureSession(ctx)
        val session = Session("https://example.org", "test_device_token", "TEST-PARTICIPANT")
        store.save(session)
        store.setPendingPayload("{\"client_response_id\":\"test\"}")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val c = GstiController(ctx, scope)
            assertEquals(Page.Login, c.state.page)
            c.login("admin", "admin")
            assertEquals(Page.Home, c.state.page)
            assertFalse(c.state.preview)
            c.saveProfile("Nombre de prueba", "demo@example.org", "Perfil local")
            c.signOut()
            assertEquals(Page.Login, c.state.page)
            assertEquals(session, store.load())
            assertNotNull(store.pendingPayload())
            assertEquals("Nombre de prueba", SecureSession(ctx).loadProfile().name)
            scope.cancel()
        }
    }

    @Test fun invalidBackendBindingDoesNotBlockLocalAdminLogin() {
        val ctx = IsolatedContext(InstrumentationRegistry.getInstrumentation().targetContext)
        SecureSession(ctx).save(Session("http://10.0.2.2:8000", "invalid_synthetic_device_token", "TEST"))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        lateinit var controller: GstiController
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            controller = GstiController(ctx, scope)
            controller.login("admin", "admin")
        }
        repeat(100) {
            if (SecureSession(ctx).load() == null && !controller.state.busy) return@repeat
            android.os.SystemClock.sleep(50)
        }
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            assertEquals(Page.Home, controller.state.page)
            assertTrue(controller.state.preview)
            assertNull(SecureSession(ctx).load())
            scope.cancel()
        }
    }

    @Test fun selectedPhotoIsCopiedToPrivateStorageAndResized() {
        val ctx = IsolatedContext(InstrumentationRegistry.getInstrumentation().targetContext)
        val source = File(ctx.filesDir, "selected.jpg")
        val bitmap = Bitmap.createBitmap(1400, 800, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.BLUE)
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()
        val imported = importProfilePhoto(ctx, Uri.fromFile(source))
        source.delete()
        assertTrue(File(imported).exists())
        assertEquals(ctx.filesDir.canonicalFile, File(imported).parentFile?.canonicalFile)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(imported, bounds)
        assertTrue(bounds.outWidth in 1..512 && bounds.outHeight in 1..512)
        val store = SecureSession(ctx)
        store.saveProfile(LocalProfile(photoPath = imported))
        assertEquals(imported, SecureSession(ctx).loadProfile().photoPath)
        deleteProfilePhoto(ctx, imported)
        assertFalse(File(imported).exists())
    }
}
