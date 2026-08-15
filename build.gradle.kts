// build.gradle.kts
// Корневой Gradle-файл.
// Здесь только объявляем плагины, но не применяем их к корневому проекту.

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}