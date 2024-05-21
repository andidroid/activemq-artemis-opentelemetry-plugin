package me.andidroid.artemis.opentelemetry.client.common;

import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentelemetry.context.propagation.TextMapGetter;

public class MessageTextMapGetter implements TextMapGetter<Message> {
    private static final Logger logger = LoggerFactory.getLogger(MessageTextMapGetter.class);

    @Override
    public String get(Message message, String key) {
        try {
            logger.debug("MessageTextMapGetter.get: {}", key);
            return message.getStringProperty(key);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    @Override
    public Iterable<String> keys(Message message) {
        logger.debug("MessageTextMapGetter.get keys");
        Set<String> keys = new HashSet<>();
        try {
            Set<SimpleString> enumeration = message.getPropertyNames();
            for (SimpleString simpleString : enumeration) {
                keys.add(simpleString.toString());
            }
            return keys;
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

}
