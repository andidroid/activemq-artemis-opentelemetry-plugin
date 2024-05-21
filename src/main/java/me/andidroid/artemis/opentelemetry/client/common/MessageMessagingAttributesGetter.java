package me.andidroid.artemis.opentelemetry.client.common;

import java.lang.invoke.MethodHandles;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.apache.activemq.artemis.api.core.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesGetter;
// import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingAttributesGetter;

import jakarta.jms.JMSException;

final class MessageMessagingAttributesGetter implements MessagingAttributesGetter<Message, Message> {

    private static final Logger logger = LoggerFactory.getLogger(MessageMessagingAttributesGetter.class);

    @Override
    public Long getBatchMessageCount(Message arg0, Message arg1) {
        return null;
    }

    @Override
    public String getClientId(Message message) {
        try {
            return message.getStringProperty("clientid");
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public String getConversationId(Message message) {
        try {
            return Objects.toString(message.getCorrelationID(), null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getDestination(Message message) {
        try {
            return Objects.toString(message.getAddress(), null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getDestinationTemplate(Message message) {
        try {
            return Objects.toString(message.getAddress(), null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Long getMessageBodySize(Message message) {
        try {
            return (long) Objects.toString(message.getStringBody(), "").length();
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public Long getMessageEnvelopeSize(Message arg0) {
        return null;
    }

    @Override
    public String getMessageId(Message message, Message response) {
        try {
            return Long.toString(message.getMessageID());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getSystem(Message arg0) {
        return "jms";// "activemq"
    }

    @Override
    public boolean isAnonymousDestination(Message arg0) {
        return false;
    }

    @Override
    public boolean isTemporaryDestination(Message arg0) {
        return false;
    }

    @Override
    public List<String> getMessageHeader(Message request, String name) {
        logger.debug("getMessageHeader {}", name);
        try {
            String stringProperty = request.getStringProperty(name);
            if (stringProperty != null) {
                return Collections.singletonList(stringProperty);
            }
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return Collections.emptyList();
    }

    // @Override
    // public Long getMessagePayloadCompressedSize(Message arg0) {
    // return null;
    // }

    // @Override
    // public Long getMessagePayloadSize(Message arg0) {
    // return null;
    // }
}