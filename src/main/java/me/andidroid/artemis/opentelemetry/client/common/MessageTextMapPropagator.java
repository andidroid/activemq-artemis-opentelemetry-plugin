package me.andidroid.artemis.opentelemetry.client.common;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.api.trace.TraceStateBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;

public class MessageTextMapPropagator implements TextMapPropagator {

    private static final Logger logger = LoggerFactory.getLogger(MessageTextMapGetter.class);

    // private static final ContextKey<List<String>> EXTRACTED_KEY_VALUES =
    // ContextKey
    // .named("passthroughpropagator-keyvalues");

    @Override
    public Collection<String> fields() {
        logger.debug("MessageTextMapPropagator.fields()");
        return List.of("traceid", "spanid", "clientid", "serverid", "traceflags");

    }

    @Override
    public <C> void inject(Context context, C carrier, TextMapSetter<C> setter) {
        logger.debug("MessageTextMapPropagator.inject()");

        String spanId = Span.fromContext(context).getSpanContext().getSpanId();
        String traceId = Span.fromContext(context).getSpanContext().getTraceId();

        String traceFlags = Span.fromContext(context).getSpanContext().getTraceFlags().asHex();
        Span.fromContext(context).getSpanContext().getTraceState()
                .forEach(new BiConsumer<String, String>() {

                    @Override
                    public void accept(String t, String u) {
                        setter.set(carrier, t, u);
                    }

                });

        setter.set(carrier, "spanid", spanId);
        setter.set(carrier, "traceid", traceId);
        setter.set(carrier, "traceflags", traceFlags);
    }

    @Override
    public <C> Context extract(Context context, C carrier, TextMapGetter<C> getter) {
        logger.debug("MessageTextMapPropagator.extract()");

        String spanId = getter.get(carrier, "spanid");
        String traceId = getter.get(carrier, "traceid");

        String traceFlags = getter.get(carrier, "traceflags");
        TraceStateBuilder tsb = TraceState.builder();
        getter.keys(carrier).forEach(new Consumer<String>() {

            @Override
            public void accept(String t) {
                tsb.put(t, getter.get(carrier, t));
            }

        });

        SpanContext spanContext = SpanContext.createFromRemoteParent(traceId, spanId,
                traceFlags == null ? TraceFlags.getDefault() : TraceFlags.fromHex(traceFlags, 0),
                tsb.build());
        context = Span.wrap(spanContext).storeInContext(context);
        // Context.current().makeCurrent();
        context.makeCurrent();
        return context;

    }
}
