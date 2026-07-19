<configuration xmlns="urn:activemq" xmlns:xsi="http://w3.org" xsi:schemaLocation="urn:activemq /schema/artemis-server.xsd">
    <core>
        
        <!-- REGISTRIERUNG DEINER ENTERPRISE BROKER PLUGINS -->
        <broker-plugins>
            <broker-plugin class-name="de.example.artemis.plugin.CrisisAlertingPlugin"/>
            <broker-plugin class-name="de.example.artemis.plugin.AuditTrailLedgerPlugin"/>
            <broker-plugin class-name="de.example.artemis.plugin.DynamicQosThrottlingPlugin"/>
        </broker-plugins>
        
    </core>
</configuration>