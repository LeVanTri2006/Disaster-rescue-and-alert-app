package com.example.bai1.fixture
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.bai1.demo.*
import com.example.bai1.rescue.routing.model.*
import com.example.bai1.rescue.routing.parser.GraphParseException
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DemoPhoneTest {
 private val inst get()=InstrumentationRegistry.getInstrumentation()
 private lateinit var scenario:ActivityScenario<DemoActivity>
 private lateinit var activity:DemoActivity
 private fun waitFor(message:String, f:()->Boolean) {
  val until=SystemClock.uptimeMillis()+8000
  while(SystemClock.uptimeMillis()<until) {if(f())return;SystemClock.sleep(60)}
  inst.sendStatus(2,Bundle().apply{putString("p5e10aUi",nodes().map{val r=android.graphics.Rect();it.getBoundsInScreen(r);"${it.text}|${it.contentDescription}|scroll=${it.isScrollable}|$r"}.joinToString(";"))})
  fail(message)
 }
 private fun <T> main(f:()->T):T {var v:T?=null;inst.runOnMainSync {v=f()};@Suppress("UNCHECKED_CAST") return v as T}
 private fun nodes():List<AccessibilityNodeInfo> {
  val root=inst.uiAutomation.rootInActiveWindow ?: return emptyList()
  if(root.packageName?.toString()!=inst.targetContext.packageName)return emptyList()
  root.refresh()
  val out=mutableListOf<AccessibilityNodeInfo>();fun visit(n:AccessibilityNodeInfo){n.refresh();out.add(n);for(i in 0 until n.childCount)n.getChild(i)?.let(::visit)};visit(root);return out
 }
 private fun visible(v:String)=nodes().any{(it.text?.toString()==v || it.contentDescription?.toString()==v)&&it.isVisibleToUser}
 private fun scroll(back:Boolean) {
  val rect=nodes().filter{it.isScrollable}.map{val r=android.graphics.Rect();it.getBoundsInScreen(r);r}.filter{it.height()>=100}.maxByOrNull{it.height()} ?: return
  if(rect.height()<100)return
  val x=rect.exactCenterX();val y1=if(back)rect.top+40f else rect.bottom-40f;val y2=if(back)rect.bottom-40f else rect.top+40f;val down=SystemClock.uptimeMillis()
  fun touch(action:Int,y:Float){val event=android.view.MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0);event.source=android.view.InputDevice.SOURCE_TOUCHSCREEN;try{check(inst.uiAutomation.injectInputEvent(event,true))}finally{event.recycle()}}
  check(inst.uiAutomation.rootInActiveWindow?.packageName?.toString()==inst.targetContext.packageName)
  touch(android.view.MotionEvent.ACTION_DOWN,y1)
  for(i in 1..10){SystemClock.sleep(20);touch(android.view.MotionEvent.ACTION_MOVE,y1+(y2-y1)*i/10)}
  touch(android.view.MotionEvent.ACTION_UP,y2);SystemClock.sleep(250);inst.waitForIdleSync()
 }
 private fun click(v:String) {
  var scrollAttempts=0
  waitFor("Click own UI $v") {
   for(match in nodes().filter {it.text?.toString()==v || it.contentDescription?.toString()==v}) {
    var node:AccessibilityNodeInfo?=match
    while(node!=null) {if(node.isVisibleToUser && node.isClickable && node.isEnabled && node.performAction(AccessibilityNodeInfo.ACTION_CLICK))return@waitFor true;node=node.parent}
   }
   scroll(scrollAttempts++ % 6 < 3);false
  };inst.waitForIdleSync()
 }
 private fun expect(v:String) {waitFor("Expected own UI $v"){if(visible(v))true else {scroll(false);false}}}
 private fun launch() {
  check(InstrumentationRegistry.getArguments().getString("physicalUsb")=="true")
  val port=InstrumentationRegistry.getArguments().getString("hostFixturePort")!!.toInt()
  scenario=ActivityScenario.launch(Intent(inst.targetContext,DemoActivity::class.java).putExtra("fixturePort",port))
  scenario.onActivity{activity=it};expect("Người dân demo")
 }
 private fun login(role:String){click(if(role=="citizen")"Người dân demo" else "Đội cứu hộ demo");waitFor("actual role auth"){main{activity.role==role&&!activity.busy}}}
 private fun citizen(){launch();login("citizen");click("SOS synthetic");waitFor("SOS form"){main{activity.page=="form"}}}
 private fun send():String{click("Gửi SOS fixture");waitFor("POST actual production client"){main{activity.lastCreated!=null&&!activity.busy}};return main{activity.lastCreated!!}}
 private fun nextCitizen(){click("Đăng xuất");waitFor("logout"){main{!activity.loggedIn}};click("Người dân TEST: 1");waitFor("second synthetic account"){main{activity.citizenIndex==2}};login("citizen");click("SOS synthetic");waitFor("form"){main{activity.page=="form"}}}
 private fun rescue(){click("Đăng xuất");waitFor("logout"){main{!activity.loggedIn}};login("rescuer")}
 private fun refresh(){val n=main{activity.refreshes};click("Refresh SOS");waitFor("GET actual production client"){main{activity.refreshes==n+1&&!activity.busy}}}
 private fun export(){inst.sendStatus(2,Bundle().apply{putString("p5e10aOutput",main{com.google.gson.Gson().toJson(mapOf("role" to activity.role,"lastCreated" to activity.lastCreated,"message" to activity.message,"markers" to activity.markers,"selected" to activity.selected,"refreshes" to activity.refreshes))})})}
 @After fun close(){if(::scenario.isInitialized){export();scenario.close()}}
 @Test fun citizenPostThenIndependentRescuerGetRendersClickableMarker(){
  citizen();click("Thêm người");val id=send();assertTrue(main{activity.markers.isEmpty()});assertTrue(main{activity.message.endsWith("status=ACTIVE")})
  rescue();assertTrue(main{activity.markers.isEmpty()});refresh();assertEquals("GET_OK 1 SOS",main{activity.message});val s=main{activity.markers.single()}
  assertEquals(id,s.eventId);assertEquals(2,s.adults);assertEquals("ACTIVE",s.status)
  assertEquals(main{activity.graph.nodes.getValue("B").position.latitude},s.latitude,0.0);assertEquals(main{activity.graph.nodes.getValue("B").position.longitude},s.longitude,0.0)
  click("SOS $id");waitFor("real marker selected"){main{activity.selected?.eventId==id}};expect("ID: $id");expect("Trạng thái: ACTIVE");expect("Người lớn: 2")
 }
 @Test fun multipleSosHaveSeparateResponseMarkersAndRefreshNeverPosts(){
  citizen();val first=send();nextCitizen();click("Tọa độ TEST: B");waitFor("synthetic H"){main{activity.goal=="H"}};val second=send();assertNotEquals(first,second)
  rescue();refresh();assertEquals(setOf(first,second),main{activity.markers.map{it.eventId}.toSet()});assertEquals(2,main{activity.markers.map{it.latitude to it.longitude}.toSet().size})
  click("SOS $first");waitFor("first marker"){main{activity.selected?.eventId==first}};click("SOS $second");waitFor("second marker"){main{activity.selected?.eventId==second}}
  repeat(3){refresh();assertEquals(2,main{activity.markers.size})}
 }
 @Test fun sameCoordinateSosRetainDifferentIds(){citizen();val a=send();nextCitizen();val b=send();rescue();refresh();assertEquals(2,main{activity.markers.size});assertEquals(setOf(a,b),main{activity.markers.map{it.eventId}.toSet()});assertEquals(1,main{activity.markers.map{it.latitude to it.longitude}.toSet().size});expect("SOS $a");expect("SOS $b")}
 private fun failure(mode:String){citizen();click(mode);click("Gửi SOS fixture");waitFor("actual POST failure"){main{activity.message.startsWith("FIXTURE_ERROR")&&!activity.busy}};assertNull(main{activity.lastCreated});assertTrue(main{activity.markers.isEmpty()});SystemClock.sleep(3200);rescue();refresh();assertEquals("GET_OK 0 SOS",main{activity.message});assertTrue(main{activity.markers.isEmpty()})}
 @Test fun duplicateActiveSosForSameCitizenReturns409(){citizen();val id=send();click("Gửi SOS fixture");waitFor("duplicate denied"){main{activity.message.startsWith("FIXTURE_ERROR HTTP 409")&&!activity.busy}};assertNull(main{activity.lastCreated});rescue();refresh();assertEquals(listOf(id),main{activity.markers.map{it.eventId}})}
 @Test fun post403DoesNotStoreOrShowFalseSuccess(){failure("HTTP 403")}
 @Test fun post500DoesNotStoreOrShowFalseSuccess(){failure("HTTP 500")}
 @Test fun postTimeoutPrecommitFixtureDoesNotShowLateSuccess(){failure("Timeout")}
 @Test fun postOfflineDoesNotReachFixtureOrShowSuccess(){failure("Offline")}
 @Test fun failedGetRemovesStaleMarkersAndReportsFailure(){citizen();send();rescue();refresh();assertEquals(1,main{activity.markers.size});click("HTTP 500");refresh();assertTrue(main{activity.message.startsWith("GET_ERROR")});assertTrue(main{activity.markers.isEmpty()});assertNull(main{activity.selected});click("HTTP OK");refresh();assertEquals(1,main{activity.markers.size})}
 @Test fun get403TimeoutAndOfflineNeverInventMarkers(){launch();login("rescuer");for(mode in listOf("HTTP 403","Timeout","Offline")){click(mode);refresh();assertTrue(main{activity.message.startsWith("GET_ERROR")});assertTrue(main{activity.markers.isEmpty()})}}
 @Test fun activityRecreationRequiresLoginAndReloadsSameServerIds(){citizen();val id=send();rescue();refresh();scenario.recreate();scenario.onActivity{activity=it};assertFalse(main{activity.loggedIn});assertTrue(main{activity.markers.isEmpty()});assertNull(main{activity.lastCreated});login("rescuer");refresh();assertEquals(listOf(id),main{activity.markers.map{it.eventId}})}
 @Test fun logoutClearsSessionFormSelectionAndRetainsOnlyServerData(){citizen();click("Thêm người");val id=send();rescue();refresh();click("SOS $id");waitFor("selection"){main{activity.selected!=null}};click("Đăng xuất");waitFor("logout"){main{!activity.loggedIn}};assertTrue(main{activity.markers.isEmpty()});assertNull(main{activity.selected});assertNull(main{activity.lastCreated});assertEquals(1,main{activity.adults});assertEquals("ok",main{activity.fault});login("rescuer");refresh();assertEquals(id,main{activity.markers.single().eventId})}
 @Test fun logoutDuringPendingGetCannotRestoreMarkersOrSession(){citizen();send();rescue();click("Timeout");click("Refresh SOS");waitFor("GET pending"){main{activity.busy}};click("Đăng xuất");SystemClock.sleep(3500);assertFalse(main{activity.loggedIn});assertFalse(main{activity.busy});assertTrue(main{activity.markers.isEmpty()});assertNull(main{activity.selected})}
 @Test fun logoutDuringPendingPostCannotRestoreSuccess(){citizen();click("Timeout");click("Gửi SOS fixture");waitFor("POST pending"){main{activity.busy}};click("Đăng xuất");SystemClock.sleep(3500);assertFalse(main{activity.loggedIn});assertNull(main{activity.lastCreated});assertTrue(main{activity.markers.isEmpty()});assertEquals("Đã xóa phiên demo",main{activity.message})}
 @Test fun rescuerHasNoCreationOrMissionActionAndStrictGateStaysClosed(){launch();login("rescuer");assertFalse(visible("Gửi SOS fixture"));assertFalse(visible("Nhận nhiệm vụ"));main{activity.submit()};assertNull(main{activity.lastCreated});assertTrue(main{activity.graph.directedEdges.all{it.compatibilityStatus==EdgeCompatibilityStatus.UNVERIFIED && it.condition==RoadCondition.UNKNOWN}});assertEquals(UcsRouteStatus.STRICT_ROUTING_DISABLED,DemoGraph.analyze(main{activity.graph},"A","B").status);expect("NAVIGATION ALLOWED = FALSE • STRICT_ROUTING_DISABLED")}
}
