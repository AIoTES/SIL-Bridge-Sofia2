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

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import eu.interiot.intermw.commons.DefaultConfiguration;
import eu.interiot.intermw.commons.interfaces.Configuration;
import eu.interiot.intermw.commons.model.Platform;
import eu.interiot.message.EntityID;
import eu.interiot.message.Message;
import eu.interiot.message.URI.URIManagerMessageMetadata.MessageTypesEnum;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import spark.Spark;

import java.net.URL;
import java.util.Date;
import java.util.Set;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ExampleBridgeTest {
    private ExamplePlatformEmulator platformEmulator;

    @Before
    public void setUp() throws Exception {
        platformEmulator = new ExamplePlatformEmulator(4569, 5);
        platformEmulator.start();
    }

    @After
    public void tearDown() {
        platformEmulator.stop();
    }

    @Test
    public void testBridge() throws Exception {
        Configuration configuration = new DefaultConfiguration("*.bridge.properties");

        URL callbackUrl = new URL(configuration.getProperty("bridge.callback.address"));
        int callbackPort = callbackUrl.getPort();
        Spark.port(callbackPort);

        // create Message objects from serialized messages
        URL url1 = Resources.getResource("messages/platform-register.json");
        String platformRegisterJson = Resources.toString(url1, Charsets.UTF_8);
        Message platformRegisterMsg = new Message(platformRegisterJson);

        URL url2 = Resources.getResource("messages/thing-register.json");
        String thingRegisterJson = Resources.toString(url2, Charsets.UTF_8);
        Message thingRegisterMsg = new Message(thingRegisterJson);

        URL url3 = Resources.getResource("messages/thing-subscribe.json");
        String thingSubscribeJson = Resources.toString(url3, Charsets.UTF_8);
        Message thingSubscribeMsg = new Message(thingSubscribeJson);

        // create Platform object using platform-register message
        EntityID platformId = platformRegisterMsg.getMetadata().asPlatformMessageMetadata().getReceivingPlatformIDs().iterator().next();
        Platform platform = new Platform(platformId.toString(), platformRegisterMsg.getPayload());

        ExampleBridge exampleBridge = new ExampleBridge(configuration, platform);
        PublisherMock<Message> publisher = new PublisherMock<>();
        exampleBridge.setPublisher(publisher);

        // register platform
        exampleBridge.send(platformRegisterMsg);
        Message responseMsg = publisher.retrieveMessage();
        Set<MessageTypesEnum> messageTypesEnumSet = responseMsg.getMetadata().getMessageTypes();
        assertTrue(messageTypesEnumSet.contains(MessageTypesEnum.RESPONSE));
        assertTrue(messageTypesEnumSet.contains(MessageTypesEnum.PLATFORM_REGISTER));

        // register thing
        exampleBridge.send(thingRegisterMsg);
        responseMsg = publisher.retrieveMessage();
        messageTypesEnumSet = responseMsg.getMetadata().getMessageTypes();
        assertTrue(messageTypesEnumSet.contains(MessageTypesEnum.RESPONSE));
        assertTrue(messageTypesEnumSet.contains(MessageTypesEnum.THING_REGISTER));

        // subscribe to thing
        exampleBridge.send(thingSubscribeMsg);
        responseMsg = publisher.retrieveMessage();
        messageTypesEnumSet = responseMsg.getMetadata().getMessageTypes();
        assertTrue(messageTypesEnumSet.contains(MessageTypesEnum.RESPONSE));
        assertTrue(messageTypesEnumSet.contains(MessageTypesEnum.SUBSCRIBE));

        // wait for observation message
        Long startTime = new Date().getTime();
        Message observationMsg = null;
        do {
            Thread.sleep(1000);
            observationMsg = publisher.retrieveMessage();
            if (observationMsg != null) {
                break;
            }
        } while (new Date().getTime() - startTime < 20000);

        if (observationMsg != null) {
            messageTypesEnumSet = observationMsg.getMetadata().getMessageTypes();
            assertTrue(messageTypesEnumSet.contains(MessageTypesEnum.RESPONSE));
            assertTrue(messageTypesEnumSet.contains(MessageTypesEnum.OBSERVATION));
        } else {
            fail("Timeout waiting for observation messages.");
        }
    }
}
