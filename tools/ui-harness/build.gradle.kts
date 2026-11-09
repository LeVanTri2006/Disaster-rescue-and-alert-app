plugins {
    id("com.android.application") version "9.4.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10"
}

// Standalone test APK: never applies google-services or includes the production app.
val appSource = file("../../app/src")
val tokenSource = file("../../app/src/main/java/com/example/bai1/sos/network/SosApiClient.kt")
val generateTokenInterface = tasks.register("generateTokenInterface") {
    inputs.file(tokenSource)
    val output = layout.buildDirectory.dir("generated/token-interface")
    outputs.dir(output)
    doLast {
        val declaration = Regex("interface AccessTokenProvider \\{[^}]+}").find(tokenSource.readText())
            ?: error("Production token interface changed: audit isolation again")
        check(declaration.value.trim() == "interface AccessTokenProvider {\n    fun getAccessToken(): String?\n}")
        output.get().file("com/example/bai1/sos/network/AccessTokenProvider.kt").asFile.apply {
            parentFile.mkdirs()
            writeText("package com.example.bai1.sos.network\n\n" + declaration.value + "\n")
        }
    }
}
android {
    namespace = "com.example.bai1"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.example.bai1.p5e02.uiisolated"
        minSdk = 30
        targetSdk = 37
        versionCode = 1
        versionName = "p5e02"
        testInstrumentationRunner = "com.example.bai1.fixture.IsolatedInstrumentationRunner"
    }
    buildFeatures { buildConfig = true; compose = true }
    buildTypes {
        create("releaseLike") {
            initWith(getByName("release"))
            isMinifyEnabled = false
            isDebuggable = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    testBuildType = providers.gradleProperty("policyVariant").getOrElse("debug")
    sourceSets {
        getByName("main") {
            listOf("algorithm", "model", "network", "download", "parser", "loader", "repository").forEach {
                kotlin.directories.add(File(appSource, "main/java/com/example/bai1/rescue/routing/$it").path)
            }
            kotlin.directories.add(layout.buildDirectory.dir("generated/token-interface").get().asFile.path)
            kotlin.directories.add(layout.buildDirectory.dir("generated/production-ui").get().asFile.path)
            res.directories.add("build/generated/production-res")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11 }
}
tasks.named("preBuild").configure { dependsOn(generateTokenInterface) }
// Use original policy files directly; no mirrored policy that can drift.
android.sourceSets.getByName("main").res.directories.add("build/generated/policy/main")
android.sourceSets.getByName("debug").res.directories.add("build/generated/policy/debug")
val preparePolicy = tasks.register<Copy>("preparePolicy") {
    from(File(appSource, "main/res/xml/routing_network_security_config.xml")) { into("main/xml") }
    from(File(appSource, "debug/res/xml/routing_network_security_config.xml")) { into("debug/xml") }
    into(layout.buildDirectory.dir("generated/policy"))
}
tasks.named("preBuild").configure { dependsOn(preparePolicy) }
dependencies {
    implementation("com.squareup.okio:okio:3.9.1")
    implementation("com.google.guava:guava:31.1-android")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.2.10")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk7:2.2.10")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.annotation:annotation-experimental:1.4.1")
    androidTestImplementation("com.google.guava:guava:31.1-android")
    androidTestImplementation("com.google.code.findbugs:jsr305:3.0.2")
}
// Existing AndroidX Test 1.5 references old lifecycle-common; use the cached JVM variant
// already present in the application graph, without downloading obsolete artifacts.
configurations.configureEach {
    resolutionStrategy.dependencySubstitution {
        substitute(module("androidx.lifecycle:lifecycle-common"))
            .using(module("androidx.lifecycle:lifecycle-common-jvm:2.11.0"))
    }
}

// Exact source copies, including cloud-free Login UI and injected production Auth lifecycle; no app DI/cloud SDK.
val prepareProductionUi = tasks.register<Copy>("prepareProductionUi") {
    from(File(appSource, "main/java")) {
        include("com/example/bai1/ui/theme/*.kt")
        include("com/example/bai1/presentation/auth/RegisterScreen.kt")
        include("com/example/bai1/presentation/auth/OnboardingScreen.kt")
        include("com/example/bai1/presentation/settings/AboutAppScreen.kt")
        include("com/example/bai1/presentation/components/CommonButtons.kt")
        include("com/example/bai1/presentation/components/AppTopBar.kt")
        include("com/example/bai1/presentation/components/FeedbackStates.kt")
        include("com/example/bai1/data/model/Models.kt")
        include("com/example/bai1/auth/network/AuthApiClient.kt")
        include("com/example/bai1/auth/session/AuthModels.kt")
        include("com/example/bai1/presentation/auth/LoginScreen.kt")
        include("com/example/bai1/presentation/auth/RegistrationValidation.kt")
        include("com/example/bai1/auth/AuthRepository.kt")
        include("com/example/bai1/auth/AuthViewModel.kt")
        include("com/example/bai1/auth/session/TokenStorage.kt")
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
    }
    into(layout.buildDirectory.dir("generated/production-ui"))
}
val prepareProductionImages = tasks.register<Copy>("prepareProductionImages") {
    from(File(appSource, "main/res")) { include("drawable/chonvaitro.*", "drawable/avatar.*", "drawable/news_storm.*", "drawable/news_flood.*", "drawable/splash_bg.*", "drawable/onboarding*.png", "drawable-nodpi/onboarding*.png", "drawable/icondangnhap.png", "drawable/google.webp", "drawable/facebook_logo.png", "drawable/zalo_logo.jpg") }
    into(layout.buildDirectory.dir("generated/production-res"))
}
tasks.named("preBuild").configure { dependsOn(prepareProductionUi, prepareProductionImages) }
dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.02.01"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material3:material3-android:1.4.0")
    implementation("androidx.compose.material:material-icons-extended-android:1.7.8")
    implementation("androidx.compose.ui:ui-android:1.11.2")
    implementation("androidx.navigation:navigation-compose-android:2.10.1")
}
dependencies {
}

// Execute the exact deterministic production-repository tests on the physical runtime too.
val prepareLifecycleTests = tasks.register<Copy>("prepareLifecycleTests") {
    from(File(appSource, "test/java")) { include("com/example/bai1/auth/AuthLifecycleSafetyTest.kt") }
    into(layout.buildDirectory.dir("generated/lifecycle-tests"))
}
android.sourceSets.getByName("androidTest").kotlin.directories.add(layout.buildDirectory.dir("generated/lifecycle-tests").get().asFile.path)
tasks.named("preBuild").configure { dependsOn(prepareLifecycleTests) }
