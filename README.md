Apache ActiveMQ Artemis OpenTelemetry Plugin

Tracing with ActiveMQServerPlugin
Logging with Log4j opentelemetry-log4j-appender-2.17 [Log4j2 Appender Instrumentation](https://github.com/open-telemetry/opentelemetry-java-instrumentation/tree/main/instrumentation/log4j/log4j-appender-2.17/library)
Metrics with Micrometer and ActiveMQMetricsPlugin [Micrometer bridge instrumentation](https://github.com/open-telemetry/opentelemetry-java-instrumentation/tree/main/instrumentation/micrometer/micrometer-1.5)

Based on:
[ActiveMQ Artemis opentelemetry Plugin Example](https://github.com/apache/activemq-artemis/tree/main/examples/features/standard/opentelemetry) and [Artemis Prometheus Metrics Plugin](https://github.com/rh-messaging/artemis-prometheus-metrics-plugin)

```
<metrics>
   <jvm-memory>true</jvm-memory> <!-- defaults to true -->
   <jvm-gc>true</jvm-gc> <!-- defaults to false -->
   <jvm-threads>true</jvm-threads> <!-- defaults to false -->
   <netty-pool>true</netty-pool> <!-- defaults to false -->
   <file-descriptors>true</file-descriptors> <!-- defaults to false -->
   <processor>true</processor> <!-- defaults to false -->
   <uptime>true</uptime> <!-- defaults to false -->
   <plugin class-name="me.andidroid.artemis.opentelemetry.OpenTelemetryMetricsPlugin"/>
</metrics>
```
