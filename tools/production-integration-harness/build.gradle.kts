import java.util.Properties

plugins {
 id("com.android.application") version "9.4.0"
 id("org.jetbrains.kotlin.plugin.compose") version "2.2.10"
}
val mapsProperties = Properties().apply {
 rootProject.file("../../local.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
}
val authorizedMapsKey = providers.environmentVariable("GMAPS01_API_KEY").orNull
 ?: mapsProperties.getProperty("MAPS_API_KEY")
require(!authorizedMapsKey.isNullOrBlank() && authorizedMapsKey != "PLACEHOLDER") { "Authorized Maps API key is required (value redacted)" }

android {
 namespace = "com.example.bai1"
 compileSdk = 37
 defaultConfig {
  applicationId = "com.example.bai1.p5e10production.demo"
  minSdk = 30
  targetSdk = 37
  versionCode = 1
  versionName = "E10-PRODUCTION-UI"
  manifestPlaceholders["GMAPS01_MAPS_KEY"] = authorizedMapsKey
  testInstrumentationRunner = "com.example.bai1.fixture.IsolatedInstrumentationRunner"
 }
 buildFeatures { compose = true; buildConfig = true }
 sourceSets.getByName("main").kotlin.directories.add("build/generated/exact-source")
 compileOptions { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11 }
}
// Cached Maps/Compose versions plus exact production HTTP/domain copies.
// No google-services plugin or production DI. Key reuse is user-authorized.
dependencies {
 implementation("com.google.maps.android:maps-compose:4.4.1")
 implementation("com.google.android.gms:play-services-maps:19.0.0")
 implementation(platform("androidx.compose:compose-bom:2026.02.01"))
 implementation("androidx.compose.ui:ui-android:1.11.2")
 implementation("androidx.compose.material3:material3-android:1.4.0")
 implementation("com.google.guava:guava:31.1-android") // Existing E10A/main cached graph supplies ListenableFuture.
 implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.2.10")
 implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk7:2.2.10")
}

// Align only this standalone module to artifacts already cached by the main app.
dependencies {
 constraints {
  implementation("androidx.activity:activity:1.13.0")
  implementation("androidx.activity:activity-compose:1.13.0")
  implementation("androidx.activity:activity-ktx:1.13.0")
  implementation("androidx.compose.animation:animation-android:1.10.5")
  implementation("androidx.compose.animation:animation-core-android:1.10.5")
  implementation("androidx.compose.foundation:foundation-android:1.10.5")
  implementation("androidx.compose.foundation:foundation-layout-android:1.10.5")
  implementation("androidx.core:core:1.18.0")
  implementation("androidx.core:core-ktx:1.18.0")
  implementation("androidx.lifecycle:lifecycle-livedata:2.11.0")
  implementation("androidx.lifecycle:lifecycle-livedata-core:2.11.0")
  implementation("androidx.lifecycle:lifecycle-livedata-core-ktx:2.11.0")
  implementation("androidx.lifecycle:lifecycle-process:2.11.0")
  implementation("androidx.lifecycle:lifecycle-runtime-android:2.11.0")
  implementation("androidx.lifecycle:lifecycle-runtime-compose-android:2.11.0")
  implementation("androidx.lifecycle:lifecycle-runtime-ktx-android:2.11.0")
  implementation("androidx.lifecycle:lifecycle-viewmodel:2.11.0")
  implementation("androidx.lifecycle:lifecycle-viewmodel-android:2.11.0")
  implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.11.0")
  implementation("androidx.lifecycle:lifecycle-viewmodel-savedstate-android:2.11.0")
  implementation("androidx.savedstate:savedstate-android:1.5.0")
  implementation("androidx.savedstate:savedstate-compose-android:1.5.0")
  implementation("androidx.savedstate:savedstate-ktx:1.5.0")
  implementation("com.google.android.gms:play-services-base:18.9.0")
  implementation("com.google.android.gms:play-services-basement:18.9.0")
  implementation("com.google.android.gms:play-services-tasks:18.4.0")
 }
}

dependencies {
 implementation("androidx.activity:activity-compose:1.13.0")
 androidTestImplementation("androidx.test.ext:junit:1.1.5")
 androidTestImplementation("androidx.test:runner:1.5.2")
 androidTestImplementation("androidx.annotation:annotation-experimental:1.4.1")
 androidTestImplementation("com.google.code.findbugs:jsr305:3.0.2")
}
configurations.configureEach {
 resolutionStrategy.dependencySubstitution {
  substitute(module("androidx.lifecycle:lifecycle-common")).using(module("androidx.lifecycle:lifecycle-common-jvm:2.11.0"))
 }
}

val appSource=file("../../app/src/main/java")
val exactSource = tasks.register<Copy>("exactSource") {
 from(appSource) {
  include("com/example/bai1/ui/theme/*.kt")
  include("com/example/bai1/presentation/auth/RegisterScreen.kt")
  include("com/example/bai1/presentation/auth/OnboardingScreen.kt")
  include("com/example/bai1/presentation/settings/AboutAppScreen.kt")
  include("com/example/bai1/presentation/components/CommonButtons.kt")
  include("com/example/bai1/presentation/components/AppTopBar.kt")
  include("com/example/bai1/presentation/components/FeedbackStates.kt")
  include("com/example/bai1/data/model/Models.kt")
  include("com/example/bai1/presentation/auth/LoginScreen.kt")
  include("com/example/bai1/presentation/auth/RegistrationValidation.kt")
  include("com/example/bai1/auth/AuthRepository.kt")
  include("com/example/bai1/auth/AuthViewModel.kt")
  include("com/example/bai1/auth/firebase/FirebaseAuthSession.kt")
  include("com/example/bai1/presentation/auth/RoleSelectionScreen.kt")
  include("com/example/bai1/auth/RoleSelectionViewModel.kt")
  include("com/example/bai1/presentation/profile/ProfileScreen.kt")
  include("com/example/bai1/presentation/settings/SettingsScreen.kt")
  include("com/example/bai1/presentation/home/HomeScreen.kt")
  include("com/example/bai1/presentation/warning/WarningListScreen.kt")
  include("com/example/bai1/presentation/warning/StormDetailScreen.kt")
  include("com/example/bai1/presentation/warning/FloodDetailScreen.kt")
  include("com/example/bai1/presentation/notification/NotificationScreen.kt")
  include("com/example/bai1/data/repository/DisasterRepository.kt")
  include("com/example/bai1/presentation/components/AppBottomNavigation.kt")
  include("com/example/bai1/presentation/components/EmergencyCards.kt")
  include("com/example/bai1/presentation/components/TaskCard.kt")
  include("com/example/bai1/MainActivity.kt")
  include("com/example/bai1/MainAppHost.kt")
  include("com/example/bai1/presentation/navigation/AppNavGraph.kt")
  include("com/example/bai1/presentation/navigation/Routes.kt")
  include("com/example/bai1/presentation/navigation/AppNavScreens.kt")
  include("com/example/bai1/presentation/navigation/AuthNavigationPolicy.kt")
  include("com/example/bai1/auth/credentials/CredentialStateCleaner.kt")
  include("com/example/bai1/presentation/auth/SplashScreen.kt")
  include("com/example/bai1/presentation/sos/SOSScreen.kt")
  include("com/example/bai1/presentation/sos/SosRescueRequestScreen.kt")
  include("com/example/bai1/presentation/sos/SosRescueRequestViewModel.kt")
  include("com/example/bai1/presentation/location/LocationContract.kt")
  include("com/example/bai1/presentation/location/CurrentLocationViewModel.kt")
  include("com/example/bai1/presentation/map/DisasterMapScreen.kt")
  include("com/example/bai1/presentation/map/DisasterMapViewModel.kt")
  include("com/example/bai1/presentation/map/MapRescueMarker.kt")
  include("com/example/bai1/sos/presentation/*.kt")
  include("com/example/bai1/sos/repository/SosRepository.kt")
  include("com/example/bai1/sos/repository/InMemorySosRepository.kt")
  include("com/example/bai1/sos/repository/R2SosPhotoUploader.kt")
  include("com/example/bai1/sos/transport/SosTransportManager.kt")
  include("com/example/bai1/sos/provider/BatteryInfoProvider.kt")
  include("com/example/bai1/sos/provider/NetworkAvailabilityProvider.kt")
  include("com/example/bai1/rescue/network/*.kt")
  include("com/example/bai1/rescue/repository/RescueMissionRepository.kt")

  include("com/example/bai1/auth/session/AuthModels.kt")
  include("com/example/bai1/auth/session/TokenStorage.kt")
  include("com/example/bai1/auth/network/AuthApiClient.kt")
  include("com/example/bai1/sos/network/SosApiClient.kt")
  include("com/example/bai1/sos/network/SosApiModels.kt")
  include("com/example/bai1/sos/domain/*.kt")
  include("com/example/bai1/sos/transport/SosTransport.kt")
  include("com/example/bai1/sos/transport/ApiSosTransport.kt")
 }
 into(layout.buildDirectory.dir("generated/exact-source"))
}
tasks.named("preBuild").configure { dependsOn(exactSource) }

dependencies {
 implementation("com.squareup.okio:okio:3.9.1")
 implementation("com.squareup.okhttp3:okhttp:4.12.0")
 implementation("com.google.code.gson:gson:2.10.1")
 implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}

dependencies {
 implementation("androidx.navigation:navigation-compose-android:2.10.1")
 implementation("androidx.compose.material:material-icons-extended-android:1.7.8")
 implementation("androidx.lifecycle:lifecycle-viewmodel-compose-android:2.11.0")
}
val productionImages=tasks.register<Copy>("productionImages") {
 from(file("../../app/src/main/res")) { include("drawable/chonvaitro.*", "drawable/avatar.*", "drawable/news_storm.*", "drawable/news_flood.*", "drawable/splash_bg.*", "drawable/onboarding*.png", "drawable-nodpi/onboarding*.png", "drawable/icondangnhap.png", "drawable/google.webp", "drawable/facebook_logo.png", "drawable/zalo_logo.jpg") }
 into(layout.buildDirectory.dir("generated/production-res"))
}
android.sourceSets.getByName("main").res.directories.add("build/generated/production-res")
tasks.named("preBuild").configure { dependsOn(productionImages) }
