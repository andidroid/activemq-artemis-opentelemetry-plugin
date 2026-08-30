package me.andidroid.artemis.plugins;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.server.ServerMessage;
import org.apache.activemq.artemis.core.server.ServerSession;
import org.apache.activemq.artemis.core.server.Transaction;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerPlugin;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.ActiveMQServer;

/**
 * Das Dynamic Message-TTL Overrider Plugin (Automatisches Queue-Shedding)Das Szenario:Bei einem plötzlichen Datenbank-Ausfall oder einer Netzwerkunterbrechung in Richtung WildFly stauen sich Millionen von Nachrichten im Broker. Wenn nachgelagerte Systeme wieder online gehen, verarbeiten die Worker oft stundenlang veraltete Datenleichen (z. B. abgelaufene SMS-OTPs oder transiente Sensor-Telemetrie). Dies blockiert den JDBC-Pool unnötig.Die Lösung:Das Plugin klinkt sich in das beforeSend-Event ein. Es analysiert die aktuelle Füllrate der Ziel-Queue. Steigt die Warteschlangen-Länge über einen kritischen Schwellenwert (z. B. 10.000 Nachrichten), überschreibt das Plugin die TTL (Time-To-Live) aller neu reinkommenden Nachrichten dynamisch auf ein Minimum (z. B. 15 Sekunden). Der Broker bereinigt sich im Staufall autonom, um Platz für brandaktuelle Daten zu sichern.
 * 
 * DynamicTtlOverriderPlugin
 */
public class DynamicTtlOverriderPlugin implements ActiveMQServerPlugin {

    private final ActiveMQServer server;
    private static final SimpleString TARGET_QUEUE = SimpleString.of("queue.telemetry");
    private static final int BACKLOG_THRESHOLD = 10_000;
    private static final long COMPressed_TTL_MS = 15_000; // 15 Sekunden Notfall-TTL

    public DynamicTtlOverriderPlugin(ActiveMQServer server) {
        this.server = server;
    }

    @Override
    public void beforeSend(ServerSession session, Transaction tx, ServerMessage message, boolean direct, boolean noUUID) {
        if (TARGET_QUEUE.equals(message.getAddressSimpleString())) {
            try {
                // Hole den Live-Zähler der Queue direkt aus dem Kernel-RAM
                Queue queue = server.getPostOffice().getBinding(TARGET_QUEUE).getBindable();
                
                if (queue != null && queue.getMessageCount() > BACKLOG_THRESHOLD) {
                    // DYNAMISCHER LASTABWURF: Überschreibe das Ablaufdatum der Nachricht
                    long emergencyExpiration = System.currentTimeMillis() + COMPressed_TTL_MS;
                    message.setExpiration(emergencyExpiration);
                    
                    // Markiere die Nachricht als "gedrosselt", damit WildFly-Auditoren es sehen
                    message.putStringProperty("X-Shedding-Active", "true");
                }
            } catch (Exception e) {
                // Fallback: Ignorieren, um den Hauptstrom nicht zu blockieren
            }
        }
    }
}