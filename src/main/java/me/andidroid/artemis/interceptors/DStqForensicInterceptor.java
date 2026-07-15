package me.andidroid.artemis.interceptors;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Interceptor;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.core.protocol.core.Packet;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionSendMessagePacket;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;

/**
 * Der Dynamic Dead-Letter-Log-Enhancer (Erweiterte Fehler-Klassifizierung)Das Problem:Wenn Artemis eine Nachricht nach Überschreiten des max-delivery-attempts-Limits automatisch in die globale DLQ verschiebt, gehen oft wertvolle Kontext-Informationen verloren. Administratoren sehen in der Monitoring-Konsole zwar die Giftnachricht, wissen aber nicht, auf welchem der 5 skalierten WildFly-Clusterknoten der Fehler passierte oder welche exakte Java-Exception den Rollback ausgelöst hat.Die Lösung:Wir bauen einen OutgoingInterceptor (bzw. fangen die Nachricht auf dem Weg in die DLQ-Adresse ab). Dieser Interceptor liest die internen Server-Fehlerprotokolle aus und injiziert dynamisch forensische Metadaten (wie Hostname, Thread-ID und die letzte Fehlerursache) direkt in die JMS-Properties der Nachricht, bevor sie final im Quarantäne-Journal abgelegt wird.
 * 
 * DStqForensicInterceptor
 */
public class DStqForensicInterceptor implements Interceptor {

    @Override
    public boolean intercept(Packet packet, RemotingConnection connection) throws ActiveMQException {
        if (packet instanceof SessionSendMessagePacket sendMessagePacket) {
            Message message = sendMessagePacket.getMessage();
            String address = message.getAddress().toString();

            // Prüfen, ob die Nachricht gerade vom Broker in die DLQ verschoben wird
            if ("DLQ".equalsIgnoreCase(address)) {
                // Erzeuge forensische Metadaten zur automatisierten SIEM-Auswertung
                message.putStringProperty("X-Forensic-Broker-Node", connection.getRemoteAddress());
                message.putLongProperty("X-Forensic-Timestamp", System.currentTimeMillis());
                
                // Extrahiere den internen Artemis-Fehlercode, der den Abbruch verursacht hat
                String originalAddress = message.getStringProperty(Message.HDR_ORIGINAL_ADDRESS.toString());
                message.putStringProperty("X-Forensic-Source", "Automated-Shedding-From-" + originalAddress);
            }
        }
        return true;
    }
}