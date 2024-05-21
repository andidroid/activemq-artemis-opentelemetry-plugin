package me.andidroid.artemis.opentelemetry.client.common;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import org.apache.activemq.artemis.api.core.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.logs.LoggerProvider;
import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessageOperation;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesExtractorBuilder;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.ContextCustomizer;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusExtractor;
// import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessageOperation;
// import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingAttributesExtractor;
// import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingAttributesGetter;
// import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingSpanNameExtractor;
import io.opentelemetry.instrumentation.api.internal.PropagatorBasedSpanLinksExtractor;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
// import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;

public class OpenTelemetryJMSClientUtils {

    private static final Logger logger = LoggerFactory.getLogger(OpenTelemetryJMSClientUtils.class);

    private static final OpenTelemetry openTelemetry = GlobalOpenTelemetry.get();

    public static final io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesGetter<Message, Message> getter = new MessageMessagingAttributesGetter();

    // private static InstrumenterBuilder<Message, Message> messageReceiveBuilder =
    // createBuilder(
    // MessageOperation.RECEIVE);
    // private static InstrumenterBuilder<Message, Message> messagePublishBuilder =
    // createBuilder(
    // MessageOperation.PUBLISH);
    private static InstrumenterBuilder<Message, Message> messageProcessBuilder = createBuilder(
            MessageOperation.PROCESS);

    public OpenTelemetryJMSClientUtils() {

    }

    private static InstrumenterBuilder<Message, Message> createBuilder(
            MessageOperation messageOperation) {

        // GlobalOpenTelemetry.set();GlobalOpenTelemetry.get()

        InstrumenterBuilder<Message, Message> builder = Instrumenter.builder(getOpenTelemetry() /*
                                                                                                 * GlobalOpenTelemetry.
                                                                                                 * get()
                                                                                                 * getOpenTelemetry
                                                                                                 * ()
                                                                                                 */,
                "jms-server",
                MessagingSpanNameExtractor.create(getter, messageOperation));// new MessageSpanNameExtractor()

        // builder.addAttributesExtractors(new )

        builder.setSpanStatusExtractor(new SpanStatusExtractor<Message, Message>() {

            @Override
            public void extract(SpanStatusBuilder arg0, Message arg1, Message arg2, Throwable arg3) {
                logger.debug("SpanStatusExtractor.extract");
                if (arg3 == null) {
                    arg0.setStatus(StatusCode.OK);
                } else {
                    arg0.setStatus(StatusCode.ERROR);
                }

            }

        });

        List<String> capturedHeaders = List.of("traceid", "spanid", "clientid", "serverid", "traceflags", "tracestate");
        AttributesExtractor<Message, Message> messagingAttributesExtractor = MessagingAttributesExtractor
                .builder(getter, messageOperation).setCapturedHeaders(capturedHeaders).build();
        builder.addAttributesExtractor(messagingAttributesExtractor);

        builder.addSpanLinksExtractor(
                new PropagatorBasedSpanLinksExtractor<Message>(
                        /* GlobalOpenTelemetry.getPropagators().getTextMapPropagator() */ new MessageTextMapPropagator(),
                        new MessageTextMapGetter()));

        return builder;
    }

    public static OpenTelemetry getOpenTelemetry() {
        logger.debug("get open telemetry with text map propagator: {}", openTelemetry);
        return openTelemetry;
    }

    // public static Instrumenter<Message, Message> getConsumerInstrumenter() {

    // return builder.buildConsumerInstrumenter(new MessageTextMapGetter());
    // }

    // public static Instrumenter<Message, Message> getProducerInstrumenter() {

    // return builder.buildProducerInstrumenter(new MessageTextMapSetter());
    // }

    // public static Instrumenter<Message, Message> getConsumerInstrumenter() {

    // return messageReceiveBuilder.buildConsumerInstrumenter(new
    // MessageTextMapGetter());
    // }

    // public static Instrumenter<Message, Message> getProducerInstrumenter() {

    // return messagePublishBuilder.buildProducerInstrumenter(new
    // MessageTextMapSetter());
    // }

    // public static Instrumenter<Message, Message> getProcessProducerInstrumenter()
    // {

    // return messageProcessBuilder.buildProducerInstrumenter(new
    // MessageTextMapSetter());
    // }

    public static Instrumenter<Message, Message> getProcessServerInstrumenter() {

        return messageProcessBuilder.buildServerInstrumenter(new MessageTextMapGetter());
    }
}
