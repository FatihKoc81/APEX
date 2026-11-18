# APEX — Proje Haritası

> İlk harita: 18 Kasım 2026. APEX'in `apex/free-multi-provider` geliştirme dalı hedeflenmiştir. Depo kökü ve README incelendi; alt kaynak dosyalarının tam bağımlılık grafiği sonraki taramada genişletilmelidir.

## Projenin amacı
Android istemcisi ve sunucu tarafı görev orkestrasyonuyla birden fazla yapay zekâ sağlayıcısını yöneten çoklu ajan sistemi.

## Üst seviye mimari
Android istemcisi (`android/`) → APEX API (`server/`) → görev kuyruğu/orkestrasyon worker'ı → sağlayıcı adaptörleri → sonuç ve kontrol noktalarının istemciye dönmesi.

## Kök dizin
- `android/`: Kotlin + Jetpack Compose Android istemcisi.
- `server/`: Node.js/TypeScript API ve görev orkestrasyon sunucusu.
- `docs/`: mimari ve sağlayıcı kurulum notları.
- `.github/`: CI iş akışları (Android APK ve sunucu kontrolü).
- `render.yaml`: Render dağıtım tanımı.
- `README.md`: proje kurulumu ve mimari özeti.
- `.nexora-import-trigger`: depo kökünde bulunan içe aktarma tetik dosyası; işlevi ayrıca doğrulanmalı.

## Sağlayıcılar
- OpenAI: `OPENAI_API_KEY`
- Gemini: `GEMINI_API_KEY`
- OpenRouter: `OPENROUTER_API_KEY`
- Kimi ve DeepSeek: OpenRouter üzerinden ücretsiz model kimliği tanımlanırsa etkinleştirilecek koşullu yollar.

## Görev akışı
1. Android istemcisi kullanıcı görevini API'ye yollar.
2. Sunucu görevi kalıcı depoya/checkpoint mekanizmasına kaydeder.
3. Worker ana sağlayıcıyla görevi yürütür.
4. Yapılandırılmışsa diğer sağlayıcılar inceleme/denetim yapar.
5. Sonuç ve olaylar istemciye sunulur.

## Dağıtım ve derleme
- Sunucu: Render yapılandırması `render.yaml`.
- Android: `.github/workflows/android-build.yml` iş akışı.
- Sunucu kontrolü: `.github/workflows/server-build.yml`.
- API anahtarları Android uygulamasına gömülmemeli; sunucu ortam değişkenlerinde tutulmalı.

## Bilinen sınırlar
README, başlangıç sürümünde SQLite kontrol noktalarından bahseder; çoklu sunucu üretim dağıtımı için PostgreSQL + kuyruk (ör. Redis/BullMQ) ve şifreli secret saklama önerir.
Bu harita canlı sağlayıcı çağrılarının başarıyla test edildiğini iddia etmez.

## Harita güncelleme kuralı
Yeni agent, provider, endpoint, worker adımı, veri tablosu veya Android ekranı eklendiğinde bu dosyayı aynı commit/PR içinde güncelle.
