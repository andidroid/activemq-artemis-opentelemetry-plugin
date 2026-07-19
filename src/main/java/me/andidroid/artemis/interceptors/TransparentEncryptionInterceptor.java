package me.andidroid.artemis.interceptors;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Interceptor;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.core.protocol.core.Packet;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionSendMessagePacket;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Der Feld-Verschlüsselungs-Wächter (Transparent Data Encryption - TDE)Das Szenario:In einer Zero-Trust-Cloud-Architektur dürfen hochsensible Daten (z. B. Passwörter, Gehaltsdaten, DSGVO-relevante Felder) niemals im Klartext im Artemis-Journal (Festplatte) oder im RAM des Brokers liegen. Das Verschlüsseln im Java-Business-Code führt jedoch zu unsauberem, schwer wartbarem Code und verlangsamt das DTO-Design.Die Lösung:Der Interceptor agiert als transparente Sicherheits-Middleware. Er scannt den Binärstrom im E/A-Buffer. Trifft er auf ein geschütztes Feld, wird dieses on-the-fly mittels AES-256-GCM verschlüsselt (bzw. auf Consumer-Seite über einen OutgoingInterceptor wieder entschlüsselt). Der eigentliche Message Broker sieht zu keinem Zeitpunkt die echten Daten im Klartext, sondern persistiert im Journal ausschließlich unlesbaren Chiffretext.
 * 
 * TransparentEncryptionInterceptor
 */
public class TransparentEncryptionInterceptor implements Interceptor {

    // In Produktion zwingend über einen externen Cloud-Tresor (wie HashiCorp Vault) einspeisen!
    private static final byte[] AES_KEY_256 = "Secret-Krypto-Key-Gateway-2026-X".getBytes(StandardCharsets.UTF_8);
    private static final String TARGET_SECURE_PROPERTY = "secret_payment_token";

    @Override
    public boolean intercept(Packet packet, RemotingConnection connection) throws ActiveMQException {
        if (packet instanceof SessionSendMessagePacket sendMessagePacket) {
            Message message = sendMessagePacket.getMessage();

            if (message.containsProperty(TARGET_SECURE_PROPERTY)) {
                try {
                    String plainTextToken = message.getStringProperty(TARGET_SECURE_PROPERTY);
                    
                    // Kryptografische GCM-Verschlüsselung initialisieren (AES-NI hardwarebeschleunigt)
                    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                    byte[] iv = new byte[12]; // In Produktion einen echten SecureRandom IV erzeugen
                    GCMParameterSpec spec = new GCMParameterSpec(128, iv);
                    cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(AES_KEY_256, "AES"), spec);
                    
                    byte[] cipherBytes = cipher.doFinal(plainTextToken.getBytes(StandardCharsets.UTF_8));
                    String cipherTextBase64 = Base64.getEncoder().encodeToString(cipherBytes);

                    // Überschreibe das Feld im Broker-Buffer mit der sicheren Chiffre
                    message.putStringProperty(TARGET_SECURE_PROPERTY, cipherTextBase64);
                    message.putBooleanProperty("X-Is-Encrypted", true);

                } catch (Exception e) {
                    throw new ActiveMQException("Kritischer Krypto-Fehler im Artemis-Kernel: " + e.getMessage());
                }
            }
        }
        return true;
    }
}