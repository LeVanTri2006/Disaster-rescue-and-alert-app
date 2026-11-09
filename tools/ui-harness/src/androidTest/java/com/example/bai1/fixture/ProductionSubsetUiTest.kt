package com.example.bai1.fixture

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.bai1.uiisolated.IsolatedUiActivity
import com.example.bai1.rescue.routing.network.RoutingApiResult
import kotlinx.coroutines.*
import androidx.lifecycle.viewModelScope
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

/** Native API37 accessibility actions on actual Compose UI; no obsolete Espresso input reflection. */
@RunWith(AndroidJUnit4::class)
class ProductionSubsetUiTest {
    private val usb get() = InstrumentationRegistry.getArguments().getString("physicalUsb") == "true"
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private lateinit var scenario: ActivityScenario<IsolatedUiActivity>
    private lateinit var activity: IsolatedUiActivity
    private fun waitFor(message: String, timeout: Long = 8000, condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + timeout
        while (SystemClock.uptimeMillis() < end) { if (condition()) return; SystemClock.sleep(80) }
        if (usb) {
            val summary = nodes().joinToString(";") { "${it.className}:scroll=${it.isScrollable}:editable=${it.isEditable}:visible=${it.isVisibleToUser}:actions=${it.actionList.map { a -> a.id }}" }
            fail(message + " own-window=" + inst.uiAutomation.rootInActiveWindow?.packageName + "; nodes=" + summary)
        }
        fail(message)
    }
    private fun nodes(): List<AccessibilityNodeInfo> {
        val root = inst.uiAutomation.rootInActiveWindow ?: return emptyList()
        if (usb && root.packageName?.toString() != inst.targetContext.packageName) return emptyList()
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
    private fun launch(mode: String = "ok", route: String = "register", authScope: Boolean = false) {
        val port = InstrumentationRegistry.getArguments().getString("hostFixturePort")!!.toInt()
        val started = SystemClock.elapsedRealtime()
        scenario = ActivityScenario.launch(Intent(inst.targetContext, IsolatedUiActivity::class.java)
            .putExtra("authScope", authScope).putExtra("resetAuthStorage", InstrumentationRegistry.getArguments().getString("preserveAuthSession") != "true").putExtra("usbFixture", usb).putExtra("fixturePort", port).putExtra("mode", mode).putExtra("startRoute", route))
        scenario.onActivity { activity = it }; expect("ISOLATED PRODUCTION UI SUBSET — STRICT ROUTING DISABLED")
        metric("startup_ms", SystemClock.elapsedRealtime() - started)
    }
    private fun metric(name: String, value: Long) {
        if (usb) inst.sendStatus(2, Bundle().apply { putString("p5e03Metric", "$name=$value") })
    }
    @After fun close() { if (::scenario.isInitialized) scenario.close() }
    private fun permission(allow: Boolean) {
        if (usb) {
            check(android.os.Build.VERSION.SDK_INT < 37) { "Personal phone permission prompt requires user confirmation" }
            check(allow); return
        }
        click("Allow local fixture")
        val names = if (allow) listOf("permission_allow_button", "permission_allow_foreground_only_button") else listOf("permission_deny_button")
        waitFor("Real OS permission action must be available") {
            nodes().any { n -> names.any { n.viewIdResourceName?.substringAfter(":id/") == it } && n.isEnabled && n.performAction(AccessibilityNodeInfo.ACTION_CLICK) }
        }
        waitFor("Activity must receive the actual permission result") { activity.permissionState == if (allow) "granted" else "denied" }
    }
    private fun scrollForward() {
        val node = nodes().firstOrNull { it.isScrollable && !it.isEditable && it.className?.toString() != "android.widget.EditText" } ?: return
        if (usb) {
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
    private fun register(email: String = "fixture@example.invalid", phone: String = "0000000000", confirm: String = "fixture-only-password", terms: Boolean = true, password: String = "fixture-only-password", name: String = "Fixture Person") {
        edit("Họ và tên", name); edit("Số điện thoại", phone); edit("Email", email)
        edit("Mật khẩu", password); edit("Xác nhận mật khẩu", confirm)
        if (!terms) { reveal("Tôi đồng ý với Điều khoản sử dụng và Chính sách bảo mật"); click("Tôi đồng ý với Điều khoản sử dụng và Chính sách bảo mật") }
        submitRegister()
    }
    private fun reveal(value: String) { waitFor("Reveal owned UI node") { if (visible(value)) true else { scrollForward(); false } } }
    private fun submitRegister() {
        if (usb) {
            scenario.onActivity { it.window.insetsController?.hide(android.view.WindowInsets.Type.ime()); it.currentFocus?.clearFocus() }
            inst.waitForIdleSync()
        }
        waitFor("Register submit visible") { if (visible("Đăng ký")) true else { scrollForward(); false } }
        click("Đăng ký")
    }
    private fun waitDone() { waitFor("Fixture operation finishes") { !activity.busy } }
    private fun recreate() { scenario.recreate(); scenario.onActivity { activity = it }; expect("ISOLATED PRODUCTION UI SUBSET — STRICT ROUTING DISABLED") }

    @Test fun actualProductionOnboardingAndAboutNavigationRenderWithoutCloud() {
        launch(route = "onboarding"); expect("Bỏ qua"); click("Bỏ qua"); expect("Đăng ký tài khoản")
        click("Fixture: about"); expect("CẢNH BÁO THIÊN TAI"); click("Quay lại"); expect("Đăng ký tài khoản")
        assertEquals("com.example.bai1.p5e02.uiisolated", inst.targetContext.packageName)
        for (name in listOf("com.google.firebase.FirebaseApp", "com.example.bai1.DisasterApplication", "com.example.bai1.presentation.navigation.ProductionAppNavScreens",
            "com.example.bai1.auth.AuthAppDependencies", "com.example.bai1.sos.SosAppDependencies", "com.example.bai1.auth.firebase.AndroidFirebaseAuthSession", "com.example.bai1.presentation.auth.ProductionLoginScreenKt"))
            assertTrue(runCatching { inst.targetContext.classLoader.loadClass(name) }.isFailure)
    }
    @Test fun realPermissionGrantEnablesLocalFixtureAndSurvivesActivityRestart() {
        launch(); permission(true); scenario.onActivity { it.probe() }; waitDone(); assertEquals("Fixture connected", activity.error)
        recreate(); assertEquals("granted", activity.permissionState); assertNull(activity.token)
        scenario.onActivity { it.probe() }; waitDone(); assertEquals("Fixture connected", activity.error)
    }
    @Test fun grantedPermissionSurvivesProcessRestartWithoutRestoringFixtureSession() {
        launch(); assertEquals(PackageManager.PERMISSION_GRANTED, activity.checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK))
        assertEquals("granted", activity.permissionState); assertNull(activity.token)
        assertEquals("unauthenticated", activity.sessionState)
        scenario.onActivity { it.probe() }; waitDone(); assertEquals("Fixture connected", activity.error)
    }
    @Test fun realPermissionDenialRendersProductionErrorStateWithoutCrash() {
        launch(); permission(false); scenario.onActivity { it.probe() }; waitDone(); click("Fixture: states")
        expect("Fixture unavailable — check local permission"); assertNull(activity.token)
    }
    @Test fun absentPermissionBlocksActualFixtureAndRestartKeepsUnauthenticated() {
        launch(); assertEquals(PackageManager.PERMISSION_DENIED, activity.checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK))
        scenario.onActivity { it.probe() }; waitDone(); assertEquals("Fixture unavailable — check local permission", activity.error)
        recreate(); assertEquals("not-granted", activity.permissionState); assertEquals("unauthenticated", activity.sessionState)
    }
    @Test fun productionRegisterUsesActualApiClientAgainstSyntheticFixtureThenLogoutClearsRouting() {
        launch(); permission(true); register(); waitDone(); assertEquals("fixture-authenticated", activity.sessionState); expect("Phiên fixture")
        assertNotNull(activity.token)
        runBlocking {
            val result = activity.routing.getCurrentRouting()
            assertTrue("Routing fixture result kind=${(result as? RoutingApiResult.Failure)?.error?.kind}; transport=${activity.networkDiagnostic}", result is RoutingApiResult.Success)
        }
        assertFalse(activity.routing.currentRoutingStatus.value!!.canNavigateSafely)
        val graph = runBlocking { activity.routing.downloadGraph() }
        assertTrue(graph is com.example.bai1.rescue.routing.download.GraphDownloadResult.Success)
        assertFalse(activity.routing.currentRoutingStatus.value!!.strictRoutingReady)
        click("Fixture logout"); assertNull(activity.token); assertNull(activity.routing.currentRoutingStatus.value); expect("Đăng ký tài khoản")
    }
    @Test fun syntheticSessionExpiryDoesNotRestoreRoutingOnRestart() {
        launch(); permission(true); register(); waitDone(); click("Fixture expire session")
        assertEquals("expired", activity.sessionState); assertNull(activity.token); assertNull(activity.routing.currentRoutingStatus.value)
        recreate(); assertNull(activity.token)
    }
    private fun errorCase(mode: String, message: String) {
        launch(mode); permission(true); register(); waitDone(); expect(message); assertNull(activity.token); assertNull(activity.routing.currentRoutingStatus.value)
    }
    @Test fun actualRegisterRenders401FixtureError() = errorCase("401", "Email/số điện thoại hoặc mật khẩu không đúng.")
    @Test fun actualRegisterRenders403FixtureError() = errorCase("403", "Tài khoản chưa được phép đăng nhập hoặc đã bị khóa.")
    @Test fun actualRegisterRenders500FixtureError() = errorCase("500", "Máy chủ đang bận. Vui lòng thử lại sau.")
    @Test fun actualRegisterRendersOfflineFixtureError() = errorCase("offline", "Không có kết nối mạng. Vui lòng kiểm tra lại.")
    @Test fun actualRegisterRendersActualSocketTimeout() = errorCase("timeout", "Kết nối quá thời gian. Vui lòng thử lại.")
    @Test fun actualRegisterLoadingStateAndProductionFeedbackComponentsRender() {
        launch("slow"); permission(true); register()
        try {
            waitFor("Real loading indicator") { activity.busy && nodes().any { it.className?.toString() == "android.widget.ProgressBar" } }
        } finally {
            val port = InstrumentationRegistry.getArguments().getString("hostFixturePort")!!.toInt()
            okhttp3.OkHttpClient.Builder().proxy(java.net.Proxy.NO_PROXY).build().newCall(okhttp3.Request.Builder()
                .url("http://${if (usb) "127.0.0.1" else "10.0.2.2"}:$port/fixture/release-loading").build()).execute().use { assertEquals(200, it.code) }
        }
        waitDone(); assertNotNull(activity.token); click("Fixture: states"); expect("Fixture loading"); expect("Fixture empty"); expect("Fixture offline")
    }
    @Test fun revokedPermissionBlocksConnectionAndRestartsDenied() {
        launch(); assertEquals(PackageManager.PERMISSION_DENIED, activity.checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK))
        scenario.onActivity { it.probe() }; waitDone(); click("Fixture: states"); expect("Fixture unavailable — check local permission")
        recreate(); assertEquals("not-granted", activity.permissionState); assertNull(activity.token)
    }
    private fun invalidRegistration(expected: String, action: () -> Unit) {
        launch(); action(); waitDone(); expect(expected)
        assertNull(activity.token); assertEquals(0, activity.registrationSubmissions)
    }
    @Test fun authRegisterMismatchBlocksHttp() = invalidRegistration("Mật khẩu xác nhận không khớp.") { register(confirm = "different-fixture-password") }
    @Test fun authRegisterUncheckedTermsBlocksHttp() = invalidRegistration("Vui lòng đồng ý với Điều khoản sử dụng và Chính sách bảo mật.") { register(terms = false) }
    @Test fun authRegisterBlankFieldsBlockHttp() = invalidRegistration("Vui lòng nhập họ và tên.") { submitRegister() }
    @Test fun authRegisterInvalidEmailBlocksHttp() = invalidRegistration("Email không hợp lệ.") { register(email = "invalid-email") }
    @Test fun authRegisterInvalidPhoneBlocksHttp() = invalidRegistration("Số điện thoại phải có từ 8 đến 15 ký tự.") { register(email = "", phone = "123") }
    @Test fun authRegisterMissingContactBlocksHttp() = invalidRegistration("Vui lòng nhập email hoặc số điện thoại.") { register(email = "", phone = "") }
    @Test fun authRegisterBlankPasswordBlocksHttp() = invalidRegistration("Mật khẩu phải có ít nhất 6 ký tự.") { register(password = "", confirm = "") }
    @Test fun authRegisterTooLongNameBlocksHttp() = invalidRegistration("Họ và tên không được quá 100 ký tự.") { register(name = "a".repeat(101)) }
    @Test fun authRegisterEmailOnlyIsAccepted() {
        launch(authScope = true); register(phone = ""); waitDone(); expect("Phiên fixture")
        assertTrue(activity.repository.state.value is com.example.bai1.auth.session.AuthState.Authenticated)
    }
    @Test fun authRegisterPhoneOnlyIsAccepted() {
        launch(authScope = true); register(email = ""); waitDone(); expect("Phiên fixture")
        assertTrue(activity.repository.state.value is com.example.bai1.auth.session.AuthState.Authenticated)
    }
    private fun loginForm(identifier: String = "fixture@example.invalid", password: String = "fixture-only-password") {
        edit("Email hoặc số điện thoại", identifier); edit("Mật khẩu", password)
        scenario.onActivity { it.window.insetsController?.hide(android.view.WindowInsets.Type.ime()) }
        reveal("Đăng nhập"); click("Đăng nhập")
    }
    private fun repositoryLogin(mode: String = "ok") { launch(mode, "login", authScope = true); expect("Login Logo"); loginForm() }
    private fun repositoryError(mode: String, message: String) {
        repositoryLogin(mode); waitDone(); reveal(message); expect(message)
        assertTrue(activity.repository.state.value is com.example.bai1.auth.session.AuthState.Error)
        assertNull(activity.storage.getAccessToken()); assertNull(activity.token)
    }
    @Test fun authLoginActualUiSuccessLogoutInvalidatesRouting() {
        repositoryLogin(); waitDone(); expect("Phiên repository")
        assertTrue(activity.repository.state.value is com.example.bai1.auth.session.AuthState.Authenticated)
        assertEquals(1, activity.cloud.signIns)
        assertTrue(runBlocking { activity.routing.getCurrentRouting() } is RoutingApiResult.Success)
        assertFalse(activity.routing.currentRoutingStatus.value!!.strictRoutingReady)
        click("Fixture logout"); waitDone(); expect("Login Logo")
        assertNull(activity.storage.getAccessToken()); assertNull(activity.routing.currentRoutingStatus.value)
        assertTrue(activity.cloud.signOuts > 0)
    }
    @Test fun authLoginBlankValidationBlocksHttp() {
        launch(route = "login", authScope = true); click("Đăng nhập"); waitDone()
        reveal("Vui lòng nhập email/số điện thoại và mật khẩu.")
        assertTrue(activity.repository.state.value is com.example.bai1.auth.session.AuthState.Error)
        assertNull(activity.token)
    }
    @Test fun authLogin401InvalidCredentials() = repositoryError("401", "Email/số điện thoại hoặc mật khẩu không đúng.")
    @Test fun authLogin403Forbidden() = repositoryError("403", "Tài khoản chưa được phép đăng nhập hoặc đã bị khóa.")
    @Test fun authLogin500ServerError() = repositoryError("500", "Máy chủ đang bận. Vui lòng thử lại sau.")
    @Test fun authLoginSocketTimeout() = repositoryError("timeout", "Kết nối quá thời gian. Vui lòng thử lại.")
    @Test fun authLoginOffline() = repositoryError("offline", "Không có kết nối mạng. Vui lòng kiểm tra lại.")
    @Test fun authLoginMissingReverseFailsClosed() = repositoryError("ok", "Không có kết nối mạng. Vui lòng kiểm tra lại.")
    private fun releaseLoading() {
        val port = InstrumentationRegistry.getArguments().getString("hostFixturePort")!!.toInt()
        okhttp3.OkHttpClient.Builder().proxy(java.net.Proxy.NO_PROXY).build().newCall(okhttp3.Request.Builder()
            .url("http://127.0.0.1:$port/fixture/release-loading").build()).execute().use { assertEquals(200, it.code) }
    }
    @Test fun authLoginLoadingUsesProductionUi() {
        repositoryLogin("slow")
        try { expect("Đang đăng nhập..."); assertTrue(activity.busy) } finally { releaseLoading() }
        waitDone(); expect("Phiên repository")
    }
    @Test fun authLoginCancellationCannotRestoreSessionAfterClose() {
        repositoryLogin("slow"); expect("Đang đăng nhập...")
        val old = activity.repository
        scenario.close(); releaseLoading()
        launch(route = "login", authScope = true); expect("Login Logo"); waitDone()
        assertFalse(old.state.value is com.example.bai1.auth.session.AuthState.Authenticated)
        assertNull(activity.storage.getAccessToken()); assertNull(activity.token)
    }
    @Test fun authSyntheticCredentialCallbackHasNoCloud() {
        launch(route = "login", authScope = true); reveal("Đăng nhập Google"); click("Đăng nhập Google")
        reveal("Synthetic credential launcher cancelled"); expect("Synthetic credential launcher cancelled")
        assertNull(activity.token); assertEquals(0, activity.cloud.signIns)
    }
    @Test fun authRepositoryRegistrationUsesProductionLifecycle() {
        launch(authScope = true); register(); waitDone(); expect("Phiên fixture")
        assertTrue(activity.repository.state.value is com.example.bai1.auth.session.AuthState.Authenticated)
        assertTrue(activity.storage.isLoggedIn()); assertEquals(1, activity.cloud.signIns)
        click("Fixture logout"); waitDone(); assertFalse(activity.storage.isLoggedIn())
    }
    @Test fun authNavigationUsesActualScreensInSubsetHost() {
        launch(route = "onboarding", authScope = true); click("Bỏ qua"); expect("Login Logo")
        reveal("Đăng ký"); click("Đăng ký"); expect("Đăng ký tài khoản")
        reveal("Đăng nhập"); click("Đăng nhập"); expect("Login Logo")
        click("Fixture: about"); expect("CẢNH BÁO THIÊN TAI"); click("Quay lại"); expect("Login Logo")
    }
    @Test fun authActivityRecreationRestoresOwnedSavedSession() {
        repositoryLogin(); waitDone(); expect("Phiên repository"); recreate(); waitDone(); expect("Phiên repository")
        assertTrue(activity.storage.isLoggedIn()); assertNull(activity.routing.currentRoutingStatus.value)
    }
    @Test fun authExpiredSessionRefreshRejectionClearsStorageAndRouting() {
        repositoryLogin("refreshReject"); waitDone(); expect("Phiên repository")
        assertTrue(runBlocking { activity.routing.getCurrentRouting() } is RoutingApiResult.Success)
        activity.storage.save("p5e04-expired-synthetic", "p5e02-refresh-synthetic-only")
        runBlocking { activity.repository.restoreSession() }; waitDone(); expect("Login Logo")
        assertTrue(activity.repository.state.value is com.example.bai1.auth.session.AuthState.Unauthenticated)
        assertFalse(activity.storage.isLoggedIn()); assertNull(activity.routing.currentRoutingStatus.value)
    }
    @Test fun authExpiredSessionRefreshSuccessStillDeniesRouting() {
        repositoryLogin("refreshOk"); waitDone(); expect("Phiên repository")
        activity.storage.save("p5e04-expired-synthetic", "p5e02-refresh-synthetic-only")
        runBlocking { activity.repository.restoreSession() }; waitDone()
        assertTrue(activity.repository.state.value is com.example.bai1.auth.session.AuthState.Authenticated)
        assertTrue(runBlocking { activity.routing.getCurrentRouting() } is RoutingApiResult.Success)
        assertFalse(activity.routing.currentRoutingStatus.value!!.canNavigateSafely)
    }
    @Test fun authPrepareSavedSessionForProcessRestart() {
        repositoryLogin(); waitDone(); expect("Phiên repository"); assertTrue(activity.storage.isLoggedIn())
    }
    @Test fun authSavedSessionSurvivesProcessRestart() {
        launch(route = "login", authScope = true); waitDone(); expect("Phiên repository")
        assertTrue(activity.repository.state.value is com.example.bai1.auth.session.AuthState.Authenticated)
        assertTrue(activity.storage.isLoggedIn()); assertNull(activity.routing.currentRoutingStatus.value)
        click("Fixture logout"); waitDone(); assertFalse(activity.storage.isLoggedIn())
    }
    private fun barrierControl(name: String, action: String): Int {
        val port = InstrumentationRegistry.getArguments().getString("hostFixturePort")!!.toInt()
        return okhttp3.OkHttpClient.Builder().proxy(java.net.Proxy.NO_PROXY).callTimeout(2, java.util.concurrent.TimeUnit.SECONDS).build()
            .newCall(okhttp3.Request.Builder().url("http://127.0.0.1:$port/fixture/barrier/$name/$action").build()).execute().use { it.code }
    }
    private fun entered(name: String) { waitFor("Actual host barrier entered", 8000) { barrierControl(name,"entered") == 200 } }
    private fun release(name: String) { assertEquals(200,barrierControl(name,"release")) }
    private fun arm(name: String, path: String) { activity.barrierPath=path;activity.barrierTag=name }
    private fun unarm() { activity.barrierTag=null;activity.fixtureFault=null }
    private fun assertRepositoryLoggedOut() {
        assertTrue(activity.repository.state.value is com.example.bai1.auth.session.AuthState.Unauthenticated)
        assertFalse(activity.storage.isLoggedIn());assertNull(activity.routing.currentRoutingStatus.value)
    }
    @Test fun lifecycleHttpLoginAfterLogoutIsStale() = runBlocking {
        launch("barrier","login",true);arm("login-a","/auth/login")
        val old=async(Dispatchers.Default){activity.repository.login("fixture@example.invalid","fixture-only-password")}
        try { entered("login-a");activity.repository.logout();val writes=activity.tokenWrites;release("login-a");old.await();assertRepositoryLoggedOut();assertEquals(writes,activity.tokenWrites) }
        finally { release("login-a");old.cancelAndJoin() }
    }
    @Test fun lifecycleHttpRefreshAfterLogoutCannotWriteTokens() = runBlocking {
        repositoryLogin("barrier");waitDone();expect("Phiên repository")
        activity.storage.save("p5e04-expired-synthetic","p5e02-refresh-synthetic-only");arm("refresh-a","/auth/refresh")
        val old=async(Dispatchers.Default){activity.repository.restoreSession()}
        try { entered("refresh-a");activity.repository.logout();val writes=activity.tokenWrites;release("refresh-a");old.await();assertRepositoryLoggedOut();assertEquals(writes,activity.tokenWrites) }
        finally { release("refresh-a");old.cancelAndJoin() }
    }
    @Test fun lifecycleHttpRestoreAfterLogoutCannotPublishUser() = runBlocking {
        repositoryLogin("barrier");waitDone();expect("Phiên repository");arm("restore-a","/auth/me")
        val old=async(Dispatchers.Default){activity.repository.restoreSession()}
        try { entered("restore-a");activity.repository.logout();release("restore-a");old.await();assertRepositoryLoggedOut() }
        finally { release("restore-a");old.cancelAndJoin() }
    }
    private fun concurrentHttpLogin(staleFailure: Boolean) = runBlocking {
        launch("barrier","login",true);arm("login-old","/auth/login");if(staleFailure)activity.fixtureFault="401"
        val old=async(Dispatchers.Default){activity.repository.login("fixture@example.invalid","fixture-only-password")}
        try {
            entered("login-old");unarm();activity.repository.login("fixture@example.invalid","fixture-only-password")
            val writes=activity.tokenWrites;assertTrue(activity.repository.state.value is com.example.bai1.auth.session.AuthState.Authenticated)
            release("login-old");old.await();assertEquals(writes,activity.tokenWrites)
            assertTrue(activity.repository.state.value is com.example.bai1.auth.session.AuthState.Authenticated)
        } finally { release("login-old");old.cancelAndJoin() }
    }
    @Test fun lifecycleHttpDuplicateLoginLatestWins() = concurrentHttpLogin(false)
    @Test fun lifecycleHttpStaleFailureCannotReplaceLatestSuccess() = concurrentHttpLogin(true)
    @Test fun lifecycleHttpOldRefreshCannotReplaceNewLogin() = runBlocking {
        repositoryLogin("barrier");waitDone();expect("Phiên repository")
        activity.storage.save("p5e04-expired-synthetic","p5e02-refresh-synthetic-only");arm("refresh-old","/auth/refresh")
        val old=async(Dispatchers.Default){activity.repository.restoreSession()}
        try {
            entered("refresh-old");unarm();activity.repository.login("fixture@example.invalid","fixture-only-password");val writes=activity.tokenWrites
            release("refresh-old");old.await();assertEquals(writes,activity.tokenWrites)
            assertTrue(activity.repository.state.value is com.example.bai1.auth.session.AuthState.Authenticated)
            assertNull(activity.routing.currentRoutingStatus.value)
        } finally { release("refresh-old");old.cancelAndJoin() }
    }
    @Test fun lifecycleViewModelLogoutWinsActualSlowNetworkLogin() = runBlocking {
        launch("barrier","login",true);arm("vm-login","/auth/login");loginForm();entered("vm-login")
        val pending=activity.authModel.viewModelScope.coroutineContext[Job]!!.children.toList()
        try {
            scenario.onActivity { it.authModel.logout() };waitFor("Local logout while HTTP pending") { activity.repository.state.value is com.example.bai1.auth.session.AuthState.Unauthenticated }
            release("vm-login");pending.joinAll();assertRepositoryLoggedOut()
        } finally { release("vm-login") }
    }
    @Test fun lifecycleViewModelClearRevokesActualPendingLogin() = runBlocking {
        launch("barrier","login",true);arm("vm-clear","/auth/login");loginForm();entered("vm-clear")
        val old=activity;val pending=old.authModel.viewModelScope.coroutineContext[Job]!!.children.toList()
        try {
            scenario.close();assertTrue(old.repository.state.value is com.example.bai1.auth.session.AuthState.Unauthenticated)
            release("vm-clear");pending.joinAll();assertFalse(old.storage.isLoggedIn())
            launch(route="login",authScope=true);expect("Login Logo");assertFalse(activity.storage.isLoggedIn())
        } finally { release("vm-clear") }
    }
    @Test fun lifecycleAdapterThrowIsLocalLogoutAndRetryableUncertainty() = runBlocking {
        repositoryLogin();waitDone();expect("Phiên repository");activity.cloud.throwSignOut=true
        activity.repository.logout();assertRepositoryLoggedOut();assertTrue(activity.repository.firebaseCleanupPending.value)
        activity.cloud.throwSignOut=false;activity.repository.logout();assertFalse(activity.repository.firebaseCleanupPending.value);assertFalse(activity.cloud.signedIn)
    }
    @Test fun lifecycleRefreshRejectionCleansNoCloudAdapter() = runBlocking {
        repositoryLogin("refreshReject");waitDone();expect("Phiên repository");assertTrue(activity.cloud.signedIn)
        activity.storage.save("p5e04-expired-synthetic","p5e02-refresh-synthetic-only")
        activity.repository.restoreSession();assertRepositoryLoggedOut();assertFalse(activity.cloud.signedIn)
    }
    @Test fun lifecyclePrepareExpiredSessionForRestart() = runBlocking {
        repositoryLogin("refreshReject");waitDone();expect("Phiên repository")
        activity.storage.save("p5e04-expired-synthetic","p5e02-refresh-synthetic-only")
        activity.repository.restoreSession();assertRepositoryLoggedOut()
    }
    @Test fun lifecycleRestartAfterExpiryRemainsUnauthenticated() {
        launch(route="login",authScope=true);waitDone();expect("Login Logo")
        assertRepositoryLoggedOut();assertNull(activity.token)
    }
    @Test fun usbActualPlatformNetworkPolicy() {
        check(usb); launch(); assertEquals("not-applicable", activity.permissionState)
        val policy = android.security.NetworkSecurityPolicy.getInstance()
        assertFalse(policy.isCleartextTrafficPermitted)
        assertTrue(policy.isCleartextTrafficPermitted("127.0.0.1"))
        assertTrue(policy.isCleartextTrafficPermitted("localhost"))
        assertFalse(policy.isCleartextTrafficPermitted("example.invalid"))
        assertFalse(policy.isCleartextTrafficPermitted("192.0.2.1"))
    }
    @Test fun usbSyntheticLoginApiSuccessAndFailure() {
        check(usb); launch()
        assertTrue(runBlocking { activity.loginSyntheticFixture() } is com.example.bai1.auth.network.AuthApiResult.Success)
        scenario.close(); launch("401")
        val failure = runBlocking { activity.loginSyntheticFixture() }
        assertTrue(failure is com.example.bai1.auth.network.AuthApiResult.Failure)
        assertEquals(com.example.bai1.auth.network.AuthApiErrorKind.UNAUTHORIZED,
            (failure as com.example.bai1.auth.network.AuthApiResult.Failure).error.kind)
        assertNull(activity.token) // API-only check; does not pretend to be LoginScreen.
    }
    @Test fun usbGraphCacheGenerationAndTamperDetection() {
        check(usb); launch(); register(); waitDone()
        assertTrue(runBlocking { activity.routing.getCurrentRouting() } is RoutingApiResult.Success)
        val started = SystemClock.elapsedRealtime()
        val first = runBlocking { activity.routing.downloadGraph(forceRefresh = true) }
        metric("graph_download_parse_ms", SystemClock.elapsedRealtime() - started)
        assertTrue(first is com.example.bai1.rescue.routing.download.GraphDownloadResult.Success)
        first as com.example.bai1.rescue.routing.download.GraphDownloadResult.Success
        assertFalse(first.fromCache); assertNotNull(first.parsedGraph)
        assertEquals(2, first.parsedGraph!!.nodes.size); assertEquals(1, first.parsedGraph!!.directedEdges.size)
        assertEquals(InstrumentationRegistry.getArguments().getString("expectedGraphSha256"), first.verifiedSha256)
        assertTrue(activity.routing.isCurrentGraphCached())
        val cached = runBlocking { activity.routing.downloadGraph() }
        assertTrue(cached is com.example.bai1.rescue.routing.download.GraphDownloadResult.Success)
        assertTrue((cached as com.example.bai1.rescue.routing.download.GraphDownloadResult.Success).fromCache)
        assertEquals(first.file.canonicalPath, cached.file.canonicalPath)
        check(first.file.canonicalPath.startsWith(inst.targetContext.cacheDir.canonicalPath + "/"))
        first.file.writeText(first.file.readText().replace("111", "999"))
        assertFalse(activity.routing.isCurrentGraphCached())
        val repaired = runBlocking { activity.routing.downloadGraph() }
        assertTrue(repaired is com.example.bai1.rescue.routing.download.GraphDownloadResult.Success)
        assertFalse((repaired as com.example.bai1.rescue.routing.download.GraphDownloadResult.Success).fromCache)
        assertNotEquals(first.file.canonicalPath, repaired.file.canonicalPath)
        assertTrue(activity.routing.isCurrentGraphCached()); assertFalse(activity.routing.currentRoutingStatus.value!!.strictRoutingReady)
    }
    @Test fun usbLoopbackMarkerAndActivityRestart() {
        check(usb); launch(); scenario.onActivity { it.probe() }; waitDone()
        assertEquals("Fixture connected", activity.error); recreate()
        scenario.onActivity { it.probe() }; waitDone(); assertEquals("Fixture connected", activity.error)
        assertNull(activity.token)
    }
    @Test fun usbMissingReverseFailsClosed() {
        check(usb); launch(); scenario.onActivity { it.probe() }; waitDone()
        assertEquals("Fixture unavailable — check local permission", activity.error)
        assertNull(activity.token); assertNull(activity.routing.currentRoutingStatus.value)
    }
    @Test fun usbResetConnectionFailsClosed() {
        check(usb); launch("reset"); permission(true); register(); waitDone()
        assertNotNull(activity.error); assertNull(activity.token); assertNull(activity.routing.currentRoutingStatus.value)
    }
    @Test fun usbInvalidGraphRejected() {
        check(usb); launch("invalid"); permission(true); register(); waitDone(); assertNotNull(activity.token)
        assertTrue(runBlocking { activity.routing.getCurrentRouting() } is RoutingApiResult.Success)
        val rejected = runBlocking { activity.routing.downloadGraph(forceRefresh = true) }
        assertTrue(rejected is com.example.bai1.rescue.routing.download.GraphDownloadResult.Failure)
        assertEquals(com.example.bai1.rescue.routing.download.GraphDownloadErrorKind.GRAPH_VALIDATION_FAILED,
            (rejected as com.example.bai1.rescue.routing.download.GraphDownloadResult.Failure).error.kind)
        assertFalse(activity.routing.currentRoutingStatus.value!!.strictRoutingReady)
    }
    @Test fun genuineProductionAboutScreenshot() {
        launch(route = "about"); expect("CẢNH BÁO THIÊN TAI")
        assertEquals(inst.targetContext.packageName, inst.uiAutomation.rootInActiveWindow.packageName.toString())
        val bitmap = inst.uiAutomation.takeScreenshot(); assertNotNull(bitmap)
        File(inst.targetContext.filesDir, "production-about.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
}
