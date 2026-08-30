package me.andidroid.artemis.plugins;

import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerPlugin;
import org.apache.activemq.artemis.core.server.ServerMessage;
import org.apache.activemq.artemis.api.core.SimpleString;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Das Predictive Dead-Letter-Alerting & SMS-NotrufsystemDas Szenario: Im Normalbetrieb fängt die Dead-Letter-Queue (DLQ) Giftnachrichten ab. Wenn jedoch aufgrund eines fatalen Software-Bugs (z. B. nach einem fehlerhaften API-Update im Backend) plötzlich hunderte geschäftskritische Zahlungen pro Minute in die DLQ wandern, droht ein massiver wirtschaftlicher Schaden. Ein manuelles Prüfen der Logdateien am nächsten Morgen ist zu spät.Die Lösung: Das Plugin klinkt sich in das afterMoveToDeadLetterAddress-Event ein. Es analysiert die Rate der eintreffenden Fehlernachrichten. Steigt die Frequenz innerhalb von 60 Sekunden über ein kritisches Limit, feuert das Plugin direkt aus dem Broker heraus einen asynchronen HTTP-Ruf (Webhook) an ein SIEM-System (PagerDuty, Slack oder Splunk), um die Rufbereitschaft per Notruf zu alarmieren.
 * 
 * CrisisAlertingPlugin
 */
public class CrisisAlertingPlugin implements ActiveMQServerPlugin {

    private final AtomicInteger dLqCounter = new AtomicInteger(0);
    private final AtomicLong lastAlertTime = new AtomicLong(0);
    
    private static final int TRIGGER_LIMIT = 50; // 50 Fehler in...
    private static final long TIME_WINDOW_MS = 60000; // ... 60 Sekunden
    private static final String WEBHOOK_URL = "https://pagerduty.com";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(2))
            .build();

    @Override
    public void afterMoveToDeadLetterAddress(ServerMessage message, SimpleString deadLetterAddress, long queueID) {
        long now = System.currentTimeMillis();
        int currentFailures = dLqCounter.incrementAndGet();

        // Zeitfenster-Reset nach 60 Sekunden
        if (now - lastStateResetTime > TIME_WINDOW_MS) {
            dLqCounter.set(1);
            lastStateResetTime = now;
            return;
        }

        // KRISE ERKANNT: Schwellenwert im Zeitfenster gerissen!
        if (currentFailures >= TRIGGER_LIMIT && (now - lastAlertTime.get() > TIME_WINDOW_MS)) {
            lastAlertTime.set(now);
            triggerEmergencyWebhook(currentFailures, message.getMessageID());
        }
    }

    private void triggerEmergencyWebhook(int failureCount, long sampleMessageId) {
        // Asynchroner Zero-Blocking HTTP-Call direkt aus dem Broker-Kernel
        String jsonPayload = String.format(
            "{\"service_id\":\"JMS-GATEWAY\",\"severity\":\"CRITICAL\",\"summary\":\"MASSIVE-DLQ-SPIKE: %d Nachrichten in 60s blockiert. Letzte ID: %d\"}",
            failureCount, sampleMessageId
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(WEBHOOK_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding());
    }

    private volatile long lastStateResetTime = System.currentTimeMillis();
}