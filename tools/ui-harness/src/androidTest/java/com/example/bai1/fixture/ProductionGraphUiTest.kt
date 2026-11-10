package com.example.bai1.fixture

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.bai1.MainActivity
import com.example.bai1.uiisolated.GraphFixtureApplication
import com.example.bai1.presentation.navigation.Routes
import com.example.bai1.auth.session.AuthState
import com.example.bai1.rescue.routing.network.RoutingApiResult
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Launches exact production MainActivity; all route declarations/callbacks are exact AppNavGraph source. */
@RunWith(AndroidJUnit4::class)
class ProductionGraphUiTest {
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var app: GraphFixtureApplication
    private fun waitFor(message: String, timeout: Long = 8000, condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + timeout
        while (SystemClock.uptimeMillis() < end) { if (condition()) return; SystemClock.sleep(80) }
        fail(message)
    }
    private fun nodes(): List<AccessibilityNodeInfo> {
        val root = inst.uiAutomation.rootInActiveWindow ?: return emptyList()
        if (root.packageName?.toString() != inst.targetContext.packageName) return emptyList()
        if (root.packageName?.toString() !in setOf(inst.targetContext.packageName, "com.android.permissioncontroller", "com.google.android.permissioncontroller")) return emptyList()
        val list = mutableListOf<AccessibilityNodeInfo>()
        fun visit(n: AccessibilityNodeInfo) { list.add(n); for (i in 0 until n.childCount) n.getChild(i)?.let(::visit) }
        visit(root); return list
    }
    private fun text(value: String) = nodes().firstOrNull { it.text?.toString() == value || it.contentDescription?.toString() == value || it.hintText?.toString() == value }
    private fun visible(value: String) = text(value)?.isVisibleToUser == true
    private fun expect(value: String) { waitFor("Expected production UI text: $value") { visible(value) } }
    private fun click(value: String) {
        waitFor("Clickable UI node: $value") {
            for (match in nodes().filter { it.text?.toString() == value || it.contentDescription?.toString() == value }) {
                var n: AccessibilityNodeInfo? = match
                while (n != null) { if (n.isVisibleToUser && n.isClickable && n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return@waitFor true; n = n.parent }
            }
            false
        }
        inst.waitForIdleSync()
    }
    private fun scrollForward() {
        val node = nodes().firstOrNull { it.isScrollable && !it.isEditable && it.className?.toString() != "android.widget.EditText" } ?: return
        if (true) {
            check(inst.uiAutomation.rootInActiveWindow?.packageName?.toString() == inst.targetContext.packageName)
            val rect = android.graphics.Rect(); node.getBoundsInScreen(rect)
            val x = rect.exactCenterX(); val startY = rect.bottom - 60f; val endY = rect.top + 80f
            check(startY > endY)
            val down = SystemClock.uptimeMillis()
            fun touch(action: Int, y: Float) {
                val event = android.view.MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
                event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
                try { check(inst.uiAutomation.injectInputEvent(event, true)) } finally { event.recycle() }
            }
            touch(android.view.MotionEvent.ACTION_DOWN, startY)
            for (step in 1..12) { SystemClock.sleep(20); touch(android.view.MotionEvent.ACTION_MOVE, startY + (endY-startY)*step/12) }
            touch(android.view.MotionEvent.ACTION_UP, endY)
        } else node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        // Let Compose finish before inspecting the next native accessibility snapshot.
        SystemClock.sleep(450)
        inst.waitForIdleSync()
    }
    private fun edit(label: String, value: String) {
        waitFor("Editable production field: $label") {
            var n = text(label)
            while (n != null) {
                if (n.isEditable && n.isVisibleToUser) return@waitFor n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,
                    Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value) })
                n = n.parent
            }
            scrollForward()
            false
        }
        inst.waitForIdleSync()
    }

    private fun <T> onMain(block: () -> T): T {
        var result: T? = null
        inst.runOnMainSync { result = block() }
        @Suppress("UNCHECKED_CAST") return result as T
    }
    private fun route() = onMain { app.nav.currentDestination?.route }
    private fun stack() = onMain { app.nav.currentBackStack.value.mapNotNull { it.destination.route } }
    private fun at(route: String) { waitFor("Actual graph route must be $route") { route() == route } }
    private fun launch(mode: String = "ok") {
        check(InstrumentationRegistry.getArguments().getString("physicalUsb") == "true")
        val port = InstrumentationRegistry.getArguments().getString("hostFixturePort")!!.toInt()
        scenario = ActivityScenario.launch(Intent(inst.targetContext, MainActivity::class.java)
            .putExtra("fixturePort", port).putExtra("usbFixture", true).putExtra("mode", mode)
            .putExtra("resetAuthStorage", InstrumentationRegistry.getArguments().getString("preserveAuthSession") != "true"))
        scenario.onActivity { app = it.application as GraphFixtureApplication }
        waitFor("Actual ViewModel must finish restore") { app.repository.state.value !is AuthState.Loading }
    }
    @After fun close() { if (::scenario.isInitialized) scenario.close() }
    private fun loginRoute(mode: String = "ok") {
        launch(mode); at(Routes.SPLASH); click("Tiếp theo"); at(Routes.ONBOARDING)
        click("Bỏ qua"); at(Routes.LOGIN); expect("Login Logo")
    }
    private fun reveal(value: String) { waitFor("Reveal own UI $value") { if (visible(value)) true else { scrollForward(); false } } }
    private fun hideKeyboard() { scenario.onActivity { it.window.insetsController?.hide(android.view.WindowInsets.Type.ime()); it.currentFocus?.clearFocus() }; inst.waitForIdleSync() }
    private fun submitLogin() {
        edit("Email hoặc số điện thoại", "fixture@example.invalid"); edit("Mật khẩu", "fixture-only-password")
        hideKeyboard(); reveal("Đăng nhập"); click("Đăng nhập")
    }
    private fun loggedIn(mode: String = "ok") {
        loginRoute(mode); submitLogin(); at(Routes.HOME); expect("UNAVAILABLE production UI: home")
        assertTrue(app.repository.state.value is AuthState.Authenticated); assertTrue(app.storage.isLoggedIn())
        assertEquals(listOf(Routes.HOME), stack())
    }
    private fun loggedOut() {
        at(Routes.LOGIN); expect("Login Logo")
        assertFalse(app.storage.isLoggedIn()); assertNull(app.routing.currentRoutingStatus.value)
        assertFalse(app.repository.state.value is AuthState.Authenticated)
        assertEquals(listOf(Routes.LOGIN), stack())
    }
    private fun profileLogout() { click("Fixture profile"); at(Routes.PROFILE); click("Fixture logout"); loggedOut() }
    private fun recreate() { scenario.recreate(); scenario.onActivity { app = it.application as GraphFixtureApplication } }
    private fun error(mode: String, message: String) {
        loginRoute(mode); submitLogin()
        waitFor("Actual repository failure") { app.repository.state.value is AuthState.Error }
        at(Routes.LOGIN); reveal(message); expect(message)
        assertFalse(app.storage.isLoggedIn()); assertEquals(listOf(Routes.LOGIN), stack())
    }
    @Test fun graphActualMainStartupAndManifestHaveNoCloud() {
        launch(); at(Routes.SPLASH); expect("Tiếp theo")
        assertEquals("com.example.bai1.p5e02.uiisolated", inst.targetContext.packageName)
        for (name in listOf("com.google.firebase.FirebaseApp", "androidx.credentials.CredentialManager",
            "com.example.bai1.DisasterApplication", "com.example.bai1.auth.AuthAppDependencies",
            "com.example.bai1.sos.SosAppDependencies", "com.example.bai1.rescue.RescueMissionAppDependencies",
            "com.example.bai1.presentation.navigation.ProductionAppNavScreens")) {
            assertTrue(name, runCatching { inst.targetContext.classLoader.loadClass(name) }.isFailure)
        }
        assertNotNull(inst.targetContext.classLoader.loadClass("com.example.bai1.MainActivity"))
        assertNotNull(inst.targetContext.classLoader.loadClass("com.example.bai1.presentation.navigation.AppNavGraphKt"))
        assertFalse(app.storage.isLoggedIn()); assertEquals(0, app.cloud.signIns)
    }
    @Test fun graphOnboardingLoginRegisterBackStackUsesActualCallbacks() {
        loginRoute(); assertEquals(listOf(Routes.LOGIN), stack())
        reveal("Đăng ký"); click("Đăng ký"); at(Routes.REGISTER); expect("Đăng ký tài khoản")
        assertEquals(listOf(Routes.LOGIN, Routes.REGISTER), stack())
        reveal("Đăng nhập"); click("Đăng nhập"); at(Routes.LOGIN)
        assertEquals(listOf(Routes.LOGIN), stack()); assertEquals(0, app.writes.get())
    }
    @Test fun graphActualLoginHomeProfileLogoutClearsProtectedHistory() {
        loggedIn()
        assertTrue(runBlocking { app.routing.getCurrentRouting() } is RoutingApiResult.Success)
        assertFalse(app.routing.currentRoutingStatus.value!!.canNavigateSafely)
        profileLogout(); waitFor("Injected credential cleanup") { app.credentialClears.get() == 1 }
        assertFalse(onMain { app.nav.popBackStack() }); assertFalse(app.storage.isLoggedIn())
    }
    @Test fun graphActualRegisterNewAccountSelectsRoleThenHome() {
        loginRoute(); reveal("Đăng ký"); click("Đăng ký"); at(Routes.REGISTER)
        edit("Họ và tên", "Fixture Person"); edit("Số điện thoại", "0000000000"); edit("Email", "fixture@example.invalid")
        edit("Mật khẩu", "fixture-only-password"); edit("Xác nhận mật khẩu", "fixture-only-password")
        hideKeyboard(); reveal("Đăng ký"); click("Đăng ký"); at(Routes.ROLE_SELECTION)
        assertEquals(listOf(Routes.ROLE_SELECTION), stack()); click("Fixture continue role"); at(Routes.HOME)
        assertEquals(listOf(Routes.HOME), stack()); assertTrue(app.storage.isLoggedIn())
    }
    @Test fun graphSettingsAboutActualUiBackThenLogout() {
        loggedIn(); click("Fixture profile"); click("Fixture settings"); at(Routes.SETTINGS)
        click("Fixture about"); at(Routes.ABOUT_APP); expect("CẢNH BÁO THIÊN TAI")
        click("Quay lại"); at(Routes.SETTINGS); click("Fixture logout"); loggedOut()
        waitFor("Settings credential cleanup") { app.credentialClears.get() == 1 }
    }
    @Test fun graphSessionExpiryClearsNestedBackStackToLogin() {
        loggedIn(); click("Fixture profile"); click("Fixture settings"); at(Routes.SETTINGS)
        runBlocking { app.repository.logout() }; loggedOut()
        assertFalse(onMain { app.nav.popBackStack() })
    }
    @Test fun graphRefreshRejectedReturnsLoginAndClearsRouting() {
        loggedIn(); assertTrue(runBlocking { app.routing.getCurrentRouting() } is RoutingApiResult.Success)
        app.expiredAccess = true; app.mode = "refreshReject"
        runBlocking { app.repository.restoreSession() }; loggedOut(); assertFalse(app.cloud.signedIn)
    }
    @Test fun graphActivityRecreatePreservesAuthenticatedRouteAndVm() {
        loggedIn(); click("Fixture profile"); at(Routes.PROFILE); val old = app.model; val writes = app.writes.get()
        recreate(); at(Routes.PROFILE); assertSame(old, app.model); assertEquals(writes, app.writes.get())
        click("Fixture logout"); loggedOut(); recreate(); loggedOut()
    }
    @Test fun graphUnauthenticatedRecreateKeepsRegisterHistory() {
        loginRoute(); reveal("Đăng ký"); click("Đăng ký"); at(Routes.REGISTER)
        recreate(); at(Routes.REGISTER); assertEquals(listOf(Routes.LOGIN, Routes.REGISTER), stack())
        onMain { app.nav.popBackStack() }; at(Routes.LOGIN); assertFalse(app.storage.isLoggedIn())
    }
    @Test fun graphPrepareSavedSessionForProcessRestart() { loggedIn() }
    @Test fun graphSavedSessionSurvivesProcessRestartThroughActualSplashGraph() {
        launch(); at(Routes.HOME); assertTrue(app.storage.isLoggedIn()); assertEquals(listOf(Routes.HOME), stack())
        assertNull(app.routing.currentRoutingStatus.value); profileLogout()
    }
    @Test fun graphPrepareExpiredSessionForProcessRestart() {
        loggedIn(); app.expiredAccess = true; app.mode = "refreshReject"
        runBlocking { app.repository.restoreSession() }; loggedOut()
    }
    @Test fun graphRestartAfterExpiryNeverRestoresProtectedRoute() {
        launch(); at(Routes.SPLASH); assertFalse(app.storage.isLoggedIn()); assertEquals(0, app.cloud.signIns)
        click("Tiếp theo"); click("Bỏ qua"); loggedOut()
    }
    @Test fun graphHttp401KeepsLogin() = error("401", "Email/số điện thoại hoặc mật khẩu không đúng.")
    @Test fun graphHttp403KeepsLogin() = error("403", "Tài khoản chưa được phép đăng nhập hoặc đã bị khóa.")
    @Test fun graphHttp500KeepsLogin() = error("500", "Máy chủ đang bận. Vui lòng thử lại sau.")
    @Test fun graphSocketTimeoutKeepsLogin() = error("timeout", "Kết nối quá thời gian. Vui lòng thử lại.")
    @Test fun graphOfflineKeepsLogin() = error("offline", "Không có kết nối mạng. Vui lòng kiểm tra lại.")
    @Test fun graphMissingReverseKeepsLogin() = error("ok", "Không có kết nối mạng. Vui lòng kiểm tra lại.")
    private fun control(name: String, action: String): Int = app.client.newCall(okhttp3.Request.Builder()
        .url("${app.base}/fixture/barrier/$name/$action").build()).execute().use { it.code }
    @Test fun graphLateLoginAfterLogoutCannotNavigateHome() = runBlocking {
        loginRoute("barrier"); app.barrier = "graph-login"; submitLogin()
        waitFor("Host entered actual graph login barrier") { control("graph-login", "entered") == 200 }
        val jobs = app.model.viewModelScope.coroutineContext[Job]!!.children.toList()
        try {
            onMain { app.model.logout() }; waitFor("Local logout while blocked") { app.repository.state.value is AuthState.Unauthenticated }
            loggedOut(); val writes = app.writes.get(); assertEquals(200, control("graph-login", "release"))
            jobs.joinAll(); loggedOut(); assertEquals(writes, app.writes.get())
        } finally { control("graph-login", "release") }
    }
    @Test fun graphProtectedRouteCannotRenderWithoutSession() {
        loginRoute(); onMain { app.nav.navigate(Routes.MAP) }; loggedOut()
        assertTrue(nodes().none { it.text?.toString()?.startsWith("UNAVAILABLE production UI:") == true })
        assertEquals(0, app.cloud.signIns); assertTrue(app.unavailableRenders.isEmpty())
    }
    @Test fun graphUnavailableMapSosRescueAreOnlyGraphRoutingCoverage() {
        loggedIn(); click("Fixture map"); at(Routes.MAP); expect("UNAVAILABLE production UI: DisasterMapScreen")
        onMain { app.nav.popBackStack() }; at(Routes.HOME); click("Fixture SOS"); at(Routes.SOS)
        expect("UNAVAILABLE production UI: SOSScreen"); onMain { app.nav.popBackStack() }; at(Routes.HOME)
        click("Fixture rescue"); at(Routes.MISSION_LIST); expect("UNAVAILABLE production UI: RescueMissionListScreen")
    }
    @Test fun graphCredentialButtonUsesOnlyInjectedNoCloudLauncher() {
        loginRoute(); reveal("Đăng nhập Google"); click("Đăng nhập Google")
        assertEquals(1, app.credentialLaunches.get()); assertEquals(0, app.cloud.signIns); assertFalse(app.storage.isLoggedIn())
        at(Routes.LOGIN)
    }
}
