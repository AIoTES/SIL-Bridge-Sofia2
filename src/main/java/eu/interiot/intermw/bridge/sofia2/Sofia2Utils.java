package eu.interiot.intermw.bridge.sofia2;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import eu.interiot.intermw.bridge.exceptions.BridgeException;
import eu.interiot.intermw.commons.model.IoTDevice;
import eu.interiot.message.Message;
import eu.interiot.message.ID.EntityID;
import eu.interiot.message.ID.PropertyID;
import eu.interiot.message.payload.types.IoTDevicePayload;
import eu.interiot.translators.syntax.sofia2.Sofia2Translator;



public class Sofia2Utils {
	
	private final static Logger logger = LoggerFactory.getLogger(Sofia2Utils.class);
	public static final String URIsosa = "http://www.w3.org/ns/sosa/";
	public static final String URIoldssn = "http://purl.oclc.org/NET/ssnx/ssn#";
	public static final String propHasIdURI = Sofia2Translator.sofia2baseURI + "hasId";
	public final static String INTERIOT_PREFIX = "http://wwww.inter-iot.eu/dev/"; // TODO: CHANGE PREFIX?

    // Types
 //   public static final String EntityTypeDevice = Sofia2Translator.sofia2baseURI + "Instance";
    public static final String EntityTypeDevice = URIoldssn + "Device";
    public static final String EntityTypePlatform = URIsosa + "Platform"; // From class INTERMWDemoUtils
    
	
	static List<IoTDevice> extractDevices(Message message) {
        IoTDevicePayload ioTDevicePayload = message.getPayloadAsGOIoTPPayload().asIoTDevicePayload();
        Set<EntityID> deviceEntityIds = ioTDevicePayload.getIoTDevices();
        List<IoTDevice> devices = new ArrayList<>();
        for (EntityID deviceEntityId : deviceEntityIds) {
            String deviceId = deviceEntityId.toString();
            Optional<EntityID> hostedBy = ioTDevicePayload.getIsHostedBy(deviceEntityId);
            Optional<EntityID> location = ioTDevicePayload.getHasLocation(deviceEntityId);
            Optional<String> name = ioTDevicePayload.getHasName(deviceEntityId);

            IoTDevice ioTDevice = new IoTDevice(deviceId);
            ioTDevice.setHostedBy(hostedBy.isPresent() ? hostedBy.get().toString() : null);
            ioTDevice.setLocation(location.isPresent() ? location.get().toString() : null);
            ioTDevice.setName(name.orElse(null));
            devices.add(ioTDevice);
        }
        return devices;
    }

    static List<String> extractDeviceIds(Message message) {
        IoTDevicePayload ioTDevicePayload = message.getPayloadAsGOIoTPPayload().asIoTDevicePayload();
        Set<EntityID> deviceEntityIds = ioTDevicePayload.getIoTDevices();
        List<String> deviceIds = new ArrayList<>();
        for (EntityID deviceEntityId : deviceEntityIds) {
            deviceIds.add(deviceEntityId.toString());
        }
        return deviceIds;
    }
    
    static String extractConversationId(Message message) throws BridgeException {
    	String conversationId = message.getMetadata().asPlatformMessageMetadata().getSubscriptionId().get();

        if (conversationId==null || conversationId=="") {
            throw new BridgeException("Invalid UNSUBSCRIBE message: failed to extract conversationId");
        }
        return conversationId;
    }
    
    
//    public static Set<String> getEntityIDsFromPayload(MessagePayload payload, String entityType) {
//        Model model = payload.getJenaModel();
//        return model.listStatements(new SimpleSelector(null, RDF.type, model.createResource(entityType))).toSet().stream().map(x -> x.getSubject().toString()).collect(Collectors.toSet());
//    }
    

//    public static Set<String> getEntityIds(Message message){
//		return getEntityIDsFromPayload(message.getPayload(), EntityTypeDevice);
//	}
    
    public static String[] filterThingID(String thingId) {
    	String[] filteredString = null; //= thingId;
    	if (thingId.contains("http://")) {
    		if(thingId.contains("#")){
    			// ThingId http://inter-iot.eu/dev/{ontName}/{idName}#{id}
    			thingId = thingId.replace("#", "/");
        		String[] splitId = thingId.split("/");
        		filteredString = new String[3];
        		filteredString[0] = splitId[splitId.length - 3];  // Ontology name
        		filteredString[1] = splitId[splitId.length - 2]; // Id name
        		filteredString[2] = splitId[splitId.length - 1]; // id value
    		}else{
    			// http://inter-iot.eu/dev/{ontName}
    			String[] splitId = thingId.split("/");
        		filteredString = new String[1];
        		filteredString[0] = splitId[splitId.length - 1]; // Ontology name (for subscription to an ontology)
    		}
    		
		}else{
			filteredString = new String[1];
			filteredString[0] = thingId;
			if (thingId.contains("/")) {
				filteredString[0] = filteredString[0].replace("/", "-");
			}
			if (thingId.contains("#")) {
				filteredString[0] = filteredString[0].replace("#", "+");
			}
		}
		return filteredString;
	}
    
    public static String getOntName(String data){
    	String ontName;
    	JsonParser parser = new JsonParser();
    	JsonObject dataObject = parser.parse(data).getAsJsonObject();
    	Iterator<Entry<String, JsonElement>> it = dataObject.entrySet().iterator();
    	do{
    		Map.Entry<String, JsonElement> instanceAttr = it.next();
    		ontName = instanceAttr.getKey();
    	}while(it.hasNext() && (ontName.equals("contextData") || ontName.equals("_id")));
    	
    	return ontName;
    }
    
    public static String getUpdateData(String observation){
    	JsonObject data;
    	JsonParser parser = new JsonParser();
	    JsonObject inputData = parser.parse(observation).getAsJsonObject();
    	if(inputData.has("body")){
//	    	inputData.remove("messageType");
//	    	inputData.addProperty("messageType", "UPDATE");
//	    	inputData.remove("direction");
//	    	inputData.addProperty("direction", "REQUEST");
	    	data = inputData.get("body").getAsJsonObject();
	    	data = data.remove("@type").getAsJsonObject();
	    }else data = inputData;
    	if(inputData.has("contextData")) data.remove("contextData").getAsJsonObject(); // Just in case
    	return data.toString();
    }
    
    
    public static String deurlize(String id) {
		String deurlizedId[] = filterThingID(id);
		return deurlizedId[deurlizedId.length - 1];
	}

	

	public static String urlize(String id, String type) {
		String urlizedId = INTERIOT_PREFIX + type + "/_id#" + id;
		return urlizedId;
	}

    
}
