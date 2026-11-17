# RemoteTV - Ban da sua de build duoc tren GitHub Actions

## Tai sao ban cu KHONG build duoc?
1. **Khong co Gradle project**: Zip cu chi co `phone_app/AndroidManifest.xml`, `phone_app/res/`, `phone_app/src/...`
   - Thieu: `build.gradle`, `settings.gradle`, `gradlew`, `.github/workflows/`
   - GitHub Actions khong biet build cai gi -> loi `gradlew: No such file` / workflow khong chay.
2. **Sai cau truc thu muc**: Android Studio bat buoc:
   - `app/src/main/AndroidManifest.xml` (ban cu de o `phone_app/AndroidManifest.xml`)
   - `app/src/main/java/vn/herotran/tvcontrol/*.java` (ban cu de o `phone_app/src/...`)
   - `app/src/main/res/...` (ban cu de o `phone_app/res/...`)
3. **Manifest cu dung `package` + `<uses-sdk>`**: AGP 8.x cam, phai chuyen sang `namespace` trong `app/build.gradle`.
4. **Loi Android 14 (neu nang targetSdk len 34)**:
   - `registerReceiver()` thieu flag `RECEIVER_NOT_EXPORTED`
   - `startForeground()` thieu `FOREGROUND_SERVICE_TYPE_DATA_SYNC`
   - Thieu quyen `FOREGROUND_SERVICE_DATA_SYNC` + `POST_NOTIFICATIONS`
   -> Da fix trong ban nay.

## Ban nay da sua gi?
- Tao lai dung cau truc: `app/src/main/...` + `settings.gradle` + `build.gradle` + `gradlew`
- `app/build.gradle`: namespace `vn.herotran.tvcontrol`, compileSdk 34, minSdk 30, targetSdk 34, versionCode 12, Java 17, zero-dependency
- `.github/workflows/android.yml`: checkout -> JDK 17 -> Android SDK -> `./gradlew assembleDebug` -> upload APK
- Fix 3 file Java cho Android 14

## Cach dung
1. Push toan bo thu muc nay len GitHub (branch main)
2. Vao tab Actions -> chay workflow Android Build
3. Tai APK o muc Artifacts: `RemoteTV-debug-apk` -> `app-debug.apk`
4. Cai APK do len ca DT + TV (ghi de ban cu)

## Luu y
- App khong dung Firebase SDK, dung REST + org.json co san nen khong can `google-services.json`.
- Neu muon build Release co ky, them signing config sau.
