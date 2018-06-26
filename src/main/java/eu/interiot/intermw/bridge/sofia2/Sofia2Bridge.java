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
import eu.interiot.intermw.commons.model.IoTDevice;
import eu.interiot.intermw.commons.model.Platform;
import eu.interiot.message.ID.EntityID;
import eu.interiot.message.Message;
import eu.interiot.message.MessageMetadata;
import eu.interiot.message.MessagePayload;
import eu.interiot.message.managers.URI.URIManagerMessageMetadata;
import eu.interiot.message.exceptions.payload.PayloadException;
import eu.interiot.message.metadata.PlatformMessageMetadata;
import eu.interiot.translators.syntax.sofia2.Sofia2Translator;

import org.apache.jena.rdf.model.Model;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Spark;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

@eu.interiot.intermw.bridge.annotations.Bridge(platformType = "http://inter-iot.eu/sofia2")
public class Sofia2Bridge extends AbstractBridge {
    private final Logger logger = LoggerFactory.getLogger(Sofia2Bridge.class);
    final static String PROPERTIES_PREFIX = "sofia2-";
   
//	private Map<String,String> subscriptionIds = new HashMap<String,String>();
	private Map<String, List<String>> subscriptionIds = new HashMap<String,List<String>>();
    
    private Sofia2Client client;

    public Sofia2Bridge(Configuration configuration, Platform platform) throws MiddlewareException {
        super(configuration, platform);
        logger.debug("SOFIA2 bridge is initializing...");
        Properties properties = configuration.getProperties();
        
        if (bridgeCallbackUrl == null) { // From the AbstractBridge class
            throw new BridgeException("Invalid SOFIA2 bridge configuration.");
        }
        
        try{
        	client = new Sofia2Client(properties, platform.getBaseEndpoint().toString());
        }catch (Exception e) {
        	throw new BridgeException(e);
        }
                
        logger.info("SOFIA2 bridge has been initialized successfully.");
    }
    
    
	@Override
	public Message registerPlatform(Message message) throws Exception {
		// SSAP JOIN
		Message responseMessage = createResponseMessage(message);
		
        String platformId = platform.getPlatformId();
        logger.debug("Registering platform {}...", platformId);
        try {
			client.join();
			logger.debug("Platform {} has been registered.", platformId);
		} catch (Exception e) {
			logger.error("Register Platform  " + e);
			e.printStackTrace();
			responseMessage.getMetadata().setStatus("KO");
			responseMessage.getMetadata().addMessageType(URIManagerMessageMetadata.MessageTypesEnum.ERROR);
			responseMessage.getMetadata().asErrorMessageMetadata().setExceptionStackTrace(e);
		}
        return responseMessage;
	}
	
	@Override
	public Message unregisterPlatform(Message message) throws Exception {
		// SSAP LEAVE
		// TODO: CLEANUP (SHOULD REMOVE ALL ACTIVE SUBSCRIPTIONS?)
		Message responseMessage = createResponseMessage(message);
		String platformId = platform.getPlatformId();
        logger.debug("Unregistering platform {}...", platformId);
        try {
			client.leave();
			logger.debug("Platform {} has been unregistered.", platformId);
			subscriptionIds.clear();
		} catch (Exception e) {
			logger.error("Unregister Platform  " + e);
			e.printStackTrace();
			responseMessage.getMetadata().setStatus("KO");
			responseMessage.getMetadata().addMessageType(URIManagerMessageMetadata.MessageTypesEnum.ERROR);
			responseMessage.getMetadata().asErrorMessageMetadata().setExceptionStackTrace(e);
		}
        return responseMessage;
	}

	@Override
	public Message subscribe(Message message) throws Exception {
		Message responseMessage = createResponseMessage(message);
		logger.debug("subscribe() started.");
		String conversationId = message.getMetadata().getConversationId().orElse(null); 
		List<String> deviceIds = Sofia2Utils.extractDeviceIds(message);
		
		String endpoint = conversationId; // UNIQUE ENDPOINT
		URL callbackUrl = new URL(bridgeCallbackUrl, endpoint);
		List<String> subIds = new ArrayList<String>();
		
		if (deviceIds.isEmpty()) {	
          throw new PayloadException("No entities of type Device found in the Payload.");
		}
		// TODO: FIND A BETTER WAY TO DO THIS
		try{
			logger.debug("Subscribing to things using conversationId {}...", conversationId);
			for (String deviceId : deviceIds) {
				String thingId[] = Sofia2Utils.filterThingID(deviceId);
				logger.debug("Sending Subscribe request to the platform for device {}...", deviceId);
				System.out.println("Sending subscribe request to the platform for device {}... " + thingId[0] + "." + thingId[1] + ":" + thingId[2]);
				String subId = "";
            	if(thingId.length > 1){
    				subId = client.subscribe(thingId[0], thingId[1], thingId[2], callbackUrl.toString()); // Subscription to a thing in SOFIA2
    			}
//				else{
//            		subId = client.subscribe(thingId[thingId.length - 1], callbackUrl.toString()); // Subscription to an ontology?
//				}
            	if(subId != null) subIds.add(subId);
        	}
			
			
			Sofia2Translator translator = new Sofia2Translator();
			
			subscriptionIds.put(conversationId, subIds); // SUBSCRIPTION ID IS NEEDED FOR UNSUBSCRIBE METHOD. UNSUBSCRIBE MESSAGE CONTAINS CONVERSATIONID
			
			Spark.post(endpoint, (request, response) -> {
	            logger.debug("Received observation from the platform.");
	            PlatformMessageMetadata metadata = new MessageMetadata().asPlatformMessageMetadata();
	            metadata.initializeMetadata();
	            metadata.addMessageType(URIManagerMessageMetadata.MessageTypesEnum.OBSERVATION);
//	            metadata.addMessageType(URIManagerMessageMetadata.MessageTypesEnum.RESPONSE); // THIS OBSERVATION MESSAGE SHOULD NOT HAVE TYPE RESPONSE
	            metadata.setSenderPlatformId(new EntityID(platform.getPlatformId()));
	            metadata.setConversationId(conversationId);        
	            
	            JsonParser parser = new JsonParser();
	    		JsonObject ssapObject = parser.parse(request.body().toString()).getAsJsonObject();
	    		
	    		System.out.println(request.body().toString());
	    		String observation;
	    		if (ssapObject.has("version") && ssapObject.get("version").getAsString().equals("LEGACY")){
	    			JsonObject body = ssapObject.get("body").getAsJsonObject();
	    			System.out.println("Received data: " + body.toString());
		    		observation = body.get("data").getAsString();
		    		System.out.println("Received data: " + observation);
	    		}else{
	    			JsonObject body = parser.parse(ssapObject.get("body").getAsString()).getAsJsonObject();
		    		observation = body.get("data").getAsString();
		    		System.out.println("Received data: " + observation);
//		    		JsonArray array = parser.parse("[" + observation + "]").getAsJsonArray(); // In case SOFIA2 returns more than one value
//		    		observation = array.get(0).getAsJsonObject().toString(); // Get only the new value
	    		}
	    			    		
	            Model translatedModel = translator.toJenaModel(observation);
	    		// Create a new message payload for the response message
	    		MessagePayload responsePayload = new MessagePayload(translatedModel);
	            	            
	            Message observationMessage = new Message();
	            observationMessage.setMetadata(metadata);
	            observationMessage.setPayload(responsePayload); 
	
	            publisher.publish(observationMessage);
	            logger.debug("Observation message has been published upstream.");
	            System.out.println(observationMessage.serializeToJSONLD());
	            response.status(200);
	            return "";
	        });
			
		}catch (Exception e){ 
			logger.error("Error subscribing: " + e.getMessage());
			responseMessage.getMetadata().setStatus("KO");
			responseMessage.getMetadata().addMessageType(URIManagerMessageMetadata.MessageTypesEnum.ERROR);
			responseMessage.getMetadata().asErrorMessageMetadata().setExceptionStackTrace(e);
		}
		
		return responseMessage;
	}
	

	@Override
	public Message unsubscribe(Message message) throws Exception {
		Message responseMessage = createResponseMessage(message);
	    String conversationId = Sofia2Utils.extractConversationId(message);
		
		try{
			logger.info("Unsubscribing from things in conversation {}...", conversationId);
			List<String> subId = subscriptionIds.get(conversationId); // RETRIEVE SUBSCRIPTION IDs
			for (String subscriptionId : subId){
				try{
					client.unsubscribe(subscriptionId);
				}catch (Exception e){ // FIXME: The server sometimes returns a 500 code. It's probably a bug
					logger.error("Error unsubscribing: " + e.getMessage());
					e.printStackTrace();
				}
			}
			subscriptionIds.remove(conversationId);
			
		} catch (Exception e){ 
			logger.error("Error unsubscribing: " + e.getMessage());
			e.printStackTrace();
			responseMessage.getMetadata().setStatus("KO");
			responseMessage.getMetadata().addMessageType(URIManagerMessageMetadata.MessageTypesEnum.ERROR);
			responseMessage.getMetadata().asErrorMessageMetadata().setExceptionStackTrace(e);
		}
		
		return responseMessage;
	}
	
	@Override
	public Message query(Message message) throws Exception {
		Message responseMessage = createResponseMessage(message);
		try{
			Set<String> deviceIds = Sofia2Utils.getEntityIds(message);
			for(String deviceId : deviceIds){
				String thingId[] = Sofia2Utils.filterThingID(deviceId);
//				String responseBody = client.query(thingId);
				String responseBody = client.query(thingId[0], thingId[1], thingId[2]);
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
			responseMessage.getMetadata().addMessageType(URIManagerMessageMetadata.MessageTypesEnum.ERROR);
			responseMessage.getMetadata().asErrorMessageMetadata().setExceptionStackTrace(e);
		}
		return responseMessage;
	}
	
	@Override
	public Message listDevices(Message message) throws Exception {
		/*
		 * TODO:
		 * The information should then be sent in a series of DEVICE_ADD_OR_UPDATE messages, with information about one device per message. 
		 * 
		 * */
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
			responseMessage.getMetadata().addMessageType(URIManagerMessageMetadata.MessageTypesEnum.ERROR);
			responseMessage.getMetadata().asErrorMessageMetadata().setExceptionStackTrace(e);
		}
		return responseMessage;
	}
	
	@Override
	public Message platformCreateDevices(Message message) throws Exception {
		// TODO: USE SOFIA2 TRANSLATOR (?)
		Message responseMessage = createResponseMessage(message);
		try{
			List<IoTDevice> devices = Sofia2Utils.extractDevices(message);
			// TODO: FIND A BETTER WAY TO DO THIS
			for (IoTDevice iotDevice : devices) {
				String thingId[] = Sofia2Utils.filterThingID(iotDevice.getDeviceId());
	            logger.debug("Sending create-device (start-to-manage) request to the platform for device {}...", iotDevice.getDeviceId());
	            System.out.println("Sending create-device (start-to-manage) request to the platform for device {}... " + thingId[0] + "." + thingId[1] + ":" + thingId[2]);
	            client.register(thingId[0], thingId[1], thingId[2]); // TODO: CHANGE THIS TO BE ABLE TO ACTUALLY INSERT THE DEVICE DATA
	    		logger.debug("Success");  
	        }
    	}catch(Exception e){
    		logger.error("Error registering devices: " + e.getMessage());
			responseMessage.getMetadata().setStatus("KO");
			responseMessage.getMetadata().addMessageType(URIManagerMessageMetadata.MessageTypesEnum.ERROR);
			responseMessage.getMetadata().asErrorMessageMetadata().setExceptionStackTrace(e);
    	}
		return responseMessage;
	}
	
	
	@Override
	public Message platformUpdateDevices(Message message) throws Exception {
		// TODO
		// USE SOFIA2 TRANSLATOR TO GENERATE UPDATE MESSAGE DATA
		return null;
	}
	
	@Override
	public Message platformDeleteDevices(Message message) throws Exception {
		// TODO: CHECK DEVICE ID
		Message responseMessage = createResponseMessage(message);
		try {
			logger.debug("Removing devices...");
			Set<String> deviceIds = Sofia2Utils.getEntityIds(message);
			for(String deviceId : deviceIds){
				String[] transformedId = Sofia2Utils.filterThingID(deviceId);
				client.delete(transformedId[0], transformedId[1], transformedId[2]);
				logger.debug("Device {} has been removed.", deviceId);
			}
			responseMessage.getMetadata().setStatus("OK");
		} 
		catch (Exception e) {
			logger.error("Error removing devices: " + e.getMessage());
			responseMessage.getMetadata().setStatus("KO");
			responseMessage.getMetadata().addMessageType(URIManagerMessageMetadata.MessageTypesEnum.ERROR);
			responseMessage.getMetadata().asErrorMessageMetadata().setExceptionStackTrace(e);
		}
		return responseMessage;
	}
	
	@Override
	public Message observe(Message message) throws Exception {
		// TRANSLATE MESSAGE PAYLOAD TO FORMAT X AND SEND IT TO PLATFORM
		Message responseMessage = createResponseMessage(message);
		try{
			logger.debug("Sending observation to the platform {}...", platform.getPlatformId());
			Sofia2Translator translator = new Sofia2Translator();
			String body = translator.toFormatX(message.getPayload().getJenaModel());
			// Get ontology and data for update
			String ontName = Sofia2Utils.getOntName(body);
		    String data = Sofia2Utils.getUpdateData(body);
		    
		    logger.debug("SOFIA2 ontology: " + ontName);
		    logger.debug("Observation data: " + data);
		    
		    client.update(ontName, data); // Needs object ID
			
		}catch(Exception ex){
			logger.error("Error in observe: " + ex.getMessage());
			throw ex;
		}
		return responseMessage;
	}
	
	@Override
	public Message actuate(Message message) throws Exception {
		// TRANSLATE MESSAGE PAYLOAD TO FORMAT X AND SEND IT TO PLATFORM
		Message responseMessage = createResponseMessage(message);
		try{
			logger.debug("Sending actuation to the platform {}...", platform.getPlatformId());
			Sofia2Translator translator = new Sofia2Translator();
			String body = translator.toFormatX(message.getPayload().getJenaModel());
			// Get ontology and data for update
			String ontName = Sofia2Utils.getOntName(body);
		    String data = Sofia2Utils.getUpdateData(body);
		    
		    logger.debug("SOFIA2 ontology: " + ontName);
		    logger.debug("Actuation data: " + data);
		    
		    client.update(ontName, data); // Needs object ID
			
		}catch(Exception ex){
			logger.error("Error in actuate: " + ex.getMessage());
			throw ex;
		}
		return responseMessage;
	}

	@Override
	public Message error(Message message) throws Exception {
		logger.debug("Error occured in {}...", message);
		Message responseMessage = createResponseMessage(message);
		responseMessage.getMetadata().setStatus("KO");
//		responseMessage.getMetadata().addMessageType(URIManagerMessageMetadata.MessageTypesEnum.ERROR); // Do we need to add the "error" type in this case ?
		return responseMessage;
	}

	@Override
	public Message unrecognized(Message message) throws Exception {
		logger.debug("Unrecognized message type.");
		Message responseMessage = createResponseMessage(message);
		responseMessage.getMetadata().setStatus("OK");
		return responseMessage;
	}
	
	
}
