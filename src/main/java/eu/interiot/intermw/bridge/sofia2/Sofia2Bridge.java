/*
 * Copyright 2020 Universitat Politècnica de València
 *
 * See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package eu.interiot.intermw.bridge.sofia2;

/**
 * For more information, contact:
 * - @author <a href="mailto:majuse@upv.es">Matilde Julián</a>  
 */
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

@eu.interiot.intermw.bridge.annotations.Bridge(platformType = "http://inter-iot.eu/sofia2")
public class Sofia2Bridge extends AbstractBridge {
    private final Logger logger = LoggerFactory.getLogger(Sofia2Bridge.class);
	private Map<String, List<String>> subscriptionIds = new HashMap<String,List<String>>();
    private Sofia2Client client;

    public Sofia2Bridge(BridgeConfiguration configuration, Platform platform) throws MiddlewareException {
        super(configuration, platform);
        logger.debug("SOFIA2 bridge is initializing...");
        Properties properties = configuration.getProperties();
        
        if (bridgeCallbackUrl == null) {
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
			responseMessage.getMetadata().asErrorMessageMetadata().setErrorDescription(e.toString());
			responseMessage.getMetadata().asErrorMessageMetadata().setOriginalMessage(message.toString());
		}
        return responseMessage;
	}
	
	@Override
	public Message unregisterPlatform(Message message) throws Exception {
		// SSAP LEAVE
		/* TODO: CLEANUP
		* SHOULD REMOVE ALL ACTIVE SUBSCRIPTIONS?
		* Remove devices from the registry: send DEVICE_REGISTRY_INITIALIZE message upstream
		*/ 
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
			responseMessage.getMetadata().asErrorMessageMetadata().setErrorDescription(e.toString());
			responseMessage.getMetadata().asErrorMessageMetadata().setOriginalMessage(message.toString());
		}
        return responseMessage;
	}
	
	@Override
	public Message updatePlatform(Message message) throws Exception {
		// TODO: update base endpoint, user, password
		// TODO: send join request if connection data has been uploaded
		Message responseMessage = createResponseMessage(message);
		logger.info("Updating platform {}...", platform.getPlatformId());
		return  responseMessage;
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
	            metadata.setSenderPlatformId(new EntityID(platform.getPlatformId()));
	            metadata.setConversationId(conversationId);        
	            
	            JsonParser parser = new JsonParser();
	    		JsonObject ssapObject = parser.parse(request.body().toString()).getAsJsonObject();
	    		
	    		System.out.println(request.body().toString());
	    		String observation;
	    		if (ssapObject.has("version") && ssapObject.get("version").getAsString().equals("LEGACY")){
	    			JsonObject body = ssapObject.get("body").getAsJsonObject();
//	    			System.out.println("Received data: " + body.toString());
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
	        if (devices.isEmpty()) {
	        	// Return status or last measurement of each device
	        	String responseBody = client.list();
	        	Sofia2Translator translator = new Sofia2Translator();
				// Create the model from the response JSON
				Model translatedModel = translator.toJenaModel(responseBody);
				// Create a new message payload for the response message
				MessagePayload responsePayload = new MessagePayload(translatedModel);
				// Attach the payload to the message
				responseMessage.setPayload(responsePayload);
	        }else{
	        	for (IoTDevice iotDevice : devices) {
	        		String thingId[] = Sofia2Utils.filterThingID(iotDevice.getDeviceId());
	        		String responseBody = client.query(thingId[0], thingId[1], thingId[2]);
	        		Sofia2Translator translator = new Sofia2Translator();
	        		// Create the model from the response JSON
	        		Model translatedModel = translator.toJenaModel(responseBody);
	        		// Create a new message payload for the response message
	        		MessagePayload responsePayload = new MessagePayload(translatedModel);
	        		// Attach the payload to the message
	        		responseMessage.setPayload(responsePayload);
	        	}
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
			// Set the OK status
			responseMessage.getMetadata().setStatus("OK");
			
			/*
			 * Add devices to the registry
			 * */
			String conversationId = message.getMetadata().getConversationId().orElse(null); 
			if(responseBody!=null){
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
			}else logger.debug("0 new devices have been added to the registry");
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
		 * Creates virtual sensors on the platform
		 * Only the device id should be mandatory in the corresponding SOFIA2 ontologies
		 * */
		Message responseMessage = createResponseMessage(message);
		try{
			List<IoTDevice> devices = Sofia2Utils.extractDevices(message);
			for (IoTDevice iotDevice : devices) {
				String thingId[] = Sofia2Utils.filterThingID(iotDevice.getDeviceId());
	            logger.debug("Sending create-device (start-to-manage) request to the platform for device {}...", iotDevice.getDeviceId());
	            System.out.println("Sending create-device (start-to-manage) request to the platform for device {}... " + thingId[0] + "." + thingId[1] + ":" + thingId[2]);
	            client.register(thingId[0], thingId[1], thingId[2]); // TODO: Include name as an attribute of the virtual device
	    		logger.debug("Success");  
	        }
    	}catch(Exception e){
    		logger.error("Error registering devices: " + e.getMessage());
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
	public Message platformUpdateDevices(Message message) throws Exception {
		// TODO: UPDATE VIRTUAL DEVICES
		return null;
	}
	
	@Override
	public Message platformDeleteDevices(Message message) throws Exception {
		// DELETE VIRTUAL DEVICES
		Message responseMessage = createResponseMessage(message);
		try {
			logger.debug("Removing devices...");
			List<IoTDevice> devices = Sofia2Utils.extractDevices(message);
			for (IoTDevice iotDevice : devices) {
				String transformedId[] = Sofia2Utils.filterThingID(iotDevice.getDeviceId());
				client.delete(transformedId[0], transformedId[1], transformedId[2]);
				logger.debug("Device {} has been removed.", iotDevice.getDeviceId());
			}
			responseMessage.getMetadata().setStatus("OK");
		} 
		catch (Exception e) {
			logger.error("Error removing devices: " + e.getMessage());
			responseMessage.getMetadata().setStatus("KO");
			responseMessage.getMetadata().addMessageType(URIManagerMessageMetadata.MessageTypesEnum.ERROR);
			responseMessage.getMetadata().asErrorMessageMetadata().setExceptionStackTrace(e);
			responseMessage.getMetadata().asErrorMessageMetadata().setErrorDescription(e.toString());
			responseMessage.getMetadata().asErrorMessageMetadata().setOriginalMessage(message.toString());
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
