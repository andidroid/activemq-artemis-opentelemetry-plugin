package me.andidroid.artemis.interceptors;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Interceptor;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.core.protocol.core.Packet;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionSendMessagePacket;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;

/**
 * Der Tenant-Isolation & Dynamische TTL-Wächter (SLA-Enforcement)Das Szenario: Du betreibst ein Multi-Tenant-System (Mandantenfähigkeit). Große oder bösartige Mandanten überfluten das System mit Millionen von Nachrichten, wodurch die Queues der zahlenden Premium-Kunden verstopfen (Noisy Neighbor Problem).Die Lösung: Der Interceptor liest die X-Tenant-ID aus. Er prüft, wie viele Nachrichten dieser Mandant aktuell in der Warteschlange hat oder welcher SLA-Stufe er angehört. Er überschreibt dynamisch die TTL (Time-To-Live) der Nachricht: Nachrichten von Test-Usern erhalten eine extrem kurze TTL (z. B. 10 Sekunden). Werden sie in dieser Zeit wegen Überlastung nicht verarbeitet, wirft Artemis sie automatisch weg (Shedding), um Platz für Premium-Nachrichten zu schaffen.
 * 
 * SlaEnforcementInterceptor
 */
public class SlaEnforcementInterceptor implements Interceptor {

    private static final String TENANT_HEADER = "X-Tenant-ID";

    @Override
    public boolean intercept(Packet packet, RemotingConnection connection) throws ActiveMQException {
        if (packet instanceof SessionSendMessagePacket sendMessagePacket) {
            Message message = sendMessagePacket.getMessage();
            String tenantId = message.getStringProperty(TENANT_HEADER);

            if ("tenant-free-trial".equalsIgnoreCase(tenantId)) {
                // Zwinge die Nachricht auf ein extrem kurzes Verfallsdatum (10.000 Millisekunden)
                // Wenn das Backend staut, löscht der Broker diese Nachrichten autonom,
                // um RAM und Plattenplatz für zahlende Premium-Kunden zu sichern!
                message.setExpiration(System.currentTimeMillis() + 10000);
                
                // Drossel die Priorität auf die niedrigste Stufe (0 = niedrig, 9 = höchste)
                message.setPriority((byte) 0);
            } else if ("tenant-premium-enterprise".equalsIgnoreCase(tenantId)) {
                // Enterprise-Nachrichten fliegen auf der Überholspur durch den Broker
                message.setPriority((byte) 9);
            }
        }
        return true;
    }
}