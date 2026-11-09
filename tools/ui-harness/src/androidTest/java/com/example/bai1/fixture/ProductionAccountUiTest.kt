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
class ProductionAccountUiTest {
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
            .putExtra("accountScreens", true).putExtra("fixturePort", port).putExtra("usbFixture", true).putExtra("mode", mode)
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
    @Test fun accountRoleUiDisplaysExistingChoicesAndRejectsElevatedRole() {
        registerToRole(); expect("Người dùng"); expect("Nhà hoạt động cứu hộ")
        assertFalse(visible("ADMIN")); continueRole()
        reveal("Tài khoản chưa được phép đăng nhập hoặc đã bị khóa."); at(Routes.ROLE_SELECTION)
        assertSafe()
    }
    @Test fun accountRoleUserSuccessClearsRoleHistory() {
        registerToRole(); chooseUser(); continueRole(); at(Routes.HOME)
        assertEquals(listOf(Routes.HOME), stack()); assertSafe(); profile(); expect("Fixture Person")
    }
    @Test fun accountRoleRecreateRetainsUserSelection() {
        registerToRole(); chooseUser(); recreate(); at(Routes.ROLE_SELECTION)
        continueRole(); at(Routes.HOME); assertSafe()
    }
    @Test fun accountRoleLoadingAndRecreateDuringHttpRetainSubmittedRole() = runBlocking {
        registerToRole("barrier"); chooseUser(); app.barrierPath = "/users/me/role"; app.barrier = "account-role"
        continueRole(); waitFor("Actual role HTTP entered") { control("account-role", "entered") == 200 }
        try {
            waitFor("Production role loading UI") { nodes().any { it.className?.toString() == "android.widget.ProgressBar" } }
            recreate(); at(Routes.ROLE_SELECTION); assertEquals(200, control("account-role", "release"))
            at(Routes.HOME); assertSafe()
        } finally { control("account-role", "release") }
    }
    @Test fun accountRoleHttp401ShowsProductionError() = roleError("401", "Email/số điện thoại hoặc mật khẩu không đúng.")
    @Test fun accountRoleHttp403ShowsProductionError() = roleError("403", "Tài khoản chưa được phép đăng nhập hoặc đã bị khóa.")
    @Test fun accountRoleHttp500ShowsProductionError() = roleError("500", "Máy chủ đang bận. Vui lòng thử lại sau.")
    @Test fun accountRoleTimeoutShowsProductionError() = roleError("timeout", "Kết nối quá thời gian. Vui lòng thử lại.")
    @Test fun accountRoleOfflineShowsProductionError() {
        registerToRole(); chooseUser(); app.mode = "offline"; continueRole()
        reveal("Không có kết nối mạng. Vui lòng kiểm tra lại."); at(Routes.ROLE_SELECTION)
    }
    @Test fun accountRoleLateResponseAfterExpiryCannotRestoreProtectedUi() = runBlocking {
        registerToRole("barrier"); chooseUser(); app.barrierPath = "/users/me/role"; app.barrier = "account-stale-role"
        continueRole(); waitFor("Actual role entered") { control("account-stale-role", "entered") == 200 }
        val pending = app.roleModel.viewModelScope.coroutineContext[Job]!!.children.toList()
        try {
            app.repository.logout(); loggedOut(); val writes = app.writes.get()
            assertEquals(200, control("account-stale-role", "release"))
            waitFor("Host role response done") { control("account-stale-role", "done") == 200 }
            pending.joinAll(); loggedOut(); assertEquals(writes, app.writes.get())
        } finally { control("account-stale-role", "release") }
    }
    @Test fun accountProfileRendersSyntheticUserAndUserRole() {
        loggedIn(); profile(); expect("Fixture Person"); expect("Người dùng")
        val user = (app.repository.state.value as AuthState.Authenticated).user
        assertEquals("fixture@example.invalid", user.email)
        assertTrue(nodes().none { it.text?.toString()?.startsWith("UNAVAILABLE production UI:") == true })
        assertSafe()
    }
    @Test fun accountProfileEmptyNameUsesProductionFallback() = runBlocking {
        loggedIn(); app.profileShape = "empty"; app.repository.restoreSession(); profile()
        expect("Người dùng"); assertNull((app.repository.state.value as AuthState.Authenticated).user.fullName)
        assertFalse(visible("Fixture Person"))
    }
    @Test fun accountProfileActualSettingsAndHomeNavigation() {
        loggedIn(); profile(); settings(); click("Quay lại"); at(Routes.PROFILE)
        click("Quay lại"); at(Routes.HOME); expect("Trang chủ")
    }
    @Test fun accountProfileSessionExpiryClearsProtectedHistory() = runBlocking {
        loggedIn(); profile(); app.repository.logout(); loggedOut()
        assertFalse(onMain { app.nav.popBackStack() })
    }
    @Test fun accountProfileRefreshRejectedReturnsLogin() = runBlocking {
        loggedIn(); profile(); app.expiredAccess = true; app.mode = "refreshReject"
        app.repository.restoreSession(); loggedOut(); assertFalse(app.cloud.signedIn)
    }
    @Test fun accountProfileRestoreErrorReturnsLoginWithoutStaleUser() = runBlocking {
        loggedIn(); profile(); app.mode = "offline"; app.repository.restoreSession(); loggedOut()
        assertFalse(visible("Fixture Person"))
    }
    @Test fun accountProfileRecreateRetainsActualUiAndViewModel() {
        loggedIn(); profile(); val model = app.model; val writes = app.writes.get()
        recreate(); at(Routes.PROFILE); expect("Fixture Person"); assertSame(model, app.model)
        assertEquals(writes, app.writes.get()); assertSafe()
    }
    @Test fun accountPrepareProfileSessionForProcessRestart() { loggedIn(); profile(); expect("Fixture Person") }
    @Test fun accountProfileAfterProcessRestartUsesIsolatedRestore() {
        launch(); at(Routes.HOME); profile(); expect("Fixture Person"); assertSafe(); profileLogoutFromProfile()
    }
    private fun profileLogoutFromProfile() { settings(); reveal("Đăng xuất"); click("Đăng xuất"); loggedOut() }
    private fun switches() = nodes().filter { it.isCheckable }
    @Test fun accountSettingsRendersLocalStateAndSafeToggles() {
        loggedIn(); profile(); settings(); expect("Tài khoản")
        waitFor("Existing three switches") { switches().size == 3 }
        assertTrue(switches().all { it.isChecked })
        val s = switches()[1]; assertTrue(s.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        waitFor("Local location toggle") { !switches()[1].isChecked }
        scenario.onActivity { assertEquals(android.content.pm.PackageManager.PERMISSION_DENIED, it.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)) }
        assertTrue(app.storage.isLoggedIn()); assertSafe()
    }
    @Test fun accountSettingsAboutBackAndActualLogout() {
        loggedIn(); profile(); settings(); reveal("Về ứng dụng"); click("Về ứng dụng")
        at(Routes.ABOUT_APP); expect("CẢNH BÁO THIÊN TAI"); click("Quay lại"); at(Routes.SETTINGS)
        reveal("Đăng xuất"); click("Đăng xuất"); loggedOut()
        waitFor("Injected credential cleanup") { app.credentialClears.get() == 1 }
        assertFalse(onMain { app.nav.popBackStack() })
    }
    @Test fun accountSettingsExpiryLeavesLoginAcrossRecreate() = runBlocking {
        loggedIn(); profile(); settings(); app.repository.logout(); loggedOut(); recreate(); loggedOut()
    }
    @Test fun accountSettingsRecreateKeepsRouteAndExistingEphemeralDefaults() {
        loggedIn(); profile(); settings(); waitFor("Existing switches") { switches().size == 3 }
        assertTrue(switches()[0].performAction(AccessibilityNodeInfo.ACTION_CLICK))
        waitFor("Local notification toggle changed") { !switches()[0].isChecked }
        recreate(); at(Routes.SETTINGS); waitFor("Switch defaults reset by existing remember contract") { switches().size == 3 && switches().all { it.isChecked } }
    }
    @Test fun accountPrepareSettingsSessionForProcessRestart() { loggedIn(); profile(); settings() }
    @Test fun accountSettingsAfterProcessRestartUsesIsolatedRestore() {
        launch(); at(Routes.HOME); profile(); settings(); expect("Cài đặt"); assertSafe()
    }
    @Test fun accountPrepareRoleSessionForProcessRestart() { registerToRole(); chooseUser() }
    @Test fun accountRoleAfterProcessRestartUsesExistingHomeRestoreSemantics() {
        launch(); at(Routes.HOME); assertEquals("USER", (app.repository.state.value as AuthState.Authenticated).user.role)
        assertEquals(listOf(Routes.HOME), stack()); assertSafe()
    }
    @Test fun accountPrepareExpiredSessionForProcessRestart() = runBlocking {
        loggedIn(); profile(); settings(); app.expiredAccess = true; app.mode = "refreshReject"; app.repository.restoreSession(); loggedOut()
    }
    @Test fun accountRestartAfterExpiryCannotShowProfileOrSettings() {
        launch(); at(Routes.SPLASH); click("Tiếp theo"); click("Bỏ qua"); loggedOut()
        assertFalse(visible("Hồ sơ cá nhân")); assertFalse(visible("Cài đặt"))
    }
    @Test fun accountHomeActualUiHasNoCloudOnProfileRoundTrip() {
        loggedIn(); expect("Xin chào,"); profile(); click("Trang chủ"); at(Routes.HOME)
        assertTrue(app.unavailableRenders.isEmpty()); assertSafe()
    }
}
