/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package me.andidroid.artemis.opentelemetry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.instrumentation.micrometer.v1_5.OpenTelemetryMeterRegistry;
import io.opentelemetry.instrumentation.runtimemetrics.java17.JfrFeature;
import io.opentelemetry.instrumentation.runtimemetrics.java17.RuntimeMetrics;

import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.metrics.ActiveMQMetricsPlugin;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

public class CompositeMetricsPlugin implements ActiveMQMetricsPlugin {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private CompositeMeterRegistry compositeMeterRegistry = new CompositeMeterRegistry();

   private List<ActiveMQMetricsPlugin> plugins = new ArrayList<>();

   @Override
   public ActiveMQMetricsPlugin init(Map<String, String> arg0) {

      try {
         logger.info("start CompositeMetricsPlugin");
         plugins.add(
               (ActiveMQMetricsPlugin) Class.forName("me.andidroid.artemis.opentelemetry.OpenTelemetryMetricsPlugin")
                     .getDeclaredConstructor()
                     .newInstance());
         plugins.add((ActiveMQMetricsPlugin) Class
               .forName("com.redhat.amq.broker.core.server.metrics.plugins.ArtemisPrometheusMetricsPlugin")
               .getDeclaredConstructor()
               .newInstance());
         plugins.add((ActiveMQMetricsPlugin) Class
               .forName("org.apache.activemq.artemis.core.server.metrics.plugins.LoggingMetricsPlugin")
               .getDeclaredConstructor()
               .newInstance());

      } catch (Throwable t) {
         t.printStackTrace();
      }

      for (ActiveMQMetricsPlugin activeMQMetricsPlugin : plugins) {
         activeMQMetricsPlugin.init(arg0);
         compositeMeterRegistry.add(activeMQMetricsPlugin.getRegistry());
      }

      return this;
   }

   @Override
   public void registered(ActiveMQServer server) {
      for (ActiveMQMetricsPlugin activeMQMetricsPlugin : plugins) {
         activeMQMetricsPlugin.registered(server);
      }
   }

   @Override
   public MeterRegistry getRegistry() {
      return compositeMeterRegistry;
   }
}