# CatcherAuto

[![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?style=flat&logo=kotlin&logoColor=white)](.)
[![Android](https://img.shields.io/badge/Android-34A853?style=flat&logo=android&logoColor=white)](.)
[![ML Kit](https://img.shields.io/badge/ML_Kit-4285F4?style=flat&logo=google&logoColor=white)](.)

> Automatizacion Android con Google ML Kit para aceptar pedidos automaticamente en apps de reparto. Utiliza OCR y analisis de pantalla en tiempo real via Accessibility Service.

## Como funciona

1. **Captura de pantalla** cada 300ms cuando el escaneo esta activo
2. **Deteccion de pixeles** para encontrar botones GO! en la app de reparto
3. **OCR con ML Kit** para leer ciudad, distancia y restaurante
4. **Filtrado**: solo acepta pedidos validos (ciudad, distancia <= 3km, restaurante en whitelist)
5. **Auto-accept** con verificacion post-aceptacion (confirmado, fallido o reintentar)

## Stack

| Capa | Tecnologia |
|------|------------|
| Lenguaje | Kotlin 1.9 |
| Build | Gradle KTS, AGP 8.2 |
| UI | Material Design 3, tema oscuro con acentos cyan |
| OCR | Google ML Kit Text Recognition 16.0 |
| Min SDK | 24 (Android 7.0) |
| Target SDK | 34 (Android 14) |

## Funcionalidades

- Toggle ON/OFF para activar/desactivar el escaneo automatico
- 8 restaurantes configurables individualmente con Material switches
- Deteccion multi-boton (varios pedidos en pantalla simultaneamente)
- Indicador visual de estado: INACTIVO / ACTIVO / PAUSADO
- Anillo de pulso animado durante el escaneo activo
- Verificacion post-aceptacion: deteccion de confirmacion, fallo o pantalla en blanco
- Vibracion al aceptar pedido
- Atajo directo a ajustes de accesibilidad del sistema

## Como ejecutar

1. Abrir el proyecto en **Android Studio**
2. Sync Gradle y ejecutar en dispositivo fisico (API 30+ para captura de pantalla)
3. Activar **CatcherAuto** como Accessibility Service en Ajustes del sistema
4. Configurar restaurantes deseados y activar escaneo

## Autor

**[Deiby Gorrin](https://deiby.dev)** — Fullstack Developer

- Portfolio: [deiby.dev](https://deiby.dev)
- LinkedIn: [in/deibygorrin](https://www.linkedin.com/in/deibygorrin)
- GitHub: [@DeibyGS](https://github.com/DeibyGS)
