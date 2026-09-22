package com.example.service

import com.example.db.PushSubscriptionsTable
import com.example.db.dbQuery
import nl.martijndwars.webpush.Notification
import nl.martijndwars.webpush.PushService
import org.jetbrains.exposed.sql.select
import nl.martijndwars.webpush.Utils
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

object WebPushService {
    init {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    // Čitamo String-ove iz OS okruženja (Render / IntelliJ)
    private val publicKeyString: String = System.getenv("VAPID_PUBLIC_KEY")
        ?: error("VAPID_PUBLIC_KEY nije podešen!")

    private val privateKeyString: String = System.getenv("VAPID_PRIVATE_KEY")
        ?: error("VAPID_PRIVATE_KEY nije podešen!")

    private val subjectString: String = System.getenv("VAPID_SUBJECT")
        ?: "mailto:podrska@tvojdomen.com"

    // 2. Inicijalizacija PushService-a uz konverziju String-a u PublicKey/PrivateKey
    val pushService = PushService().apply {
        publicKey = Utils.loadPublicKey(publicKeyString)   // Konvertuje String u PublicKey
        privateKey = Utils.loadPrivateKey(privateKeyString) // Konvertuje String u PrivateKey
        subject = subjectString
    }

    suspend fun notifyUsersAboutNewMatch(hostId: Int, sport: String, venue: String) = dbQuery {
        // Pronađi sve pretplate osim korisnika koji je kreirao meč (hostId)
        val subscriptions = PushSubscriptionsTable
            .select { PushSubscriptionsTable.userId neq hostId }
            .map {
                Triple(
                    it[PushSubscriptionsTable.endpoint],
                    it[PushSubscriptionsTable.p256dh],
                    it[PushSubscriptionsTable.auth]
                )
            }

        val jsonPayload = """
            {
                "title": "Novi meč: $sport ⚽",
                "body": "Kreiran je novi meč na lokaciji $venue. Uđi i prijavi se!",
                "url": "/matches"
            }
        """.trimIndent()

        // Slanje svakom uređaju osobno
        for ((endpoint, p256dh, auth) in subscriptions) {
            try {
                val notification = Notification(endpoint, p256dh, auth, jsonPayload.toByteArray())
                pushService.send(notification)
            } catch (e: Exception) {
                // Ako uređaj više ne postoji/odbije, opciono obriši subscription iz baze
                println("Greška pri slanju push-a: ${e.message}")
            }
        }
    }
}