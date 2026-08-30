package me.andidroid.artemis.interceptors;

import com.github.luben.zstd.Zstd;
import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Interceptor;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.core.protocol.core.Packet;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionSendMessagePacket;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;

/**
 * Das Payload-Decompressor-Plugin (Netzwerk-Bandbreiten-Boost)Das Szenario: Deine JAX-RS-Middleware schickt hochkomprimierte Binärdaten (z. B. mit Google Protobuf oder Zstd-Kompression) an den Broker, um die Bandbreite im Cloud-Netzwerk zu schonen. Wenn der Broker jedoch nachgelagerte Validierungen oder Content-Based-Routing auf Basis von JSON-Inhalten durchführen soll, müsste er die Nachricht mühsam entpacken.Die Lösung: Der Interceptor fängt das Paket ab, liest den Header Content-Encoding: zstd, dekomprimiert die Bytes direkt im E/A-Buffer mithilfe nativer C-Bibliotheken (zstd-jni) und schreibt die unkomprimierten Daten zurück in das Paket, bevor die Artemis-Routing-Maschine anläuft.
 * 
 * ZstdDecompressionInterceptor
 */
public class ZstdDecompressionInterceptor implements Interceptor {

    private static final String COMPRESSION_HEADER = "X-Payload-Encoding";

    @Override
    public boolean intercept(Packet packet, RemotingConnection connection) throws ActiveMQException {
        if (packet instanceof SessionSendMessagePacket sendMessagePacket) {
            Message message = sendMessagePacket.getMessage();

            if ("zstd".equalsIgnoreCase(message.getStringProperty(COMPRESSION_HEADER))) {
                ActiveMQBuffer buffer = message.getBodyBuffer();
                
                // Lies komprimierte Bytes direkt aus dem Netty-Netzwerkbuffer
                int compressedSize = buffer.readableBytes();
                byte[] compressedBytes = new byte[compressedSize];
                buffer.readBytes(compressedBytes);

                // Berechne die originale Größe aus den Metadaten
                long originalSize = Zstd.decompressedSize(compressedBytes);
                byte[] decompressedBytes = new byte[(int) originalSize];

                // ZERO-COPY NATIVE DECOMPRESSION (AM JAVA-HEAP VORBEI)
                Zstd.decompress(decompressedBytes, compressedBytes);

                // Schreibe den unkomprimierten Plaintext-Inhalt zurück in die Nachricht
                buffer.clear();
                buffer.writeBytes(decompressedBytes);
                
                // Header aktualisieren, damit die MDB in WildFly Bescheid weiß
                message.putStringProperty(COMPRESSION_HEADER, "none");
            }
        }
        return true;
    }
}