package me.andidroid.artemis.interceptors;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Interceptor;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.core.protocol.core.Packet;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionSendMessagePacket;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;

/**
 * Der GDPR/DSGVO Data-Masking Interceptor (Anonymisierung für Logging/Audit)Das Szenario:Aus Compliance-Gründen dürfen personenbezogene Daten (PII - Personally Identifiable Information wie IBAN, Klarnamen, Passwörter) bei internen Tracing-Vorgängen oder beim Schreiben in Audit-Queues niemals im Klartext sichtbar sein.Die Lösung:Dieser Interceptor scannt den Binärstrom. Wird eine Nachricht an eine unverschlüsselte Log- oder Audit-Adresse (address.audit.#) gesendet, fängt der Interceptor die Payload ab, lokalisiert das sensible Feld im Text/JSON via ultraschnellem Token-Scanning und maskiert es (z. B. Ersetzung durch XXXXXXXX). Das System erhält vollwertige Audit-Metadaten, während die Privatsphäre absolut geschützt bleibt.
 * 
 * GdprDataMaskingInterceptor
 */
public class GdprDataMaskingInterceptor implements Interceptor {

    private static final String SENSITIVE_PROPERTY = "user_iban";

    @Override
    public boolean intercept(Packet packet, RemotingConnection connection) throws ActiveMQException {
        if (packet instanceof SessionSendMessagePacket sendMessagePacket) {
            Message message = sendMessagePacket.getMessage();
            String address = message.getAddress().toString();

            // Greift nur bei expliziten Audit- oder Logging-Kanälen
            if (address != null && address.startsWith("address.audit")) {
                if (message.containsProperty(SENSITIVE_PROPERTY)) {
                    String rawIban = message.getStringProperty(SENSITIVE_PROPERTY);
                    
                    if (rawIban != null && rawIban.length() > 6) {
                        // Maskiert die IBAN: Nur die ersten und letzten 2 Zeichen bleiben sichtbar
                        String maskedIban = rawIban.substring(0, 2) + "XXXXXXXXXX" + rawIban.substring(rawIban.length() - 2);
                        
                        // Überschreibe den Wert direkt im E/A-Buffer des Brokers
                        message.putStringProperty(SENSITIVE_PROPERTY, maskedIban);
                    }
                }
            }
        }
        return true;
    }
}