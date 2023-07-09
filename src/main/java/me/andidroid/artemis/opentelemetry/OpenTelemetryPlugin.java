package me.andidroid.artemis.opentelemetry;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.core.postoffice.RoutingStatus;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.core.server.ServerConsumer;
import org.apache.activemq.artemis.core.server.ServerSession;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerPlugin;
import org.apache.activemq.artemis.core.transaction.Transaction;
import org.apache.activemq.artemis.core.server.ActiveMQServer;

import io.opentelemetry.instrumentation.log4j.appender.v2_17.OpenTelemetryAppender;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

public class OpenTelemetryPlugin implements ActiveMQServerPlugin {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final String OPERATION_NAME = "ArtemisMessageDelivery";
   private OpenTelemetry openTelemetry;
   private Tracer tracer;// = GlobalOpenTelemetry.getTracer(OpenTelemetryPlugin.class.getName());

   private final AtomicReference<ActiveMQServer> server = new AtomicReference<>();


   
   @Override
   public void init(Map<String, String> properties) {
      logger.info("start OpenTelemetryPlugin");

      logger.info(properties.toString());
      openTelemetry = OpenTelemetryInitializer.create(properties).getOpenTelemetry();
      tracer = openTelemetry.getTracer(OpenTelemetryPlugin.class.getName());

      //initialize Log4j Appender
      OpenTelemetryAppender.install((OpenTelemetrySdk)openTelemetry);
   }

   @Override
   public void registered(ActiveMQServer server) {
      this.server.set(server);
   }

   @Override
   public void unregistered(ActiveMQServer server) {
      this.server.set(null);
   }

   @Override
   public void beforeSend(ServerSession session,
         Transaction tx,
         Message message,
         boolean direct,
         boolean noAutoCreateQueue) throws ActiveMQException {

      // TODO: find a way to inject a context based in
      // https://github.com/kittylyst/OTel/blob/8faea2aab7b19680f78804ddff3d59b7b1135aab/src/main/java/io/opentelemetry/examples/utils/OpenTelemetryConfig.java#L96-L100
      // if a client has the metadata, we should get the parent context here

      SpanBuilder spanBuilder = getTracer().spanBuilder(OPERATION_NAME).setAttribute("message", message.toString())
            .setSpanKind(SpanKind.SERVER);
      Span span = spanBuilder.startSpan();
      message.setUserContext(Span.class, span);
   }

   @Override
   public void afterSend(Transaction tx,
         Message message,
         boolean direct,
         boolean noAutoCreateQueue,
         RoutingStatus result) throws ActiveMQException {
      Span span = getSpan(message);
      span.addEvent("send " + result.name());
   }

   @Override
   public void afterDeliver(ServerConsumer consumer, MessageReference reference) throws ActiveMQException {
      Span span = (Span) reference.getMessage().getUserContext(Span.class);
      span.addEvent("deliver " + consumer.getSessionName());
      span.end();
   }

   @Override
   public void onSendException(ServerSession session,
         Transaction tx,
         Message message,
         boolean direct,
         boolean noAutoCreateQueue,
         Exception e) throws ActiveMQException {
      getSpan(message).setStatus(StatusCode.ERROR).recordException(e);
   }

   public Tracer getTracer() {
      return tracer;
   }

   public void setTracer(Tracer myTracer) {
      tracer = myTracer;
   }

   private Span getSpan(Message message) {
      Span span = (Span) message.getUserContext(Span.class);
      return span;
   }

}
