# Oyna — iPhone uslubidagi shisha launcher (Android)

Android telefonning bosh ekranini almashtiradigan launcher. Dok, papkalar, vidjetlar, qidiruv
va boshqaruv paneli iOS 26 dagi **Liquid Glass** kabi — orqa fon rasmini linzadek sindiradi.
Redmi Note 12 (Android 13/14) uchun sinalgan holda yozilgan.

## Imkoniyatlar

- **Bosh ekran:** bir nechta sahifa, 4 yoki 5 ustunli setka, fon parallaksi
- **Sudrab ko'chirish:** ilovani uzoq bosib suring; boshqa ilova ustiga qo'ysangiz — papka hosil bo'ladi;
  ekran chetiga olib borsangiz — keyingi sahifaga o'tadi; dokka ham qo'yish mumkin
- **Tahrirlash rejimi:** bo'sh joyni uzoq bosing — ikonkalar titraydi, «−» bilan olib tashlanadi
- **Papkalar:** shisha papka, nomini o'zgartirish, papkadan chiqarish, papkani tarqatish
- **Dok:** 5 tagacha ilova yoki papka, shisha panel
- **Ilovalar ro'yxati:** tepaga suring yoki «Qidiruv» tugmasini bosing; A–Z, tezkor qidiruv
- **Uzoq bosish menyusi:** ilovaning tezkor amallari (shortcuts), «Ilova haqida», «O'chirish»
- **Vidjetlar:** Soat, Kalendar, Batareya (shisha) + telefondagi har qanday tizim vidjeti
- **Boshqaruv paneli** (pastga suring): Wi-Fi, Bluetooth, mobil internet, samolyot rejimi, fonar,
  yorqinlik va ovoz slayderlari, ekran aylanishi, tovush rejimi, «Bezovta qilmaslik», kamera,
  kalkulyator, bildirishnomalar
- **Sozlamalar:** fon rasmi (galereyadan, tizimga ham o'rnatish), sinish kuchi, xiralik, shaffoflik,
  ikonka uslubi (Asl / Shisha / Qorong'i), ikonka shakli (Skvirkl / Yumaloq kvadrat / Doira),
  ikonka o'lchami, nomlar, ustunlar soni

## O'rnatish

1. Telefonda oching: `https://github.com/farruxworkin-beep/oyna/releases/latest/download/Oyna.apk`
2. APK ni o'rnating va **Oyna** ni oching
3. Yuqoridagi bannerdagi **O'rnatish** tugmasi → «Bosh ekran ilovasi» → **Oyna**
4. Endi Home tugmasi Oyna'ni ochadi

## Cheklovlar (Android qoidalari)

- Launcher faqat **o'z ekranlarini** boshqaradi. Tizim paneli (soat, batareya), Xiaomi sozlamalari va
  boshqa ilovalar ko'rinishini oddiy ilova o'zgartira olmaydi (root kerak).
- Android 10+ da Wi-Fi / Bluetooth / mobil internetni ilova o'zi yoqa olmaydi — tizimning kichik paneli ochiladi.
- Yorqinlik va ekran aylanishi uchun bir marta «Tizim sozlamalarini o'zgartirish» ruxsati so'raladi.
- Jim rejim va «Bezovta qilmaslik» uchun bir marta tegishli ruxsat so'raladi.
- Haqiqiy sinish Android 13+ da, Android 12 da blur, undan pastda yarim shaffof panel.
- Ba'zi MIUI/HyperOS versiyalarida begona launcher bilan ekran imo-ishoralari cheklanishi mumkin.

## Kod tuzilishi

```
data/      Apps (LauncherApps), HomeStore (joylashuv + sozlamalar), Wallpaper
system/    SystemControls (fonar, yorqinlik, ovoz...), WidgetHostManager
ui/glass/  LiquidGlass — AGSL shader bilan sinish effekti
ui/home/   bosh ekran, sudrash, papka, menyu
ui/drawer/ ilovalar ro'yxati
ui/control/boshqaruv paneli
ui/widgets/vidjetlar
ui/settings/sozlamalar
```

GitHub Actions har bir push'dan keyin APK yig'ib, Releases'ga `Oyna.apk` nomi bilan joylaydi.
