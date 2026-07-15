package me.andidroid.artemis.plugins;

import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerPlugin;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.impl.QueueImpl;

/**
 * Das "Noisy-Neighbor" Dynamic Queue-Throttling (Automatisches QoS)Das Szenario: Ein Mandant läuft Amok oder schickt fehlerhafte Skripte ins Rennen. Seine Queue läuft mit Millionen Nachrichten voll. Obwohl wir zuvor einen Interceptor gebaut haben, der harte Limits blockiert, wollen wir hier sanfter drosseln (Fair-Share Scheduling): Wenn ein Mandant zu viel Last erzeugt, soll er nicht hart blockiert, sondern seine Consumer künstlich verlangsamt werden, um anderen Mandanten Vorrang zu gewähren.Die Lösung: Wir nutzen das Event beforeDeliver. Wenn der Broker eine Nachricht aus der Queue an deine WildFly-CDI-Bean zustellen will, prüft das Plugin den Absender. Ist dieser als "überlastend" registriert, zwingt das Plugin den ausliefernden Thread zu einer künstlichen Gedenksekunde (Thread.sleep()). Die Datenverarbeitung dieses Mandanten wird gedrosselt, während die Sockets der anderen unberührt bleiben.
 * 
 */
public class DynamicQosThrottlingPlugin implements ActiveMQServerPlugin {

    private static final String SYSTEM_BUSY_FLAG = "X-Tenant-Overuse";

    @Override
    public void beforeDeliver(Queue queue, MessageReference reference) {
        ServerMessage message = reference.getMessage();

        // Überprüfe, ob der Interceptor oder der Metrik-Ticker diese Nachricht 
        // zuvor als "Last-Verursacher" markiert hat
        if (message.containsProperty(SYSTEM_BUSY_FLAG)) {
            try {
                // BREMSE INJIZIEREN: Wir drosseln den ausliefernden Netty-Worker-Thread
                // künstlich um 50 Millisekunden. Der Mandant wird eingebremst (Traffic Shaping).
                // Das gibt anderen, gesunden Queues im Cluster die CPU-Hoheit zurück!
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}