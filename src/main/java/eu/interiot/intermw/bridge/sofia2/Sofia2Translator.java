package eu.interiot.intermw.bridge.sofia2;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ValueNode;
import eu.interiot.translators.syntax.IllegalSyntaxException;
import eu.interiot.translators.syntax.SyntacticTranslator;
import org.apache.jena.datatypes.RDFDatatype;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.RDF;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;


public class Sofia2Translator extends SyntacticTranslator<String> {

    private String idURI;
    private String typeURI;
    private String valueURI;
    private String nameURI;
    
    private String attributeURI;
    private String instanceURI;

    private String instanceTypeURI;
    private String attributeTypeURI;

    private String arrayTypeURI;
    private String elementTypeURI;
    private String hasElementURI;
    private String hasNumberURI;

    private String valueTypeURI;
    
    ///////////// CONTEXT DATA
    private String timestampURI;
    private String sessionKeyURI;
    private String userURI;
    private String kpURI;
    private String kpInstanceURI;
    /////////////////////////////////
    
    private Property hasId;
    private Property hasType;

    private Property hasValue;
    private Property hasName;
    private Property hasAttribute;

    private Resource instanceType;
    private Resource attributeType;

    private Resource arrayType;
    private Resource elementType;

    private Property hasElement;
    private Property hasNumber;
    private Resource valueType;
    
    ///////////// CONTEXT DATA
    private Property hasTimestamp;
    private Property hasSessionKey;
    private Property hasUser;
    private Property hasKp;
    private Property hasKpInstance;
    /////////////////////////////////
    
    
    public static String sofia2baseURI = "http://inter-iot.eu/syntax/SOFIA2#";

    public Sofia2Translator() {
        super(sofia2baseURI, "SOFIA2");

        setIdURI(getBaseURI() + "hasId");
        setTypeURI(getBaseURI() + "hasType");
        setValueURI(getBaseURI() + "hasValue");
        setNameURI(getBaseURI() + "hasName");
        
        setAttributeURI(getBaseURI() + "hasAttribute");
        setInstanceURI(getBaseURI() + "hasInstance");

        setInstanceTypeURI(getBaseURI() + "Instance"); 
        setAttributeTypeURI(getBaseURI() + "Attribute");

        setArrayTypeURI(getBaseURI() + "Array");
        setElementTypeURI(getBaseURI() + "ArrayElement");
        setHasElementURI(getBaseURI() + "hasElement");
        setHasNumberURI(getBaseURI() + "hasNumber");

        setValueTypeURI(getBaseURI() + "Value");
        
        /////////////////////////////////////
        setTimestampURI(getBaseURI() + "hasTimestamp");
        setSessionKeyURI(getBaseURI() + "hasSessionKey");
        setUserURI(getBaseURI() + "hasUser");
        setKpURI(getBaseURI() + "hasKp");
        setKpInstanceURI(getBaseURI() + "hasKpInstance");
        ////////////////////////////////////
        
        Model jenaModel = ModelFactory.createDefaultModel();

        hasId = jenaModel.createProperty(getIdURI());
        hasType = jenaModel.createProperty(getTypeURI());
        hasValue = jenaModel.createProperty(getValueURI());
        hasName = jenaModel.createProperty(getNameURI());
        
        hasAttribute = jenaModel.createProperty(getAttributeURI());

        instanceType = jenaModel.createResource(getInstanceTypeURI());
        attributeType = jenaModel.createResource(getAttributeTypeURI());

        arrayType = jenaModel.createResource(getArrayTypeURI());
        elementType = jenaModel.createResource(getElementTypeURI());
        hasElement = jenaModel.createProperty(getHasElementURI());
        hasNumber = jenaModel.createProperty(getHasNumberURI());

        valueType = jenaModel.createResource(getValueTypeURI());
        
        ///////////////////////////////
        hasTimestamp = jenaModel.createProperty(getTimestampURI());
        hasSessionKey = jenaModel.createProperty(getSessionKeyURI());
        hasUser = jenaModel.createProperty(getUserURI());
        hasKp = jenaModel.createProperty(getKpURI());
        hasKpInstance = jenaModel.createProperty(getKpInstanceURI());
        ///////////////////////////////
        
    }

    @Override
    public Model toJenaModel(String formatXString) throws IOException {

        ObjectMapper mapper = new ObjectMapper();
        JsonFactory factory = mapper.getFactory();
        JsonParser parser = factory.createParser(formatXString);
        JsonNode topLevelNode = mapper.readTree(parser);

        Model jenaModel = ModelFactory.createDefaultModel();

        if (topLevelNode.isObject()) {
            Resource myEntity = jenaModel.createResource();
            parseJSONObjectToJena(myEntity, topLevelNode, jenaModel);
        } else if (topLevelNode.isArray()) {
            Resource arrayResource = jenaModel.createResource();
            arrayResource.addProperty(RDF.type, arrayType);
            parseArrayToJena(arrayResource, topLevelNode, jenaModel);
        } else if (topLevelNode.isValueNode()) {
            Resource valueResource = jenaModel.createResource();
            valueResource.addProperty(RDF.type, valueType);
            parseValueToJena(valueResource, topLevelNode, jenaModel);
//            valueResource.addProperty(hasValue, topLevelNode.asText());
        }

        return jenaModel;
    }

    private void parseJSONObjectToJena(Resource objectResource, JsonNode objectNode, Model jenaModel) {
        Iterator<Map.Entry<String, JsonNode>> it = objectNode.fields();

        while (it.hasNext()) {
            Map.Entry<String, JsonNode> field = it.next();
            if (field.getKey().equals("_id")) {
                //The instance has to have an id
            	// {"_id":{"$oid":"..."}}
                objectResource.addProperty(RDF.type, instanceType);
                JsonNode idNode = field.getValue();
                objectResource.addProperty(hasId, idNode.get("$oid").asText());
            } else if (field.getKey().equals("contextData")) {
                objectResource.addProperty(hasSessionKey, field.getValue().get("session_key").asText());
                objectResource.addProperty(hasUser, field.getValue().get("user").asText());
                objectResource.addProperty(hasKp, field.getValue().get("kp").asText());
                objectResource.addProperty(hasKpInstance, field.getValue().get("kp_instancia").asText());
                JsonNode timeNode = field.getValue().get("timestamp");
                objectResource.addProperty(hasTimestamp, timeNode.get("$date").asText());
            } else {
                //Add attribute to object
            	// JSON OBJECT OR ONTOLOGY INSTANCE. IF IT'S AN ONTOLOGY INSTANCE, TRANSLATE AS "hasType" + OTHER ATTRIBUTES
            	// CHECK IF THIS IS TRUE
            	JsonNode node = field.getValue();
            	if(node.isObject()){
            		 Iterator<Map.Entry<String, JsonNode>> itNode = objectNode.fields();
            		 Map.Entry<String, JsonNode> instance = itNode.next();
            		 	if(!itNode.hasNext() && !instance.getKey().equals("$date")){
            		 		objectResource.addProperty(hasType, instance.getKey()); // The key is the class name
            		 		parseJSONObjectToJena(objectResource, instance.getValue(), jenaModel); // The value is a JSON object containing the attributes
            		 	}else{
            		 		Resource attr = jenaModel.createResource();
                            attr.addProperty(RDF.type, attributeType);
                            objectResource.addProperty(hasAttribute, attr);
                            attr.addProperty(hasName, field.getKey());
                            parseValueToJena(attr, field.getValue(), jenaModel);
            		 	}
            	}else{
            		Resource attr = jenaModel.createResource();
                    attr.addProperty(RDF.type, attributeType);
                    objectResource.addProperty(hasAttribute, attr);
                    attr.addProperty(hasName, field.getKey());
                    parseValueToJena(attr, field.getValue(), jenaModel);
            	}
                
//                parseJSONObjectToJena(attr, field.getValue(), jenaModel);
            }
        }
    }

    private void parseValueToJena(Resource res, JsonNode jsonNode, Model jenaModel) {
        Property hasValProperty = hasValue;

        //If its a data value
        if (jsonNode.isValueNode()) {
            if (jsonNode.isNumber()) {
                //Numeric value
                if (jsonNode.isInt()) {
                    res.addLiteral(hasValProperty, new Integer(jsonNode.asInt()));
                } else if (jsonNode.isLong()) {
                    res.addLiteral(hasValProperty, jsonNode.asLong());
                } else if (jsonNode.isFloat() || jsonNode.isFloatingPointNumber()) {
                    res.addLiteral(hasValProperty, new Float(jsonNode.asDouble()));
                } else {
                    res.addLiteral(hasValProperty, jsonNode.asDouble());
                }
            } else if (jsonNode.isBoolean()) {
                //Boolean value
                res.addLiteral(hasValProperty, jsonNode.asBoolean());
            } else if (jsonNode.isTextual()) {
                //Textual value
                res.addLiteral(hasValProperty, jsonNode.asText());
            } else {
                //Not-typed (string) value
                res.addProperty(hasValProperty, jsonNode.asText());
            }
//            res.addLiteral(hasValProperty, jsonNode.asText());
        } else if (jsonNode.isArray()) {
            //If its an array of values
            Resource arrayResource = jenaModel.createResource();
            arrayResource.addProperty(RDF.type, arrayType);
            res.addProperty(hasValProperty, arrayResource);

            parseArrayToJena(arrayResource, jsonNode, jenaModel);
        } else if (jsonNode.isObject()) {
            //If its a JSON object
            Resource objectResource = jenaModel.createResource();
            //TODO: Add type to object resource? Is this necessary?
//            objectResource.addProperty(RDF.type, "xxxxxx");
            res.addProperty(hasValProperty, objectResource);
            parseJSONObjectToJena(objectResource, jsonNode, jenaModel);
        }
    }

    private void parseArrayToJena(Resource arrayResource, JsonNode arrayNode, Model jenaModel) {
        int counter = 1;
        Iterator<JsonNode> arrayIt = arrayNode.elements();
        while (arrayIt.hasNext()) {
            JsonNode element = arrayIt.next();
            Resource jenaElement = jenaModel.createResource();
            jenaElement.addProperty(RDF.type, elementType);
            arrayResource.addProperty(hasElement, jenaElement);
            jenaElement.addLiteral(hasNumber, new Integer(counter++));
            parseValueToJena(jenaElement, element, jenaModel);
        }
    }

    @Override
    public String toFormatX(Model jenaModelParam) throws IllegalSyntaxException {
    	// TODO
        Model jenaModel = ModelFactory.createDefaultModel().add(jenaModelParam);
        ObjectMapper mapper = new ObjectMapper();
        LinkedList<JsonNode> jsonNodeList = new LinkedList<JsonNode>();

        //Find the top-level RDF Entity (the entity that does not appear in Object of RDF triples)
        Query topLevelQuery = QueryFactory.create("SELECT DISTINCT ?top WHERE { ?top ?y ?z MINUS {?a ?b ?top} }");
        QueryExecution topLevelQueryExec = QueryExecutionFactory.create(topLevelQuery, jenaModel);
        ResultSet results = topLevelQueryExec.execSelect();

        while (results.hasNext()) {
            QuerySolution solution = results.next();
            RDFNode resultNode = solution.get("top");
//            System.out.println(resultNode);

            StmtIterator typesIt = jenaModel.listStatements(new SimpleSelector(resultNode.asResource(), RDF.type, (RDFNode) null));
            StmtIterator propsIt = jenaModel.listStatements(new SimpleSelector(resultNode.asResource(), null, (RDFNode) null));
            Set<Resource> types = typesIt.toSet().stream().map(x -> x.getObject().asResource()).collect(Collectors.toSet());
            Set<Property> props = propsIt.toSet().stream().map(x -> x.getPredicate()).collect(Collectors.toSet());
            if (
                //Assume that a resource that has some RDF type from SOFIA2 is a SOFIA2 object/array/value
                    types.contains(arrayType) ||
                            types.contains(valueType) ||
                            types.contains(instanceType) ||
                            //Assume that a resource that has some of the SOFIA2 properties is of SOFIA2 type
                            props.contains(hasAttribute) ||
                            props.contains(hasValue) ||
                            props.contains(hasType) ||
                            props.contains(hasTimestamp) ||
                            props.contains(hasUser) ||
                            props.contains(hasSessionKey) ||
                            props.contains(hasKp) ||
                            props.contains(hasKpInstance) ||
                            props.contains(hasName) ||
                            props.contains(hasId) ||
                            props.contains(hasElement)
                    ) {
                JsonNode someTopLevelNode = parseRDFEntityToJson(resultNode.asResource(), jenaModel, mapper);
                if (someTopLevelNode != null) {
                    jsonNodeList.add(someTopLevelNode);
                }
            }
        }

        JsonNode topLevelNode = null;
        if (jsonNodeList.isEmpty()) {
            //TODO: Throw exception "invalid RDF structure for FIWARE translation - no top-level entity found"
            throw new IllegalSyntaxException("No top-level RDF entity found (a root node in RDF graph is required in SOFIA2 RDF)");
//            topLevelNode = mapper.createObjectNode();
        } else if (jsonNodeList.size() == 1) {
            topLevelNode = jsonNodeList.getFirst();
        } else {
            topLevelNode = mapper.createArrayNode();
            for (JsonNode node : jsonNodeList)
                ((ArrayNode) topLevelNode).add(node);
        }

        return topLevelNode.toString();

        //Pretty printing
//        try {
//            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(topLevelNode);
//        } catch (Exception e) {
//            //Log exception
//            return null;
//        }
    }

    private JsonNode parseRDFEntityToJson(Resource entityResource, Model jenaModel, ObjectMapper mapper) {
        //TODO: Implement

        //Check entity type
        StmtIterator typesIt = jenaModel.listStatements(new SimpleSelector(entityResource, RDF.type, (RDFNode) null));
        while (typesIt.hasNext()) {
            Statement stmt = typesIt.next();
            Resource type = stmt.getObject().asResource();

            if (type.equals(instanceType)) {
                ObjectNode jsonNode = mapper.createObjectNode();
//                typesIt.remove();
                parseAttributesToJson(entityResource, jsonNode, jenaModel, mapper);
                return jsonNode;

            } else if (type.equals(arrayType)) {
                ArrayNode jsonNode = parseArrayEntityToJsonArray(entityResource, jenaModel, mapper);
//                typesIt.remove();
                return jsonNode;

            } else if (type.equals(valueType)) {
                //Parse value
                StmtIterator valuesIt = jenaModel.listStatements(entityResource, hasValue, (String) null);
                while (valuesIt.hasNext()) {
                    Statement valStmt = valuesIt.next();

                    if (valStmt.getObject().isLiteral()) {
                        ValueNode jsonNode = parseLiteralToValueNode(valStmt.getObject().asLiteral(), mapper);
//                        typesIt.remove();
//                        valuesIt.remove();
                        return jsonNode;
                    }
                }
            }
        }

        //Do we assume object node at this point?
        //No type - just parse properties (and type, id, name, value and metadata) and add them as attributes
        ObjectNode jsonNode = mapper.createObjectNode();
//                typesIt.remove();
        parseAttributesToJson(entityResource, jsonNode, jenaModel, mapper);
        return jsonNode;
    }

    private void parseAttributesToJson(Resource entityResource, ObjectNode entity, Model jenaModel, ObjectMapper mapper) {

        //TODO
    	String value;
    	
        //Parse id
        NodeIterator nodeIterator = jenaModel.listObjectsOfProperty(entityResource, hasId); // TRANSLATE AS {"_id":{"$oid":"..."}}
        if (nodeIterator.hasNext()) {
        	value = nodeIterator.next().toString();
            ObjectNode attributeNode = mapper.createObjectNode();
            attributeNode.put("$oid", value);
            entity.set("_id", attributeNode);
        }
        
        // PARSE CONTEXT DATA
        ObjectNode contextData = mapper.createObjectNode();
        
        //Parse timestamp
        nodeIterator = jenaModel.listObjectsOfProperty(entityResource, hasTimestamp);
        if (nodeIterator.hasNext()) {
        	value = nodeIterator.next().toString();
        	ObjectNode dateNode = mapper.createObjectNode();
        	dateNode.put("$date", value);
        	contextData.set("timestamp", dateNode);
        }
        
        nodeIterator = jenaModel.listObjectsOfProperty(entityResource, hasUser);
        if (nodeIterator.hasNext()) {
        	value = nodeIterator.next().toString();
        	contextData.put("user", value);
        }
        
        nodeIterator = jenaModel.listObjectsOfProperty(entityResource, hasSessionKey);
        if (nodeIterator.hasNext()) {
        	value = nodeIterator.next().toString();
        	contextData.put("session_key", value);
        }
        
        nodeIterator = jenaModel.listObjectsOfProperty(entityResource, hasKp);
        if (nodeIterator.hasNext()) {
        	value = nodeIterator.next().toString();
        	contextData.put("kp", value);
        }
        
        nodeIterator = jenaModel.listObjectsOfProperty(entityResource, hasKpInstance);
        if (nodeIterator.hasNext()) {
        	value = nodeIterator.next().toString();
        	contextData.put("kp_instancia", value);
        }
        
        if (contextData.size() > 0){
        	entity.set("contextData", contextData);
        }
        
      //Parse type
        nodeIterator = jenaModel.listObjectsOfProperty(entityResource, hasType); // TRANSLATE AS {"OntologyName":{...}}
        if (nodeIterator.hasNext()) {
        	String ontName = nodeIterator.next().toString();
        	ObjectNode dataNode = mapper.createObjectNode();
        	entity.set(ontName, dataNode);
        	parseAttributesToJson(nodeIterator.next().asResource(), dataNode, jenaModel, mapper);
 //       	JsonNode dataNode = parseRDFEntityToJson(nodeIterator.next().asResource(), jenaModel, mapper);
 //       	entity.set(ontName, dataNode);
        }
        
        int i = 1;
        NodeIterator it = jenaModel.listObjectsOfProperty(entityResource, hasAttribute); // TODO: CHECK IF THIS IS CORRECT
        while (it.hasNext()) {
            RDFNode attribute = it.next();
            //Check if it really is an attribute
            if (attribute.isResource() && attribute.asResource().hasProperty(RDF.type, attributeType)) {

                String attributeName = "";
                //Parse attribute name
                NodeIterator names = jenaModel.listObjectsOfProperty(attribute.asResource(), hasName);
                if (names.hasNext()) {
                    attributeName = names.next().toString();
                } else {
                    attributeName = "anonymous_attribute_" + i++;
                }

                NodeIterator valIterator = jenaModel.listObjectsOfProperty(attribute.asResource(), hasValue);
                if (valIterator.hasNext()) {
                    RDFNode valueNode = valIterator.next();

                    if (valueNode.isResource()) {
//                        JsonNode jsonNode = parseRDFEntityToJson(valueNode.asResource(), jenaModel, mapper);
                        ObjectNode attributeNode = mapper.createObjectNode();
//                        entity.set(attributeName, attributeNode);
                        entity.set(attributeName, parseRDFEntityToJson(valueNode.asResource(), jenaModel, mapper));
//                        parseAttributesToJson(valueNode.asResource(), attributeNode, jenaModel, mapper);
                    } else if (valueNode.isLiteral()) {
                        ValueNode jsonValueNode = parseLiteralToValueNode(valueNode.asLiteral(), mapper);
                        entity.set(attributeName, jsonValueNode);
                    }
                } else {
                    ObjectNode attributeNode = mapper.createObjectNode();
                    entity.set(attributeName, attributeNode);
                    parseAttributesToJson(attribute.asResource(), attributeNode, jenaModel, mapper);
                }
            }
        }

    }

    private ArrayNode parseArrayEntityToJsonArray(Resource arrayResource, Model jenaModel, ObjectMapper mapper) {
        //TODO: Finish this
        ArrayNode jsonArrayNode = mapper.createArrayNode();

        //Cannot ask for a binding to a blank node :(
//        String queryString = String.format("SELECT DISTINCT ?index ?value \n{ \n" +
//                        "?array <%s> ?element . \n" +
//                        "?element <%s> ?index . \n" +
//                        "?element <%s> ?value . \n" +
//                        "BIND (<%s> AS ?array)\n}",
//                hasElement, hasNumber, hasValue, arrayResource);

        StmtIterator arrayElementsIt = jenaModel.listStatements(new SimpleSelector(arrayResource, hasElement, (RDFNode) null));
        //We need a map, because elements may arrive in any order
        TreeMap<Integer, JsonNode> arrayElements = new TreeMap<Integer, JsonNode>();
        while (arrayElementsIt.hasNext()) {
            Statement stmt = arrayElementsIt.next();
            if (stmt.getObject().isResource()) {
                //Get index
                Optional<Integer> i = Optional.empty();
                StmtIterator indexIterator = jenaModel.listStatements(new SimpleSelector(stmt.getObject().asResource(), hasNumber, (String) null));
                if (indexIterator.hasNext()) {
                    RDFNode indexNode = indexIterator.next().getObject();
                    if (indexNode.isLiteral()) {
                        i = Optional.of(indexNode.asLiteral().getInt());
                    }
                }
                //Get value
                Optional<JsonNode> elementValueNode = Optional.empty();
                StmtIterator valueIterator = jenaModel.listStatements(new SimpleSelector(stmt.getObject().asResource(), hasValue, (RDFNode) null));
                if (valueIterator.hasNext()) {
                    RDFNode valueNode = valueIterator.next().getObject();
                    if (valueNode.isLiteral()) {
                        elementValueNode = Optional.of(parseLiteralToValueNode(valueNode.asLiteral(), mapper));
                    } else if (valueNode.isResource()) {
                        elementValueNode = Optional.of(parseRDFEntityToJson(valueNode.asResource(), jenaModel, mapper));
                    }

                }

                if (i.isPresent() && elementValueNode.isPresent()) {
                    arrayElements.put(i.get(), elementValueNode.get());
                }
            }
        }

        //TODO: Test and check if this is ALWAYS sorted
        Iterator<java.util.Map.Entry<Integer, JsonNode>> it = arrayElements.entrySet().iterator();
        while (it.hasNext()) {
            jsonArrayNode.add(it.next().getValue());
        }

        return jsonArrayNode;
    }

    private ValueNode parseLiteralToValueNode(Literal literal, ObjectMapper mapper) {
        //TODO: Maybe add more datatypes

        RDFDatatype datatype = literal.getDatatype();
        if (datatype != null) {
            if (datatype.equals(XSDDatatype.XSDboolean)) {
                return mapper.getNodeFactory().booleanNode(literal.getBoolean());
            }

            if (datatype.equals(XSDDatatype.XSDint) || datatype.equals(XSDDatatype.XSDinteger)) {
                return mapper.getNodeFactory().numberNode(literal.getInt());
            }

            if (datatype.equals(XSDDatatype.XSDlong)) {
                return mapper.getNodeFactory().numberNode(literal.getLong());
            }

            if (datatype.equals(XSDDatatype.XSDfloat)) {
                return mapper.getNodeFactory().numberNode(literal.getFloat());
            }

            if (datatype.equals(XSDDatatype.XSDdouble)) {
                return mapper.getNodeFactory().numberNode(literal.getDouble());
            }
            return mapper.getNodeFactory().textNode(literal.getString());
        }

        return mapper.getNodeFactory().textNode(literal.getValue().toString());
    }

    /**
     * Takes in a JSON string and attempts to make it pretties by adding spaces, indentation and new lines
     *
     * @param jsonString
     * @return
     * @throws IOException
     */
    public String prettifyJsonString(String jsonString) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonFactory factory = mapper.getFactory();
        JsonParser parser = factory.createParser(jsonString);
        JsonNode jsonNode = mapper.readTree(parser);
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonNode);
    }

    public String getIdURI() {
        return idURI;
    }

    public void setIdURI(String idURI) {
        this.idURI = idURI;
    }

    public String getTypeURI() {
        return typeURI;
    }

    public void setTypeURI(String typeURI) {
        this.typeURI = typeURI;
    }

    public String getValueURI() {
        return valueURI;
    }

    public void setValueURI(String valueURI) {
        this.valueURI = valueURI;
    }

    public String getNameURI() {
        return nameURI;
    }

    public void setNameURI(String nameURI) {
        this.nameURI = nameURI;
    }

    public String getAttributeURI() {
        return attributeURI;
    }

    public void setAttributeURI(String attributeURI) {
        this.attributeURI = attributeURI;
    }

    public String getInstanceURI() {
        return instanceURI;
    }

    public void setInstanceURI(String entityURI) {
        this.instanceURI = entityURI;
    }

    public String getInstanceTypeURI() {
        return instanceTypeURI;
    }

    public void setInstanceTypeURI(String entityTypeURI) {
        this.instanceTypeURI = entityTypeURI;
    }

    public String getAttributeTypeURI() {
        return attributeTypeURI;
    }

    public void setAttributeTypeURI(String attributeTypeURI) {
        this.attributeTypeURI = attributeTypeURI;
    }

    public String getArrayTypeURI() {
        return arrayTypeURI;
    }

    public void setArrayTypeURI(String arrayTypeURI) {
        this.arrayTypeURI = arrayTypeURI;
    }

    public String getElementTypeURI() {
        return elementTypeURI;
    }

    public void setElementTypeURI(String elementTypeURI) {
        this.elementTypeURI = elementTypeURI;
    }

    public String getHasElementURI() {
        return hasElementURI;
    }

    public void setHasElementURI(String hasElementURI) {
        this.hasElementURI = hasElementURI;
    }

    public String getHasNumberURI() {
        return hasNumberURI;
    }

    public void setHasNumberURI(String hasNumberURI) {
        this.hasNumberURI = hasNumberURI;
    }

    public String getValueTypeURI() {
        return valueTypeURI;
    }

    public void setValueTypeURI(String valueTypeURI) {
        this.valueTypeURI = valueTypeURI;
    }
    
    ////////////////////////////////////
    
    public void setTimestampURI(String timestampURI) {
        this.timestampURI = timestampURI;
    }
    
    public String getTimestampURI() {
        return timestampURI;
    }
    
    public void setSessionKeyURI(String sessionKeyURI) {
        this.sessionKeyURI = sessionKeyURI;
    }
    
    public String getSessionKeyURI() {
        return sessionKeyURI;
    }
    
    public void setUserURI(String userURI) {
        this.userURI = userURI;
    }
    
    public String getUserURI() {
        return userURI;
    }
    
    public void setKpURI(String kpURI) {
        this.kpURI = kpURI;
    }
    
    public String getKpURI() {
        return kpURI;
    }
    
    public void setKpInstanceURI(String kpInstanceURI) {
        this.kpInstanceURI = kpInstanceURI;
    }
    
    public String getKpInstanceURI() {
        return kpInstanceURI;
    }
    
    
}
