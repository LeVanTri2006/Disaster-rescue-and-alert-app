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
  fail(message)
 }
 private fun <T> main(f:()->T):T {var v:T?=null;inst.runOnMainSync {v=f()};@Suppress("UNCHECKED_CAST") return v as T}
 private fun nodes():List<AccessibilityNodeInfo> {
  val root=inst.uiAutomation.rootInActiveWindow ?: return emptyList()
  if(root.packageName?.toString()!=inst.targetContext.packageName)return emptyList()
  val out=mutableListOf<AccessibilityNodeInfo>();fun visit(n:AccessibilityNodeInfo){out.add(n);for(i in 0 until n.childCount)n.getChild(i)?.let(::visit)};visit(root);return out
 }
 private fun visible(v:String)=nodes().any{(it.text?.toString()==v || it.contentDescription?.toString()==v)&&it.isVisibleToUser}
 private fun scroll(back:Boolean) {
  val node=nodes().firstOrNull{it.isScrollable} ?: return
  val rect=android.graphics.Rect();node.getBoundsInScreen(rect)
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
 private fun launch(usePort:Boolean=true) {
  check(InstrumentationRegistry.getArguments().getString("physicalUsb")=="true")
  val port=InstrumentationRegistry.getArguments().getString("hostFixturePort")!!.toInt()
  scenario=ActivityScenario.launch(Intent(inst.targetContext,DemoActivity::class.java).putExtra("fixturePort",if(usePort)port else 0))
  scenario.onActivity{activity=it};expect("Đăng nhập demo")
 }
 private fun login(){launch();click("Đăng nhập demo");waitFor("Actual Auth HTTP synthetic login"){main{activity.loggedIn}};expect("Map / UCS")}
 private fun analyze(){click("Phân tích đường đi");waitFor("Public routing result"){main{activity.result!=null}};assertEquals(UcsRouteStatus.STRICT_ROUTING_DISABLED,main{activity.result!!.status});assertTrue(main{activity.result!!.pathEdges.isEmpty() && activity.result!!.pathGeometry.isEmpty()});expect("KẾT QUẢ: STRICT_ROUTING_DISABLED")}
 private fun sos(){login();click("Cứu hộ fixture");expect("Gửi tới fixture")}
 @After fun close(){if(::scenario.isInitialized)scenario.close()}
 @Test fun offlineGraphRendersWithoutTilesGpsOrLogin(){launch();click("Chẩn đoán offline");expect("8 nút / 8 cạnh • A/B được chọn; H tách rời • tím: SOS fixture");assertFalse(main{activity.loggedIn});assertEquals(8,main{activity.graph.nodes.size});analyze()}
 @Test fun actualPublicRouterRunsFromUiAndRemainsDenied(){login();analyze();assertEquals(1,main{activity.invocations});inst.sendStatus(2,Bundle().apply { putString("p5e09Output",main {com.google.gson.Gson().toJson(mapOf("graphVersion" to activity.graph.graphVersion,"start" to activity.start,"goal" to activity.goal,"status" to activity.result!!.status.name,"path" to activity.result!!.pathNodeIds,"rawDefaultCost" to activity.result!!.totalCost,"costMeaning" to "N/A: denied before kernel", "edges" to activity.graph.directedEdges.map{mapOf("id" to it.id,"lengthMeters" to it.lengthMeters,"condition" to it.condition.name,"compatibility" to it.compatibilityStatus.name)})) }) });assertEquals("A",main{activity.start});assertEquals("B",main{activity.goal});assertEquals(0.0,main{activity.result!!.totalCost},0.0)}
 @Test fun selectedStartAndDisconnectedDestinationUpdateInputNotInventPath(){login();click("Điểm đi: A");click("Điểm đến: B");waitFor("Selected C/H"){main{activity.start=="C"&&activity.goal=="H"}};analyze();assertFalse(main{activity.graph.directedEdges.any{it.toNodeId=="H"}});assertEquals(0,main{activity.result!!.visitedNodesCount})}
 @Test fun blockedRoadUpdatesGraphAndRunsPublicGateAgain(){login();analyze();click("Chặn AC");waitFor("Blocked graph"){main{activity.graph.directedEdges.first{it.id=="AC"}.condition==RoadCondition.BLOCKED}};analyze();assertEquals(2,main{activity.invocations})}
 @Test fun actualTurnIndexChangesFromAllowToDenyWithoutNavigationAuthority(){login();analyze();assertEquals("ALLOW",main{activity.transition});click("Cấm rẽ");waitFor("Turn fixture selected"){main{activity.scenario=="turn"}};analyze();assertEquals("DENY_PROHIBITED",main{activity.transition})}
 @Test fun malformedGraphRejectedByProductionParserAndUiDisablesAnalysis(){login();click("Graph lỗi");waitFor("Actual parser rejects negative length"){main{activity.validation.startsWith("PARSER_REJECTED")}};assertNull(main{activity.result});assertTrue(main{activity.validation.contains("InvalidEdgeLength")})}
 @Test fun missingNodeEmptyGraphAndVehicleUnknownRemainFailClosed(){val g=DemoGraph.parse();assertEquals(8,g.nodes.size);assertTrue(g.directedEdges.all{it.compatibilityStatus==EdgeCompatibilityStatus.UNVERIFIED && it.condition==RoadCondition.UNKNOWN});assertEquals(UcsRouteStatus.INVALID_INPUT,DemoGraph.analyze(g,"MISSING","B").status);assertEquals(UcsRouteStatus.GRAPH_UNAVAILABLE,DemoGraph.analyze(RoadGraph(emptyMap(),emptyList()),"A","B").status);assertEquals("RESCUE_TRUCK_4X4",g.vehicleProfile)}
 @Test fun actualSosTransportSendsSyntheticFormAndPlacesReturnedMarker(){sos();click("Thêm người");click("Gửi tới fixture");waitFor("Actual SOS HTTP response"){main{activity.message.startsWith("FIXTURE_RECEIVED")}};assertEquals(2,main{activity.adults});assertTrue(main{activity.message.endsWith("status=ACTIVE")});assertEquals(main{activity.graph.nodes.getValue("B").position},main{activity.marker});click("Map / UCS");expect("8 nút / 8 cạnh • A/B được chọn; H tách rời • tím: SOS fixture")}
 @Test fun sosHttp403RejectedByActualClientAndTransport(){sos();click("HTTP 403");click("Gửi tới fixture");waitFor("HTTP denial"){main{activity.message.startsWith("FIXTURE_ERROR HTTP 403")}};assertNull(main{activity.marker})}
 @Test fun sosTimeoutDoesNotCommitLateSuccess(){sos();click("Timeout");click("Gửi tới fixture");waitFor("Actual socket timeout"){main{activity.message.startsWith("FIXTURE_ERROR")&&!activity.busy}};SystemClock.sleep(3200);assertNull(main{activity.marker});assertFalse(main{activity.message.startsWith("FIXTURE_RECEIVED")})}
 @Test fun sosOfflineFailsLocallyWithoutSyntheticSuccess(){sos();click("Offline");click("Gửi tới fixture");waitFor("Offline failure"){main{activity.message.startsWith("FIXTURE_ERROR")}};assertNull(main{activity.marker})}
 @Test fun logoutDuringPendingSosCannotCommitAndBackReturnsLogin(){sos();click("Timeout");click("Gửi tới fixture");waitFor("Pending actual transport"){main{activity.busy}};click("Đăng xuất");SystemClock.sleep(3200);assertFalse(main{activity.loggedIn});assertNull(main{activity.marker});expect("Đăng nhập demo")}
 @Test fun ownBackFromSosReturnsDiagnosticWithoutDispatch(){sos();main{activity.onBackPressedDispatcher.onBackPressed()};waitFor("Own Back returns map"){main{activity.page=="map"}};analyze()}
 @Test fun demoPrepareForProcessRestart(){login();analyze();assertTrue(main{activity.loggedIn})}
 @Test fun demoRestartUsesOwnPortButNoAuthSession(){launch(false);assertFalse(main{activity.loggedIn});assertNull(main{activity.result});assertTrue(main{activity.base.startsWith("http://127.0.0.1:")});click("Chẩn đoán offline");analyze()}
 @Test fun activityRecreationDoesNotRestoreAuthOrGrantRoute(){login();analyze();scenario.recreate();scenario.onActivity{activity=it};assertFalse(main{activity.loggedIn});assertNull(main{activity.result});expect("Đăng nhập demo")}
}
