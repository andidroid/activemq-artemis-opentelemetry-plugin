package me.andidroid.artemis.opentelemetry;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.postoffice.RoutingStatus;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.RoutingContext;
import org.apache.activemq.artemis.core.server.ServerConsumer;
import org.apache.activemq.artemis.core.server.ServerSession;
import org.apache.activemq.artemis.core.server.impl.AckReason;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerPlugin;
import org.apache.activemq.artemis.core.transaction.Transaction;
import org.apache.activemq.artemis.core.server.ActiveMQServer;

import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessageOperation;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.log4j.appender.v2_17.OpenTelemetryAppender;
import me.andidroid.artemis.opentelemetry.client.common.MessageTextMapGetter;
import me.andidroid.artemis.opentelemetry.client.common.OpenTelemetryJMSClientUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

public class OpenTelemetryPlugin implements ActiveMQServerPlugin {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final String OPERATION_NAME_SEND = "ArtemisMessageSending";
   private static final String OPERATION_NAME_DELIVER = "ArtemisMessageDelivery";
   private static final String OPERATION_NAME_ROUTE = "ArtemisMessageRoute";
   private OpenTelemetry openTelemetry;
   private Tracer tracer;// = GlobalOpenTelemetry.getTracer(OpenTelemetryPlugin.class.getName());

   private final AtomicReference<ActiveMQServer> server = new AtomicReference<>();

   @Override
   public void init(Map<String, String> properties) {
      logger.info("start OpenTelemetryPlugin");

      logger.info(properties.toString());
      openTelemetry = OpenTelemetryInitializer.create(properties).getOpenTelemetry();
      tracer = openTelemetry.getTracer(OpenTelemetryPlugin.class.getName());

      // initialize Log4j Appender
      OpenTelemetryAppender.install(openTelemetry);
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
   public void messageExpired(MessageReference reference, SimpleString messageExpiryAddress) throws ActiveMQException {
      // Span span = getSpan(message.getMessage(), OPERATION_NAME_SEND);
      // if (span != null) {
      // span.addEvent("expired " + messageExpiryAddress);
      // }
      Message message = reference.getMessage();
      Instrumenter<Message, Message> instrumenter = OpenTelemetryJMSClientUtils.getProcessServerInstrumenter();
      Context context = Context.current();
      boolean shouldStart = instrumenter.shouldStart(context, message);
      Scope scope = null;
      if (shouldStart) {
         context = instrumenter.start(context, message);
         scope = context.makeCurrent();
         Span.fromContext(context).addEvent("messageExpired").setAttribute("messageExpiryAddress",
               messageExpiryAddress.toString());

      } else {
         return;
      }

      // Span span = TracingMessageUtils.startListenerSpan(message, tracer);
      // if (traceInLog) {
      // MDC.put("spanId", span.getSpanContext().getSpanId());
      // MDC.put("traceId", span.getSpanContext().getTraceId());
      // }

      try {
         // span.makeCurrent();

      } finally {
         // span.end();
         instrumenter.end(context, message, message, null);
         if (scope != null) {
            scope.close();
         }
         // if (traceInLog) {
         // MDC.remove("spanId");
         // MDC.remove("traceId");
         // }
      }

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

      // SpanBuilder spanBuilder =
      // getTracer().spanBuilder(OPERATION_NAME_SEND).setAttribute("message",
      // message.toString())
      // .setSpanKind(SpanKind.SERVER);
      // Span span = spanBuilder.startSpan();
      // setSpan(message, OPERATION_NAME_SEND, span);

      Instrumenter<Message, Message> instrumenter = OpenTelemetryJMSClientUtils.getProcessServerInstrumenter();
      // consumer.getConnectionRemoteAddress();

      // instrumenter.builder(GlobalOpenTelemetry.get(), OPERATION_NAME,
      // MessagingSpanNameExtractor.create(OpenTelemetryJMSClientUtils.getter,
      // MessageOperation.RECEIVE)).;

      Context context = Context.current();
      boolean shouldStart = instrumenter.shouldStart(context, message);
      Scope scope = null;
      if (shouldStart) {
         setInstrumenter(message, OPERATION_NAME_SEND, instrumenter);
         context = instrumenter.start(context, message);
         setContext(message, OPERATION_NAME_SEND, context);
         scope = context.makeCurrent();
         Span.fromContext(context).updateName(OPERATION_NAME_SEND).addEvent("beforeSend");
         scope.close();
      }

   }

   @Override
   public void afterSend(ServerSession session, Transaction tx,
         Message message,
         boolean direct,
         boolean noAutoCreateQueue,
         RoutingStatus result) throws ActiveMQException {
      // Span span = getSpan(message, OPERATION_NAME_SEND);
      // span.addEvent("send " + result.name());
      // span.end();

      Instrumenter<Message, Message> instrumenter = getInstrumenter(message, OPERATION_NAME_SEND);
      Context context = getContext(message, OPERATION_NAME_SEND);
      Span.fromContext(context).addEvent("afterSend");
      Scope scope = context.makeCurrent();
      instrumenter.end(context, message, message, null);
      if (scope != null) {
         scope.close();
      }
   }

   @Override
   public void onSendException(ServerSession session,
         Transaction tx,
         Message message,
         boolean direct,
         boolean noAutoCreateQueue,
         Exception e) throws ActiveMQException {
      // getSpan(message,
      // OPERATION_NAME_SEND).setStatus(StatusCode.ERROR).recordException(e).end();//
      // TODO end span here?

      Instrumenter<Message, Message> instrumenter = getInstrumenter(message, OPERATION_NAME_SEND);
      Context context = getContext(message, OPERATION_NAME_SEND);
      Span.fromContext(context).addEvent("onSendException");
      Scope scope = context.makeCurrent();
      instrumenter.end(context, message, message, e);
      if (scope != null) {
         scope.close();
      }
   }

   @Override
   public void beforeDeliver(ServerConsumer consumer, MessageReference reference) throws ActiveMQException {
      // SpanBuilder spanBuilder = getTracer().spanBuilder(OPERATION_NAME_DELIVER)
      // .setAttribute("message", reference.getMessage().toString())
      // .setAttribute("consumer", consumer.getID())
      // .setSpanKind(SpanKind.SERVER);
      // Span span = spanBuilder.startSpan();
      // setSpan(reference.getMessage(), OPERATION_NAME_DELIVER, span);

      Message message = reference.getMessage();

      Instrumenter<Message, Message> instrumenter = OpenTelemetryJMSClientUtils.getProcessServerInstrumenter();

      Context context = Context.current();
      boolean shouldStart = instrumenter.shouldStart(context, message);
      Scope scope = null;
      if (shouldStart) {
         setInstrumenter(message, OPERATION_NAME_DELIVER, instrumenter);
         context = instrumenter.start(context, message);
         setContext(message, OPERATION_NAME_DELIVER, context);
         scope = context.makeCurrent();
         Span.fromContext(context).updateName(OPERATION_NAME_DELIVER).addEvent("beforeDeliver");
         scope.close();
      }
   }

   @Override
   public void afterDeliver(ServerConsumer consumer, MessageReference reference) throws ActiveMQException {
      // Span span = getSpan(reference.getMessage(), OPERATION_NAME_DELIVER);
      // span.addEvent("deliver " + consumer.getSessionName());
      // span.end();
      Message message = reference.getMessage();
      Instrumenter<Message, Message> instrumenter = getInstrumenter(message, OPERATION_NAME_DELIVER);
      Context context = getContext(message, OPERATION_NAME_DELIVER);
      Span.fromContext(context).addEvent("afterDeliver");
      Scope scope = context.makeCurrent();
      instrumenter.end(context, message, message, null);
      if (scope != null) {
         scope.close();
      }
   }

   @Override
   public void beforeMessageRoute(Message message, RoutingContext routingContext, boolean direct,
         boolean rejectDuplicates)
         throws ActiveMQException {
      // SpanBuilder spanBuilder = getTracer().spanBuilder(OPERATION_NAME_ROUTE)
      // .setAttribute("message", message.toString())
      // .setAttribute("address", Objects.toString(context.getAddress(), ""))
      // .setSpanKind(context.isInternal() ? SpanKind.INTERNAL : SpanKind.SERVER);
      // Span span = spanBuilder.startSpan();
      // setSpan(message, OPERATION_NAME_ROUTE, span);

      Instrumenter<Message, Message> instrumenter = OpenTelemetryJMSClientUtils.getProcessServerInstrumenter();

      Context context = Context.current();
      boolean shouldStart = instrumenter.shouldStart(context, message);
      Scope scope = null;
      if (shouldStart) {

         setInstrumenter(message, OPERATION_NAME_ROUTE, instrumenter);
         context = instrumenter.start(context, message);
         setContext(message, OPERATION_NAME_ROUTE, context);
         scope = context.makeCurrent();
         Span.fromContext(context).updateName(OPERATION_NAME_ROUTE).addEvent("beforeMessageRoute")
               .addEvent(Objects.toString(routingContext.getAddress(), ""));
         scope.close();
      }
   }

   @Override
   public void afterMessageRoute(Message message, RoutingContext routingContext, boolean direct,
         boolean rejectDuplicates,
         RoutingStatus result) throws ActiveMQException {
      // Span span = getSpan(message, OPERATION_NAME_ROUTE);
      // span.addEvent("routed " + result.name());
      // span.end();

      Instrumenter<Message, Message> instrumenter = getInstrumenter(message, OPERATION_NAME_ROUTE);
      Context context = getContext(message, OPERATION_NAME_ROUTE);
      Span.fromContext(context).addEvent("afterMessageRoute");
      Scope scope = context.makeCurrent();
      instrumenter.end(context, message, message, null);
      if (scope != null) {
         scope.close();
      }
   }

   @Override
   public void onMessageRouteException(Message message, RoutingContext routingContext, boolean direct,
         boolean rejectDuplicates, Exception e) throws ActiveMQException {
      // getSpan(message,
      // OPERATION_NAME_ROUTE).setStatus(StatusCode.ERROR).recordException(e).end();//
      // TODO end span here?

      Instrumenter<Message, Message> instrumenter = getInstrumenter(message, OPERATION_NAME_ROUTE);
      Context context = getContext(message, OPERATION_NAME_ROUTE);
      Span.fromContext(context).addEvent("onMessageRouteException");
      Scope scope = context.makeCurrent();
      instrumenter.end(context, message, message, e);
      if (scope != null) {
         scope.close();
      }
   }

   public Tracer getTracer() {
      return tracer;
   }

   public void setTracer(Tracer myTracer) {
      tracer = myTracer;
   }

   private Span getSpan(Message message, String key) {
      Span span = (Span) message.getUserContext(Span.class + "_" + key);
      return span;
   }

   private void setSpan(Message message, String key, Span span) {
      message.setUserContext(Span.class + "_" + key, span);
   }

   private Context getContext(Message message, String key) {
      Context context = (Context) message.getUserContext(Context.class + "_" + key);
      return context;
   }

   private void setContext(Message message, String key, Context context) {
      message.setUserContext(Context.class + "_" + key, context);
   }

   private Instrumenter<Message, Message> getInstrumenter(Message message, String key) {
      Instrumenter<Message, Message> instrumenter = (Instrumenter<Message, Message>) message
            .getUserContext(Instrumenter.class + "_" + key);
      return instrumenter;
   }

   private void setInstrumenter(Message message, String key, Instrumenter<Message, Message> instrumenter) {
      message.setUserContext(Instrumenter.class + "_" + key, instrumenter);
   }

   @Override
   public void messageAcknowledged(final Transaction tx, final MessageReference ref, final AckReason reason,
         final ServerConsumer consumer) throws ActiveMQException {

      Message message = (ref == null ? null : ref.getMessage());
      Queue queue = (ref == null ? null : ref.getQueue());

      Instrumenter<Message, Message> instrumenter = OpenTelemetryJMSClientUtils.getProcessServerInstrumenter();
      // consumer.getConnectionRemoteAddress();

      // instrumenter.builder(GlobalOpenTelemetry.get(), OPERATION_NAME,
      // MessagingSpanNameExtractor.create(OpenTelemetryJMSClientUtils.getter,
      // MessageOperation.RECEIVE)).;

      Context context = Context.current();
      boolean shouldStart = instrumenter.shouldStart(context, message);
      Scope scope = null;
      if (shouldStart) {

         context = instrumenter.start(context, message);
         scope = context.makeCurrent();
         Span.fromContext(context).addEvent("messageAcknowledged").setAttribute("queue", queue.toString())
               .setAttribute("reason", reason.toString());

         // SpanBuilder spanBuilder =
         // getTracer().spanBuilder("ArtemisMessageDelivery.messageAcknowledged")
         // .setParent(context)
         // .setAttribute("message", message.toString())
         // .setSpanKind(SpanKind.SERVER);
         // Span span = spanBuilder.startSpan();
         // span.addEvent("messageAcknowledged");
         // span.end();

      } else {
         return;
      }

      // Span span = TracingMessageUtils.startListenerSpan(message, tracer);
      // if (traceInLog) {
      // MDC.put("spanId", span.getSpanContext().getSpanId());
      // MDC.put("traceId", span.getSpanContext().getTraceId());
      // }

      try {
         // span.makeCurrent();

      } finally {
         // span.end();
         instrumenter.end(context, message, message, null);
         if (scope != null) {
            scope.close();
         }
         // if (traceInLog) {
         // MDC.remove("spanId");
         // MDC.remove("traceId");
         // }
      }
   }
}
