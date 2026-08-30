package me.andidroid.artemis.interceptors;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Interceptor;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.core.protocol.core.Packet;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionReceiveMessagePacket;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;

/**
 * Der Message TTL-Shedder für reaktive Retries (Stale Message Eviction)Das Szenario:Wenn ein WildFly-Clusterknoten abstürzt, bleiben unbestätigte Nachrichten im Broker blockiert. Wenn das System sie nach Minuten oder Stunden wiederholt zustellt, sind die darin enthaltenen zeitkritischen Daten (z. B. ein transienter OTP-Code oder ein Aktienkurs) bereits wertlos (Stale Data). Ihre Verarbeitung verschwendet nur kostbare Datenbank-Ressourcen.Die Lösung:Dieser Interceptor überprüft bei der Zustellung (Outgoing) einer Nachricht, ob sie ein kritisches Alter überschritten hat. Er berechnet die Differenz zwischen der aktuellen Serverzeit und dem Erstellungs-Zeitstempel der Nachricht. Ist das Fenster überschritten, deklariert er die Nachricht als abgelaufen und wirft sie weg, ohne sie jemals an WildFly auszuliefern.
 * 
 * StaleMessageEvictionInterceptor
 */
/**
 * WICHTIG: Dies ist ein OUTGOING Interceptor. Er klinkt sich ein, 
 * wenn der Broker eine Nachricht aus der Queue an WildFly ausliefern will!
 */
public class StaleMessageEvictionInterceptor implements Interceptor {

    // Maximal zulässiges Alter einer transienten Nachricht im System: 30 Sekunden
    private static final long MAX_STALE_AGE_MS = 30_000; 

    @Override
    public boolean intercept(Packet packet, RemotingConnection connection) throws ActiveMQException {
        // Wir fangen das ausgehende Zustell-Paket (ReceiveMessagePacket für den Client) ab
        if (packet instanceof SessionReceiveMessagePacket receivePacket) {
            Message message = receivePacket.getMessage();
            
            long messageTimestamp = message.getTimestamp();
            long age = System.currentTimeMillis() - messageTimestamp;

            if (age > MAX_STALE_AGE_MS && message.containsProperty("X-Is-Transient")) {
                // SYSTEM-SÄUBERUNG: Durch das Zurückgeben von 'false' verweigert der Interceptor
                // die Netty-Übertragung zum WildFly-Server. Die Nachricht verfällt augenblicklich im Broker.
                // Deine Worker-Threads bleiben komplett verschont von veralteten Datenleichen!
                return false; 
            }
        }
        return true;
    }
}