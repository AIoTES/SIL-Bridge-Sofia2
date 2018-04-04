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

import com.google.common.base.Strings;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import eu.interiot.intermw.bridge.abstracts.AbstractBridge;
import eu.interiot.intermw.bridge.exceptions.BridgeException;
import eu.interiot.intermw.commons.exceptions.MiddlewareException;
import eu.interiot.intermw.commons.interfaces.Configuration;
import eu.interiot.intermw.commons.model.Platform;
import eu.interiot.message.EntityID;
import eu.interiot.message.Message;
import eu.interiot.message.MessageMetadata;
import eu.interiot.message.MessagePayload;
import eu.interiot.message.URI.URIManagerMessageMetadata;
import eu.interiot.message.URI.URIManagerMessageMetadata.MessageTypesEnum;
import eu.interiot.message.exceptions.payload.PayloadException;
import eu.interiot.message.metaTypes.PlatformMessageMetadata;
//import eu.interiot.message.utils.INTERMWDemoUtils;
import org.apache.jena.rdf.model.Model;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Spark;

import java.net.URL;
import java.util.Properties;
import java.util.Set;

@eu.interiot.intermw.bridge.annotations.Bridge(platformType = "sofia2")
public class Sofia2Bridge extends AbstractBridge {
    private final Logger logger = LoggerFactory.getLogger(Sofia2Bridge.class);
    private final static String PROPERTIES_PREFIX = "sofia2-";
    private static final String DEFAULT_URL = "https://sofia2.televes.com/"; // TODO: CHANGE DEFAULT URL. ADD URL TO PROPERTIES
    private URL bridgeCallbackUrl;
    private String sofiaUser;
    private String sofiaPassword;
    private String sofiaUrl;
    private String TOKEN; // AUTHENTICATION TOKEN. (INCLUDE IT IN THE BRIDGE CONFIGURATION?)
   
    
    private Sofia2Client client;

    public Sofia2Bridge(Configuration configuration, Platform platform) throws MiddlewareException {
        super(configuration, platform);
        logger.debug("SOFIA2 bridge is initializing...");
        Properties properties = configuration.getProperties();
        // TODO: DEFINE BRIDGE CONFIGURATION
        try {
            bridgeCallbackUrl = new URL(configuration.getProperty("bridge.callback.address"));
            sofiaUser = properties.getProperty("sofia2-user");
            sofiaPassword = properties.getProperty("sofia2-password");
            sofiaUrl = properties.getProperty("sofia2-url", DEFAULT_URL); // USER + PASSWORD OR TOKEN?

        } catch (Exception e) {
            throw new BridgeException("Failed to read SOFIA2 bridge configuration: " + e.getMessage());
        }

        if (bridgeCallbackUrl == null ||
                Strings.isNullOrEmpty(sofiaUser) ||
                Strings.isNullOrEmpty(sofiaPassword)) {
            throw new BridgeException("Invalid SOFIA2 bridge configuration.");
        }
        
//        client = new Sofia2Client(sofiaUrl, sofiaUser, sofiaPassword);
        client = new Sofia2Client(sofiaUrl, TOKEN);
        
        logger.info("SOFIA2 bridge has been initialized successfully.");
    }
    
    
	@Override
	public Message registerPlatform(Message message) throws Exception {
		// SSAP JOIN
		Message responseMessage = createResponseMessage(message);
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
		Message responseMessage = createResponseMessage(message);
		Set<String> entityIDs = Sofia2Utils.getEntityIDsFromPayload(message.getPayload(), Sofia2Utils.EntityTypePlatform); // Instead of class INTERMWDemoUtils
        if (entityIDs.size() != 1) {
            throw new BridgeException("Missing platform ID.");
        }
        String platformId = entityIDs.iterator().next();
        logger.debug("Unregistering platform {}...", platformId);
        try {
			client.leave();
			logger.debug("Platform {} has been unregistered.", platformId);
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
		Message responseMessage = createResponseMessage(message);
		Set<String> entities = Sofia2Utils.getEntityIDsFromPayload(message.getPayload(), Sofia2Utils.EntityTypeDevice); // Instead of class INTERMWDemoUtils
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
			client.subscribe(thingId, callbackUrl.toString()); // TODO: RETRIEVE SUBSCRIPTION ID (IT'S NEEDER FOR UNSUBSCRIBE METHOD)
			
			Spark.put(conversationId, (request, response) -> { // SOFIA 2 sends data using a HTTP PUT query
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
		// TODO: RETRIEVE AND USE SUBSCRIPTION ID. USE SOFIA2 TRANSLATOR
		Message responseMessage = createResponseMessage(message);
		String conversationID = message.getMetadata().getConversationId().orElse(null);
		
		try{
			Set<String> entityIds = Sofia2Utils.getEntityIds(message);

			for (String entityId : entityIds) {
				logger.info("Unsubscribing from thing {}...", entityId);
				// TODO: RETRIEVE SUBSCRIPTION ID
				String responseBody = client.unsubscribe(conversationID); // TODO: MUST USE SUBSCRIPTION ID
				
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
		Message responseMessage = createResponseMessage(message);
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
		Message responseMessage = createResponseMessage(message);
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
		Message responseMessage = createResponseMessage(message);
		try{
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
		Message responseMessage = createResponseMessage(message);
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
		Message responseMessage = createResponseMessage(message);
		responseMessage.getMetadata().setStatus("KO");
		responseMessage.getMetadata().setMessageType(MessageTypesEnum.ERROR);
		return responseMessage;
	}

	@Override
	public Message unrecognized(Message message) throws Exception {
		logger.debug("Unrecognized message type.");
		Message responseMessage = createResponseMessage(message);
		responseMessage.getMetadata().setStatus("OK");
		return responseMessage;
	}
	
	
	/* 
	 * 
	 * SEND METHOD IS NOW IN PARENT CLASS 
	 * 
	 * */
	
	 //   @Override
	 //   public void send(Message message) throws BridgeException {
	 //       Set<URIManagerMessageMetadata.MessageTypesEnum> messageTypesEnumSet = message.getMetadata().getMessageTypes();
	 //       try {
	 //           if (messageTypesEnumSet.contains(URIManagerMessageMetadata.MessageTypesEnum.PLATFORM_REGISTER)) {
	 //               // When  registering a new platform (an instance of Example platform), a new ExampleBridge instance is created.
	 //               // After that, the bridge receives corresponding PLATFORM_REGISTER message which was translated by IPSM to do any further processing.
	 //               Set<String> entityIDs = INTERMWDemoUtils.getEntityIDsFromPayload(
	 //                       message.getPayload(), INTERMWDemoUtils.EntityTypePlatform);
	 //               if (entityIDs.size() != 1) {
	 //                   throw new BridgeException("Missing platform ID.");
	 //               }
	//
//	                registerPlatform(entityIDs.iterator().next());
	//
//	            } else if (messageTypesEnumSet.contains(URIManagerMessageMetadata.MessageTypesEnum.PLATFORM_UNREGISTER)) {
//	                Set<String> entityIDs = INTERMWDemoUtils.getEntityIDsFromPayload(
//	                        message.getPayload(), INTERMWDemoUtils.EntityTypePlatform);
//	                if (entityIDs.size() != 1) {
//	                    throw new BridgeException("Missing platform ID.");
//	                }
	//
//	                unregisterPlatform(entityIDs.iterator().next());
	//
//	            } else if (messageTypesEnumSet.contains(URIManagerMessageMetadata.MessageTypesEnum.THING_REGISTER)) {
//	                Set<String> entityIds = INTERMWDemoUtils.getEntityIDsFromPayload(message.getPayload(),
//	                        INTERMWDemoUtils.EntityTypeDevice);
	//
//	                for (String entityId : entityIds) {
//	                    registerThing(entityId);
//	                }
	//
//	            } else if (messageTypesEnumSet.contains(URIManagerMessageMetadata.MessageTypesEnum.THING_UNREGISTER)) {
//	                Set<String> entityIds = INTERMWDemoUtils.getEntityIDsFromPayload(message.getPayload(),
//	                        INTERMWDemoUtils.EntityTypeDevice);
//	                for (String entityId : entityIds) {
//	                    unregisterThing(entityId);
//	                }
	//
//	            } else if (messageTypesEnumSet.contains(URIManagerMessageMetadata.MessageTypesEnum.THING_UPDATE)) {
//	                if (messageTypesEnumSet.contains(URIManagerMessageMetadata.MessageTypesEnum.OBSERVATION)) {
//	                    Set<String> entities = INTERMWDemoUtils.getEntityIDsFromPayload(message.getPayload(),
//	                            INTERMWDemoUtils.EntityTypeDevice);
//	                    if (entities.isEmpty()) {
//	                        throw new BridgeException("No entities of type Device found in the Payload.");
//	                    }
//	                    String entity = entities.iterator().next();
//	                    updateThing(entity, message);
//	                }
	//
//	            } else if (messageTypesEnumSet.contains(URIManagerMessageMetadata.MessageTypesEnum.QUERY)) {
//	                throw new UnsupportedActionException("QUERY operation is currently not supported.");
	//
//	            } else if (messageTypesEnumSet.contains(URIManagerMessageMetadata.MessageTypesEnum.SUBSCRIBE)) {
//	                Set<String> entities = INTERMWDemoUtils.getEntityIDsFromPayload(message.getPayload(),
//	                        INTERMWDemoUtils.EntityTypeDevice);
//	                if (entities.isEmpty()) {
//	                    throw new PayloadException("No entities of type Device found in the Payload.");
//	                } else if (entities.size() > 1) {
//	                    throw new PayloadException("Only one device is supported by Subscribe operation.");
//	                }
	//
//	                String entity = entities.iterator().next();
//	                subscribe(entity, message.getMetadata().getConversationId().get());
	//
//	            } else if (messageTypesEnumSet.contains(URIManagerMessageMetadata.MessageTypesEnum.UNSUBSCRIBE)) {
//	                String conversationID = message.getMetadata().getConversationId().get();
//	                unsubscribe(new SubscriptionId(conversationID));
	//
//	            } else if (messageTypesEnumSet.contains(URIManagerMessageMetadata.MessageTypesEnum.DISCOVERY)) {
//	                throw new UnsupportedActionException("The DISCOVERY operation is currently not supported.");
	//
//	            } else if (messageTypesEnumSet.contains(URIManagerMessageMetadata.MessageTypesEnum.UNRECOGNIZED)) {
//	                throw new UnknownActionException(String.format(
//	                        "The action is labelled as UNRECOGNIZED and thus is unprocessable by component %s in platform %s.",
//	                        this.getClass().getName(), platform.getId().getId()));
//	            } else {
//	                throw new UnknownActionException(String.format(
//	                        "The message type is not properly handled and can't be processed by component %s in platform %s.",
//	                        this.getClass().getName(), platform.getId().getId()));
//	            }
	//
//	            Message responseMessage = createResponseMessage(message);
//	            try {
//	                publisher.publish(responseMessage);
//	            } catch (BrokerException e) {
//	                throw new MessageException("Failed to publish response message: " + e.getMessage(), e);
//	            }
	//
//	        } catch (Exception e) {
//	            logger.error("Failed to process request: " + e.getMessage(), e);
//	            throw new BridgeException("SOFIA2 bridge failed to process request: " + e.getMessage(), e);
//	        }
//	    }
}
