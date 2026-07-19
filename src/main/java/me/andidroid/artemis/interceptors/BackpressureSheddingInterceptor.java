package me.andidroid.artemis.interceptors;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Interceptor;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.protocol.core.Packet;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionSendMessagePacket;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;

/**
 * Der Backpressure-Shedding Interceptor (Proaktive Queue-Drosselung)Das Szenario:Bei einem massiven DDoS-Angriff oder einem Systemausfall stauen sich Millionen von Nachrichten in einer bestimmten Queue. Wenn die Queue-Größe ein kritisches Limit überschreitet, droht die Festplatte vollzulaufen, oder das Journal wird träge. Normalerweise reagiert Artemis erst, wenn der Speicher physisch voll ist (PAGE oder BLOCK Policy).Die Lösung:Der Interceptor fragt die interne Queue-Infrastruktur des Brokers direkt im Netzwerk-Thread ab. Hat die Ziel-Queue bereits mehr als z. B. 50.000 wartende Nachrichten, fängt der Interceptor das Sende-Paket ab, verwirft es und signalisiert der JAX-RS-Schicht von WildFly sofort ein Fehlschlagen – bevor die Nachricht überhaupt die Speicher-Engine des Brokers berührt.
 * 
 * BackpressureSheddingInterceptor
 */
public class BackpressureSheddingInterceptor implements Interceptor {

    private final ActiveMQServer server;
    private static final SimpleString TARGET_QUEUE = SimpleString.of("queue.orders");
    private static final int MAX_QUEUE_MESSAGE_LIMIT = 50_000;

    // Der Server-Kontext wird über ein Artemis-ServerPlugin injiziert oder global gesetzt
    public BackpressureSheddingInterceptor(ActiveMQServer server) {
        this.server = server;
    }

    @Override
    public boolean intercept(Packet packet, RemotingConnection connection) throws ActiveMQException {
        if (packet instanceof SessionSendMessagePacket sendMessagePacket) {
            // Prüfe, ob die Nachricht an unsere kritische CRUD-Queue adressiert ist
            SimpleString address = sendMessagePacket.getMessage().getAddressSimpleString();
            
            if (TARGET_QUEUE.equals(address)) {
                // Ermittle die exakte Live-Anzahl der Nachrichten in der Queue im Broker-RAM
                Queue queue = server.getPostOffice().getBinding(TARGET_QUEUE).getBindable();
                
                if (queue != null && queue.getMessageCount() > MAX_QUEUE_MESSAGE_LIMIT) {
                    // PURE RESILIENCE: Blockiere die Annahme sofort auf Socket-Ebene!
                    // Verhindert das Volllaufen des Journals bei DDoS-Spikes oder Systemstau.
                    return false; 
                }
            }
        }
        return true;
    }
}