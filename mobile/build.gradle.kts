import java.security.KeyFactory
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.interfaces.ECPublicKey
import java.security.spec.PKCS8EncodedKeySpec

plugins {
    alias(libs.plugins.android.application)
}

// Standalone authentication is always an explicit, local-only build input. Never fall back to a
// repository directory: a release must not silently include a stale or unintended identity.
val localAuthenticationAssets = providers.environmentVariable("DIPLAY_AUTH_ASSETS_DIR")
    .orNull?.let { file(it).canonicalFile }
val androidKeystorePath = providers.environmentVariable("ANDROID_KEYSTORE_PATH").orNull
val androidKeystorePassword = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD").orNull
val androidKeyAlias = providers.environmentVariable("ANDROID_KEY_ALIAS").orNull
val androidKeyPassword = providers.environmentVariable("ANDROID_KEY_PASSWORD").orNull
val androidKeystoreFile = androidKeystorePath?.let { file(it).canonicalFile }
val externalSigningValues = listOf(
    androidKeystorePath,
    androidKeystorePassword,
    androidKeyAlias,
    androidKeyPassword,
)
val hasExternalSigning = externalSigningValues.all { !it.isNullOrBlank() }
check(externalSigningValues.all { it.isNullOrBlank() } || hasExternalSigning) {
    "Android signing configuration is incomplete"
}

android {
    namespace = "com.shilapi.xcertplay"
    // Keep this in sync with the reproducible GitHub Actions SDK. This affects
    // compilation only; minSdk below remains the Android 4.2/API17 baseline.
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.shihab.diplay"
        // CS55's head unit runs Android 4.2.2 (API 17). Keep the app and every
        // dependency on the same install baseline; runtime-only features remain
        // guarded in the compatibility layer.
        minSdk = 17
        // API 36 is the newest stable SDK package available to the reproducible
        // GitHub build.  This does not change the API17 runtime baseline.
        targetSdk = 36
        multiDexEnabled = true
        versionCode = 35
        versionName = "0.2.14-legacy.api17.1"

    }


    localAuthenticationAssets?.let { sourceSets.getByName("main").assets.srcDir(it) }

    signingConfigs {
        create("release") {
            storeFile = androidKeystoreFile ?: file("missing-release-keystore.jks")
            storePassword = androidKeystorePassword ?: ""
            keyAlias = androidKeyAlias ?: ""
            keyPassword = androidKeyPassword ?: ""
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".hudtest"
            versionNameSuffix = "-hud-test"
            if (hasExternalSigning) signingConfig = signingConfigs.getByName("release")
        }
        release {
            optimization {
                enable = false
            }
            signingConfig = if (signingConfigs.getByName("release").storeFile?.isFile == true) { signingConfigs.getByName("release") } else { signingConfigs.getByName("debug") }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(project(":common"))
    implementation(project(":shared"))
    implementation("androidx.multidex:multidex:2.0.1")
}

// No implicit import. Only the two explicitly selected local runtime assets are allowed.
val credentialAssets = files(android.sourceSets.flatMap { source ->
    source.assets.directories.map { directory ->
        fileTree(directory) {
            include("**/offline-mfi/**", "**/*.pk8", "**/*.p7b", "**/*.key",
                "**/*.pem", "**/*.p12", "**/*.pfx", "**/*.jks", "**/*.keystore")
        }
    }
})
val rejectBundledCredentials by tasks.registering {
    group = "verification"
    description = "Reject unexpected credential files in APK assets."
    val filesToCheck = credentialAssets
    val allowed = localAuthenticationAssets?.let { dir ->
        listOf("identity.pk8", "certificate.p7b").map { dir.resolve("offline-mfi/$it").canonicalFile }.toSet()
    } ?: emptySet()
    inputs.files(filesToCheck)
    doLast {
        check(allowed.all { it.isFile }) { "Explicit local authentication assets are incomplete" }
        val unexpected = filesToCheck.files.filter { it.canonicalFile !in allowed }
        check(unexpected.isEmpty()) { "Unexpected credential files in APK assets" }
    }
}
tasks.named("preBuild") { dependsOn(rejectBundledCredentials) }

// Car-test packages must be standalone. Keep ordinary source/CI builds identity-free.
val verifyStandaloneAuthentication by tasks.registering {
    group = "verification"
    description = "Require the explicit runtime authentication input for a standalone car-test APK."
    val directory = localAuthenticationAssets
    doLast {
        check(directory != null) {
            "Standalone car builds require DIPLAY_AUTH_ASSETS_DIR; assembleDebug alone is source-only."
        }
        val identityFile = directory.resolve("offline-mfi/identity.pk8")
        val certificateFile = directory.resolve("offline-mfi/certificate.p7b")
        check(listOf(identityFile, certificateFile).all { it.isFile && it.length() in 1..16_384 }) {
            "Standalone CarPlay authentication files are missing, empty, or too large"
        }
        val encodedKey = identityFile.readBytes()
        val privateKey = try {
            KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(encodedKey))
        } finally {
            encodedKey.fill(0)
        }
        val certificates = certificateFile.inputStream().use { input ->
            CertificateFactory.getInstance("X.509").generateCertificates(input)
        }
        check(certificates.size == 1) { "Expected exactly one accessory certificate" }
        val publicKey = certificates.single().publicKey as? ECPublicKey
            ?: error("Expected an EC accessory certificate")
        check(publicKey.params.order.toString(16) ==
            "ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551") {
            "Expected a P-256 accessory certificate"
        }
        val challenge = ByteArray(32) { index -> index.toByte() }
        val signer = Signature.getInstance("NONEwithECDSA").apply {
            initSign(privateKey)
            update(challenge)
        }
        val verifier = Signature.getInstance("NONEwithECDSA").apply {
            initVerify(publicKey)
            update(challenge)
        }
        check(verifier.verify(signer.sign())) {
            "Standalone CarPlay private key does not match its certificate"
        }
    }
}
val verifyStandaloneSigning by tasks.registering {
    group = "verification"
    notCompatibleWithConfigurationCache("Reads protected signing inputs supplied only for this invocation")
    doLast {
        check(hasExternalSigning) {
            "Standalone builds require external Android signing variables"
        }
        check(androidKeystoreFile?.isFile == true) { "Standalone Android signing keystore is missing" }
    }
}
tasks.named("preBuild") { mustRunAfter(verifyStandaloneAuthentication, verifyStandaloneSigning) }
tasks.register("assembleStandaloneDebug") {
    group = "build"
    description = "Build a standalone car-test APK with explicitly provisioned authentication."
    dependsOn(verifyStandaloneAuthentication, verifyStandaloneSigning, "assembleDebug")
}

tasks.register("assembleStandaloneRelease") {
    group = "build"
    description = "Build a signed standalone APK with explicitly provisioned authentication."
    dependsOn(verifyStandaloneAuthentication, verifyStandaloneSigning, "assembleRelease")
}
