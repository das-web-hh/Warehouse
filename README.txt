WAREHOUSE  — нативная Android-обёртка
===================================================

Что это
-------
Маленькое настоящее Android-приложение (не PWA, не TWA). Оно:
- открывает ваш сайт (warehouse.html) в обычном WebView;
- само, средствами Android (без участия Chrome/Web Share Target),
  ловит системное "Поделиться" для PDF, картинок, Excel и CSV;
- читает файл напрямую через ContentResolver и передаёт его в
  JS-код страницы через window.receiveNativeSharedFiles(...).

Так баг Chrome (Verify caller URI permissions before Web Share
Target in TWA, Chrome 153) вообще не участвует в цепочке — файл
никогда не идёт через Web Share Target.

Перед сборкой
--------------
1. В файле app/src/main/java/com/warehouse/sharebridge/MainActivity.kt
   проверьте строку:
       private val siteUrl = "https://as-web-hh.github.io/warehouse.html"
   и поправьте её на точный адрес вашего сайта, если он другой.

2. В warehouse.html должен быть добавлен блок с
   window.receiveNativeSharedFiles — он уже добавлен в файле
   warehouse.html, который я прислал вместе с этим архивом.
   Загрузите этот обновлённый warehouse.html на ваш хостинг
   (GitHub Pages) — без него нативное приложение не сможет
   передать файл в JS.

Как собрать APK (через Android Studio, бесплатно)
--------------------------------------------------
1. Скачайте и установите Android Studio: https://developer.android.com/studio
   (ставится на компьютер — Windows/Mac/Linux; на телефоне через
   Termux такой проект собрать практически нереально, слишком
   тяжёлые инструменты).
2. Откройте Android Studio → "Open" → выберите папку
   WarehouseShareBridge (эту, целиком).
3. Дождитесь автоматической синхронизации Gradle (может попросить
   доустановить Android SDK/Build Tools — соглашайтесь, это
   стандартный процесс).
4. Меню Build → Build Bundle(s) / APK(s) → Build APK(s).
5. Готовый файл появится в
   app/build/outputs/apk/debug/app-debug.apk — это и есть
   ваш APK, его можно перенести на телефон и установить (может
   потребоваться разрешить установку "из неизвестных источников").

Значок приложения
------------------
Сейчас используется ваша иконка icon-v2-512.png как есть (без
адаптивных слоёв). Это временно, но рабочий вариант для первой
сборки; при желании потом сделаем через Image Asset Studio в
Android Studio (правый клик на res → New → Image Asset).

Если что-то не собирается
--------------------------
Android Studio обычно сама подсказывает, что поправить (например,
предложит обновить версию Gradle/AGP при первом открытии проекта —
соглашайтесь на её собственные предложения). Если появится
конкретная ошибка сборки — пришлите её текст, разберём.
