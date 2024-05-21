package me.andidroid.artemis.opentelemetry.client.common;

import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigurablePropagatorProvider;

public class MessageTextMapPropagatorProvider implements ConfigurablePropagatorProvider {

    public static final String NAME = "message-propagator";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public TextMapPropagator getPropagator(ConfigProperties arg0) {
        return new MessageTextMapPropagator();
    }

}
