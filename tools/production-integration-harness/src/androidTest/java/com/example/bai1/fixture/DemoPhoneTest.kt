package com.example.bai1.fixture
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.*
import android.graphics.Bitmap
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.bai1.MainActivity
import com.example.bai1.productionisolated.ProductionFixtureApplication
import com.example.bai1.auth.session.AuthState
import com.example.bai1.presentation.navigation.Routes
import com.example.bai1.sos.presentation.SosUiState
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.LatLng
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
@RunWith(AndroidJUnit4::class)
class DemoPhoneTest {
 private val inst get()=InstrumentationRegistry.getInstrumentation()
 private val app get()=inst.targetContext.applicationContext as ProductionFixtureApplication
 private lateinit var scenario:ActivityScenario<MainActivity>
 private lateinit var activity:MainActivity
 private val clicks=mutableListOf<Map<String,Any?>>()
 private fun <T> main(f:()->T):T{var value:T?=null;inst.runOnMainSync{value=f()};@Suppress("UNCHECKED_CAST")return value as T}
 private fun waitFor(message:String,seconds:Int=20,f:()->Boolean){val end=SystemClock.uptimeMillis()+seconds*1000;while(SystemClock.uptimeMillis()<end){if(f())return;SystemClock.sleep(100)};inst.sendStatus(2,Bundle().apply{putString("productionUiTree",nodes().map{val r=android.graphics.Rect();it.getBoundsInScreen(r);"${it.text}|scroll=${it.isScrollable}|$r"}.joinToString(";"))});captureOwnWindow();fail(message)}
 private fun captureOwnWindow(){
  val size=main{activity.window.decorView.width to activity.window.decorView.height};val b=Bitmap.createBitmap(size.first,size.second,Bitmap.Config.ARGB_8888);val latch=java.util.concurrent.CountDownLatch(1);var code=-1
  main{PixelCopy.request(activity.window,b,{c->code=c;latch.countDown()},android.os.Handler(android.os.Looper.getMainLooper()))};latch.await(3,java.util.concurrent.TimeUnit.SECONDS)
  if(code==PixelCopy.SUCCESS)File(inst.targetContext.filesDir,"failure-own-window.png").outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()
 }
 private fun nodes():List<AccessibilityNodeInfo>{val root=inst.uiAutomation.rootInActiveWindow ?: return emptyList();root.refresh();if(root.packageName?.toString()!=inst.targetContext.packageName)return emptyList();val out=mutableListOf<AccessibilityNodeInfo>();fun visit(n:AccessibilityNodeInfo){n.refresh();out.add(n);for(i in 0 until n.childCount)n.getChild(i)?.let(::visit)};visit(root);return out}
 private fun ownWindow(){waitFor("Owned foreground"){nodes().isNotEmpty()}}
 private fun event(action:Int,x:Float,y:Float,down:Long){val e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0);e.source=InputDevice.SOURCE_TOUCHSCREEN;try{check(inst.uiAutomation.injectInputEvent(e,true))}finally{e.recycle()}}
 private fun scroll(back:Boolean){val r=nodes().filter{it.isScrollable}.map{val v=android.graphics.Rect();it.getBoundsInScreen(v);v}.filter{it.height()>150}.maxByOrNull{it.height()} ?: return;ownWindow();val viewport=main{val v=android.graphics.Rect();activity.window.decorView.getWindowVisibleDisplayFrame(v);v};if(!r.intersect(viewport)||r.height()<180)return;val x=r.exactCenterX();val y1=if(back)r.top+50f else r.bottom-50f;val y2=if(back)r.bottom-50f else r.top+50f;val d=SystemClock.uptimeMillis();event(MotionEvent.ACTION_DOWN,x,y1,d);for(i in 1..10){SystemClock.sleep(20);event(MotionEvent.ACTION_MOVE,x,y1+(y2-y1)*i/10,d)};event(MotionEvent.ACTION_UP,x,y2,d);SystemClock.sleep(250)}
 private fun click(text:String){var n=0;waitFor("Actual production UI click $text"){
  for(match in nodes().filter{it.text?.toString()==text||it.contentDescription?.toString()==text}){var node:AccessibilityNodeInfo?=match;while(node!=null){if(node.isVisibleToUser&&node.isClickable&&node.isEnabled&&node.performAction(AccessibilityNodeInfo.ACTION_CLICK))return@waitFor true;node=node.parent}}
  scroll(n++%24>=12);false
 };inst.waitForIdleSync()}
 private fun route()=main{app.nav.currentBackStackEntry?.destination?.route}
 private fun launch(){check(InstrumentationRegistry.getArguments().getString("physicalUsb")=="true");val port=InstrumentationRegistry.getArguments().getString("hostFixturePort")!!.toInt();scenario=ActivityScenario.launch(Intent(inst.targetContext,MainActivity::class.java).putExtra("fixturePort",port));scenario.onActivity{activity=it};waitFor("Actual login route"){route()==Routes.LOGIN&&nodes().any{it.isEditable}}}
 private fun login(actor:String){
  waitFor("Actual LoginScreen fields"){nodes().count{it.isEditable}==2}
  fun set(index:Int,value:String){val n=nodes().filter{it.isEditable}[index];assertTrue(n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,Bundle().apply{putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value)}));SystemClock.sleep(150)}
  set(0,"$actor@example.invalid");set(1,"fixture-only-password");click("Đăng nhập");waitFor("Actual AuthViewModel -> AuthRepository -> HTTP login/me"){main{app.repository.state.value is AuthState.Authenticated}&&route()==Routes.HOME}
  assertEquals(if(actor=="rescuer")"RESCUER" else "USER",main{(app.repository.state.value as AuthState.Authenticated).user.role})
 }
 private fun form(){click("SOS");waitFor("Actual SOSScreen"){route()==Routes.SOS};ownWindow();val node=nodes().filter{it.text?.toString()=="SOS"}.map{val r=android.graphics.Rect();it.getBoundsInScreen(r);r}.filter{it.height()>30}.maxByOrNull{it.height()} ?: error("Actual hold control missing");val d=SystemClock.uptimeMillis();event(MotionEvent.ACTION_DOWN,node.exactCenterX(),node.exactCenterY(),d);SystemClock.sleep(1500);event(MotionEvent.ACTION_UP,node.exactCenterX(),node.exactCenterY(),d);waitFor("Actual hold -> navigation -> production form"){route()==Routes.SOS_RESCUE_REQUEST&&app.formModel!=null};waitFor("Synthetic provider in real VM"){main{app.formModel!!.formData.value.latitude==app.testLatitude}};click("Kẹt trong nhà");waitFor("Condition selected"){main{app.formModel!!.selectedConditions.value.contains("Kẹt trong nhà")}}
  // Only synthetic text enters the actual production editable form.
  waitFor("Description field"){if(nodes().any{it.isEditable})true else{scroll(false);false}}
  val field=nodes().first{it.isEditable};assertTrue(field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,Bundle().apply{putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,"SYNTHETIC_TEST_DATA cần hỗ trợ") }));SystemClock.sleep(200)
 }
 private fun submit():String{click("GỬI YÊU CẦU CỨU HỘ");waitFor("Production form submitted via actual repository/transport"){main{app.formModel!!.uiState.value is SosUiState.Submitted}};return main{(app.formModel!!.uiState.value as SosUiState.Submitted).result.eventId}}
 private fun logout(){if(route()==Routes.SOS_RESCUE_REQUEST)click("XEM TRÊN BẢN ĐỒ");waitFor("Bottom navigation after success"){nodes().any{it.text?.toString()=="Cá nhân"}};click("Cá nhân");waitFor("Actual Profile"){route()==Routes.PROFILE};click("Settings");waitFor("Actual Settings"){route()==Routes.SETTINGS};click("Đăng xuất");waitFor("Actual AppNavGraph logout clears protected routes"){route()==Routes.LOGIN&&main{!app.storage.isLoggedIn()}}}
 private fun mapLoaded(){waitFor("Actual production GoogleMap SDK tiles callback",60){main{app.sdkMap!=null&&app.loadedCallbacks>0}}}
 private fun openMap(){click("Bản đồ");waitFor("Actual DisasterMapScreen"){route()==Routes.MAP&&app.mapModel!=null};mapLoaded();waitFor("Real GET produced active marker or explicit empty/error"){main{app.mapModel!=null}}}
 private fun mapView():MapView=main{fun find(v:View):MapView?{if(v is MapView)return v;if(v is ViewGroup)for(i in 0 until v.childCount)find(v.getChildAt(i))?.let{return it};return null};find(activity.window.decorView) ?: error("Actual MapView missing")}
 private fun nativeClick(id:String){waitFor("GET ID appears in production VM"){main{app.mapModel!!.markers.value.any{it.eventId==id}}};val row=main{app.mapModel!!.markers.value.single{it.eventId==id}};SystemClock.sleep(1400);val point=main{app.sdkMap!!.projection.toScreenLocation(LatLng(row.latitude,row.longitude))};val loc=IntArray(2);val v=mapView();main{v.getLocationOnScreen(loc)};ownWindow();for(off in listOf(24f,42f,60f,12f)){val d=SystemClock.uptimeMillis();event(MotionEvent.ACTION_DOWN,loc[0]+point.x.toFloat(),loc[1]+point.y-off,d);SystemClock.sleep(50);event(MotionEvent.ACTION_UP,loc[0]+point.x.toFloat(),loc[1]+point.y-off,d);SystemClock.sleep(500);if(main{app.selected?.eventId==id})break};assertEquals(id,main{app.selected?.eventId});val n=main{app.clicked!!};assertEquals(LatLng(row.latitude,row.longitude),main{n.position});assertTrue(main{n.isInfoWindowShown});clicks.add(main{mapOf("eventId" to id,"latitude" to n.position.latitude,"longitude" to n.position.longitude,"title" to n.title,"snippet" to n.snippet,"infoWindowShown" to n.isInfoWindowShown)})}
 private fun screenshot(name:String){ownWindow();SystemClock.sleep(500);val b=inst.uiAutomation.takeScreenshot()!!;File(inst.targetContext.filesDir,name).outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()}
 @After fun close(){if(::scenario.isInitialized){inst.sendStatus(2,Bundle().apply{putString("p5e10aOutput",main{com.google.gson.Gson().toJson(mapOf("route" to app.nav.currentBackStackEntry?.destination?.route,"role" to (app.repository.state.value as? AuthState.Authenticated)?.user?.role,"markers" to app.mapModel?.markers?.value,"nativeClicks" to clicks,"callbacks" to app.loadedCallbacks,"rootPackage" to inst.uiAutomation.rootInActiveWindow?.packageName?.toString(),"ownWindowFocused" to activity.hasWindowFocus(),"myLocation" to app.sdkMap?.isMyLocationEnabled,"error" to app.mapModel?.refreshError?.value))})});scenario.close()}}
 @Test fun productionUiPostLogoutRescuerGetAndNativeMapMarker(){launch();login("citizen");form();val id=submit();logout();login("rescuer");openMap();nativeClick(id);assertEquals(16.051,main{app.clicked!!.position.latitude},0.0);assertEquals(108.203,main{app.clicked!!.position.longitude},0.0);assertTrue(nodes().any{it.text?.toString()?.contains("Người cần cứu: 1")==true});screenshot("production-sos-map.png")}
 @Test fun productionEmptyMapLoadsWithoutInventedSos(){launch();login("rescuer");openMap();SystemClock.sleep(1200);assertTrue(main{app.mapModel!!.markers.value.isEmpty()});assertFalse(main{app.sdkMap!!.isMyLocationEnabled});screenshot("production-empty.png")}
 @Test fun productionFormHttp403ShowsError(){launch();login("citizen");form();main{app.fault="403"};click("GỬI YÊU CẦU CỨU HỘ");waitFor("Production form 403 error"){main{app.formModel!!.uiState.value is SosUiState.Error}};assertFalse(main{app.formModel!!.uiState.value is SosUiState.Submitted});screenshot("production-form-error.png")}
 @Test fun productionGetFailureClearsMarkersAndShowsError(){launch();login("citizen");form();val id=submit();logout();login("rescuer");openMap();nativeClick(id);main{app.fault="500";app.mapModel!!.loadNearbyRequests(16.051,108.203)};waitFor("GET error clears production map"){main{app.mapModel!!.refreshError.value!=null&&app.mapModel!!.markers.value.isEmpty()}}}
 @Test fun productionLogoutDoesNotRestoreProtectedMap(){launch();login("rescuer");openMap();logout();assertNull(main{app.mapModel});scenario.recreate();scenario.onActivity{activity=it};waitFor("Logged-out actual graph stays public"){route()==Routes.LOGIN};assertFalse(main{app.storage.isLoggedIn()})}
 @Test fun productionMapRecreationUsesSyntheticDependencies(){launch();login("rescuer");openMap();scenario.recreate();scenario.onActivity{activity=it};waitFor("Recreated production map route"){route()==Routes.MAP};mapLoaded();assertFalse(main{app.sdkMap!!.isMyLocationEnabled})}
 @Test fun productionVariantHasNoSensitivePermissions(){launch();login("rescuer");openMap();val p=inst.targetContext.packageManager.getPackageInfo(inst.targetContext.packageName,android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty();assertTrue(p.none{it.contains("LOCATION")||it.contains("CAMERA")||it.contains("CONTACTS")||it.contains("SMS")||it.contains("RECORD_AUDIO")});assertFalse(main{app.sdkMap!!.uiSettings.isMapToolbarEnabled})}
}
