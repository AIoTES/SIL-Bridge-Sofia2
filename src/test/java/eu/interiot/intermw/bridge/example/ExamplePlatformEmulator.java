/**
 * INTER-IoT. Interoperability of IoT Platforms.
 * INTER-IoT is a R&D project which has received funding from the European
 * Union’s Horizon 2020 research and innovation programme under grant
 * agreement No 687283.
 * <p>
 * Copyright (C) 2016-2018, by (Author's company of this file):
 * - XLAB d.o.o.
 * <p>
 * This code is licensed under the EPL license, available at the root
 * application directory.
 */
package eu.interiot.intermw.bridge.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;

import eu.interiot.intermw.bridge.sofia2.Sofia2Bridge;

import org.apache.http.HttpEntity;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Service;

import java.net.URL;
import java.util.*;

public class ExamplePlatformEmulator {
    private final Logger logger = LoggerFactory.getLogger(Sofia2Bridge.class);
    private Map<String, Subscription> subscriptions = new HashMap<>();
    private Map<String, Thread> subscriptionThreads = new HashMap<>();
    private ObjectMapper objectMapper = new ObjectMapper();
    private int port;
    private int observationsDelay;
    private Service spark;

    public ExamplePlatformEmulator(int port, int observationsDelay) {
        this.port = port;
        this.observationsDelay = observationsDelay;
    }

    public void start() throws Exception {
        logger.debug("ExamplePlatformEmulator is initializing...");
        Set<String> registeredThings = new HashSet<>();

        spark = Service.ignite()
                .port(port);

        spark.post("/things/register", (request, response) -> {
            logger.debug("Received /things/register request.");
            ThingRegisterInput input;
            try {
                input = objectMapper.readValue(request.body(), ThingRegisterInput.class);
            } catch (Exception e) {
                response.status(400);
                return e.getMessage();
            }

            if (registeredThings.contains(input.thingId)) {
                response.status(409);
                return "Thing '" + input.thingId + "' is already registered.";
            } else {
                registeredThings.add(input.thingId);
                logger.debug("Thing '{}' has been registered.", input.thingId);
                response.status(204);
                return "";
            }
        });

        spark.post("/things/subscribe", (request, response) -> {
            logger.debug("Received /things/subscribe request.");
            ThingSubscribeInput input;
            try {
                input = objectMapper.readValue(request.body(), ThingSubscribeInput.class);
            } catch (Exception e) {
                response.status(400);
                return e.getMessage();
            }

            if (subscriptions.containsKey(input.conversationId)) {
                response.status(409);
                return "Already subscribed.";
            }
            if (!registeredThings.contains(input.thingId)) {
                response.status(400);
                return "Thing '" + input.thingId + "' is not registered.";
            }

            Subscription subscription = new Subscription(input.thingId, input.callbackUrl, input.conversationId);
            subscriptions.put(input.conversationId, subscription);

            ObservationsPublisher obsPublisher = new ObservationsPublisher(subscription);
            Thread obsPublisherThread = new Thread(obsPublisher);
            obsPublisherThread.start();
            subscriptionThreads.put(input.conversationId, obsPublisherThread);

            logger.debug("Subscribed to thing '{}' with conversationId {}.", input.thingId, input.conversationId);
            response.status(200);
            return "";
        });

        logger.info("ExamplePlatformEmulator is listening on port {}.", port);
    }

    public void stop() {
        spark.stop();
        for (Thread thread : subscriptionThreads.values()) {
            thread.interrupt();
        }
        logger.debug("ExamplePlatformEmulator has stoped.");
    }

    public static void main(String[] args) throws Exception {
        new ExamplePlatformEmulator(4568, 10).start();
    }

    class ObservationsPublisher implements Runnable {
        private String observationN3Template;
        private Subscription subscription;

        public ObservationsPublisher(Subscription subscription) throws Exception {
            this.subscription = subscription;
            URL url = Resources.getResource("observations/example-sensor-observation.n3");
            observationN3Template = Resources.toString(url, Charsets.UTF_8);
        }

        @Override
        public void run() {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                return;
            }
            HttpClient httpClient = HttpClientBuilder.create().build();

            while (!Thread.interrupted()) {
                try {
                    HttpPost httpPost = new HttpPost(subscription.getCallbackUrl());
                    String observationN3 = observationN3Template.replace("%TIMESTAMP%", Long.toString(new Date().getTime()));
                    HttpEntity httpEntity = new StringEntity(observationN3, ContentType.getByMimeType("text/n3"));
                    httpPost.setEntity(httpEntity);
                    httpClient.execute(httpPost);
                    logger.debug("Observation for thing {} has been sent to {}.", subscription.getThingId(),
                            subscription.getCallbackUrl());

                } catch (Exception e) {
                    logger.error("Failed to send observation to {}.", subscription.getCallbackUrl());
                }
                try {
                    Thread.sleep(observationsDelay * 1000);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }
    }

    static class Subscription {
        private String thingId;
        private String callbackUrl;
        private String conversationId;

        public Subscription(String thingId, String callbackUrl, String conversationId) {
            this.thingId = thingId;
            this.callbackUrl = callbackUrl;
            this.conversationId = conversationId;
        }

        public String getThingId() {
            return thingId;
        }

        public String getCallbackUrl() {
            return callbackUrl;
        }

        public String getConversationId() {
            return conversationId;
        }
    }

    static class ThingRegisterInput {
        public String thingId;
    }

    static class ThingSubscribeInput {
        public String thingId;
        public String callbackUrl;
        public String conversationId;
    }
}
