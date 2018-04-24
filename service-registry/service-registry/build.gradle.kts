import org.gradle.api.JavaVersion.VERSION_1_8
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  id("com.android.library")
  kotlin("android")
  kotlin("kapt")
}

apply {
  from(rootProject.file("gradle/config-kotlin-sources.gradle"))
}

android {
  compileSdkVersion(deps.android.build.compileSdkVersion)
  buildToolsVersion(deps.android.build.buildToolsVersion)

  defaultConfig {
    minSdkVersion(deps.android.build.minSdkVersion)
    targetSdkVersion(deps.android.build.targetSdkVersion)
  }
  compileOptions {
    setSourceCompatibility(JavaVersion.VERSION_1_8)
    setTargetCompatibility(JavaVersion.VERSION_1_8)
  }
  lintOptions {
    setLintConfig(file("lint.xml"))
    isAbortOnError = true
    check("InlinedApi")
    check("NewApi")
    fatal("NewApi")
    fatal("InlinedApi")
    enable("UnusedResources")
    isCheckReleaseBuilds = true
    textReport = deps.build.ci
    textOutput("stdout")
    htmlReport = !deps.build.ci
    xmlReport = !deps.build.ci
  }
}

tasks.withType<KotlinCompile> {
  kotlinOptions {
    freeCompilerArgs = listOf("-Xjsr305=strict")
  }
}

kapt {
  correctErrorTypes = true
  useBuildCache = true
  mapDiagnosticLocations = true
}

dependencies {
  kapt(kapt(deps.dagger.apt.compiler))
  kapt(kapt(deps.crumb.compiler))
  kapt(project(":service-registry:service-registry-compiler"))

  implementation(project(":service-registry:service-registry-annotations"))
  implementation(deps.kotlin.stdlib.jdk7)

  api(project(":services:hackernews"))
  api(project(":services:reddit"))
  api(project(":services:medium"))
  api(project(":services:producthunt"))
//  api(project(":services:imgur"))
  api(project(":services:slashdot"))
  api(project(":services:designernews"))
  api(project(":services:dribbble"))
  api(project(":services:github"))
}
