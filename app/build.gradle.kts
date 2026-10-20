plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.pocketccd.prototype"

    // AGP 9.x DSL: release(37) is the compile SDK for this prototype.
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.pocketccd.prototype"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }

        /**
         * 性能基准用的构建类型。
         *
         * 为什么必须有它：`debug` 包是 debuggable 的，ART 对这类进程不做完整优化，
         * 实测同一段 sRGB 编码循环在 debug 下是 2.5 秒、在非 debuggable 下差一个量级
         * 以上。拿 debug 的耗时当性能结论会得出完全错误的判断（曾经就这么误判过，
         * 以为编码是瓶颈，其实有一半是 debuggable 带来的）。`cmd package compile -m speed`
         * 也救不了 —— debuggable 进程的优化限制不在 AOT 上。
         *
         * 用 debug 签名、不混淆，保证能直接安装、且测的就是仓库里这份代码。
         * 不加 applicationIdSuffix，这样 pack 目录和 [FilmSelfTestActivity] 的
         * 启动组件名都不变，测完不用重推 pack。
         */
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = false
            isMinifyEnabled = false
            matchingFallbacks += listOf("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    sourceSets.getByName("main") {
        // Fotufilm 的 .so 由 CI 交叉编译产出，不进仓库（见 .gitignore 的 prebuilt/）。
        // 目录不存在时 AGP 只是不带入原生库、构建照常成功 —— 这样干净克隆也能编过，
        // 代价是运行时 nativeAvailable() 为 false，胶片功能自行降级。
        jniLibs.directories.add(
            rootProject.layout.projectDirectory.dir("prebuilt/fotufilm/lib").asFile.absolutePath
        )

        // pack 也进 APK（assets 根目录下的 *.fswp），首次启动复制到
        // `Android/data/<pkg>/files/film/`。
        //
        // 为什么必须进 APK：此前 pack 只能 `adb push`，别人装上这个包会在胶卷库里看到
        // 0 卷、快门永久置灰 —— 等于装了个打不开的相机。assets 是让"装完就能拍"
        // 成立的唯一自包含方式，且运行期仍以 files/film 为准，用户/开发者可以覆盖。
        //
        // 许可：pack 是 film profile 的改编，按 CC BY-SA 4.0 分发，需署名 Fotufilm /
        // MUAStudio Inc. 并声明是否修改（见 docs/fotufilm-license-inventory.md §2）。
        assets.srcDir(
            rootProject.layout.projectDirectory.dir("prebuilt/fotufilm/packs").asFile.absolutePath
        )
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)

    // FOT-007 相机接入：Preview + ImageCapture 两个用例就够，不引 video/extensions。
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
