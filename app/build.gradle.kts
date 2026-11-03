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
        // 2026-10-23 夜间包：相册查看器/收藏、成片 EXIF、权限自愈、图标与无障碍。
        // 升 versionCode 是为了覆盖安装能顺利升级（同一 debug 签名）。
        // 0.4.1：消除窗口与底盖之间的死黑（窗→工具条→底盖连续），变焦/EV 收进工具条，快门/按键收小。
        // 显影页重做、开源声明页重排。
        // 0.5.0（D-063）：视觉方向 C「白纸档案」——取景页黑闸门/纸白刻字，
        // 成片/照片墙/胶卷库/设置/声明改相纸；相册查看器黑底灯箱铺满系统栏。
        // 0.6.0（D-064）：B 方案 1:1 版面——底部四 Tab（相册/胶卷/发现/设置）、
        // 成片页 1:1、照片墙 Photo Wall、胶卷库 Film Library 黑色 CURRENT 卡、
        // 设置四分类手风琴、发现页真实统计脚手架。新功能回调待其他智能体接入。
        // 0.6.1：真机反馈修正——TabBar 误用暗色令牌导致黑底；返回统一回相机；
        // 成片页印相放大到 92%/8dp 纸边、ISO 行；设置页版本信息改左对齐三行。
        // 0.6.2：真机第二轮——成片页纸底铺到状态栏；帧号改连续序号；发现图标；
        // 横屏锁竖屏；保存/收藏/分享/更多接真实行为；换卷加确认；发现页文案落地。
        // 0.7.0（D-069..D-071）：整套 UI 换成玻璃拟态 + Material You（`ui/film/` 整包删除）；
        // 去掉"一卷 36 张"卷模型；内置胶卷从 3 支扩到 8 支（+ektar100 / acros100 /
        // provia100f / velvia50 / cinestill800t）；反转片补 SDR 肩部（D-068）。
        // 0.8.0（D-074，真机反馈第一轮）：Material Symbols 图标修正（viewBox 负原点导致
        // 整批图标不可见）、去掉跟随壁纸取色、拍摄页顶栏/快门/EV 面板重排、成像链路
        // （JPEG 95、旋转不滤波、stripRows 256）、屏幕常亮与快门触感。
        // 0.8.1（D-077）：查看器盖住 TabBar（模态层级）、拍摄页宽屏上限 560dp、
        // 无相机会话时不画变焦轨、设置页加诊断入口。
        // 0.8.2（D-079）：拍摄页 dock 上滑打开"去哪儿"抽屉（相册/胶卷/发现/设置），
        // 相机页终于能直达发现页。
        // 0.8.3（D-080）：取景框重做 —— 3:4 带边框的取景窗（不再是挨边全屏），
        // 预览流固定 4:3 以精确铺满窗口。
        // 0.8.4（D-081）：片边（135 半格胶片条）与日期后背（盖章），设置页可开关。
        // 0.8.5（D-082）：片边/日期改成曝光合成 —— 5×7 点阵掩膜 + 晕光，片基噪点、
        // 齿孔光渗与亚毫米抖动、印字墨扩散，不再是一眼假的矢量字。
        // 0.8.6（D-083）：拍摄页强制横屏（sensorLandscape），其它页竖屏；横版改成
        // 左右两侧控件的相机式排版、取景窗 4:3；横拍落盘即横版，片边 1:1 不缩。
        // 0.8.7（D-084）：齿孔改到画布长边 —— 胶片方向跟照片走，画门按 135 的 24mm 高度、
        // 照片 1:1 贴进画门，画面占片宽 69%。
        // versionCode 必须递增：同一 debug 签名覆盖安装时，相同 versionCode 在部分 ROM 上会被拒。
        versionCode = 21
        versionName = "0.8.8"
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

    testOptions {
        unitTests {
            // Robolectric 要读真实资源（drawable/strings），否则截图测试只能看到黑块。
            isIncludeAndroidResources = true
        }
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

    // 玻璃组件：捕获背后图层 → RenderEffect 模糊 → AGSL 折射/边缘高光。
    // API 31 以下自动退化（库内部按 SDK_INT 判 capability），不需要额外分支。
    implementation(libs.kyant.backdrop)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    // 布局回归：Robolectric + Compose 截图测试（`./gradlew :app:testDebugUnitTest -Pscreenshot`）。
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
