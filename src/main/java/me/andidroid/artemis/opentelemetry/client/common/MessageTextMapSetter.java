package me.andidroid.artemis.opentelemetry.client.common;

import org.apache.activemq.artemis.api.core.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentelemetry.context.propagation.TextMapSetter;

public class MessageTextMapSetter implements TextMapSetter<Message> {
    private static final Logger logger = LoggerFactory.getLogger(MessageTextMapSetter.class);

    @Override
    public void set(Message message, String key, String value) {
        logger.debug("MessageTextMapSetter.set: {}", key);
        try {
            message.putStringProperty(key, value);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

}
