/**
 * ACTIVAGE. ACTivating InnoVative IoT smart living environments for AGEing well.
 * ACTIVAGE is a R&D project which has received funding from the European 
 * Union’s Horizon 2020 research and innovation programme under grant 
 * agreement No 732679.
 * 
 * Copyright (C) 2016-2018, by :
 * - Universitat Politècnica de València, http://www.upv.es/
 * 
 *
 * For more information, contact:
 * - @author <a href="mailto:majuse@upv.es">Matilde Julián</a>  
 * - Project coordinator:  <a href="mailto:coordinator@activage.eu"></a>
 *  
 *
 *    This code is licensed under the EPL license, available at the root
 *    application directory.
 */
package eu.interiot.intermw.bridge.sofia2;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import eu.interiot.intermw.commons.DefaultConfiguration;
import eu.interiot.intermw.commons.interfaces.Configuration;
import eu.interiot.intermw.commons.model.Platform;
import eu.interiot.message.ID.EntityID;
import eu.interiot.message.Message;
import eu.interiot.message.managers.URI.URIManagerMessageMetadata;
import eu.interiot.message.managers.URI.URIManagerMessageMetadata.MessageTypesEnum;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import spark.Spark;

import java.net.URL;
import java.util.Date;
import java.util.Set;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class Sofia2BridgeTest {
    private Sofia2PlatformEmulator platformEmulator;

    @Before
    public void setUp() throws Exception {
        platformEmulator = new Sofia2PlatformEmulator(4569, 5);
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
//        URL callbackUrl = new URL(configuration.getProperty("sofia2-callback-address"));
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

        Sofia2Bridge sofiaBridge = new Sofia2Bridge(configuration, platform);
        PublisherMock<Message> publisher = new PublisherMock<>();
        sofiaBridge.setPublisher(publisher);

        // register platform
        sofiaBridge.send(platformRegisterMsg);
        Message responseMsg = publisher.retrieveMessage();
        Set<MessageTypesEnum> messageTypesEnumSet = responseMsg.getMetadata().getMessageTypes();
        assertTrue(messageTypesEnumSet.contains(MessageTypesEnum.RESPONSE));
        assertTrue(messageTypesEnumSet.contains(URIManagerMessageMetadata.MessageTypesEnum.PLATFORM_REGISTER));
        
        // register thing
//        sofiaBridge.send(thingRegisterMsg);
//        Message responseMsg2 = publisher.retrieveMessage();
//        Set<MessageTypesEnum> messageTypesEnumSet2 = responseMsg2.getMetadata().getMessageTypes();
//        assertTrue(messageTypesEnumSet2.contains(MessageTypesEnum.RESPONSE));
//        assertTrue(messageTypesEnumSet2.contains(URIManagerMessageMetadata.MessageTypesEnum.PLATFORM_CREATE_DEVICE));

        // subscribe to thing
        sofiaBridge.send(thingSubscribeMsg);
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