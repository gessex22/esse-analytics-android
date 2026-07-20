package com.esseanalytics.android.core.designsystem.theme

import androidx.compose.ui.graphics.Color

// Mismos colores de marca que ya usa el frontend web/desktop (VideosView,
// SyncPanel, StatsView — frontend/src/components/icons/PlatformLogos.tsx) —
// se reusan tal cual para que la identidad de cada red se vea igual en las 3
// plataformas de la app (desktop, web, Android). Validados con el skill de
// dataviz (CVD + contraste, claro y oscuro) al agregarlos originalmente.
val YoutubeRed = Color(0xFFEF4444)
val InstagramPurple = Color(0xFFA855F7)
val TiktokPink = Color(0xFFEC4899)

// Urgencia del calendario (mismos tonos que UrgencyPill en
// frontend/src/components/PublishingQueue.tsx: past=red-500, today=orange-500,
// soon=amber-600) -- fijos, independientes del tema rojo/ámbar activo, igual
// que los colores de marca de arriba.
val UrgencyPast = Color(0xFFEF4444)
val UrgencyToday = Color(0xFFF97316)
val UrgencySoon = Color(0xFFD97706)

// Portados 1:1 de frontend/src/styles/theme.css — el frontend web es
// dark-only (el bloque .dark/:root claro de ahí es boilerplate de shadcn sin
// usar, nunca se aplica) con dos temas de color conmutables por
// localStorage['videx-theme']: "rojo" (default) y "ámbar". Se define acá el
// tema completo "ámbar" también para cuando exista un selector de tema en
// Ajustes (Fase 2) — hoy solo se usa el de "rojo".

// Tema "rojo" (default) — theme.css:185-206
val PrimaryRojo = Color(0xFFE63946)
val BackgroundRojo = Color(0xFF0D0D0F)
val SurfaceRojo = Color(0xFF141417) // --card
val OnSurfaceRojo = Color(0xFFF0F0F2) // --foreground / --card-foreground
val SurfaceVariantRojo = Color(0xFF1A1A20) // --muted
val OnSurfaceVariantRojo = Color(0xFF6B6B7A) // --muted-foreground
val OutlineRojo = Color(0x14FFFFFF) // --border: rgba(255,255,255,0.08)
val PopoverRojo = Color(0xFF1A1A1F) // --popover
val InputBackgroundRojo = Color(0xFF1E1E24) // --input-background
val DestructiveRojo = Color(0xFFE63946) // --destructive

// Tema "ámbar" (alterno) — theme.css:208-229
val PrimaryAmbar = Color(0xFFF59E0B)
val BackgroundAmbar = Color(0xFF0A0A0A)
val SurfaceAmbar = Color(0xFF111111)
val OnSurfaceAmbar = Color(0xFFF5F0E8)
val SurfaceVariantAmbar = Color(0xFF161616)
val OnSurfaceVariantAmbar = Color(0xFF6B6355)
val OutlineAmbar = Color(0x1FF59E0B) // --border: rgba(245,158,11,0.12)
val PopoverAmbar = Color(0xFF181818) // --popover
val InputBackgroundAmbar = Color(0xFF1C1C1C) // --input-background
val DestructiveAmbar = Color(0xFFEF4444) // --destructive
