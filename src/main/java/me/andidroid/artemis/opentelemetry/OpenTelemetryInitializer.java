package me.andidroid.artemis.opentelemetry;

import java.io.InputStream;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

import io.opentelemetry.api.common.Attributes;
// import io.opentelemetry.api.logs.GlobalLoggerProvider;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporter;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.instrumentation.micrometer.v1_5.OpenTelemetryMeterRegistry;
import io.opentelemetry.instrumentation.runtimemetrics.java17.JfrFeature;
import io.opentelemetry.instrumentation.runtimemetrics.java17.RuntimeMetrics;
// import io.opentelemetry.instrumentation.runtimemetrics.BufferPools;
// import io.opentelemetry.instrumentation.runtimemetrics.Classes;
// import io.opentelemetry.instrumentation.runtimemetrics.Cpu;
// import io.opentelemetry.instrumentation.runtimemetrics.GarbageCollector;
// import io.opentelemetry.instrumentation.runtimemetrics.MemoryPools;
// import io.opentelemetry.instrumentation.runtimemetrics.Threads;
//import io.opentelemetry.exporter.otlp.log.OtlpGrpcLogRecordExporter;
//import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.logs.LogRecordProcessor;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

public class OpenTelemetryInitializer {

        private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

        private static OpenTelemetryInitializer INSTANCE = null;

        private OpenTelemetrySdk openTelemetry;

        /**
         * @return the iNSTANCE
         */
        public static OpenTelemetryInitializer getINSTANCE() {
                return INSTANCE;
        }

        public static OpenTelemetryInitializer create(Map<String, String> properties) {
                INSTANCE = new OpenTelemetryInitializer(properties);
                return INSTANCE;
        }

        public OpenTelemetryInitializer(Map<String, String> properties) {
                logger.info("start OpenTelemetryInitializer");
                try

                {
                        InputStream input = OpenTelemetryPlugin.class.getClassLoader()
                                        .getResourceAsStream("opentelemetry.properties");
                        if (input == null) {
                                throw new NullPointerException("Unable to find tracing.properties file");
                        }
                        Properties prop = new Properties(System.getProperties());
                        prop.load(input);
                        prop.putAll(properties);
                        logger.info(prop.toString());
                        System.setProperties(prop);

                        String otelEndpoint = Objects
                                        .toString(prop.getOrDefault("otel.exporter.otlp.endpoint",
                                                        "http://localhost:4317"));

                        // sdk = AutoConfiguredOpenTelemetrySdk.initialize().getOpenTelemetrySdk();

                        String serviceName = Objects
                                        .toString(prop.getOrDefault("otel.service.name", "activemq-artemis"));

                        Resource resource = Resource.getDefault()
                                        .merge(Resource.create(
                                                        Attributes.of(ResourceAttributes.SERVICE_NAME, serviceName)));

                        SdkTracerProvider sdkTracerProvider = SdkTracerProvider.builder()
                                        .addSpanProcessor(BatchSpanProcessor
                                                        .builder(OtlpGrpcSpanExporter.builder().build()).build())
                                        .setResource(resource)
                                        .build();

                        SdkMeterProvider sdkMeterProvider = SdkMeterProvider.builder()
                                        .registerMetricReader(
                                                        PeriodicMetricReader.builder(
                                                                        OtlpGrpcMetricExporter.builder().build())
                                                                        .build())
                                        .setResource(resource)
                                        .build();
                        // OtlpGrpcLogRecordExporter

                        LogRecordExporter logRecordExporter = OtlpGrpcLogRecordExporter.builder()
                                        .setEndpoint(otelEndpoint)
                                        .build();
                        // use syso-log-exporter for testing
                        // logRecordExporter = SystemOutLogRecordExporter.create();
                        LogRecordProcessor logRecordProcessor = BatchLogRecordProcessor.builder(logRecordExporter)
                                        .build();
                        // LogRecordExporter logRecordExporter =
                        // BatchLogRecordProcessor.builder(OtlpJsonLoggingLogRecordExporter.create());
                        // LogRecordExporter logRecordExporter = InMemoryLogRecordExporter.create();
                        // SimpleLogRecordProcessor.create(logRecordExporter)
                        SdkLoggerProvider sdkLoggerProvider = SdkLoggerProvider.builder()
                                        .setResource(resource)
                                        .addLogRecordProcessor(logRecordProcessor)
                                        .build();

                        openTelemetry = OpenTelemetrySdk.builder()
                                        .setTracerProvider(sdkTracerProvider)
                                        .setMeterProvider(sdkMeterProvider)
                                        .setLoggerProvider(sdkLoggerProvider)
                                        .setPropagators(ContextPropagators
                                                        .create(W3CTraceContextPropagator.getInstance()))
                                        .buildAndRegisterGlobal();
                        // dont call this, already called internally via buildAndRegisterGlobal()
                        // GlobalOpenTelemetry.set(sdk);

                        // GlobalLoggerProvider.set(openTelemetry.getSdkLoggerProvider());

                } catch (Throwable t) {
                        t.printStackTrace();
                }

                try {

                        // GlobalLoggerProvider.set(sdk.getSdkLoggerProvider());
                        // Resource resource = Resource.getDefault();
                        // LogRecordExporter logRecordExporter = InMemoryLogRecordExporter.create();
                        // SdkLoggerProvider sdkLoggerProvider = SdkLoggerProvider.builder()
                        // .setResource(resource)
                        // .addLogRecordProcessor(SimpleLogRecordProcessor.create(logRecordExporter))
                        // .build();
                        // GlobalLoggerProvider.set(sdkLoggerProvider);

                        // BufferPools.registerObservers(openTelemetry);
                        // Classes.registerObservers(openTelemetry);
                        // Cpu.registerObservers(openTelemetry);
                        // MemoryPools.registerObservers(openTelemetry);
                        // Threads.registerObservers(openTelemetry);
                        // GarbageCollector.registerObservers(openTelemetry);

                        RuntimeMetrics runtimeMetrics = RuntimeMetrics.builder(openTelemetry)
                                        .enableFeature(JfrFeature.BUFFER_METRICS)
                                        .enableFeature(JfrFeature.CLASS_LOAD_METRICS)
                                        .enableFeature(JfrFeature.CONTEXT_SWITCH_METRICS)
                                        .enableFeature(JfrFeature.CPU_COUNT_METRICS)
                                        .enableFeature(JfrFeature.CPU_UTILIZATION_METRICS)
                                        .enableFeature(JfrFeature.GC_DURATION_METRICS)
                                        .enableFeature(JfrFeature.LOCK_METRICS)
                                        .enableFeature(JfrFeature.MEMORY_ALLOCATION_METRICS)
                                        .enableFeature(JfrFeature.MEMORY_POOL_METRICS)
                                        .enableFeature(JfrFeature.NETWORK_IO_METRICS)
                                        .enableFeature(JfrFeature.THREAD_METRICS)
                                        .build();
                        // stop on shutdown
                        // runtimeMetrics.close();

                        MeterRegistry otelMeterRegistry = OpenTelemetryMeterRegistry.builder(openTelemetry)
                                        .setPrometheusMode(true)
                                        .build();
                        Metrics.addRegistry(otelMeterRegistry);

                } catch (Throwable t) {
                        t.printStackTrace();
                }
                // return sdk;
        }

        /**
         * @return the openTelemetry
         */
        public OpenTelemetrySdk getOpenTelemetry() {
                return openTelemetry;
        }
}
