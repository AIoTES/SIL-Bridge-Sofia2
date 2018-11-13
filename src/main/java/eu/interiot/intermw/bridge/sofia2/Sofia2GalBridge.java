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
import com.google.gson.JsonArray;

import eu.interiot.intermw.bridge.BridgeConfiguration;
import eu.interiot.intermw.bridge.abstracts.AbstractBridge;
import eu.interiot.intermw.bridge.exceptions.BridgeException;
import eu.interiot.intermw.commons.exceptions.MiddlewareException;
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

@eu.interiot.intermw.bridge.annotations.Bridge(platformType = "http://inter-iot.eu/sofia2Gal")
public class Sofia2GalBridge extends AbstractBridge {
    private final Logger logger = LoggerFactory.getLogger(Sofia2GalBridge.class);
//    final static String PROPERTIES_PREFIX = "sofia2-";
   
//	private Map<String,String> subscriptionIds = new HashMap<String,String>();
	private Map<String, List<String>> subscriptionIds = new HashMap<String,List<String>>();
    
    private Sofia2GalClient client;

    public Sofia2GalBridge(BridgeConfiguration configuration, Platform platform) throws MiddlewareException {
        super(configuration, platform);
        logger.debug("SOFIA2 bridge is initializing...");
        Properties properties = configuration.getProperties();
        
        if (bridgeCallbackUrl == null) { // From the AbstractBridge class
            throw new BridgeException("Invalid SOFIA2 bridge configuration.");
        }
        
        try{
        	client = new Sofia2GalClient(properties, platform.getBaseEndpoint().toString());
        }catch (Exception e) {
        	throw new BridgeException(e);
        }
                
        logger.info("SOFIA2 bridge has been initialized successfully.");
    }
    
    
	@Override
	public Message registerPlatform(Message message) throws Exception {
		// Managed automatically in the API
		String platformId = platform.getPlatformId();
        logger.debug("Registering platform {}...", platformId);
		Message responseMessage = createResponseMessage(message);
        logger.debug("Platform {} has been registered.", platformId);
        return responseMessage;
	}
	
	@Override
	public Message unregisterPlatform(Message message) throws Exception {
		/* TODO: CLEANUP
		* SHOULD REMOVE ALL ACTIVE SUBSCRIPTIONS?
		* Remove devices from the registry: send DEVICE_REGISTRY_INITIALIZE message upstream (?)
		*/ 
		String platformId = platform.getPlatformId();
        logger.debug("Unregistering platform {}...", platformId);
		Message responseMessage = createResponseMessage(message);
        logger.debug("Platform {} has been unregistered.", platformId);
        return responseMessage;
	}

	@Override
	public Message updatePlatform(Message message) throws Exception {
		// TODO update base endpoint, user, password?
		return  createResponseMessage(message);
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
            		// http://inter-iot.eu/dev/{ontName}/{idName}#{id}
//            		subId = client.subscribe(thingId[0], thingId[1], thingId[2], callbackUrl.toString()); // Subscription to a thing in SOFIA2
            		subId = client.subscribe(thingId[2], thingId[0], callbackUrl.toString()); // Subscription to a device
    			}
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
			responseMessage.getMetadata().asErrorMessageMetadata().setErrorDescription(e.toString());
			responseMessage.getMetadata().asErrorMessageMetadata().setOriginalMessage(message.toString());
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
				}catch (Exception e){
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
			responseMessage.getMetadata().asErrorMessageMetadata().setErrorDescription(e.toString());
			responseMessage.getMetadata().asErrorMessageMetadata().setOriginalMessage(message.toString());
		}
		
		return responseMessage;
	}
	
	@Override
	public Message query(Message message) throws Exception {
		Message responseMessage = createResponseMessage(message);
		try{
			List<IoTDevice> devices = Sofia2Utils.extractDevices(message);
			for (IoTDevice iotDevice : devices) {
				String thingId[] = Sofia2Utils.filterThingID(iotDevice.getDeviceId());
				String responseBody = client.query(thingId[0], thingId[1], thingId[2]); // TODO: implement query method in client
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
			responseMessage.getMetadata().asErrorMessageMetadata().setErrorDescription(e.toString());
			responseMessage.getMetadata().asErrorMessageMetadata().setOriginalMessage(message.toString());
		}
		return responseMessage;
	}
	
	@Override
	public Message listDevices(Message message) throws Exception {
		/*
		 * TODO:
		 * The information should then be sent in a series of DEVICE_ADD_OR_UPDATE messages, with information about one device per message. 
		 * Afterwards, the bridge should keep it updated with messages of type DEVICE_ADD_OR_UPDATE, DEVICE_REMOVE, or DEVICE_REGISTRY_INITIALIZE.
		 * 
		 * */
		Message responseMessage = createResponseMessage(message);
		try{
			// Discover all the registered devices
			logger.debug("ListDevices started...");
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
			
			/*
			 * Add devices to the registry
			 * */
			String conversationId = message.getMetadata().getConversationId().orElse(null); 
			JsonParser parser = new JsonParser();
			JsonArray devices = parser.parse(responseBody).getAsJsonArray();
			for(int i = 0; i < devices.size(); i++){
				Message addDeviceMessage = new Message();
				PlatformMessageMetadata metadata = new MessageMetadata().asPlatformMessageMetadata();
	            metadata.initializeMetadata();
	            metadata.addMessageType(URIManagerMessageMetadata.MessageTypesEnum.DEVICE_ADD_OR_UPDATE);
	            metadata.setSenderPlatformId(new EntityID(platform.getPlatformId()));
	            metadata.setConversationId(conversationId); 
	            // Create a new message payload with the information about the device
	            Model deviceModel = translator.toJenaModel(devices.get(i).getAsJsonObject().toString());
	    		MessagePayload devicePayload = new MessagePayload(deviceModel);
	            
	            addDeviceMessage.setMetadata(metadata);
	            addDeviceMessage.setPayload(devicePayload);
	            
	            publisher.publish(addDeviceMessage);
	            logger.debug("Device_Add_Or_Update message has been published upstream.");
			}
			logger.debug(devices.size() + " new devices have been added to the registry");
		}
		catch (Exception e) {
			logger.error("Error in query: " + e.getMessage());
			e.printStackTrace();
			responseMessage.getMetadata().setStatus("KO");
			responseMessage.getMetadata().addMessageType(URIManagerMessageMetadata.MessageTypesEnum.ERROR);
			responseMessage.getMetadata().asErrorMessageMetadata().setExceptionStackTrace(e);
			responseMessage.getMetadata().asErrorMessageMetadata().setErrorDescription(e.toString());
			responseMessage.getMetadata().asErrorMessageMetadata().setOriginalMessage(message.toString());
		}
		return responseMessage;
	}
	
	@Override
	public Message platformCreateDevices(Message message) throws Exception {
		/*
		 * Virtual devices must be created directly on the platform
		 * */
		Message responseMessage = createResponseMessage(message);
		return responseMessage;
	}
	
	
	@Override
	public Message platformUpdateDevices(Message message) throws Exception {
		// This should be done directly on the platform
		Message responseMessage = createResponseMessage(message);
		return responseMessage;
	}
	
	@Override
	public Message platformDeleteDevices(Message message) throws Exception {
		// This should be done directly on the platform
		Message responseMessage = createResponseMessage(message);
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
		    
		    client.sendObservation(data);
			
		}catch(Exception ex){
			logger.error("Error in observe: " + ex.getMessage());
			throw ex;
		}
		return responseMessage;
	}
	
	@Override
	public Message actuate(Message message) throws Exception {
		// TRANSLATE MESSAGE PAYLOAD TO FORMAT X AND SEND IT TO PLATFORM
		// Same as observe, but for actuation data
		// Needs a new method in the API
		Message responseMessage = createResponseMessage(message);
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
