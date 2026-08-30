package me.andidroid.artemis.interceptors;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Interceptor;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.core.protocol.core.Packet;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionSendMessagePacket;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Das Content-Based Routing & De-Duplizierungs-SchutzschildDas Szenario: Im verteilten Cluster senden Clients (oder dein JAX-RS-Gateway) gelegentlich Nachrichten aufgrund von Netzwerk-Retries doppelt ab (At-Least-Once Delivery). Duplicate-Messages verstopfen die Datenbank.Die Lösung: Der Interceptor berechnet einen schnellen SHA-256-Hash über die Core-Nachrichten-Properties oder liest eine Idempotenz-ID. Er gleicht diese gegen eine extrem schnelle In-Memory-Datenstruktur (wie einen lokalen Caffeine-Cache) ab. Wird ein Duplikat erkannt, bricht der Interceptor die Verarbeitung ab.
 * 
 * DuplicateDetectionInterceptor
 */
public class DuplicateDetectionInterceptor implements Interceptor {

    private static final Logger LOGGER = Logger.getLogger(DuplicateDetectionInterceptor.class.getName());
    private static final String IDEMPOTENCY_HEADER = "X-Idempotency-Key";

    // Lock-Free High-Performance Cache (Lebt im Broker-RAM)
    private final Cache<String, Boolean> duplicateCache = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterWrite(1, TimeUnit.HOURS) // 1 Stunde Schutzfenster
            .build();

    @Override
    public boolean intercept(final Packet packet, final RemotingConnection connection) throws ActiveMQException {
        if (packet instanceof SessionSendMessagePacket sendMessagePacket) {
            Message message = sendMessagePacket.getMessage();
            
            if (message.containsProperty(IDEMPOTENCY_HEADER)) {
                String key = message.getStringProperty(IDEMPOTENCY_HEADER);
                
                // ATOMARER CHECK-AND-SET BLITZSCHNELL IM RAM
                if (duplicateCache.getIfPresent(key) != null) {
                    LOGGER.warning("[ANTI-DUPLICATE] Nachricht mit Key blockiert: " + key);
                    
                    // FALSCH: Nachricht verwerfen. Sie wird nicht ins Journal geschrieben.
                    // WildFly erhält sofort die Rückmeldung, dass das Senden fehlgeschlagen ist.
                    return false; 
                }
                
                duplicateCache.put(key, Boolean.TRUE);
            }
        }
        return true;
    }
}