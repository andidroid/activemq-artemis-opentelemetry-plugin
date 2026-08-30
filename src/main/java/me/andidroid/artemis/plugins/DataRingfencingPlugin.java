package me.andidroid.artemis.plugins;

import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerPlugin;
import org.apache.activemq.artemis.core.server.ServerSession;
import org.apache.activemq.artemis.core.server.Consumer;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.ActiveMQSecurityException;

/**
 * Das Multi-Region Data Ringfencing Plugin (Compliance & Geofencing)Das Szenario:In datenschutzsensiblen Multi-Region-Clustern (z. B. Europa und USA) dürfen Nachrichten bestimmter Mandanten die EU-Grenze aufgrund rechtlicher Vorgaben (DSGVO) niemals verlassen. Wenn ein Entwickler versehentlich ein falsches Routing konfiguriert, drohen schwere Compliance-Verstöße.Die Lösung:Dieses Plugin blockiert unzulässige Consumer-Verbindungen auf Basis von Mandanten-Metadaten. Beim Event beforeConsumerCreate liest das Plugin die JCA-Verbindungsdetails des WildFly-Clients aus. Stimmt der geografische Standort der Ziel-Queue oder des Mandanten-Header-Parameters nicht mit dem anfordernden Service überein, verweigert der Broker das Erstellen des Consumers.
 * 
 * DataRingfencingPlugin
 */
public class DataRingfencingPlugin implements ActiveMQServerPlugin {

    private static final String REGION_EU = "EU-WEST";

    @Override
    public void beforeConsumerCreate(ServerSession session, SimpleString queueName, SimpleString selector, 
                                     boolean browseOnly, boolean supportLargeMessage) throws ActiveMQSecurityException {
        
        // Extrahiere Validierungs-Metadaten aus der authentifizierten JAAS-Session des WildFly-Clients
        String clientDeclaredRegion = session.getMetaData("X-Client-Region");
        
        // Strikte DSGVO-Sperre: Versucht ein Nicht-EU-Service eine geschützte EU-Queue abzusaugen?
        if (queueName.toString().startsWith("eu.protected.") && !REGION_EU.equalsIgnoreCase(clientDeclaredRegion)) {
            
            // SECURITY EXCEPTION WERFEN: Der Broker bricht die JCA-Kanalerstellung sofort ab!
            throw new ActiveMQSecurityException(String.format(
                "[DATA-LEAK-PREVENTION] Zugriff verweigert! Region %s darf keine geschuetzten EU-Daten lesen.", 
                clientDeclaredRegion
            ));
        }
    }
}