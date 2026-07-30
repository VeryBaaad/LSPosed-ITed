plugins {
    alias(libs.plugins.agp.lib)
}

android {
    namespace = "io.github.libxposed.api"

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
    api(libs.libxposed.annotation)
    lintPublish(libs.libxposed.lint)
}