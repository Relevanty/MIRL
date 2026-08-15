# SleepCycleLucidAlarm

Android-приложение для планирования сна, точных будильников, звуковых подсказок и напоминаний. Интерфейс доступен на русском и английском языках.

## Требования

- Android Studio с JDK 17
- Android SDK 35
- Android 8.0 (API 26) или новее

## Сборка

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleRelease
```

Готовые APK создаются в `app/build/outputs/apk/release/` отдельно для `arm64-v8a` и `armeabi-v7a`.

Release-сборка для локального тестирования подписывается стандартным debug-сертификатом Android. Перед публикацией в магазине необходимо настроить собственный закрытый release-keystore.
