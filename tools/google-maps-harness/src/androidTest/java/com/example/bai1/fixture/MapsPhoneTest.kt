package com.example.bai1.fixture

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.view.MotionEvent
import android.view.InputDevice
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.bai1.gmaps01.MapsDemoActivity
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.LatLng
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class MapsPhoneTest {
 private val inst get()=InstrumentationRegistry.getInstrumentation()
 private lateinit var scenario:ActivityScenario<MapsDemoActivity>
 private lateinit var activity:MapsDemoActivity
 private fun <T> main(f:()->T):T{var value:T?=null;inst.runOnMainSync{value=f()};@Suppress("UNCHECKED_CAST") return value as T}
 private fun waitFor(message:String,seconds:Int=60,f:()->Boolean){val end=SystemClock.uptimeMillis()+seconds*1000;while(SystemClock.uptimeMillis()<end){if(f())return;SystemClock.sleep(100)};fail(message)}
 private fun launch(loaded:Boolean=true){
  check(InstrumentationRegistry.getArguments().getString("physicalUsb")=="true")
  scenario=ActivityScenario.launch(Intent(inst.targetContext,MapsDemoActivity::class.java));scenario.onActivity{activity=it}
  waitFor("Actual GoogleMap / MapEffect SDK ready"){main{activity.ready&&activity.sdkMap!=null}}
  if(loaded)waitFor("Actual SDK onMapLoaded required; no synthetic success"){main{activity.loaded&&activity.loadedCallbacks>0}}
 }
 private fun mapView():MapView=main{
  fun find(v:View):MapView? {if(v is MapView)return v;if(v is ViewGroup)for(i in 0 until v.childCount)find(v.getChildAt(i))?.let{return it};return null}
  find(activity.window.decorView) ?: error("Actual SDK MapView missing")
 }
 private fun ownWindow(){waitFor("Only act on owned foreground window",15){inst.uiAutomation.rootInActiveWindow?.let{it.refresh();it.packageName?.toString()==inst.targetContext.packageName}==true}}
 private fun screenshot(name:String){
  inst.waitForIdleSync();SystemClock.sleep(1000)
  ownWindow()
  val image=inst.uiAutomation.takeScreenshot() ?: error("Device screenshot unavailable")
  File(inst.targetContext.filesDir,name).outputStream().use{check(image.compress(Bitmap.CompressFormat.PNG,100,it))};image.recycle()
 }
 private fun event(action:Int,x:Float,y:Float,down:Long){val e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0);e.source=InputDevice.SOURCE_TOUCHSCREEN;try{check(inst.uiAutomation.injectInputEvent(e,true))}finally{e.recycle()}}
 private fun tap(x:Float,y:Float){ownWindow();val t=SystemClock.uptimeMillis();event(MotionEvent.ACTION_DOWN,x,y,t);SystemClock.sleep(50);event(MotionEvent.ACTION_UP,x,y,t)}
 private fun clickMarker(position:LatLng,title:String){
  val point=main{activity.sdkMap!!.projection.toScreenLocation(position)};val loc=IntArray(2);val view=mapView();main{view.getLocationOnScreen(loc)}
  for(offset in listOf(24f,42f,60f,12f)){
   tap(loc[0]+point.x.toFloat(),loc[1]+point.y-offset);SystemClock.sleep(500)
   if(main{activity.selected.startsWith(title)})return
  }
  fail("Real SDK marker callback not received for $title")
 }
 @After fun close(){if(::scenario.isInitialized){
  val values=main{val c=activity.sdkMap?.cameraPosition;org.json.JSONObject().put("ready",activity.ready).put("loaded",activity.loaded).put("callbacks",activity.loadedCallbacks).put("state",activity.loadState).put("selected",activity.selected).put("latitude",c?.target?.latitude).put("longitude",c?.target?.longitude).put("zoom",c?.zoom).put("clickedLatitude",activity.clickedMarker?.position?.latitude).put("clickedLongitude",activity.clickedMarker?.position?.longitude).put("clickedTitle",activity.clickedMarker?.title).put("myLocation",activity.sdkMap?.isMyLocationEnabled).toString()}
  inst.sendStatus(2,Bundle().apply{putString("m01Output",values)});scenario.close()
 }}
 @Test fun actualGoogleMapLoadsTilesAtSyntheticCamera(){launch();assertNotNull(mapView());val c=main{activity.sdkMap!!.cameraPosition};assertEquals(16.0505,c.target.latitude,0.00001);assertEquals(108.2025,c.target.longitude,0.00001);assertEquals(15.5f,c.zoom,0.01f);screenshot("tiles-loaded.png")}
 @Test fun actualRescuerMarkerTapShowsCorrectInfo(){launch();clickMarker(MapsDemoActivity.RESCUER,"RESCUER TEST");assertEquals("RESCUER TEST | 16.0500, 108.2020",main{activity.selected});assertEquals(MapsDemoActivity.RESCUER,main{activity.clickedMarker!!.position});assertEquals("RESCUER TEST",main{activity.clickedMarker!!.title});assertTrue(main{activity.clickedMarker!!.isInfoWindowShown});screenshot("rescuer-info.png")}
 @Test fun actualSosMarkerTapShowsCorrectInfo(){launch();clickMarker(MapsDemoActivity.SOS,"SOS TEST");assertEquals("SOS TEST | 16.0510, 108.2030",main{activity.selected});assertEquals(MapsDemoActivity.SOS,main{activity.clickedMarker!!.position});assertEquals("SOS TEST",main{activity.clickedMarker!!.title});assertTrue(main{activity.clickedMarker!!.isInfoWindowShown});screenshot("sos-info.png")}
 @Test fun actualDoubleTapZoomAndDragPanChangeSdkCamera(){
  launch();val v=mapView();val loc=IntArray(2);main{v.getLocationOnScreen(loc)};val x=loc[0]+v.width*.25f;val y=loc[1]+v.height*.35f;val before=main{activity.sdkMap!!.cameraPosition.zoom}
  tap(x,y);SystemClock.sleep(120);tap(x,y);waitFor("Actual double tap zoom",10){main{activity.sdkMap!!.cameraPosition.zoom>before+.2f}}
  SystemClock.sleep(1000);val target=main{activity.sdkMap!!.cameraPosition.target};val cx=loc[0]+v.width*.5f;val cy=loc[1]+v.height*.55f;val t=SystemClock.uptimeMillis();event(MotionEvent.ACTION_DOWN,cx,cy,t)
  for(i in 1..12){SystemClock.sleep(30);event(MotionEvent.ACTION_MOVE,cx+180*i/12f,cy,t)};event(MotionEvent.ACTION_UP,cx+180,cy,t)
  waitFor("Actual drag pan",10){main{val p=activity.sdkMap!!.cameraPosition.target;kotlin.math.abs(p.longitude-target.longitude)>0.00001}};screenshot("zoom-pan.png")
 }
 @Test fun actualActivityRecreationReloadsSdkWithoutCrash(){launch();val old=main{activity.sdkMap};scenario.recreate();scenario.onActivity{activity=it};waitFor("Recreated actual map loaded"){main{activity.ready&&activity.loaded}};assertNotSame(old,main{activity.sdkMap});assertEquals(16.0505,main{activity.sdkMap!!.cameraPosition.target.latitude},0.00001)}
 @Test fun noLocationPermissionsAndSdkMyLocationDisabled(){launch();val permissions=inst.targetContext.packageManager.getPackageInfo(inst.targetContext.packageName,android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty().toSet();assertTrue(permissions.none{it in setOf("android.permission.ACCESS_FINE_LOCATION","android.permission.ACCESS_COARSE_LOCATION","android.permission.CAMERA","android.permission.RECORD_AUDIO","android.permission.READ_CONTACTS","android.permission.SEND_SMS")});assertFalse(main{activity.sdkMap!!.isMyLocationEnabled});assertFalse(main{activity.sdkMap!!.uiSettings.isMyLocationButtonEnabled});assertFalse(main{activity.sdkMap!!.uiSettings.isMapToolbarEnabled})}
 @Test fun isolatedDemoKeepsF06AndStrictDisabledBanner(){
  launch();ownWindow()
  fun texts():List<String>{val root=inst.uiAutomation.rootInActiveWindow ?: return emptyList();check(root.packageName.toString()==inst.targetContext.packageName);val result=mutableListOf<String>();fun visit(n:android.view.accessibility.AccessibilityNodeInfo){n.refresh();n.text?.let{result.add(it.toString())};for(i in 0 until n.childCount)n.getChild(i)?.let(::visit)};visit(root);return result}
  waitFor("Actual visible strict/F06/synthetic banner",15){val t=texts();t.any{it.contains("STRICT_ROUTING_DISABLED = true")&&it.contains("F06 OPEN")}&&t.any{it.contains("DỮ LIỆU VỊ TRÍ MÔ PHỎNG")}}
 }
 @Test fun invalidApiKeyCannotProduceLoadedSuccess(){assertTrue(inst.targetContext.packageName.endsWith(".negative"));launch(false);waitFor("Invalid-key watchdog failure state",45){main{activity.loadState.startsWith("ERROR_OR_TIMEOUT")}};assertFalse(main{activity.loaded});assertEquals(0,main{activity.loadedCallbacks});screenshot("invalid-key-no-tiles.png")}
}
