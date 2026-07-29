plugins {
    alias(libs.plugins.agp.lib)
}

android {
    namespace = "io.github.libxposed.api"
    compileSdk {
        version = release(37) {
            minorApiLevel = 0
        }
    }

    sourceSets {
        named("main") {
            setRoot("api/api/src/main")
        }
    }

    buildFeatures {
        buildConfig = false
    }

    compileOptions {
        targetCompatibility = JavaVersion.VERSION_17
        sourceCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    compileOnly(libs.androidx.annotation)
    compileOnly(libs.libxposed.annotation)
    lintPublish(libs.libxposed.lint)
}