package eu.interiot.intermw.bridge.sofia2;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.NodeIterator;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.SimpleSelector;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.vocabulary.RDF;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.interiot.intermw.commons.model.Platform;
import eu.interiot.message.Message;
import eu.interiot.message.MessagePayload;
import eu.interiot.message.ID.EntityID;
import eu.interiot.translators.syntax.sofia2.Sofia2Translator;



public class Sofia2Utils {
	
	private final static Logger logger = LoggerFactory.getLogger(Sofia2Utils.class);

  
	public static final String URIsosa = "http://www.w3.org/ns/sosa/";
	public static final String URIoldssn = "http://purl.oclc.org/NET/ssnx/ssn#";
	public static final String propHasIdURI = Sofia2Translator.sofia2baseURI + "hasId";

    

    // Types
 //   public static final String EntityTypeDevice = Sofia2Translator.sofia2baseURI + "Instance";
    public static final String EntityTypeDevice = URIoldssn + "Device";
    public static final String EntityTypePlatform = URIsosa + "Platform"; // From class INTERMWDemoUtils

	
	public static String getPlatformId(Platform platform){
		return platform.getPlatformId();
	}
			  
    public static Set<EntityID> getEntityIDsFromPayloadAsEntityIDSet(MessagePayload payload, String entityType) {
        Model model = payload.getJenaModel();
        return model.listStatements(new SimpleSelector(null, RDF.type, model.createResource(entityType))).toSet().stream().map(x -> new EntityID(x.getSubject().toString())).collect(Collectors.toSet());
    }
    
    public static Set<EntityID> getEntityIdsAsEntityIDSet(Message message){
		return getEntityIDsFromPayloadAsEntityIDSet(message.getPayload(), EntityTypeDevice);
	}
    
    public static Set<String> getIdFromPayload(EntityID entityID, MessagePayload payload) {
        Model payloadModel = payload.getJenaModel();
        Set<String> names = new HashSet<>();
        Property hasName = payloadModel.createProperty(propHasIdURI);
        StmtIterator stmtIt = payloadModel.listStatements(new SimpleSelector(entityID.getJenaResource(), hasName, (RDFNode) null));
        while (stmtIt.hasNext()) {
            RDFNode node = stmtIt.next().getObject();
            if (node.isLiteral()) {
                names.add(node.asLiteral().getValue().toString());
            } else {
                names.add(node.toString());
            }
        }
        return names;
    }
    
    public static Set<String> getPlatformIds(Message message){
		Set<EntityID> entityIds = getEntityIDsFromPayloadAsEntityIDSet(message.getPayload(), EntityTypeDevice);
		Set<String> deviceIds = new HashSet<>();
		for (EntityID entityId : entityIds) {
			deviceIds.addAll(getIdFromPayload(entityId, message.getPayload()));
		}
		return deviceIds;
	}
    
    public static Set<String> getEntityIDsFromPayload(MessagePayload payload, String entityType) {
        Model model = payload.getJenaModel();
        return model.listStatements(new SimpleSelector(null, RDF.type, model.createResource(entityType))).toSet().stream().map(x -> x.getSubject().toString()).collect(Collectors.toSet());
    }
    

    public static Set<String> getEntityIds(Message message){
		return getEntityIDsFromPayload(message.getPayload(), EntityTypeDevice);
	}
    
    public static String[] filterThingID(String thingId) {
    	String[] filteredString = null; //= thingId;

		// TODO: CHECK IF THIS IS APPROPRIATE FOR SOFIA2
    	if (thingId.contains("http://")) {
    		if(thingId.contains("#")){
    			// ThingId http://inter-iot.eu/dev/{ontName}/{idName}#{id}
    			thingId = thingId.replace("#", "/");
        		String[] splitId = thingId.split("/");
        		filteredString = new String[3];
        		filteredString[0] = splitId[splitId.length - 3];  // Ontology name
        		filteredString[1] = splitId[splitId.length - 2]; // Id name
        		filteredString[2] = splitId[splitId.length - 1]; // id value
//        		filteredString = splitId[splitId.length - 1];
//        		filteredString = filteredString.replace("#", "");
    		}else{
    			// http://inter-iot.eu/dev/{ontName}
    			String[] splitId = thingId.split("/");
        		filteredString = new String[1];
        		filteredString[0] = splitId[splitId.length - 1]; // Ontology name (for subscription to an ontology)
    		}
    		
		}else{
			if (thingId.contains("/")) {
				filteredString = new String[1];
				filteredString[0] = thingId.replace("/", "-");
			}
			if (thingId.contains("#")) {
				filteredString = new String[1];
				filteredString[0] = thingId.replace("#", "+");
			}
		}
    	
		return filteredString;
	}
    
}
