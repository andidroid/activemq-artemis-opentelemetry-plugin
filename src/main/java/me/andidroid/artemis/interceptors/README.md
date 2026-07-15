<configuration xmlns="urn:activemq" xmlns:xsi="http://w3.org" xsi:schemaLocation="urn:activemq /schema/artemis-server.xsd">
    <core>
        
        <!-- REGISTRIERUNG DEINES NATIVEN INTERCEPTORS -->
        <remoting-incoming-interceptors>
            <class-name>de.example.artemis.plugin.HighPerformanceSecurityInterceptor</class-name>
        </remoting-incoming-interceptors>

        <!-- BEISPIEL: REGISTRIERUNG EINES SERVER-PLUGINS (Falls du eines gebaut hast) -->
        <!--
        <broker-plugins>
            <broker-plugin class-name="de.example.artemis.plugin.CustomDeadLetterPlugin"/>
        </broker-plugins>
        -->
        
    </core>
</configuration>