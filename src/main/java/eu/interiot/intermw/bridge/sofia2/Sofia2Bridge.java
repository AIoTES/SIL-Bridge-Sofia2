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

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import eu.interiot.intermw.bridge.abstracts.AbstractBridge;
import eu.interiot.intermw.bridge.exceptions.BridgeException;
import eu.interiot.intermw.commons.exceptions.MiddlewareException;
import eu.interiot.intermw.commons.interfaces.Configuration;
import eu.interiot.intermw.commons.model.Platform;
import eu.interiot.message.ID.EntityID;
import eu.interiot.message.Message;
import eu.interiot.message.MessageMetadata;
import eu.interiot.message.MessagePayload;
import eu.interiot.message.managers.URI.URIManagerMessageMetadata;
import eu.interiot.message.managers.URI.URIManagerMessageMetadata.MessageTypesEnum;
import eu.interiot.message.exceptions.payload.PayloadException;
import eu.interiot.message.metadata.PlatformMessageMetadata;
import eu.interiot.message.utils.INTERMWDemoUtils;
import eu.interiot.message.utils.MessageUtils;

import org.apache.jena.rdf.model.Model;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Spark;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

@eu.interiot.intermw.bridge.annotations.Bridge(platformType = "sofia2")
public class Sofia2Bridge extends AbstractBridge {
    private final Logger logger = LoggerFactory.getLogger(Sofia2Bridge.class);
    final static String PROPERTIES_PREFIX = "sofia2-";
//    private static final String DEFAULT_URL = "https://sofia2.com/";
    private URL bridgeCallbackUrl;
   
	private Map<String,String> subscriptionIds = new HashMap<String,String>(); 
    
    private Sofia2Client client;

    public Sofia2Bridge(Configuration configuration, Platform platform) throws MiddlewareException {
        super(configuration, platform);
        logger.debug("SOFIA2 bridge is initializing...");
        Properties properties = configuration.getProperties();
        // TODO: CHECK BRIDGE CONFIGURATION
        try {
//            bridgeCallbackUrl = new URL(configuration.getProperty(PROPERTIES_PREFIX + "callback-address"));
            bridgeCallbackUrl = new URL(configuration.getProperty("bridge.callback.address")); // SAME CALLBACK FOR ALL THE BRIDGES IN ONE INSTANCE OF AIoTES
        } catch (Exception e) {
            throw new BridgeException("Failed to read SOFIA2 bridge configuration: " + e.getMessage());
        }
        
        if (bridgeCallbackUrl == null) {
            throw new BridgeException("Invalid SOFIA2 bridge configuration.");
        }
        
        try{
        	client = new Sofia2Client(properties);
        }catch (Exception e) {
        	throw new BridgeException(e);
        }
                
        logger.info("SOFIA2 bridge has been initialized successfully.");
    }
    
    
	@Override
	public Message registerPlatform(Message message) throws Exception {
		// SSAP JOIN
		Message responseMessage = MessageUtils.createResponseMessage(message);
		Set<String> entityIDs = Sofia2Utils.getEntityIDsFromPayload(message.getPayload(), Sofia2Utils.EntityTypePlatform);  // Instead of class INTERMWDemoUtils
        if (entityIDs.size() != 1) {
            throw new BridgeException("Missing platform ID.");
        }
        String platformId = entityIDs.iterator().next();
        logger.debug("Registering platform {}...", platformId);
        try {
			client.join();
			logger.debug("Platform {} has been registered.", platformId);
		} catch (Exception e) {
			logger.error("Register Platform  " + e);
			e.printStackTrace();
			responseMessage.getMetadata().setStatus("KO");
			responseMessage.getMetadata().setMessageType(MessageTypesEnum.ERROR);
			responseMessage.getMetadata().asErrorMessageMetadata().setExceptionStackTrace(e);
		}
        return responseMessage;
	}
	
	@Override
	public Message unregisterPlatform(Message message) throws Exception {
		// SSAP LEAVE
		Message responseMessage = MessageUtils.createResponseMessage(message);
		Set<String> entityIDs = Sofia2Utils.getEntityIDsFromPayload(message.getPayload(), Sofia2Utils.EntityTypePlatform); // Instead of class INTERMWDemoUtils
        if (entityIDs.size() != 1) {
            throw new BridgeException("Missing platform ID.");
        }
        String platformId = entityIDs.iterator().next();
        logger.debug("Unregistering platform {}...", platformId);
        try {
			client.leave();
			logger.debug("Platform {} has been unregistered.", platformId);
			subscriptionIds.clear();
		} catch (Exception e) {
			logger.error("Unregister Platform  " + e);
			e.printStackTrace();
			responseMessage.getMetadata().setStatus("KO");
			responseMessage.getMetadata().setMessageType(MessageTypesEnum.ERROR);
			responseMessage.getMetadata().asErrorMessageMetadata().setExceptionStackTrace(e);
		}
        return responseMessage;
	}

	@Override
	public Message subscribe(Message message) throws Exception {
		// TODO: USE SOFIA2 TRANSLATOR TO GENERATE SUBSCRIBE QUERY FOR SOFIA2
		Message responseMessage = MessageUtils.createResponseMessage(message);
		Set<String> entities = INTERMWDemoUtils.getEntityIDsFromPayload(message.getPayload(), INTERMWDemoUtils.EntityTypeDevice);
//		Set<String> entities = Sofia2Utils.getEntityIDsFromPayload(message.getPayload(), Sofia2Utils.EntityTypeDevice); // Instead of class INTERMWDemoUtils
		if (entities.isEmpty()) {
            throw new PayloadException("No entities of type Device found in the Payload.");
        } else if (entities.size() > 1) {
            throw new PayloadException("Only one device is supported by Subscribe operation.");
        }
		
//		String thingId = entities.iterator().next();
		String thingId = Sofia2Utils.filterThingID(entities.iterator().next());
	    String conversationId = message.getMetadata().getConversationId().orElse(null);
	    logger.debug("Subscribing to thing {} using conversationId {}...", thingId, conversationId);
	    
		try{
			Sofia2Translator translator = new Sofia2Translator();
			URL callbackUrl = new URL(bridgeCallbackUrl, conversationId);
			String subId = client.subscribe(thingId, callbackUrl.toString());
			
			subscriptionIds.put(thingId, subId); // SUBSCRIPTION ID IS NEEDER FOR UNSUBSCRIBE METHOD
			
			Spark.put(conversationId, (request, response) -> { // SOFIA2 sends data using a HTTP PUT query
	            logger.debug("Received observation from the platform.");
	            PlatformMessageMetadata metadata = new MessageMetadata().asPlatformMessageMetadata();
	            metadata.initializeMetadata();
	            metadata.addMessageType(URIManagerMessageMetadata.MessageTypesEnum.OBSERVATION);
	            metadata.addMessageType(URIManagerMessageMetadata.MessageTypesEnum.RESPONSE);
	            metadata.setSenderPlatformId(new EntityID(platform.getId().getId()));
	            metadata.setConversationId(conversationId);
	
//	            String observation = request.body();
	            // TODO: CHECK PLATFORM MESSAGE FORMAT
	            JsonParser parser = new JsonParser();
	    		JsonObject ssapObject = parser.parse(request.body().toString()).getAsJsonObject();
	    		String observation = ssapObject.get("data").getAsString();
	            
	            Model translatedModel = translator.toJenaModel(observation); //TODO: CHECK THIS
	    		// Create a new message payload for the response message
	    		MessagePayload responsePayload = new MessagePayload(translatedModel);
	            	            
	            Message observationMessage = new Message();
	            observationMessage.setMetadata(metadata);
	            observationMessage.setPayload(responsePayload); 
	
	            publisher.publish(observationMessage);
	            logger.debug("Observation message has been published upstream.");
	
	            response.status(200);
	            return "";
	        });
			
		} catch (Exception e){ 
			logger.error("Error subscribing: " + e.getMessage());
			responseMessage.getMetadata().setStatus("KO");
			responseMessage.getMetadata().setMessageType(MessageTypesEnum.ERROR);
			responseMessage.getMetadata().asErrorMessageMetadata().setExceptionStackTrace(e);
		}

		return responseMessage;
	}

	
	@Override
	public Message unsubscribe(Message message) throws Exception {
		Message responseMessage = MessageUtils.createResponseMessage(message);
		String conversationID = message.getMetadata().getConversationId().orElse(null);
		
		try{
			Set<String> entityIds = Sofia2Utils.getEntityIds(message);

			for (String entityId : entityIds) {
				logger.info("Unsubscribing from thing {}...", entityId);
				String subId = subscriptionIds.get(entityId); // RETRIEVE SUBSCRIPTION ID
				String responseBody = client.unsubscribe(subId);
				
				subscriptionIds.remove(entityId);
				
				// TODO: USE SOFIA2 TRANSLATOR
//				Sofia2Translator translator = new Sofia2Translator();
//				Model translatedModel = translator.toJenaModel(responseBody);			
//				MessagePayload responsePayload = new MessagePayload(translatedModel);
//				responseMessage.setPayload(responsePayload);
			}
		} catch (Exception e){ 
			logger.error("Error unsubscribing: " + e.getMessage());
			e.printStackTrace();
			responseMessage.getMetadata().setStatus("KO");
			responseMessage.getMetadata().setMessageType(MessageTypesEnum.ERROR);
			responseMessage.getMetadata().asErrorMessageMetadata().setExceptionStackTrace(e);
		}
		
		return responseMessage;
	}
	
	@Override
	public Message query(Message message) throws Exception {
		// TODO: CHECK DEVICE ID
		Message responseMessage = MessageUtils.createResponseMessage(message);
		try{
			Set<String> deviceIds = Sofia2Utils.getEntityIds(message);
			for(String deviceId : deviceIds){
				String thingId = Sofia2Utils.filterThingID(deviceId);
				String responseBody = client.query(thingId);
				Sofia2Translator translator = new Sofia2Translator();
				// Create the model from the response JSON
				Model translatedModel = translator.toJenaModel(responseBody);
				// Create a new message payload for the response message
				MessagePayload responsePayload = new MessagePayload(translatedModel);
				// Attach the payload to the message
				responseMessage.setPayload(responsePayload);
			}
			// Set the OK status
			responseMessage.getMetadata().setStatus("OK");
			// Publish the message to INTER-MW
			publisher.publish(responseMessage);
		}
		catch (Exception e) {
			logger.error("Error in query: " + e.getMessage());
			responseMessage.getMetadata().setStatus("KO");
			responseMessage.getMetadata().setMessageType(MessageTypesEnum.ERROR);
			responseMessage.getMetadata().asErrorMessageMetadata().setExceptionStackTrace(e);
		}
		return responseMessage;
	}
	
	@Override
	public Message listDevices(Message message) throws Exception {
		Message responseMessage = MessageUtils.createResponseMessage(message);
		try{
			// Discover all the registered devices
			String responseBody = client.list();
			Sofia2Translator translator = new Sofia2Translator();
			// Create the model from the response JSON
			Model translatedModel = translator.toJenaModel(responseBody);
			// Create a new message payload for the response message
			MessagePayload responsePayload = new MessagePayload(translatedModel);
			// Attach the payload to the message
			responseMessage.setPayload(responsePayload);
			// Set the OK status
			responseMessage.getMetadata().setStatus("OK");
		}
		catch (Exception e) {
			logger.error("Error in query: " + e.getMessage());
			responseMessage.getMetadata().setStatus("KO");
			responseMessage.getMetadata().setMessageType(MessageTypesEnum.ERROR);
			responseMessage.getMetadata().asErrorMessageMetadata().setExceptionStackTrace(e);
		}
		return responseMessage;
	}
	
	@Override
	public Message platformCreateDevice(Message message) throws Exception {
		// TODO: USE SOFIA2 TRANSLATOR
		Message responseMessage = MessageUtils.createResponseMessage(message);
//		Sofia2Translator translator = new Sofia2Translator();
		try{
//			String body = translator.toFormatX(message.getPayload().getJenaModel());
			
			Set<String> entityIds = Sofia2Utils.getEntityIds(message);

			for (String entityId : entityIds) {
				String thingId = Sofia2Utils.filterThingID(entityId);
				logger.debug("Registering thing {}...", thingId);
				client.register(thingId);
	    		logger.debug("Success");
			}
    	}catch(Exception e){
    		logger.error("Error creating devices: " + e.getMessage());
			responseMessage.getMetadata().setStatus("KO");
			responseMessage.getMetadata().setMessageType(MessageTypesEnum.ERROR);
			responseMessage.getMetadata().asErrorMessageMetadata().setExceptionStackTrace(e);
    	}
		return responseMessage;
	}
	
	
	@Override
	public Message platformUpdateDevice(Message message) throws Exception {
		// TODO
		// USE SOFIA2 TRANSLATOR TO GENERATE UPDATE MESSAGE DATA
		return null;
	}
	
	@Override
	public Message platformDeleteDevice(Message message) throws Exception {
		// TODO: CHECK DEVICE ID
		Message responseMessage = MessageUtils.createResponseMessage(message);
		try {
			logger.debug("Removing devices...");
			Set<String> deviceIds = Sofia2Utils.getEntityIds(message);
			for(String deviceId : deviceIds){
				String transformedId = Sofia2Utils.filterThingID(deviceId);
				client.delete(transformedId);
				logger.debug("Device {} has been removed.", transformedId);
			}
			responseMessage.getMetadata().setStatus("OK");
		} 
		catch (Exception e) {
			logger.error("Error removing devices: " + e.getMessage());
			responseMessage.getMetadata().setStatus("KO");
			responseMessage.getMetadata().setMessageType(MessageTypesEnum.ERROR);
			responseMessage.getMetadata().asErrorMessageMetadata().setExceptionStackTrace(e);
		}
		return responseMessage;
	}
	
	@Override
	public Message observe(Message message) throws Exception {
		// TODO
		// TRANSLATE MESSAGE PAYLOAD TO FORMAT X AND SEND TO PLATFORM
		return null;
	}
	
	@Override
	public Message actuate(Message message) throws Exception {
		// TODO
		// TRANSLATE MESSAGE PAYLOAD TO FORMAT X AND SEND TO PLATFORM
		return null;
	}

	@Override
	public Message error(Message message) throws Exception {
		logger.debug("Error occured in {}...", message);
		Message responseMessage = MessageUtils.createResponseMessage(message);
		responseMessage.getMetadata().setStatus("KO");
		responseMessage.getMetadata().setMessageType(MessageTypesEnum.ERROR);
		return responseMessage;
	}

	@Override
	public Message unrecognized(Message message) throws Exception {
		logger.debug("Unrecognized message type.");
		Message responseMessage = MessageUtils.createResponseMessage(message);
		responseMessage.getMetadata().setStatus("OK");
		return responseMessage;
	}
	
}
