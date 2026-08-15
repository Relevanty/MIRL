# app/proguard-rules.pro
# Правила ProGuard/R8 для release-сборки.
# Release-сборка использует minify, поэтому файл должен лежать рядом с build.gradle.kts.

# Компоненты Android сохраняются правилами, которые AGP строит из манифеста,
# а Room/Compose/Coroutines поставляют собственные consumer rules. Ручные
# глобальные -keep здесь намеренно не используются: они мешали R8 удалять
# неиспользуемый код библиотек.
