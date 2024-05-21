package me.andidroid.artemis.opentelemetry.client.common;

import org.apache.activemq.artemis.api.core.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;

final class MessageSpanNameExtractor implements SpanNameExtractor<Message> {

    private static final Logger logger = LoggerFactory.getLogger(MessageSpanNameExtractor.class);

    public MessageSpanNameExtractor() {
        logger.debug("create MessageSpanNameExtractor");
    }

    @Override
    public String extract(Message message) {
        try {
            logger.debug("MessageSpanNameExtractor.extract");
            // return message.getStringProperty("spanName");

            return Long.toString(message.getMessageID());
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}