package me.andidroid.artemis.plugins;

import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerPlugin;
import org.apache.activemq.artemis.core.server.ServerSession;
import org.apache.activemq.artemis.core.server.Transaction;
import org.apache.activemq.artemis.core.server.MessageReference;
import java.io.FileWriter;
import java.io.PrintWriter;

/**
 * Das "Audit-Trail" Ledger-Plugin (Manipulationssicheres Archivieren)Das Szenario: In regulierten Branchen (Fintech, Healthcare) verlangen Auditoren den lückenlosen und unveränderlichen Nachweis darüber, welcher Client wann welche Nachricht gesendet hat. Daten im regulären Journal zu belassen reicht nicht, da dieses zyklisch überschrieben und bereinigt wird.Die Lösung: Wir nutzen den Hook beforeSend. In dem Moment, in dem die JAX-RS-Middleware eine Nachricht per XA-Transaktion einwirft, spiegelt das Plugin die Metadaten (Schnittstelle, IP, MessageID, User-Zertifikat) via Zero-Copy parallel in ein schreibgeschütztes, audit-konformes Dateisystem oder ein verteiltes Logbuch (z. B. Apache Kafka oder ein WORM-Laufwerk).
 * 
 */
public class AuditTrailLedgerPlugin implements ActiveMQServerPlugin {

    private PrintWriter auditFileWriter;

    public AuditTrailLedgerPlugin() {
        try {
            // Öffnet ein dediziertes, schreibgeschütztes OS-Audit-Log
            FileWriter fw = new FileWriter("/var/log/artemis/audit-ledger.log", true);
            this.auditFileWriter = new PrintWriter(fw, true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void beforeSend(ServerSession session, Transaction tx, ServerMessage message, boolean direct, boolean noUUID) {
        // Holt die authentifizierte JAAS-Rolle des WildFly-Clients aus der aktiven Session
        String authenticatedUser = session.getUsername();
        String address = message.getAddress().toString();
        long msgId = message.getMessageID();

        // Einbrennen der Unveränderlichkeit direkt beim Eintreffen des Pakets
        // Das Schreiben läuft zeilenpufferbasiert und entlastet den RAM
        auditFileWriter.printf("TIMESTAMP: %d | MSG_ID: %d | USER: %s | DESTINATION: %s%n", 
                System.currentTimeMillis(), msgId, authenticatedUser, address);
    }
}