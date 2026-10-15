package com.tglabs.spotivibe.service

import android.service.notification.NotificationListenerService

/**
 * Sengaja kosong. Notifikasi app lain tidak dibaca sama sekali.
 *
 * Android hanya mengizinkan `MediaSessionManager.getActiveSessions` (membaca
 * lagu yang diputar app lain) untuk app yang punya NotificationListenerService
 * aktif. Kelas ini ada supaya Spotivibe punya komponen untuk diaktifkan user di
 * Settings → Notification access; sistem yang mengikatnya, bukan app.
 *
 * Nama kelas ini bagian dari izin yang sudah diberikan user. Mengganti nama
 * atau paketnya mencabut izin itu diam-diam.
 */
class SessionAccessService : NotificationListenerService()
