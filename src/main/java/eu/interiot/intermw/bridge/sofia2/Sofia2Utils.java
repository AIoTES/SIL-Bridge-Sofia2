package eu.interiot.intermw.bridge.sofia2;

import java.util.Set;
import java.util.stream.Collectors;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.SimpleSelector;
import org.apache.jena.vocabulary.RDF;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.interiot.intermw.commons.model.Platform;
import eu.interiot.message.Message;
import eu.interiot.message.MessagePayload;



public class Sofia2Utils {
	
	private final static Logger logger = LoggerFactory.getLogger(Sofia2Utils.class);

  
	public static final String URIsosa = "http://www.w3.org/ns/sosa/";
    

    // Types
    public static final String EntityTypeDevice = Sofia2Translator.sofia2baseURI + "Instance";
    public static final String EntityTypePlatform = URIsosa + "Platform"; // From class INTERMWDemoUtils

	
	public static String getPlatformId(Platform platform){
		return platform.getId().getId();
	}

    

    public static Set<String> getEntityIDsFromPayload(MessagePayload payload, String entityType) {
        Model model = payload.getJenaModel();
        return model.listStatements(new SimpleSelector(null, RDF.type, model.createResource(entityType))).toSet().stream().map(x -> x.getSubject().toString()).collect(Collectors.toSet());
    }
    

    public static Set<String> getEntityIds(Message message){
		return getEntityIDsFromPayload(message.getPayload(), EntityTypeDevice);
	}
    
    public static String filterThingID(String thingId) {
    	String filteredString = thingId;

		// TODO: CHECK IF THIS IS APPROPRIATE FOR SOFIA2

		if (thingId.contains("http://inter-iot.eu/dev/")) {
			filteredString = thingId.replace("http://inter-iot.eu/dev/", "");
		} 
		if (thingId.contains("/")) {
			filteredString = thingId.replace("/", "-");
		}
		if (thingId.contains("#")) {
			filteredString = thingId.replace("#", "+");
		}
		return filteredString;
	}
    
}
