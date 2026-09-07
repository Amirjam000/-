# MeshConnect (مسنجر مش هیبریدی: آفلاین P2P + آنلاین اینترنت)

یک اپلیکیشن ارتباطی پیشرفته اندروید به زبان **Kotlin** و با رابط کاربری مدرن **Jetpack Compose** که امکان پیام‌رسانی، تماس تصویری، بیسیم صوتی و ارسال فایل را در هر دو محیط آفلاین و آنلاین فراهم می‌کند.

---

## 🌟 قابلیتهای کلیدی

### ۱. شبکه محلی مش و رله چندجهشه (Multi-hop Mesh Relay)
- **عدم نیاز به اینترنت یا آنتن:** ارتباط مستقیم بین دستگاه‌ها با **Google Nearby Connections API** (`Strategy.P2P_CLUSTER`).
- **رله پیام (Mesh Store & Forward):** در صورتی که مقصد نهایی در برد مستقیم بلوتوث/وای‌فای نباشد، پیام‌ها از طریق گوشی‌های واسط (Peer ها) دست‌به‌دست شده تا به مقصد برسند.
- **جلوگیری از لوپ و طوفان برودکست:** استفاده از شناسه یکتای پیام (UUID)، کش LRU برای نادیده گرفتن پیام‌های تکراری و شمارنده طول عمر بسته (**TTL**).

### ۲. بیسیم صوتی (Push-to-Talk)
- دکمه نگهدار و صحبت کن (واکی‌تاکی) با نرخ نمونه‌برداری **16kHz PCM 16-bit** برای بهترین وضوح و مصرف بهینه پهنای باند.
- ارسال به صورت **STREAM Payload** روی Nearby Connections و بازپخش مستقیم با **AudioTrack** با حداقل تاخیر.

### ۳. تماس تصویری مدرن با WebRTC
- پیاده‌سازی رسمی با **WebRTC Android SDK**.
- شتاب‌دهنده سخت‌افزاری انکودینگ تصویر (H.264 / VP8).
- سرورهای **STUN گوگل** برای عبور خودکار از NAT در تماس‌های اینترنتی.
- پیش‌نمایش تصویر در تصویر (**Picture-in-Picture**) دوربین کاربر به همراه ویدیوی تمام‌صفحه طرف مقابل.
- کلیدهای قطع/وصل میکروفون، دوربین، تعویض دوربین جلو/عقب و قطع تماس.
- قابلیت تبادل سیگنالینگ هم از طریق شبکه محلی آفلاین مش و هم از طریق فایربیس اینترنتی.

### ۴. سوئیچ هوشمند و خودکار (Auto-Routing)
- اپلیکیشن ابتدا وجود مخاطب را در کلاستر بلوتوث/وای‌فای محلی بررسی می‌کند. در صورت حضور، پیام‌ها بدون مصرف حتی ۱ کیلوبایت اینترنت منتقل می‌شوند.
- در صورت در دسترس نبودن آفلاین و فعال بودن اینترنت، پیام به صورت خودکار از طریق سرور ابری (Firebase) ارسال می‌شود.

### ۵. ارسال فایل و ویدیو با نوار پیشرفت
- انتخاب فایل با Storage Access Framework.
- ارسال در شبکه محلی با `Payload.Type.FILE` و نمایش دقیق درصد پیشرفت ارسال و دریافت (0% تا 100%).

### ۶. سازگاری فوق‌العاده با گوشی‌های قدیمی و جدید
- حداقل نسخه اندروید: `minSdk 24` (اندروید ۷.۰ نوقا) تا `targetSdk 35` (اندروید ۱۵).
- مدیریت تفکیک‌شده مجوزها:
  - اندروید ۱۲ به بالا: `BLUETOOTH_SCAN`, `BLUETOOTH_ADVERTISE`, `BLUETOOTH_CONNECT`, `NEARBY_WIFI_DEVICES`.
  - اندرویدهای قدیمی‌تر: `ACCESS_FINE_LOCATION`, `BLUETOOTH_ADMIN`.
- قوانین Proguard و R8 کامل برای بهینه‌سازی حجم APK و جلوگیری از کرش‌های ناشی از کدهای Native (JNI) در گوشی‌های قدیمی با رم پایین.

---

## 🛠️ ساختار فایل‌های پروژه

```
MeshConnectApp/
├── app/
│   ├── build.gradle.kts          # وابستگی‌های Nearby, WebRTC, Compose, Firebase
│   ├── proguard-rules.pro        # قوانین Proguard برای WebRTC و Gson
│   └── src/main/
│       ├── AndroidManifest.xml   # تمام دسترسی‌ها و FileProvider
│       └── java/com/meshconnect/app/
│           ├── MainActivity.kt               # ناوبری و هماهنگ‌سازی صفحات
│           ├── model/Models.kt               # مدل بسته‌های مش، چت و WebRTC
│           ├── mesh/MeshRelayEngine.kt       # موتور مش، کش LRU، TTL و فورواردینگ
│           ├── nearby/NearbyManager.kt       # کلاینت P2P_CLUSTER گوگل Nearby
│           ├── audio/AudioStreamManager.kt   # واکی‌تاکی AudioRecord و AudioTrack
│           ├── webrtc/WebRtcManager.kt       # تماس صوتی و تصویری WebRTC
│           ├── signaling/SignalingManager.kt # سیگنالینگ فایربیس و پیام ابری
│           ├── router/ConnectionRouter.kt    # سوئیچ خودکار آفلاین/اینترنت
│           ├── viewmodel/MainViewModel.kt    # مدیریت جامع StateFlow
│           └── ui/
│               ├── components/               # PermissionHandler و TopBar
│               ├── screens/                  # Discovery, ChatList, Chat, VideoCall
│               └── theme/                    # تم دارک و متریال ۳
├── settings.gradle.kts
├── build.gradle.kts
└── gradle.properties
```

---

## 🚀 نحوه اجرای پروژه و ساخت APK

### روش اول: از طریق Android Studio (پیشنهادی)
1. نرم‌افزار **Android Studio** را باز کنید.
2. گزینه **Open** را انتخاب کرده و مسیر این پروژه را انتخاب کنید:
   `C:\Users\Am-PC\.gemini\antigravity\scratch\MeshConnectApp`
3. منتظر بمانید تا فرایند گریدل سینک (Gradle Sync) به پایان برسد.
4. گوشی اندرویدی خود را با کابل USB یا Wi-Fi متصل کنید و دکمه **Run** را بزنید.
5. برای ساخت فایل خروجی APK نهایی:
   - از منوی بالا گزینه **Build > Build Bundle(s) / APK(s) > Build APK(s)** را انتخاب کنید.
   - فایل خروجی APK در مسیر زیر تولید خواهد شد:
     `app/build/outputs/apk/debug/app-debug.apk`

### روش دوم: از طریق خط فرمان (Terminal / Gradle)
در پوشه پروژه دستور زیر را وارد نمایید:
```powershell
.\gradlew assembleDebug
```
یا برای ساخت نسخه ریلیز بهینه‌شده با پروگارد:
```powershell
.\gradlew assembleRelease
```

---

## ⚙️ فعال‌سازی بخش آنلاین (Firebase)
بخش آفلاین برنامه (Nearby Connections، مش رله، واکی‌تاکی صوتی و ارسال فایل) به صورت **۱۰۰٪ مستقل** و بدون نیاز به هیچ کانفیگی کار می‌کند.
برای فعال‌سازی بخش آنلاین (پیام‌های راه دور از طریق اینترنت و سیگنالینگ WebRTC ابری):
1. در [کنسول فایربیس](https://console.firebase.google.com) یک پروژه جدید بسازید.
2. اپلیکیشن اندروید را با پکیج نام `com.meshconnect.app` اضافه کنید.
3. فایل دانلود شده `google-services.json` را در پوشه `app/` قرار دهید.
4. در فایل `app/build.gradle.kts` خط زیر را از حالت غیرفعال خارج کنید:
   `id("com.google.gms.google-services")`
