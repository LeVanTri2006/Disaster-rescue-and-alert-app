plugins { id("com.android.application") version "9.4.0" }

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
        applicationId = "com.example.bai1.p5e01.isolated"
        minSdk = 30
        targetSdk = 37
        versionCode = 1
        versionName = "p5e01"
        testInstrumentationRunner = "com.example.bai1.fixture.IsolatedInstrumentationRunner"
    }
    buildFeatures { buildConfig = true }
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
