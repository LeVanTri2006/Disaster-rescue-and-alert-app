package com.example.bai1.gmaps01

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.GoogleMap as NativeGoogleMap
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.maps.android.compose.*
import kotlinx.coroutines.delay

/** M01 only: SDK map + static synthetic markers; no business DI, GPS or routing. */
class MapsDemoActivity : ComponentActivity() {
 companion object {
  val CENTER=LatLng(16.0505,108.2025)
  val RESCUER=LatLng(16.0500,108.2020)
  val SOS=LatLng(16.0510,108.2030)
 }
 var sdkMap:NativeGoogleMap?=null; private set
 var clickedMarker:com.google.android.gms.maps.model.Marker?=null;private set
 var ready by mutableStateOf(false);private set
 var loaded by mutableStateOf(false);private set
 var loadedCallbacks by mutableStateOf(0);private set
 var loadState by mutableStateOf("LOADING");private set
 var selected by mutableStateOf("");private set
 var mapBounds:Rect=Rect.Zero;private set
 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState)
  window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
  setContent {MaterialTheme {Surface {MapScreen()}}}
 }
 @OptIn(MapsComposeExperimentalApi::class)
 @Composable private fun MapScreen(){
  val camera=rememberCameraPositionState {position=CameraPosition.fromLatLngZoom(CENTER,15.5f)}
  LaunchedEffect(Unit){delay(30000);if(!loaded)loadState="ERROR_OR_TIMEOUT: chưa chứng minh tải xong; kiểm mạng/API"}
  Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(8.dp)){
   Text("GOOGLE MAPS DEMO — M01",style=MaterialTheme.typography.titleMedium)
   Text("DỮ LIỆU VỊ TRÍ MÔ PHỎNG",style=MaterialTheme.typography.labelMedium)
   Text("F06 OPEN • STRICT_ROUTING_DISABLED = true",style=MaterialTheme.typography.labelSmall)
   Text("SDK: $loadState",style=MaterialTheme.typography.labelMedium)
   GoogleMap(modifier=Modifier.fillMaxWidth().weight(1f).onGloballyPositioned{mapBounds=it.boundsInWindow()},
    cameraPositionState=camera,
    properties=MapProperties(isMyLocationEnabled=false),
    uiSettings=MapUiSettings(myLocationButtonEnabled=false,mapToolbarEnabled=false,zoomControlsEnabled=true,zoomGesturesEnabled=true,scrollGesturesEnabled=true,rotationGesturesEnabled=false,tiltGesturesEnabled=false),
    onMapLoaded={loaded=true;loadedCallbacks++;loadState="LOADED_CALLBACK";android.util.Log.i("M01_MAP","onMapLoaded count=$loadedCallbacks")}
   ){
    MapEffect(Unit){map->sdkMap=map;ready=true;if(!loaded)loadState="SDK_READY / loading tiles";android.util.Log.i("M01_MAP","SDK_READY location=false")}
    Marker(state=rememberMarkerState(position=RESCUER),title="RESCUER TEST",snippet="16.0500, 108.2020 — mô phỏng",icon=BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE),onClick={marker->clickedMarker=marker;selected="RESCUER TEST | 16.0500, 108.2020";false})
    Marker(state=rememberMarkerState(position=SOS),title="SOS TEST",snippet="16.0510, 108.2030 — mô phỏng",icon=BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED),onClick={marker->clickedMarker=marker;selected="SOS TEST | 16.0510, 108.2030";false})
   }
   Text("RESCUER TEST: 16.0500, 108.2020",style=MaterialTheme.typography.labelMedium)
   Text("SOS TEST: 16.0510, 108.2030",style=MaterialTheme.typography.labelMedium)
   Text(if(selected.isBlank())"Bấm marker để xem thông tin" else selected,style=MaterialTheme.typography.labelMedium)
   Text("Không GPS / SOS dispatch / UCS / Polyline",style=MaterialTheme.typography.labelSmall)
  }
 }
}
