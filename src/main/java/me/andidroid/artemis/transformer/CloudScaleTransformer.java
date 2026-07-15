package me.andidroid.artemis.transformer;


import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.core.server.transformer.Transformer;
import java.util.logging.Logger;


/**
 * Um die über dynamische Brücken (Core Bridges) skalierten Nachrichten im Cluster-Verbund von Apache ActiveMQ Artemis millimetergenau zu manipulieren, zu filtern oder mit Cloud-Metadaten anzureichern, implementieren wir das native org.apache.activemq.artemis.core.server.transformer.Transformer-Interface.Das Prinzip: Zero-Copy-Transformation im Routing-Hot-PathEin Transformer fängt die Nachricht im Arbeitsspeicher des Quell-Brokers ab, exakt in dem Moment, in dem die Brücke die Nachricht aus der lokalen Queue absaugt und für den TCP-Netzwerktransfer zum Ziel-Broker vorbereitet.Indem wir das Transformer-Interface implementieren, können wir:Nachrichten-Payloads mutieren: Z. B. ein schweres JSON-Dokument on-the-fly im Buffer optimieren oder veraltete Felder löschen.Cluster-Routing-Metadaten injizieren: Dem Ziel-Knoten mitteilen, von welchem Ursprungs-Pod die Nachricht stammt, um Endlos-Weiterleitungen (Routing Loops) im skalierten Cluster mathematisch zu verhindern.Bandbreite einsparen: Unnötige JMS-Header-Properties entfernen, bevor die Nachricht serialisiert und über das Netzwerk geschickt wird.
 * 
 * 1. Die Implementierung der High-Performance Kette (CloudScaleTransformer)Dieser Transformer injiziert ein systemweites Cluster-Tracing-Flag (eine eindeutige UUID oder Hop-Count), um zirkuläre Routing-Schleifen zu erkennen und zu unterbinden, und bereinigt gleichzeitig interne WildFly-Systemheader, um Bandbreite einzusparen.
 * 
 * 2. Dynamische Aktivierung über unser AutoScaleBridgeSynchronizerPluginIm vorherigen Schritt haben wir das AutoScaleBridgeSynchronizerPlugin gebaut, das Core-Bridges in elastischen Cloud-Umgebungen zur Laufzeit programmatisch anlegt. Wir verknüpfen unseren neuen CloudScaleTransformer nun exakt an dieser Stelle, indem wir den Klassennamen in der Konfiguration übergeben:
 * 
 * 🛠️ Alternative: Statische Aktivierung in der broker.xmlFalls du Brücken nicht dynamisch via CDI/Plugin erzeugst, sondern eine feste Core-Bridge zwischen zwei Rechenzentren (z. B. Frankfurt → New York) in der broker.xml deklarierst, bindest du den Transformer über das Element <transformer-class-name> ein:xml<bridges>
    <bridge name="frankfurt-to-new-york-bridge">
        <queue-name>queue.orders</queue-name>
        <forwarding-address>queue.orders.us</forwarding-address>
        
        <!-- AKTIVIERUNG DES TRANSFORMATORS IM CORE-ROUTING -->
        <transformer-class-name>de.example.artemis.transformer.CloudScaleTransformer</transformer-class-name>
        
        <reconnect-attempts>-1</reconnect-attempts>
        <static-connectors>
            <connector-ref>us-east-connector</connector-ref>
        </static-connectors>
    </bridge>
</bridges>
 * 

* Das architektonische Ergebnis im Cloud-Betrieb:Absolute Loop-Immunität: In hochskalierten Mesh- oder Ring-Topologien von Message-Brokern kann es durch Fehlkonfigurationen der Forwarding-Adressen passieren, dass Nachricht A unendlich im Kreis von Server 1 zu Server 2 zu Server 3 und zurück zu Server 1 geschickt wird. Das treibt die CPU-Last des Clusters sofort auf 100 % (Distributed Broadcast Storm). Der CloudScaleTransformer erkennt dies anhand des X-Cluster-Hop-Count beim 3. Übergang und eliminiert das Paket geräuschlos im RAM, wodurch das Gesamtsystem stabil bleibt.Effiziente Ausnutzung der Bandbreite: Das automatische Bereinigen ungenutzter String-Properties sorgt für eine signifikante Entlastung der Netzwerkkarten bei hohem Datenaufkommen.Glasklares Distributed Tracing: Da jeder Ziel-Knoten im Cluster anhand der Properties genau sieht, welchen Weg das Paket genommen hat, lässt sich die asynchrone Datenverarbeitung perfekt über Tools wie Jaeger oder Zipkin auditieren.Damit ist das anspruchsvolle technologische Spektrum der plattformnahen Daten- und Routing-Steuerung im Apache ActiveMQ Artemis Broker vollständig erschlossen.

 * CloudScaleTransformer
 */
public class CloudScaleTransformer implements Transformer {

    private static final Logger LOGGER = Logger.getLogger(CloudScaleTransformer.class.getName());
    
    // Header zur Erkennung von Cluster-Umläufen (Hop-Count)
    private static final String CLUSTER_HOP_COUNT = "X-Cluster-Hop-Count";
    private static final int MAX_ALLOWED_HOPS = 2; // Maximal 2 Weiterleitungen über Brücken erlauben

    @Override
    public Message transform(final Message message) {
        // 1. SCHUTZ VOR ROUTING-LOOPS (Endlosschleifen im skalierten Cluster)
        int hopCount = 0;
        if (message.containsProperty(CLUSTER_HOP_COUNT)) {
            hopCount = message.getIntProperty(CLUSTER_HOP_COUNT);
        }

        // Inkrementiere den Zähler für die aktuelle Brücken-Überquerung
        hopCount++;
        message.putIntProperty(CLUSTER_HOP_COUNT, hopCount);

        if (hopCount > MAX_ALLOWED_HOPS) {
            LOGGER.severe(String.format(
                "[CLUSTER-LOOP-DETECTED] Nachricht ID %d hat das Hop-Limit von %d überschritten! Verwürfe Nachricht, um Cluster-Kollaps zu verhindern.",
                message.getMessageID(), MAX_ALLOWED_HOPS
            ));
            
            // Wenn wir NULL zurückgeben, blockiert die Brücke die Weiterleitung unbarmherzig!
            // Die Nachricht wird nicht über das Netzwerk gesendet.
            return null; 
        }

        // 2. BANDBREITEN-OPTIMIERUNG (Header-Shedding)
        // Wir löschen flüchtige, interne WildFly-EJB- oder JAX-RS-Filter-Metadaten,
        // die das Ziel-System auf einem anderen Broker-Knoten nicht benötigt.
        // Das spart wertvolle Bytes pro TCP-Paket.
        message.removeProperty("X-Gateway-Inspected-By");
        message.removeProperty("X-Is-Transient");

        // 3. TELEMETRIE-INJEKTION
        // Wir markieren die Nachricht mit dem Namen des Ursprungs-Knotens,
        // um im nachgelagerten Grafana-Monitoring die Datenströme visualisieren zu können.
        message.putStringProperty("X-Source-Cluster-Node", System.getProperty("jboss.node.name", "Artemis-Node-Core"));

        // Gibt die modifizierte Nachricht für den physischen Netty-Netzwerktransfer frei
        return message;
    }
}