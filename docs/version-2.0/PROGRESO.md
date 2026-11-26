# Alerce 2.0 — progreso del encargo final (para retomar si se corta la sesión)

Base: HEAD `38a3b51`. Los cambios del encargo «administración integral» (38 archivos) siguen **sin commit**. Este encargo se agrega encima, también sin commit.

## Bloques

| Bloque | Estado |
|---|---|
| P0 · Activación de emprendedoras (migración 11): apropiación previa de la cuenta, sesiones de terceros, reversión segura | en curso |
| P0 · Registro con «Confirm email», sesiones y cuentas | pendiente |
| P1 · Motor del asistente y frases de prueba (con reserva de frases no usadas al diseñar) | pendiente |
| P1 · Asistente para visitantes, compradores y administración | pendiente |
| P1 · Ferias en Android: avisos, foto y ubicación | pendiente |
| Publicación: enlaces del APK, caché, procedimiento | pendiente |
| Suites completas, APK 2.0.0-rc1 (13), informe | pendiente |

## Hallazgos

- **Push a `main`** dispara `deploy-pages.yml`, que publica la web en GitHub Pages.
- **Los botones de descarga** de la web apuntan a `releases/download/apk-1.6-alerce/app-release.apk` (dos lugares en `index.html`, más `dist/hosting` y la prueba `hosting.spec.js`).

## Decisiones

(se completa al avanzar)

## Pruebas

(se completa al avanzar)
