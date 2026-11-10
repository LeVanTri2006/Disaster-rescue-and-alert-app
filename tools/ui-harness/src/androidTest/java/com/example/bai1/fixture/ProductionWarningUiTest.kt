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
class ProductionWarningUiTest {
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
        val node = nodes().firstOrNull { it.isScrollable && !it.isEditable && android.graphics.Rect().let { rect -> it.getBoundsInScreen(rect); rect.height() > 200 } } ?: return
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
    private fun at(route: String) {
        waitFor("Actual graph route must be $route") { route() == route }
        // Native accessibility can still expose the outgoing Home category with the same label.
        // Await the production NavHost animation before choosing a node on the destination.
        SystemClock.sleep(750); inst.waitForIdleSync()
    }
    private fun launch(mode: String = "ok") {
        check(InstrumentationRegistry.getArguments().getString("physicalUsb") == "true")
        val port = InstrumentationRegistry.getArguments().getString("hostFixturePort")!!.toInt()
        scenario = ActivityScenario.launch(Intent(inst.targetContext, MainActivity::class.java)
            .putExtra("warningScreens", true).putExtra("fixturePort", port).putExtra("usbFixture", true).putExtra("mode", mode)
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
        loginRoute(mode); submitLogin(); at(Routes.HOME); expect("Trang chủ")
        assertTrue(app.repository.state.value is AuthState.Authenticated); assertTrue(app.storage.isLoggedIn())
        assertEquals(listOf(Routes.HOME), stack())
    }
    private fun loggedOut() {
        at(Routes.LOGIN); expect("Login Logo")
        assertFalse(app.storage.isLoggedIn()); assertNull(app.routing.currentRoutingStatus.value)
        assertFalse(app.repository.state.value is AuthState.Authenticated)
        assertEquals(listOf(Routes.LOGIN), stack())
    }
    private fun profileLogout() { profile(); settings(); reveal("Đăng xuất"); click("Đăng xuất"); loggedOut() }
    private fun recreate() { val oldNav = app.nav; scenario.recreate(); scenario.onActivity { app = it.application as GraphFixtureApplication }; waitFor("New composition/nav after recreate") { app.nav !== oldNav } }
    private fun error(mode: String, message: String) {
        loginRoute(mode); submitLogin()
        waitFor("Actual repository failure") { app.repository.state.value is AuthState.Error }
        at(Routes.LOGIN); reveal(message); expect(message)
        assertFalse(app.storage.isLoggedIn()); assertEquals(listOf(Routes.LOGIN), stack())
    }

    private fun profile() { click("Cá nhân"); at(Routes.PROFILE); expect("Hồ sơ cá nhân") }
    private fun settings() { click("Settings"); at(Routes.SETTINGS); expect("Cài đặt") }
    private fun registerToRole(mode: String = "ok") {
        loginRoute(mode); reveal("Đăng ký"); click("Đăng ký"); at(Routes.REGISTER)
        edit("Họ và tên", "Fixture Person"); edit("Số điện thoại", "0000000000"); edit("Email", "fixture@example.invalid")
        edit("Mật khẩu", "fixture-only-password"); edit("Xác nhận mật khẩu", "fixture-only-password")
        hideKeyboard(); reveal("Đăng ký"); click("Đăng ký"); at(Routes.ROLE_SELECTION); expect("Chọn vai trò của bạn")
    }
    private fun chooseUser() { click("Người dùng") }
    private fun continueRole() { reveal("Tiếp tục"); click("Tiếp tục") }
    private fun assertSafe() {
        assertTrue(app.repository.state.value is AuthState.Authenticated)
        assertEquals("USER", (app.repository.state.value as AuthState.Authenticated).user.role)
        assertTrue(runBlocking { app.routing.getCurrentRouting() } is RoutingApiResult.Success)
        assertFalse(app.routing.currentRoutingStatus.value!!.canNavigateSafely)
        assertFalse(app.roleCallbacks.contains(com.example.bai1.data.model.UserRole.RESCUER))
        assertFalse(app.roleCallbacks.any { it.name == "ADMIN" })
    }
    private fun control(name: String, action: String): Int = app.client.newCall(okhttp3.Request.Builder()
        .url("${app.base}/fixture/barrier/$name/$action").build()).execute().use { it.code }
    private fun roleError(fault: String, message: String) {
        registerToRole(); chooseUser(); app.roleFault = fault; continueRole()
        reveal(message); expect(message); at(Routes.ROLE_SELECTION)
        assertEquals("USER", (app.repository.state.value as AuthState.Authenticated).user.role)
    }
    private val demo = "SYNTHETIC_TEST_DATA — DEMO\nKhông phải cảnh báo thực tế; dữ liệu chưa xác minh. Không có dữ liệu không đồng nghĩa không có nguy cơ."
    private val originals get() = com.example.bai1.data.repository.DisasterRepository.warningList
    private fun banner() { expect(demo) }
    private fun warnings() { reveal("Bão"); click("Bão"); at(Routes.WARNING_LIST); expect("Cảnh báo thiên tai"); banner() }
    private fun injected(items: List<com.example.bai1.data.model.WarningItem>) { onMain { app.warningItems = items }; inst.waitForIdleSync() }
    private fun custom(title: String = "SYNTHETIC_TEST_DATA record", id: String = "synthetic", time: String = "bad-time") = com.example.bai1.data.model.WarningItem(id, title, "", com.example.bai1.data.model.DisasterCategory.STORM, com.example.bai1.data.model.SeverityLevel.INFO, time, "")
    private fun back() { click("Quay lại") }
    private fun notification() { click("Thông báo"); at(Routes.NOTIFICATION); expect("Yêu cầu cứu hộ khẩn cấp"); banner() }
    private fun storm() { warnings(); click(originals.first().title); at(Routes.STORM_DETAIL); expect("Chi tiết cảnh báo"); banner() }
    @Test fun warningHomeCategoryUsesActualGraphAndList() { loggedIn(); warnings(); expect(originals.first().title); assertEquals(listOf(Routes.HOME, Routes.WARNING_LIST), stack()) }
    @Test fun warningStormFilterUsesExistingCategory() { loggedIn(); warnings(); click("Bão"); expect(originals.first().title); waitFor("Flood record excluded by storm filter") { !visible(originals.first { it.category == com.example.bai1.data.model.DisasterCategory.FLOOD }.title) } }
    @Test fun warningFloodFilterUsesExistingCategory() { loggedIn(); warnings(); click("Lũ lụt"); expect(originals.first { it.category == com.example.bai1.data.model.DisasterCategory.FLOOD }.title); waitFor("Storm record excluded by flood filter") { !visible(originals.first().title) } }
    @Test fun warningAllFilterRestoresList() { loggedIn(); warnings(); click("Lũ lụt"); click("Tất cả"); expect(originals.first().title) }
    @Test fun warningDefaultStormDetailAndBackUseActualCallbacks() { loggedIn(); storm(); expect(originals.first().title); back(); at(Routes.WARNING_LIST); back(); at(Routes.HOME); assertEquals(listOf(Routes.HOME), stack()) }
    @Test fun warningFloodSelectedTemplateAndBackUseActualCallbacks() { loggedIn(); warnings(); click("Lũ lụt"); waitFor("Filtered accessibility tree must exclude the previous storm row before selecting flood") { !visible(originals.first().title) }; inst.uiAutomation.waitForIdle(300, 3000); expect(originals.first { it.category == com.example.bai1.data.model.DisasterCategory.FLOOD }.title); click(originals.first { it.category == com.example.bai1.data.model.DisasterCategory.FLOOD }.title); at(Routes.FLOOD_DETAIL); expect("Chi tiết lũ lụt"); back(); at(Routes.WARNING_LIST) }
    @Test fun warningNonFloodIdStillUsesFixedStormTemplateLimitation() { loggedIn(); injected(listOf(custom("SYNTHETIC_TEST_DATA other record"))); warnings(); click("SYNTHETIC_TEST_DATA other record"); at(Routes.STORM_DETAIL); expect(originals.first().title); assertFalse(visible("SYNTHETIC_TEST_DATA other record")); banner() }
    @Test fun warningEmptyStateNeverImpliesNoRisk() { loggedIn(); injected(emptyList()); warnings(); expect("Chưa có dữ liệu cảnh báo"); expect("Không có dữ liệu không đồng nghĩa không có nguy cơ."); assertFalse(visible("Khu vực an toàn")) }
    @Test fun warningFilterWithNoMatchingRecordsHasNeutralEmptyState() { loggedIn(); injected(listOf(custom())); warnings(); click("Lũ lụt"); expect("Chưa có dữ liệu cảnh báo"); click("Tất cả"); expect("SYNTHETIC_TEST_DATA record") }
    @Test fun warningInjectedListRecomposesWithoutRememberedStaleRecords() { loggedIn(); warnings(); injected(emptyList()); expect("Chưa có dữ liệu cảnh báo"); injected(listOf(custom())); expect("SYNTHETIC_TEST_DATA record"); assertFalse(visible("Chưa có dữ liệu cảnh báo")) }
    @Test fun warningOptionalDefaultsRenderWithoutInventedAuthority() { loggedIn(); injected(listOf(custom())); warnings(); expect("SYNTHETIC_TEST_DATA record"); assertFalse(visible("VERIFIED")); banner() }
    @Test fun warningInvalidDisplayTimeIsUnparsedAndNotFreshnessProof() { loggedIn(); injected(listOf(custom(time="INVALID"))); warnings(); expect("VALID"); banner() }
    @Test fun warningOldDisplayTimeDoesNotGrantCurrentAuthority() { loggedIn(); injected(listOf(custom(time="01/01/2000 00:00"))); warnings(); expect("00:00"); banner(); assertSafe() }
    @Test fun warningDuplicateIdsDisplayDistinctRecordsInInputOrder() { loggedIn(); injected(listOf(custom("SYNTHETIC FIRST"), custom("SYNTHETIC SECOND"))); warnings(); expect("SYNTHETIC FIRST"); expect("SYNTHETIC SECOND"); val a=android.graphics.Rect();val b=android.graphics.Rect();text("SYNTHETIC FIRST")!!.getBoundsInScreen(a);text("SYNTHETIC SECOND")!!.getBoundsInScreen(b);assertTrue(a.top < b.top) }
    @Test fun warningLongTextIsBoundedAndBackRemainsUsable() { loggedIn(); val title="SYNTHETIC_TEST_DATA "+"long warning ".repeat(180); injected(listOf(custom(title))); warnings(); expect(title); back(); at(Routes.HOME) }
    @Test fun warningLocalListRemainsDemoWhileTransportOffline() { loggedIn(); app.mode="offline"; warnings(); expect(originals.first().title); banner(); back(); at(Routes.HOME) }
    @Test fun warningDetailVideoPlaceholderRetainsSyntheticBanner() { loggedIn(); storm(); reveal("Video"); click("Video"); reveal("Video cảnh báo thiên tai"); expect("Video cảnh báo thiên tai"); banner() }
    @Test fun warningStormFollowIsLocalToggleOnly() { loggedIn(); storm(); click("Theo dõi"); expect("Đang theo dõi ✓"); click("Đang theo dõi ✓"); expect("Theo dõi"); banner() }
    @Test fun warningListActivityRecreationPreservesProtectedRoute() { loggedIn(); warnings(); recreate(); at(Routes.WARNING_LIST); expect(originals.first().title); banner() }
    @Test fun warningDetailActivityRecreationPreservesTemplateRoute() { loggedIn(); storm(); recreate(); at(Routes.STORM_DETAIL); expect("Chi tiết cảnh báo"); banner() }
    @Test fun warningLogoutRemovesProtectedHistory() = runBlocking { loggedIn(); storm(); app.repository.logout(); loggedOut(); recreate(); loggedOut(); assertFalse(visible("Chi tiết cảnh báo")) }
    @Test fun warningSessionExpiryRemovesProtectedHistory() = runBlocking { loggedIn(); warnings(); app.expiredAccess=true; app.mode="refreshReject"; app.repository.restoreSession(); loggedOut(); assertFalse(visible("Cảnh báo thiên tai")) }
    @Test fun notificationActualHomeCallbackRendersStaticRows() { loggedIn(); notification(); expect("Bạn được phân công nhiệm vụ"); assertEquals(listOf(Routes.HOME, Routes.NOTIFICATION), stack()) }
    @Test fun notificationTabsOnlyChangeSelectionKnownLimitation() { loggedIn(); notification(); click("Hệ thống"); expect("Yêu cầu cứu hộ khẩn cấp"); click("Cảnh báo"); expect("Bạn được phân công nhiệm vụ"); banner() }
    @Test fun notificationScrollsToExistingLastStaticRecord() { loggedIn(); notification(); reveal("Thông báo hệ thống"); expect("1 ngày trước"); banner() }
    @Test fun notificationBackReturnsHomeAndBottomProfileUsesActualGraph() { loggedIn(); notification(); back(); at(Routes.HOME); notification(); click("Cá nhân"); at(Routes.PROFILE); expect("Hồ sơ cá nhân") }
    @Test fun notificationActivityRecreationStaysInActualGraph() { loggedIn(); notification(); recreate(); at(Routes.NOTIFICATION); expect("Yêu cầu cứu hộ khẩn cấp"); banner() }
    @Test fun notificationLocalRowsRemainDemoWhileOffline() { loggedIn(); app.mode="offline"; notification(); expect("5 phút trước"); banner() }
    @Test fun notificationSessionExpiryCannotRestoreProtectedRoute() = runBlocking { loggedIn(); notification(); app.expiredAccess=true; app.mode="refreshReject"; app.repository.restoreSession(); loggedOut(); recreate(); loggedOut() }
    @Test fun warningPrepareSessionForProcessRestart() { loggedIn(); storm(); assertTrue(app.storage.isLoggedIn()) }
    @Test fun warningAfterProcessRestartUsesExistingHomeRestore() { launch(); at(Routes.HOME); banner(); assertEquals(listOf(Routes.HOME),stack()); warnings() }
    @Test fun notificationPrepareExpiredSessionForProcessRestart() = runBlocking { loggedIn(); notification(); app.expiredAccess=true; app.mode="refreshReject"; app.repository.restoreSession(); loggedOut() }
    @Test fun notificationAfterExpiredProcessRestartCannotRestoreRows() { launch(); at(Routes.SPLASH); click("Tiếp theo"); at(Routes.ONBOARDING); click("Bỏ qua"); loggedOut(); assertFalse(visible("Yêu cầu cứu hộ khẩn cấp")); banner() }
}
