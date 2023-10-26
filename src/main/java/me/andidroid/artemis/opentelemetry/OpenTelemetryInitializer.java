package me.andidroid.artemis.opentelemetry;

import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporter;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.instrumentation.micrometer.v1_5.OpenTelemetryMeterRegistry;
import io.opentelemetry.instrumentation.netty.v4_1.NettyClientTelemetry;
import io.opentelemetry.instrumentation.netty.v4_1.NettyServerTelemetry;
import io.opentelemetry.instrumentation.runtimemetrics.java17.JfrFeature;
import io.opentelemetry.instrumentation.runtimemetrics.java17.RuntimeMetrics;
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
import io.opentelemetry.semconv.ResourceAttributes;

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

                        String serviceName = Objects
                                        .toString(prop.getOrDefault("otel.service.name", "activemq-artemis"));

                        Resource resource = Resource.getDefault()
                                        .merge(Resource.create(
                                                        Attributes.of(ResourceAttributes.SERVICE_NAME, serviceName)));

                        /*
                         * Tracing
                         */

                        SdkTracerProvider sdkTracerProvider = SdkTracerProvider.builder()
                                        .addSpanProcessor(BatchSpanProcessor
                                                        .builder(OtlpGrpcSpanExporter.builder().build()).build())
                                        .setResource(resource)
                                        .build();

                        /*
                         * Metrics
                         */
                        SdkMeterProvider sdkMeterProvider = SdkMeterProvider.builder()
                                        .registerMetricReader(
                                                        PeriodicMetricReader.builder(
                                                                        OtlpGrpcMetricExporter.builder().build())
                                                                        .build())
                                        .setResource(resource)
                                        .build();
                        /*
                         * Logging
                         */
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
                        // .build();
                        // GlobalOpenTelemetry.set(openTelemetry);

                        // add runtime hook to close opentelemetry sdk (flushes logs)
                        Runtime.getRuntime().addShutdownHook(new Thread(openTelemetry::close));

                        NettyServerTelemetry.builder(openTelemetry).build().createCombinedHandler();
                        NettyClientTelemetry.builder(openTelemetry).build().createCombinedHandler();

                } catch (Throwable t) {
                        t.printStackTrace();
                }

                // return sdk;
        }

        /**
         * @return the openTelemetry
         */
        public OpenTelemetry getOpenTelemetry() {
                return openTelemetry == null ? GlobalOpenTelemetry.get() : openTelemetry;
        }
}
