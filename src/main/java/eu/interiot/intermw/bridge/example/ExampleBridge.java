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
import com.google.common.base.Strings;
import eu.interiot.intermw.bridge.abstracts.AbstractBridge;
import eu.interiot.intermw.bridge.exceptions.BridgeException;
import eu.interiot.intermw.comm.broker.exceptions.BrokerException;
import eu.interiot.intermw.commons.exceptions.MiddlewareException;
import eu.interiot.intermw.commons.exceptions.UnknownActionException;
import eu.interiot.intermw.commons.exceptions.UnsupportedActionException;
import eu.interiot.intermw.commons.interfaces.Configuration;
import eu.interiot.intermw.commons.model.Platform;
import eu.interiot.intermw.commons.model.SubscriptionId;
import eu.interiot.message.EntityID;
import eu.interiot.message.Message;
import eu.interiot.message.MessageMetadata;
import eu.interiot.message.MessagePayload;
import eu.interiot.message.URI.URIManagerMessageMetadata;
import eu.interiot.message.exceptions.MessageException;
import eu.interiot.message.exceptions.payload.PayloadException;
import eu.interiot.message.metaTypes.PlatformMessageMetadata;
import eu.interiot.message.utils.INTERMWDemoUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Spark;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

@eu.interiot.intermw.bridge.annotations.Bridge(platformType = "ExamplePlatform")
public class ExampleBridge extends AbstractBridge {
    private final Logger logger = LoggerFactory.getLogger(ExampleBridge.class);
    private URL bridgeCallbackUrl;
    private String exampleUser;
    private String examplePassword;
    private HttpClient httpClient;
    ObjectMapper objectMapper;

    public ExampleBridge(Configuration configuration, Platform platform) throws MiddlewareException {
        super(configuration, platform);
        logger.debug("Example bridge is initializing...");
        Properties properties = configuration.getProperties();

        try {
            bridgeCallbackUrl = new URL(configuration.getProperty("bridge.callback.address"));
            exampleUser = properties.getProperty("example.user");
            examplePassword = properties.getProperty("example.password");

        } catch (Exception e) {
            throw new BridgeException("Failed to read Example bridge configuration: " + e.getMessage());
        }

        if (bridgeCallbackUrl == null ||
                Strings.isNullOrEmpty(exampleUser) ||
                Strings.isNullOrEmpty(examplePassword)) {
            throw new BridgeException("Invalid Example bridge configuration.");
        }

        httpClient = HttpClientBuilder.create().build();
        objectMapper = new ObjectMapper();

        logger.info("Example bridge has been initialized successfully.");
    }

    @Override
    public void send(Message message) throws BridgeException {
        Set<URIManagerMessageMetadata.MessageTypesEnum> messageTypesEnumSet = message.getMetadata().getMessageTypes();
        try {
            if (messageTypesEnumSet.contains(URIManagerMessageMetadata.MessageTypesEnum.PLATFORM_REGISTER)) {
                // When  registering a new platform (an instance of Example platform), a new ExampleBridge instance is created.
                // After that, the bridge receives corresponding PLATFORM_REGISTER message which was translated by IPSM to do any further processing.
                Set<String> entityIDs = INTERMWDemoUtils.getEntityIDsFromPayload(
                        message.getPayload(), INTERMWDemoUtils.EntityTypePlatform);
                if (entityIDs.size() != 1) {
                    throw new BridgeException("Missing platform ID.");
                }

                registerPlatform(entityIDs.iterator().next());

            } else if (messageTypesEnumSet.contains(URIManagerMessageMetadata.MessageTypesEnum.PLATFORM_UNREGISTER)) {
                Set<String> entityIDs = INTERMWDemoUtils.getEntityIDsFromPayload(
                        message.getPayload(), INTERMWDemoUtils.EntityTypePlatform);
                if (entityIDs.size() != 1) {
                    throw new BridgeException("Missing platform ID.");
                }

                unregisterPlatform(entityIDs.iterator().next());

            } else if (messageTypesEnumSet.contains(URIManagerMessageMetadata.MessageTypesEnum.THING_REGISTER)) {
                Set<String> entityIds = INTERMWDemoUtils.getEntityIDsFromPayload(message.getPayload(),
                        INTERMWDemoUtils.EntityTypeDevice);

                for (String entityId : entityIds) {
                    registerThing(entityId);
                }

            } else if (messageTypesEnumSet.contains(URIManagerMessageMetadata.MessageTypesEnum.THING_UNREGISTER)) {
                Set<String> entityIds = INTERMWDemoUtils.getEntityIDsFromPayload(message.getPayload(),
                        INTERMWDemoUtils.EntityTypeDevice);
                for (String entityId : entityIds) {
                    unregisterThing(entityId);
                }

            } else if (messageTypesEnumSet.contains(URIManagerMessageMetadata.MessageTypesEnum.THING_UPDATE)) {
                if (messageTypesEnumSet.contains(URIManagerMessageMetadata.MessageTypesEnum.OBSERVATION)) {
                    Set<String> entities = INTERMWDemoUtils.getEntityIDsFromPayload(message.getPayload(),
                            INTERMWDemoUtils.EntityTypeDevice);
                    if (entities.isEmpty()) {
                        throw new BridgeException("No entities of type Device found in the Payload.");
                    }
                    String entity = entities.iterator().next();
                    updateThing(entity, message);
                }

            } else if (messageTypesEnumSet.contains(URIManagerMessageMetadata.MessageTypesEnum.QUERY)) {
                throw new UnsupportedActionException("QUERY operation is currently not supported.");

            } else if (messageTypesEnumSet.contains(URIManagerMessageMetadata.MessageTypesEnum.SUBSCRIBE)) {
                Set<String> entities = INTERMWDemoUtils.getEntityIDsFromPayload(message.getPayload(),
                        INTERMWDemoUtils.EntityTypeDevice);
                if (entities.isEmpty()) {
                    throw new PayloadException("No entities of type Device found in the Payload.");
                } else if (entities.size() > 1) {
                    throw new PayloadException("Only one device is supported by Subscribe operation.");
                }

                String entity = entities.iterator().next();
                subscribe(entity, message.getMetadata().getConversationId().get());

            } else if (messageTypesEnumSet.contains(URIManagerMessageMetadata.MessageTypesEnum.UNSUBSCRIBE)) {
                String conversationID = message.getMetadata().getConversationId().get();
                unsubscribe(new SubscriptionId(conversationID));

            } else if (messageTypesEnumSet.contains(URIManagerMessageMetadata.MessageTypesEnum.DISCOVERY)) {
                throw new UnsupportedActionException("The DISCOVERY operation is currently not supported.");

            } else if (messageTypesEnumSet.contains(URIManagerMessageMetadata.MessageTypesEnum.UNRECOGNIZED)) {
                throw new UnknownActionException(String.format(
                        "The action is labelled as UNRECOGNIZED and thus is unprocessable by component %s in platform %s.",
                        this.getClass().getName(), platform.getId().getId()));
            } else {
                throw new UnknownActionException(String.format(
                        "The message type is not properly handled and can't be processed by component %s in platform %s.",
                        this.getClass().getName(), platform.getId().getId()));
            }

            Message responseMessage = createResponseMessage(message);
            try {
                publisher.publish(responseMessage);
            } catch (BrokerException e) {
                throw new MessageException("Failed to publish response message: " + e.getMessage(), e);
            }

        } catch (Exception e) {
            logger.error("Failed to process request: " + e.getMessage(), e);
            throw new BridgeException("Example bridge failed to process request: " + e.getMessage(), e);
        }
    }

    private void registerPlatform(String platformId) {
        logger.debug("Registering platform {}...", platformId);
        logger.debug("Platform {} has been registered.", platformId);
    }

    private void unregisterPlatform(String platformId) {
        logger.debug("Unregistering platform {}...", platformId);
    }

    private void registerThing(String entityId) throws Exception {
        logger.debug("Registering thing {}...", entityId);
        HttpPost httpPost = new HttpPost(platform.getBaseURL() + "/things/register");
        Map<String, Object> data = new HashMap<>();
        data.put("thingId", entityId);
        String json = objectMapper.writeValueAsString(data);
        HttpEntity httpEntity = new StringEntity(json, ContentType.APPLICATION_JSON);
        httpPost.setEntity(httpEntity);
        HttpResponse response = httpClient.execute(httpPost);
        logger.debug("Received response from the platform: {}", response.getStatusLine());
    }

    private void unregisterThing(String entityId) {
        logger.debug("Unregistering thing {}...", entityId);
    }

    private void updateThing(String entityId, Message message) {
        logger.debug("Updating thing {}...", entityId);
    }

    private void subscribe(String entityId, String conversationId) throws Exception {
        logger.debug("Subscribing to thing {} using conversationId {}...", entityId, conversationId);

        URL callbackUrl = new URL(bridgeCallbackUrl, conversationId);

        HttpPost httpPost = new HttpPost(platform.getBaseURL() + "/things/subscribe");
        Map<String, Object> data = new HashMap<>();
        data.put("thingId", entityId);
        data.put("conversationId", conversationId);
        data.put("callbackUrl", callbackUrl.toString());
        String json = objectMapper.writeValueAsString(data);
        HttpEntity httpEntity = new StringEntity(json, ContentType.APPLICATION_JSON);
        httpPost.setEntity(httpEntity);
        HttpResponse resp = httpClient.execute(httpPost);
        logger.debug("Received response from the platform: {}", resp.getStatusLine());

        logger.debug("Creating callback listener listening at {}...", callbackUrl);
        Spark.post(conversationId, (request, response) -> {
            logger.debug("Received observation from the platform.");
            PlatformMessageMetadata metadata = new MessageMetadata().asPlatformMessageMetadata();
            metadata.initializeMetadata();
            metadata.addMessageType(URIManagerMessageMetadata.MessageTypesEnum.OBSERVATION);
            metadata.addMessageType(URIManagerMessageMetadata.MessageTypesEnum.RESPONSE);
            metadata.setSenderPlatformId(new EntityID(platform.getId().getId()));
            metadata.setConversationId(conversationId);

            String observationN3 = request.body();
            Model m = ModelFactory.createDefaultModel();
            InputStream inStream = new ByteArrayInputStream(observationN3.getBytes());
            RDFDataMgr.read(m, inStream, Lang.N3);
            MessagePayload messagePayload = new MessagePayload(m);

            Message observationMessage = new Message();
            observationMessage.setMetadata(metadata);
            observationMessage.setPayload(messagePayload);

            publisher.publish(observationMessage);
            logger.debug("Observation message has been published upstream.");

            response.status(204);
            return "";
        });
        logger.debug("Successfully subscribed to thing {}.", entityId);
    }

    private void unsubscribe(SubscriptionId subscriptionId) {
        logger.debug("Unsubscribing from thing {}...", subscriptionId.getId());
    }
}
