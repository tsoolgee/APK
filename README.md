# Overlay Manager — Android

גרסת אנדרואיד לתוכנת ה-Windows המקורית. שכבות תמונה צפות (כולל GIF מונפש) מעל
כל שאר האפליקציות, עם עוגן+אחוזים למיקום, שקיפות, טינט, נעילה ל-click-through,
ושירות רקע קבוע עם התראה לניהול מהיר.

## תאימות גרסאות
האפליקציה תומכת כעת מ-**Android 4.4 (KitKat, API 19) ומעלה** — כלומר כמעט כל
מכשיר אנדרואיד קיים. הדברים הבאים משתנים אוטומטית לפי גרסת המכשיר:

| תכונה | API 19-22 | API 23-25 | API 26-27 | API 28+ |
|---|---|---|---|---|
| הרשאת overlay | ניתנת אוטומטית בהתקנה | אישור ידני בהגדרות | אישור ידני בהגדרות | אישור ידני בהגדרות |
| סוג חלון | TYPE_PHONE | TYPE_PHONE | TYPE_APPLICATION_OVERLAY | TYPE_APPLICATION_OVERLAY |
| GIF מונפש | ✅ (Movie API ידני) | ✅ (Movie API ידני) | ✅ (Movie API ידני) | ✅ (AnimatedImageDrawable) |
| טינט צבע על GIF | ❌ לא נתמך | ❌ לא נתמך | ❌ לא נתמך | ✅ |
| שירות רקע | startService רגיל | startService רגיל | startForegroundService | startForegroundService + סוג שירות |

**הערה:** תמונות סטטיות (לא GIF) מקבלות תמיכה מלאה בטינט צבע בכל הגרסאות.
המגבלה על טינט נוגעת רק ל-GIF מונפש בגרסאות ישנות מ-Android 9, כי ה-API הישן
(`android.graphics.Movie`) לא מאפשר להחיל ColorFilter על הציור בצורה נקייה.


- אין "tray icon" — יש התראה קבועה (foreground service notification) עם כפתורי
  "הצג/הסתר הכל" ו"כבה".
- אין Ctrl+Alt+H גלובלי — הפעולה המקבילה זמינה מתוך ההתראה.
- הרשאת "הצגה מעל אפליקציות אחרות" (SYSTEM_ALERT_WINDOW) חייבת אישור ידני של
  המשתמש בהגדרות המערכת — אנדרואיד לא מאפשרת לתת אותה אוטומטית.
- click-through מוגבל יותר: כשהשכבה "נעולה" היא לא תופסת מגע כלל (FLAG_NOT_TOUCHABLE),
  בדיוק כמו במקור, אבל כשהיא "פתוחה" לעריכה היא תופסת מגע (לצורך גרירה עתידית).
- תמיכה במסכים מרובים לא מומשה בגרסה הזו (Android תומך בזה טכנית, אבל זה
  משמעותית פחות נפוץ מאשר ב-Windows ודורש טיפול נפרד ב-`DisplayManager`).

## מבנה הפרויקט
```
app/src/main/java/com/overlaymanager/app/
  OverlayLayer.kt       - מודל נתונים לשכבה בודדת + סריאליזציה ל-JSON
  LayerStore.kt          - שמירה/טעינה של רשימת השכבות (מקביל ל-config.ini)
  OverlayService.kt      - השירות שמצייר את כל חלונות ה-overlay בפועל
  MainActivity.kt        - מסך רשימת השכבות + הפעלה/כיבוי + הרשאות
  LayerEditActivity.kt   - טופס עריכת שכבה עם תצוגה מקדימה חיה
  LayerAdapter.kt         - RecyclerView adapter לרשימה
  BootReceiver.kt         - הפעלה מחדש אוטומטית אחרי ריסטארט (אופציונלי)
```

## בנייה - דרך Android Studio (מומלץ)
1. פתחו את התיקייה `OverlayManagerAndroid` כפרויקט קיים ב-Android Studio
   (Hedgehog/2023.1 ומעלה, עם Kotlin 1.9+ ו-AGP 8.5+).
2. ה-Gradle Wrapper כבר כלול בפרויקט (`gradlew`, `gradlew.bat`, `gradle/wrapper/`).
3. Run על מכשיר/אמולטור עם Android 9 (API 28) ומעלה, או Build → Build APK(s).

## בנייה משורת פקודה (מחשב עם Android SDK מותקן)
```
./gradlew assembleDebug
```
ה-APK ייווצר תחת `app/build/outputs/apk/debug/app-debug.apk`.

## בנייה בענן עם GitHub Actions (בלי להתקין כלום במחשב) — מומלץ אם אין לכם Android Studio
בפרויקט כבר כלול הקובץ `.github/workflows/build.yml` שבונה APK אוטומטית.

**שלבים:**
1. צרו ריפו חדש וריק ב-GitHub (למשל `OverlayManagerAndroid`), **בלי** README/gitignore
   אוטומטיים (כדי למנוע התנגשות).
2. בטרמינל, בתוך תיקיית הפרויקט:
   ```
   git init
   git add .
   git commit -m "Initial commit"
   git branch -M main
   git remote add origin https://github.com/<שם-המשתמש-שלכם>/OverlayManagerAndroid.git
   git push -u origin main
   ```
3. עברו ל-GitHub → לשונית **Actions** בריפו שלכם. ה-workflow "Build APK" ירוץ
   אוטומטית תוך כמה דקות (אפשר גם להריץ ידנית עם "Run workflow").
4. כשהוא מסתיים בהצלחה (✔ ירוק), לחצו על ה-run שרץ → גללו למטה ל-**Artifacts** →
   הורידו את `OverlayManager-debug-apk` (קובץ zip שמכיל את ה-APK בפנים).
5. פתחו את ה-zip, תמצאו את `app-debug.apk` — זה קובץ ההתקנה.

## התקנת ה-APK על הטלפון
1. העבירו את `app-debug.apk` לטלפון (וואטסאפ לעצמך, גוגל דרייב, כבל USB וכו').
2. פתחו את הקובץ בטלפון. אם זו הפעם הראשונה שמתקינים APK מחוץ ל-Play Store,
   אנדרואיד יבקש לאשר "התקנה ממקורות לא ידועים" — אשרו רק לאפליקציה שממנה
   פתחתם את הקובץ (למשל "קבצים" או "דרייב").
3. לחצו התקן. בסיום, פתחו את "Overlay Manager".


## שימוש
1. פתחו את האפליקציה, לחצו "הוסף שכבה", תנו שם ובחרו תמונה.
2. קבעו רוחב/גובה, עוגן, מרחק באחוזים, שקיפות וטינט — כל שינוי מוצג מיד על
   המסך אם השירות פעיל.
3. הדליקו את המתג הראשי במסך הבית כדי להתחיל להציג את כל השכבות הפעילות.
   בפעם הראשונה תתבקשו לאשר הרשאת "הצגה מעל אפליקציות אחרות".
4. ההתראה הקבועה מאפשרת "הצג/הסתר הכל" בלחיצה אחת (מקביל ל-Ctrl+Alt+H).
5. "הפעלה אוטומטית עם המכשיר" תדליק את השירות שוב אחרי ריסטארט, בתנאי
   שההרשאה עדיין קיימת.

## הערות טכניות
- GIF מונפש מוצג באמצעות `AnimatedImageDrawable` (API 28+) — חלק מה-SDK
  הרשמי של אנדרואיד, בלי ספריית תמונות חיצונית (Glide/Coil), בדיוק כמו
  שהמקור מסתמך רק על GDI+ מובנה ב-Windows.
- כל שכבה היא `WindowManager.LayoutParams` נפרד מסוג `TYPE_APPLICATION_OVERLAY`
  עם `FLAG_NOT_TOUCHABLE` כשהיא נעולה — המקבילה הישירה ל-
  `WS_EX_LAYERED | WS_EX_TOPMOST | WS_EX_TRANSPARENT` בגרסת Windows.
- ההגדרות נשמרות כ-JSON בזיכרון הפנימי של האפליקציה ונטענות מחדש אוטומטית
  בכל הפעלה של השירות או המסך הראשי.

## מגבלה חשובה
בסביבה שבה נכתב הקוד אין גישה ל-Android SDK / Google Maven, ולכן **לא בוצעה כאן
בנייה מלאה שמוודאת הידור 100%**. הקוד נכתב בקפידה לפי ה-API הרשמי של אנדרואיד,
אבל אחרי push ל-GitHub או פתיחה ב-Android Studio ייתכן שיצוצו שגיאות קומפילציה
קטנות (למשל אי-התאמות גרסאות ספריות) — אם זה קורה, שלחו לי את הודעת השגיאה
המדויקת ואני אתקן.
