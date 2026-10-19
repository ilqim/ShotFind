# ShotFind — Android Uygulaması Proje Belgesi

> Bu belge, projeyi yeni bir Claude sohbetinde kaldığı yerden sürdürmek için hazırlandı. Projenin amacını, verilen kararları, teknik ayrıntıları, mevcut ilerlemeyi ve çalışma biçimini içerir.
>
> Son güncelleme: 19 Ekim 2026 (Adım 4 tamamlandı)

---

## 0. Claude için çalışma talimatları

- **Çalışma biçimi:** Kullanıcı Claude ile tarayıcıdan (claude.ai) konuşuyor. Claude kullanıcının bilgisayarına, Android Studio'ya veya telefonuna erişemez. Bu yüzden:
  - Kodu **dosya dosya** ver ve her dosyanın Android Studio'da **tam olarak nereye** konacağını yaz (ör. `app/src/main/java/com/ilkimeren/shotfind/data/ScreenshotRepository.kt`).
  - Bir dosyada değişiklik yapınca dosyanın tamamını değil, değişen kısmı nereye koyacağıyla birlikte göster; dosya karmaşıklaşırsa tamamını yeniden ver.
  - Gradle bağımlılıklarını (`libs.versions.toml` + `app/build.gradle.kts`), `AndroidManifest.xml` eklemelerini ve Android Studio menü adımlarını açıkça yaz. Bağımlılık ekledikten sonra **Sync Now** hatırlat.
  - Kullanıcı derleme hatalarını, Logcat çıktısını ve ekran görüntülerini yapıştıracak; hataları onlardan çözümle.
  - Her adım sonunda bir commit mesajı öner. Yeni dosyaların commit penceresindeki **"Unversioned Files"** grubundan işaretlenmesi gerektiğini hatırlat.
- **Kullanıcı:** İlkim Eren. Bilgisayar Mühendisliği 4. sınıf öğrencisi. Unity/C#, Unreal/C++, Python (YOLO, görüntü işleme) ve Angular deneyimi var. **Kotlin ve Android'de yeni; bu projeden önce hiç Android projesi yapmadı.** Proje hem gerçek bir ihtiyacı çözmek hem Android öğrenmek hem de portfolyoya "yayınlanmış mobil uygulama" eklemek için yapılıyor.
- **Öğretici ol:** Her adımda ne yaptığını ve **neden** yaptığını kısaca açıkla. Yeni bir kavram ilk geçtiğinde bir iki cümleyle anlat; mümkünse C#, Unity veya Angular'daki karşılığıyla benzetme yap.
- **Küçük adımlarla ilerle:** Her adım derlenip telefonda çalışır halde bitsin. Kütüphaneleri ihtiyaç duyulan adımda ekle, hepsini birden değil.
- **Önce çalışan en basit sürüm,** sonra iyileştirme. Erken soyutlamadan kaçın.
- **Dil:** Kullanıcıyla Türkçe konuş. Kod, değişken adları, yorumlar ve commit mesajları İngilizce. Arayüz metinleri şimdilik doğrudan Türkçe yazılıyor; ileride `strings.xml`'e taşınacak.
- **Sürüm uyumluluğu:** Yeni kütüphane eklerken projenin Kotlin sürümüyle (bkz. 3.1) uyumunu kontrol et. Bir kütüphanenin en yeni sürümü daha yeni bir Kotlin standart kütüphanesi çekebilir ve derleme hatası verir (Coil'de yaşandı, bkz. 3.2).
- Emin olmadığın Android API davranışlarını resmi dokümantasyondan doğrula.

---

## 1. Ürün

**Ad:** ShotFind
**Paket adı:** `com.ilkimeren.shotfind` (değiştirilemez)
**GitHub:** `github.com/ilqim/ShotFind` (herkese açık)

### Problem
Kullanıcılar ekran görüntülerini (SS) bir sebeple alıyor ama sonra neden aldıklarını unutuyor. Her SS telefonun Ekran Görüntüleri klasörüne düşüyor; klasör binlerce dosyayla doluyor. Özellikle internette veya bir sitede görülüp kaydedilen bir SS'i sonradan **bulmak** çok zor: binlerce fotoğrafın arasında kayboluyor.

### Çözüm
1. **Bulma (asıl değer):** Cihaz üzerinde OCR ile SS'lerin içindeki yazıyı tanıma ve **tam metin arama**; notlar da aranabilir. Kullanıcı geçmişteki bir SS'i saniyeler içinde bulur.
2. **Not ekleme:** SS alındığı anda (bildirimden) neden alındığını not olarak yazma.
3. **Düzenleme:** SS'leri gerçek klasörlere (galeride ayrı albümler) ayırma, hızlı ayıklama ve temizleme.

---

## 2. Temel teknik gerçekler ve verilen kararlar

1. **SS'in kaydedilmesi engellenemez.** Uygulama sistem SS'i kaydettikten **sonra** devreye girer.
2. **Dosyalar gerçekten taşınır.** MediaStore kaydındaki `RELATIVE_PATH` güncellenir; Samsung Galeri her klasörü ayrı albüm gösterir.
3. **Klasör yapısı (karar verildi):** Etiket klasörleri sistemin SS klasörünün **içinde** alt klasör olarak durur:
   ```
   DCIM/Screenshots/              ← ayıklanmamış SS'ler (Gelen kutusu)
   DCIM/Screenshots/Ders/         ← Galeri'de "Ders" albümü
   DCIM/Screenshots/Alışveriş/    ← Galeri'de "Alışveriş" albümü
   ```
   - Android, görselleri MediaStore üzerinden sadece `DCIM/` veya `Pictures/` altına koymaya izin verir. Depolama köküne (`Screenshots/...`) klasör açmak `MANAGE_EXTERNAL_STORAGE` gerektirir; kullanılmıyor.
   - Kök klasör ayarlardan değiştirilebilir olacak (yine `DCIM/` veya `Pictures/` altında).
   - **Önemli:** Gelen kutusu ve yeni SS tespiti, `RELATIVE_PATH`'in **tam olarak** `DCIM/Screenshots/` (veya stok Android'de `Pictures/Screenshots/`) olmasına bakar; alt klasörler hariç tutulur. Böylece uygulamanın taşıdığı dosyalar tekrar yeni SS sayılmaz.
   - Taşıma özelliği yazılınca ilk iş tek dosyayla deneyip Samsung Galeri'de albümün düzgün göründüğünü (Türkçe karakterli klasör adları dahil) doğrula.
4. **Uygulama sürekli çalışmayacak.** Yeni SS tespiti için WorkManager **content URI trigger** kullanılacak. Kalıcı ön plan servisi ve kalıcı bildirim yok.
5. **AccessibilityService ve `MANAGE_EXTERNAL_STORAGE` kullanılmayacak** (gereksiz, Play politikasında sorunlu).
6. **Medya yönetimi izni (`MANAGE_MEDIA`) kullanılacak:** İzin varsa taşıma/silme onaysız; yoksa toplu onay penceresi (`createWriteRequest` / `createTrashRequest`).
7. **OCR ve arama v1'e alındı** (önceden v2'deydi), çünkü uygulamanın asıl değeri bulmak.

---

## 3. Teknoloji yığını ve ortam

### 3.1 Ortam
| Alan | Değer |
|---|---|
| Bilgisayar | Windows, kullanıcı klasörü `C:\Users\HP\` |
| Proje yolu | `C:\Users\HP\AndroidStudioProjects\ShotFind` |
| Android Studio | 2026.2.1 |
| AGP | 9.4.1 (**Kotlin desteği yerleşik**; `plugins` bloğunda ayrı `kotlin-android` eklentisi yok) |
| Kotlin | Compose eklentisi 2.2.10; derleyici **2.2.x** ve en fazla **2.3** metadata'lı kütüphaneleri okuyabiliyor |
| compileSdk / targetSdk | 37 |
| minSdk | 30 (Android 11) |
| Java | 11 (`sourceCompatibility` / `targetCompatibility`) |
| Build dili | Kotlin DSL + version catalog (`gradle/libs.versions.toml`) |
| Test cihazı | **Samsung Galaxy S20 FE (SM-G780G), One UI 5.0, Android 13 (API 33)**, USB hata ayıklama açık |
| Diğer cihaz | Android tablet (model/sürüm henüz sorulmadı) |

### 3.2 Kütüphaneler (şu ana kadar eklenenler)
| Kütüphane | Sürüm | Not |
|---|---|---|
| Compose BOM | 2026.02.01 | şablondan |
| Material 3 | BOM'dan | |
| Navigation Compose | 2.9.8 | Şimdilik **string rotalar** (`"inbox"`, `"search"`, `"settings"`); type-safe rotalar serialization eklentisi gerektirdiği için ertelendi |
| Material Icons Extended | 1.7.8 | sürüm açıkça yazıldı |
| Lifecycle (runtime-ktx, viewmodel-compose, runtime-compose) | 2.11.0 | hepsi `lifecycleRuntimeKtx` sürümünü paylaşıyor |
| Coil 3 (`io.coil-kt.coil3:coil-compose`) | **3.4.0** | 3.6.1 Kotlin 2.4 stdlib çektiği için derleme hatası verdi; 3.4.0'da sabitlendi. Kotlin yükseltilirse güncellenebilir |

### 3.3 Planlanan yığın
| Alan | Seçim |
|---|---|
| Mimari | MVVM, tek Activity, Compose Navigation |
| Asenkron | Coroutines + Flow |
| ViewModel oluşturma | Şimdilik `AndroidViewModel` + `viewModel()` (DI kütüphanesi yok); büyürse Hilt |
| Yerel veritabanı | Room + FTS4 (OCR ve not araması). **KSP gerekiyor; Kotlin 2.2 + AGP 9 yerleşik Kotlin ile uyumlu sürümü önceden doğrula** |
| Ayarlar | DataStore (Preferences) |
| Arka plan | WorkManager |
| OCR | ML Kit Text Recognition v2 (cihaz üzerinde, Latin; Türkçe karakterleri kapsar) |
| QR/Barkod (v2) | ML Kit Barcode Scanning |
| EXIF | `androidx.exifinterface` |

**Sürüme bağlı API'ler:** `MANAGE_MEDIA` → API 31+, `READ_MEDIA_IMAGES` ve `POST_NOTIFICATIONS` → API 33+, kısmi fotoğraf erişimi (`READ_MEDIA_VISUAL_USER_SELECTED`) → API 34+ (test cihazı 13 olduğu için bu durum orada oluşmaz ama kodda ele alınmalı).

---

## 4. Mevcut durum (tamamlanan adımlar)

| # | Adım | Commit |
|---|---|---|
| 1 | Proje oluşturuldu (Empty Activity, Compose), telefonda çalıştırıldı | `Initial commit: empty Compose project` |
| 2 | GitHub'a yüklendi (Android Studio > Share Project on GitHub) | — |
| 3a | Marka renkleriyle Material 3 tema (dinamik renk varsayılan kapalı) | `Add brand color theme` |
| 3b-c | Navigation + ikonlar; alt menü (Gelen kutusu / Ara / Ayarlar) | `Add bottom navigation with placeholder screens` |
| 4 | Fotoğraf izni akışı + MediaStore'dan SS'leri Gelen kutusunda ızgara halinde listeleme, kaynak uygulama etiketi | `Show screenshots from MediaStore in inbox grid` |

### 4.1 Dosya yapısı
```
app/src/main/java/com/ilkimeren/shotfind/
├── MainActivity.kt                 // enableEdgeToEdge + ShotFindTheme { ShotFindApp() }
├── data/
│   ├── Screenshot.kt               // data class: id, uri, displayName, dateAddedMillis, sizeBytes, width, height, relativePath, sourceApp
│   ├── ScreenshotNameParser.kt     // object; Samsung dosya adından kaynak uygulama
│   └── ScreenshotRepository.kt     // loadInboxScreenshots(): RELATIVE_PATH IN ("DCIM/Screenshots/", "Pictures/Screenshots/"), DATE_ADDED DESC
├── util/
│   └── Permissions.kt              // imagePermission(), hasImagePermission(), openAppSettings()
└── ui/
    ├── ShotFindApp.kt              // TopLevelDestination enum, Scaffold + NavigationBar + NavHost
    ├── inbox/
    │   ├── InboxScreen.kt          // izin ekranı, yükleniyor/hata/ızgara; LifecycleResumeEffect ile her görünüşte yeniden yükler
    │   └── InboxViewModel.kt       // sealed InboxUiState (Loading/Loaded/Error), StateFlow
    ├── search/SearchScreen.kt      // yer tutucu
    ├── settings/SettingsScreen.kt  // yer tutucu
    └── theme/                      // Color.kt, Theme.kt (ShotFindTheme), Type.kt
```

### 4.2 Manifest'teki izinler
```xml
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" android:maxSdkVersion="32" />
```

### 4.3 Tema
- Marka renkleri: koyu lacivert `#0A1430` (`Navy`), vurgu sarısı `#FFC94A` (`Sun`) — kullanıcının sitesi ilqim.github.io ile uyumlu.
- Açık tema: `primary` lacivert, `secondaryContainer` sarı. Koyu tema: arka plan lacivert, `primary` sarı.
- Dinamik renk parametresi var ama varsayılan `false`; ileride ayara bağlanabilir.

### 4.4 Yaşanan sorunlar ve dersler
- Coil 3.6.1 → "compiled with an incompatible version of Kotlin, metadata 2.4.0" hatası. Çözüm: Coil 3.4.0. Yeni kütüphane eklerken Kotlin uyumuna dikkat.
- Navigation eklendikten sonra editörde `ComposableFunction0` gibi hayalet hatalar göründü ama derleme başarılıydı. Çözüm: Sync, gerekirse File > Invalidate Caches.
- Android Studio'da kırmızı dosya adı = Git'e eklenmemiş dosya (hata değil).

---

## 5. İzinler

| İzin | Neden | Durum |
|---|---|---|
| `READ_MEDIA_IMAGES` (33+) / `READ_EXTERNAL_STORAGE` (≤32) | SS'leri okumak | ✅ Eklendi. Reddedilirse "Ayarlardan aç" düğmesi |
| `MANAGE_MEDIA` (31+) | Onaysız taşıma/silme | Planlandı. `Settings.ACTION_REQUEST_MANAGE_MEDIA` ile ayar sayfası; kontrol `MediaStore.canManageMedia(context)` |
| `POST_NOTIFICATIONS` (33+) | SS bildirimi, hatırlatıcılar | Planlandı |
| Pil optimizasyonu (isteğe bağlı) | Samsung gecikmesini azaltmak | `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` sayfasına yönlendir |

Android 14+ kısmi erişimde yeni SS'ler görünmez; tespit edip tam erişime yönlendir.

**Onboarding (planlandı):** (1) fotoğraflara tam erişim → (2) bildirimler → (3) medya yönetimi (önerilir) → (4) pil ayarı (isteğe bağlı). Her adım atlanabilir.

---

## 6. SS tespiti ve dosya adları

### 6.1 Bir görselin SS olduğunu anlama
- `RELATIVE_PATH` tam olarak `DCIM/Screenshots/` veya `Pictures/Screenshots/` (alt klasörler hariç).
- Kullanıcının telefonunda SS'ler `Dahili depolama/DCIM/Screenshots/` içinde, **JPEG** olarak kaydediliyor.

### 6.2 Dosya adı formatı (kullanıcının telefonunda doğrulandı)
```
Screenshot_20261018-005226_Instagram.jpg
Screenshot_20261017-184648_Chrome.jpg
           └─tarih─┘└saat┘ └kaynak┘
```
- Tarih ile saat arasında **tire** var (alt çizgi değil).
- Ayrıştırma regex'i: `^Screenshot_\d{8}-\d{6}_(.+)\.(jpe?g|png)$` (büyük/küçük harf duyarsız).
- Henüz bakılmadı: ana ekran / kilit ekranında alınan SS'in adının sonunda ne yazdığı. Uç durum testi için kullanıcıdan istenecek.

### 6.3 Yeni SS tespiti (planlandı)
- WorkManager `OneTimeWorkRequest` + `addContentUriTrigger(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true)`, kısa `setTriggerContentUpdateDelay` / `setTriggerContentMaxDelay`.
- Tetikleyici tek kullanımlık; worker işini bitirince kendini `enqueueUniqueWork` + `ExistingWorkPolicy.REPLACE` ile yeniden kurar.
- Worker: DataStore'daki "son işlenen" değerinden sonraki SS'leri bulur → veritabanına `INBOX` olarak ekler → kural uygular (eşleşir ve izin varsa taşır, "Geri al" bildirimi) → yoksa SS bildirimi gösterir → "son işlenen"i günceller → tetikleyiciyi yeniden kurar.
- **Güvenlik ağı:** Uygulama her açıldığında aynı tarama tekrar çalışır.
- Bilinen sınırlamalar: birkaç saniye–bir dakika gecikme, Samsung pil optimizasyonu, zorla durdurmada tetikleyicinin çalışmaması.

---

## 7. OCR ve arama (v1'in çekirdeği)

- **Room veritabanı** SS kayıtlarını, notları ve OCR metnini tutar.
- **`OcrWorker`:** OCR yapılmamış SS'leri sırayla tarar (ML Kit Text Recognition v2), metni kaydeder. Mevcut binlerce SS için ilk tarama uzun sürer: arka planda, tercihen şarjdayken/boştayken, ilerleme göstergesiyle. Yeni SS'ler tespit edildikçe kuyruğa eklenir.
- **FTS4** tablosu (`note` + `ocrText`) ile tam metin arama. Türkçe karakter ve büyük/küçük harf eşleşmesini test et (ör. "İ/i", "I/ı").
- **Arama ekranı:** Yazdıkça sonuç, eşleşen metin parçasının vurgulanması, etiket ve tarih filtresi.
- Görsel büyükse OCR öncesi makul boyuta küçült (bellek).
- Hiçbir veri cihazdan çıkmaz.

---

## 8. SS bildirimi (planlandı)

- Başlık: "Yeni ekran görüntüsü" + kaynak uygulama. `BigPictureStyle` ile küçültülmüş önizleme. Kendi bildirim kanalı.
- En fazla 3 aksiyon: **Not ekle** (`RemoteInput`, PendingIntent `FLAG_MUTABLE`), **akıllı etiket** (kurala/sıklığa göre), **Sil** (çöp kutusu).
- Gövdeye dokunma: diyalog görünümlü küçük Activity (tüm etiketler, not, Geçici, Sonra hatırlat).
- Aksiyonlar `BroadcastReceiver` → `CoroutineWorker`. `MANAGE_MEDIA` yoksa onay için şeffaf Activity.
- Not Room'a yazılır; ayrıca EXIF'e (`ImageDescription` / `UserComment`) yazılmaya çalışılır (`openFileDescriptor(uri, "rw")` + `ExifInterface` + `saveAttributes()`); başarısızsa sessizce sadece veritabanı.
- Çok sayıda SS aynı anda gelirse gruplanmış bildirim.

---

## 9. Etiketler, klasörler ve kurallar

- **Etiket = gerçek klasör** (`DCIM/Screenshots/<klasör>/`). Her etiketin adı, rengi, ikonu, klasör adı var.
- Varsayılan etiketler (kullanıcı onayladı): **Ders, Alışveriş, Tarif, Sosyal, Belge, Sonra Bak, Geçici.** Kullanıcı ekleyip silebilir, ad/renk değiştirebilir.
- **Taşıma:** `ContentValues` ile `RELATIVE_PATH` güncelle; izin yoksa önce `createWriteRequest` (toplu). Taşıma sonrası ID'nin değişmediğini doğrula. Önceki yolu sakla (**Geri al**).
- **Silme:** `createTrashRequest(resolver, uris, true)` ile çöp kutusu.
- **Kurallar:** Kaynak uygulama (v1), not kelimesi, OCR kelimesi/desen (ör. IBAN → Belge). Öncelik, açık/kapalı, hedef etiket; ilk eşleşen uygulanır.
- **Kural önerisi:** Aynı uygulamadan gelen SS 3 kez aynı etikete konursa "her zaman bunu yapayım mı?" diye sor.

---

## 10. Ekranlar

1. **Onboarding / izinler**
2. **Gelen kutusu:** ✅ ızgara + izin akışı. Sonra: kaydırarak ayıklama (sağ: sakla, sol: çöp, yukarı: etiket, aşağı: geçici), Geri al, izin yoksa oturum sonunda toplu onay.
3. **Arama:** OCR + not tam metin arama, filtreler.
4. **Etiketler / albümler:** Etiket kartları, etiket ızgarası.
5. **SS detay:** Tam ekran, not, etiket, hatırlatıcı, paylaş, sil, tanınan metin ve "metni kopyala", link/QR "Aç".
6. **Hatırlatıcılar**
7. **Ayarlar:** Kök klasör, varsayılan davranışlar, geçici SS süresi (7 gün), kurallar, bildirimler, izin durumu, tetikleyici sağlığı ("son SS ne zaman yakalandı"), dinamik renk.
8. **İstatistik (v3)**

Şu anki alt menü: Gelen kutusu / Ara / Ayarlar. Etiketler sekmesi etiket özelliği gelince eklenecek.

---

## 11. Veri modeli (Room, planlandı)

```kotlin
@Entity(tableName = "screenshots")
data class ScreenshotEntity(
    @PrimaryKey val mediaId: Long,        // MediaStore._ID
    val uri: String,
    val displayName: String,
    val dateAdded: Long,                  // epoch ms
    val sizeBytes: Long,
    val width: Int?, val height: Int?,
    val sourceApp: String?,
    val status: ScreenshotStatus,         // INBOX, SORTED, KEPT, TEMPORARY, TRASHED
    val tagId: Long?,
    val note: String?,
    val noteWrittenToExif: Boolean = false,
    val originalRelativePath: String,
    val currentRelativePath: String,
    val tempExpiresAt: Long?,
    val remindAt: Long?,
    val ocrText: String? = null,
    val ocrDone: Boolean = false,
    val perceptualHash: Long? = null      // v3
)

@Entity(tableName = "tags")
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorArgb: Int,
    val icon: String,
    val folderName: String,               // DCIM/Screenshots/<folderName>/
    val isTemporary: Boolean = false,
    val sortOrder: Int
)

@Entity(tableName = "rules")
data class RuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: RuleType,                   // SOURCE_APP, NOTE_KEYWORD, OCR_KEYWORD, OCR_REGEX
    val pattern: String,
    val targetTagId: Long,
    val enabled: Boolean = true,
    val priority: Int
)

@Entity(tableName = "action_log")
data class ActionLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaId: Long,
    val action: String,                   // MOVE, TRASH, TAG, NOTE
    val fromValue: String?, val toValue: String?,
    val timestamp: Long
)
```
- `note` + `ocrText` için FTS4 tablosu.
- Room'u ilk kurarken sadece arama için gereken alanlarla başla; diğerleri özellik geldikçe migration ile eklenebilir (veya geliştirme aşamasında `fallbackToDestructiveMigration`).
- DataStore: `lastProcessedDateAdded`, `lastProcessedMediaId`, `rootFolder`, `tempDays`, onboarding durumu.

---

## 12. Arka plan işleri (WorkManager)

| İş | Tür | Ne yapar |
|---|---|---|
| `NewScreenshotWorker` | OneTime + content URI trigger, kendini yeniden kurar | Yeni SS'leri bulur, kural uygular, bildirim gösterir, OCR kuyruğuna ekler |
| `OcrWorker` | OneTime/zincir, şarjdayken veya boşta | OCR yapılmamış SS'leri tarar, metni kaydeder, OCR kurallarını uygular |
| `ActionWorker` | OneTime (bildirim aksiyonları) | Taşı, sil, etiketle, not, EXIF |
| `TempCleanupWorker` | Periyodik, günde 1 | Süresi dolan geçici SS'ler için silme onayı bildirimi |
| `ReminderWorker` | OneTime, `remindAt` | "Şu SS'e bakacaktın" bildirimi |

---

## 13. Sürüm planı (güncel)

### v1 — Çalışan çekirdek
1. ✅ Proje iskeleti, tema, alt menü
2. ✅ İzin akışı + MediaStore'dan SS listeleme (Coil)
3. ⏭️ **Room veritabanı** (KSP kurulumu; sürüm uyumunu önceden doğrula)
4. **OCR** (ML Kit, mevcut SS'leri arka planda tarama, ilerleme göstergesi)
5. **Arama ekranı** (FTS4)
6. Not ekleme (veritabanı + EXIF), aranabilir
7. Yeni SS tespiti (içerik tetikleyicisi) + SS bildirimi (önizleme, Not ekle, akıllı etiket, Sil)
8. Etiketler = gerçek klasörler; toplu onaylı taşıma, çöp kutusuna silme
9. Medya yönetimi izni (onaysız taşıma/silme)
10. Gelen kutusunda kaydırarak ayıklama + Geri al
11. Geçici etiket + otomatik temizlik hatırlatması
12. "Sonra bak" hatırlatıcısı
13. Kaynak uygulamaya göre otomatik kurallar
14. Onboarding akışı

### v2 — Akıllı özellikler
- OCR ve not tabanlı kurallar (IBAN → Belge vb.)
- QR kod ve link tanıma, detayda "Aç"
- Kural önerisi

### v3 — Süsler
- Benzer/tekrar eden SS tespiti (dHash + Hamming mesafesi)
- Quick Settings Tile, Glance widget'ı
- İstatistik ekranı
- Paylaş menüsü hedefi
- Tablet düzeni
- İngilizce arayüz (`strings.xml`)

---

## 14. Uç durumlar

- Android 14+ kısmi fotoğraf erişimi.
- `MANAGE_MEDIA` sonradan geri alınırsa: her işlemden önce `canManageMedia` kontrolü.
- Kullanıcı SS'i galeriden silerse/taşırsa: açılışta veritabanını MediaStore ile eşitle.
- Aynı anda çok sayıda SS: gruplanmış bildirim.
- Çok uzun SS (scroll capture): önizleme kırpma, OCR'de bölerek işleme.
- PNG ve JPEG (kullanıcıda JPEG): EXIF yazmayı test et.
- Klasör adlarında Türkçe karakter ve boşluk.
- SD kart (`MediaStore.getExternalVolumeNames`): v1'de dahili depolama.
- Samsung Güvenli Klasör: kapsam dışı.
- Gizlilik: İnternet izni ekleme; her şey cihaz üzerinde. (Not: Coil'in ağ modülü eklenmedi; yerel `content://` görselleri için gerekmez.)

---

## 15. Test

- Asıl test cihazı: Galaxy S20 FE, Android 13 (USB bağlantısı çalışıyor).
- Gerekirse `adb` ile test görselini SS klasörüne kopyalayıp medya taramasını tetikle.
- Birim testleri: `ScreenshotNameParser`, kural motoru, SS klasör eşleşmesi, regex'ler, Türkçe arama normalizasyonu.
- Room DAO testleri.
- Manuel test: uygulama kapalıyken SS al → bildirim; not → kayıt + EXIF; taşı → Samsung Galeri albümü; geri al; izinleri geri al; yeniden başlat → tetikleyici; OCR sonrası bir kelimeyle arama.

---

## 16. Yayın ve portfolyo

- **Google Play:** Geliştirici hesabı tek seferlik ücretli. Yeni kişisel hesaplarda yayından önce kapalı test zorunluluğu var (güncel koşulları Play Console'dan kontrol et). Gizlilik politikası: `ilqim.github.io/gizlilik.html`. Veri güvenliği formu: veri toplanmıyor. `MANAGE_MEDIA` için medya düzenleme aracı olduğunu açıkla.
- İsim kontrolü: Play Store'da "ShotFind" adıyla çakışma olmadığını yayından önce kontrol et.
- **GitHub README:** Problem, çözüm, ekran görüntüleri, GIF'ler (arama, kaydırarak ayıklama, bildirimden not), teknolojiler, mimari şeması, kurulum.
- **YouTube:** 1-2 dakikalık demo (@ilqimgames).
- **Kişisel site** (ilqim.github.io, Projeler): tür "Mobil Uygulama", etiket "Kişisel Proje", görev "Tek kişilik ekip". **LinkedIn Projeler** bölümü.
- **CV maddesi örneği:** "Designed and built ShotFind, an Android app (Kotlin, Jetpack Compose) that indexes screenshots with on-device OCR for instant full-text search, detects new screenshots in the background, and lets users annotate and file them into real gallery albums from a notification."

---

## 17. Açık sorular

1. Ana ekran / kilit ekranında alınan bir SS'in tam dosya adı (kaynak uygulama kısmında ne yazıyor?).
2. Tabletin modeli ve Android sürümü (tablet düzeni için, v3).
3. Uygulama ileride İngilizce de olacak mı? (Arayüz metinlerini `strings.xml`'e taşıma zamanlaması.)
