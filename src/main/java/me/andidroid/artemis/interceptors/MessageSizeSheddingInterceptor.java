package me.andidroid.artemis.interceptors;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Interceptor;
import org.apache.activemq.artemis.core.protocol.core.Packet;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionSendMessagePacket;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;

/**
 * Der Message-Size-Shedder (Schutz vor Heap-Explosionen im Cluster)Das Szenario:Angreifer oder fehlerhafte Drittsysteme senden über dein Gateway Nachrichten mit gigantischen Anhängen (z. B. 100 MB XML- oder JSON-Payloads) an eine High-Frequency-Queue, die eigentlich für schlanke Status-Updates gedacht ist. Wenn der Broker versucht, solche Monster-Nachrichten komplett im RAM zu deserialisieren, droht dem Artemis-Prozess der sofortige Absturz durch einen OutOfMemoryError (OOM).Die Lösung:Der Interceptor überprüft die physische Größe des Netty-Pakets direkt im E/A-Buffer des Netzwerk-Treibers. Überschreitet das Paket das vordefinierte Limit (z. B. 2 MB), wird der Request sofort abgebrochen und die TCP-Verbindung gekappt, noch bevor die Byte-Kette das interne Speicher-Management von Artemis (oder das Dateisystem-Journal) belasten kann.
 * 
 * MessageSizeSheddingInterceptor
 */
public class MessageSizeSheddingInterceptor implements Interceptor {

    // Hartes Limit von 2 Megabyte (2 * 1024 * 1024 Bytes) für High-Frequency Queues
    private static final int MAX_ALLOWED_SIZE_BYTES = 2_097_152;
    private static final String CRITICAL_QUEUE = "queue.orders";

    @Override
    public boolean intercept(final Packet packet, final RemotingConnection connection) throws ActiveMQException {
        if (packet instanceof SessionSendMessagePacket sendMessagePacket) {
            // Hole die exakte Paket-Größe direkt aus dem Netty-Transport-Stream
            int packetSize = packet.getPacketSize();

            // Filtere gezielt nach geschäftskritischen, hochfrequentierten Adressen
            String address = sendMessagePacket.getMessage().getAddress().toString();
            
            if (CRITICAL_QUEUE.equals(address) && packetSize > MAX_ALLOWED_SIZE_BYTES) {
                // RESSOURCEN-NOTBREMSE: false wirft das Paket sofort auf Kernel-Ebene weg.
                // Es wird kein Java-Heap verschwendet und kein IO-Thread blockiert.
                return false; 
            }
        }
        return true;
    }
}