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

import java.util.Map;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.instrumentation.micrometer.v1_5.OpenTelemetryMeterRegistry;
import io.opentelemetry.instrumentation.runtimemetrics.java17.JfrFeature;
import io.opentelemetry.instrumentation.runtimemetrics.java17.RuntimeMetrics;

import org.apache.activemq.artemis.core.server.metrics.ActiveMQMetricsPlugin;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

public class OpenTelemetryMetricsPlugin implements ActiveMQMetricsPlugin {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private OpenTelemetry openTelemetry;

   private MeterRegistry otelMeterRegistry = null;

   @Override
   public ActiveMQMetricsPlugin init(Map<String, String> arg0) {

      try {
         logger.info("start OpenTelemetryMetricsPlugin");
         openTelemetry = GlobalOpenTelemetry.get();// OpenTelemetryInitializer.getINSTANCE().getOpenTelemetry();

         try {

            RuntimeMetrics runtimeMetrics = RuntimeMetrics.builder(openTelemetry)
                  .enableFeature(JfrFeature.BUFFER_METRICS)
                  .enableFeature(JfrFeature.CLASS_LOAD_METRICS)
                  .enableFeature(JfrFeature.CONTEXT_SWITCH_METRICS)
                  .enableFeature(JfrFeature.CPU_COUNT_METRICS)
                  .enableFeature(JfrFeature.CPU_UTILIZATION_METRICS)
                  .enableFeature(JfrFeature.GC_DURATION_METRICS)
                  .enableFeature(JfrFeature.LOCK_METRICS)
                  .enableFeature(JfrFeature.MEMORY_ALLOCATION_METRICS)
                  .enableFeature(JfrFeature.MEMORY_POOL_METRICS)
                  .enableFeature(JfrFeature.NETWORK_IO_METRICS)
                  .enableFeature(JfrFeature.THREAD_METRICS)
                  .build();
            // stop on shutdown
            // runtimeMetrics.close();

            // MeterRegistry otelMeterRegistry =
            // OpenTelemetryMeterRegistry.builder(openTelemetry)
            // .setPrometheusMode(true)
            // .build();
            // Metrics.addRegistry(otelMeterRegistry);

         } catch (Throwable t) {
            t.printStackTrace();
         }

         otelMeterRegistry = OpenTelemetryMeterRegistry.builder(openTelemetry).setPrometheusMode(true)
               .build();
         // store.put(MeterRegistry.class, otelMeterRegistry);
         Metrics.addRegistry(otelMeterRegistry);

      } catch (Throwable t) {
         t.printStackTrace();
      }

      return this;
   }

   @Override
   public MeterRegistry getRegistry() {
      return otelMeterRegistry;
   }
}