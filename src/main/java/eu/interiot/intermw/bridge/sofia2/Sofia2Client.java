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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
//import javax.net.ssl.HttpsURLConnection;
import java.util.Properties;

import com.google.common.base.Strings;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import eu.interiot.intermw.bridge.exceptions.BridgeException;


public class Sofia2Client {
	private String url;
	private String KpInstance =  "Activage"; // TODO: add to bridge configuration (?)
	private String sessionKey;
	private String KP;
	private String deviceOntologyName;
	private String deviceIdentifier;
	private static final String DEFAULT_URL = "https://sofia2.com/"; // TODO: CHANGE DEFAULT URL
	private String TOKEN;
	
	Sofia2Client(Properties properties) throws Exception{
		String sofiaUser, sofiaPassword;
		try {
            sofiaUser = properties.getProperty(Sofia2Bridge.PROPERTIES_PREFIX + "user"); // USER + PASSWORD OR TOKEN?
            sofiaPassword = properties.getProperty(Sofia2Bridge.PROPERTIES_PREFIX + "password");
            TOKEN = properties.getProperty(Sofia2Bridge.PROPERTIES_PREFIX + "token");
            url = properties.getProperty(Sofia2Bridge.PROPERTIES_PREFIX + "address", DEFAULT_URL);
            KP = properties.getProperty(Sofia2Bridge.PROPERTIES_PREFIX + "KP", "activage"); // TODO: CREATE KP IN SOFIA2 PLATFORM
            deviceOntologyName = properties.getProperty(Sofia2Bridge.PROPERTIES_PREFIX + "device-class", "InventarioDispositivos"); // TODO: DECIDE DEFAUL ONTOLOGY NAME
            deviceIdentifier = properties.getProperty(Sofia2Bridge.PROPERTIES_PREFIX + "device-identifier", "numserie"); // TODO: DECIDE DEVICE IDENTIFIER PROPERTY
        } catch (Exception e) {
            throw new Exception("Failed to read SOFIA2 bridge configuration: " + e.getMessage());
        }
		
		if (Strings.isNullOrEmpty(TOKEN) && (Strings.isNullOrEmpty(sofiaUser) || Strings.isNullOrEmpty(sofiaPassword))) {
            throw new BridgeException("Invalid SOFIA2 bridge configuration.");
        }
		if(Strings.isNullOrEmpty(TOKEN)){
			String authUrl = url + "console/api/rest/kps/" + KP + "/tokens"; 
			getToken(authUrl, sofiaUser, sofiaPassword);
		}
	}
		
	String invoke(String queryUrl, String method, JsonObject ssapResource) throws Exception{
		URL obj = new URL(queryUrl);
		byte[] postData = ssapResource.toString().getBytes(StandardCharsets.UTF_8); 
		HttpURLConnection con = (HttpURLConnection) obj.openConnection();
		con.setDoOutput(true);
		con.setDoInput(true);
		con.setUseCaches(false);
		con.setRequestMethod(method);
		con.setRequestProperty("Content-Type", "application/json; charset=UTF-8"); 
		con.setRequestProperty("Content-Length", Integer.toString(postData.length));
		con.connect();
		OutputStream os = con.getOutputStream();
		os.write(postData);
		int responseCode = con.getResponseCode(); 
		
		if (responseCode < 200 || responseCode > 299) {
            throw new Exception("Unsuccessful server response: " + responseCode);
        }
		
		BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
		String inputLine;
		StringBuffer response = new StringBuffer();

		while ((inputLine = in.readLine()) != null) {
			response.append(inputLine);
		}
		in.close();
		os.flush();
		os.close();
		con.disconnect();
		return response.toString();
	}
	
	String invokeGet(String queryUrl) throws Exception{
		URL obj = new URL(queryUrl);
		HttpURLConnection con = (HttpURLConnection) obj.openConnection();
		con.setDoInput(true);
		con.setUseCaches(false);
		con.setRequestMethod("GET");
		con.setRequestProperty("Accept", "application/json; charset=UTF-8"); 
		con.connect();
		int responseCode = con.getResponseCode(); 
		
		if (responseCode < 200 || responseCode > 299) {
            throw new Exception("Unsuccessful server response: " + responseCode);
        }
		
		BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
		String inputLine;
		StringBuffer response = new StringBuffer();

		while ((inputLine = in.readLine()) != null) {
			response.append(inputLine);
		}
		in.close();
		return response.toString();
	}
	
	void getToken(String queryUrl, String user, String password) throws Exception{
		URL obj = new URL(queryUrl);
		HttpURLConnection con = (HttpURLConnection) obj.openConnection();
		con.setDoInput(true);
		con.setUseCaches(false);
		con.setRequestMethod("GET");
		con.setRequestProperty("Accept", "application/json; charset=UTF-8");
		String authString = user + ":" + password;
		String basicAuth = "Basic " + javax.xml.bind.DatatypeConverter.printBase64Binary(authString.getBytes());
		con.setRequestProperty ("Authorization", basicAuth);
		con.connect();
		int responseCode = con.getResponseCode(); 
		
		if (responseCode < 200 || responseCode > 299) {
            throw new Exception("Unsuccessful server response: " + responseCode);
        }
		
		BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
		String inputLine;
		StringBuffer response = new StringBuffer();

		while ((inputLine = in.readLine()) != null) {
			response.append(inputLine);
		}
		
		JsonParser parser = new JsonParser();
		JsonArray body = parser.parse(response.toString()).getAsJsonArray();
		for(int i = 0; i< body.size(); i++){
			JsonObject object = body.get(i).getAsJsonObject();
			if(object.get("activo").getAsBoolean() == true){
				TOKEN = object.get("token").getAsString();
				break;
			}
		}
		in.close();
	}
	
	void join() throws Exception{
		String queryURL = url + "sib/services/api_ssap/v01/SSAPResource/";
		JsonObject ssapResource = new JsonObject();
		ssapResource.addProperty("join", true);
		ssapResource.addProperty("instanceKP", KpInstance);
		ssapResource.addProperty("token", TOKEN);
		
		String responseJoin = invoke(queryURL, "POST", ssapResource);
		JsonParser parser = new JsonParser();
		JsonObject ssapResponse = parser.parse(responseJoin).getAsJsonObject();
		sessionKey = ssapResponse.get("sessionKey").getAsString();

		if ((sessionKey == null) || (sessionKey.equals(""))) {
			throw new Exception("JOIN operation failed");
		}
		
	}
	
	void leave() throws Exception{
		String queryURL = url + "sib/services/api_ssap/v01/SSAPResource/";
		JsonObject ssapResource = new JsonObject();
		ssapResource.addProperty("leave", true);
		ssapResource.addProperty("sessionKey", sessionKey);
		sessionKey = null;
		invoke(queryURL, "POST", ssapResource);
	}
	
	
	String query(String ontName, String fieldName, String fieldValue) throws Exception{
		// TODO: ADD MORE QUERIES
		
		String queryUrl = url + "sib/services/api_ssap/v01/SSAPResource";
		
		String params = "?$sessionKey=" + sessionKey;
		params = params + "&$ontology=" + ontName;
		params = params + "&$query={\"" + ontName + "." + fieldName + "\":\"" + fieldValue + "\"}";
		params = params + "&$queryType=NATIVE";
		String response = invokeGet(queryUrl + params);
		JsonParser parser = new JsonParser();
		JsonObject ssapObject = parser.parse(response.toString()).getAsJsonObject();
		String data = ssapObject.get("data").getAsString();
		// TODO: DO SOMETHING IF THE RESPONSE IS EMPTY
		return data;		
		
	}
	
	String query(String id) throws Exception{
		// PERHAPS THIS METHOD SHOULD CALL  GET /api_ssap/v01/SSAPResource/{oid} INSTEAD
		return query(deviceOntologyName, deviceIdentifier, id); // TODO: LOOK FOR A MORE APPROPRIATE QUERY
	}
	
	String list(String ontName) throws Exception{
		// List all instances of an ontology
		//query db.OntologyName.find().limit(X)
		
		String queryUrl = url + "sib/services/api_ssap/v01/SSAPResource";
		
		String params = "?$sessionKey=" + sessionKey;
		params = params + "&$ontology=" + ontName;
		params = params + "&$query={\"" + ontName + ".find()\"}";
		params = params + "&$queryType=NATIVE";
		String response = invokeGet(queryUrl + params);
		JsonParser parser = new JsonParser();
		JsonObject ssapObject = parser.parse(response.toString()).getAsJsonObject();
		String data = ssapObject.get("data").getAsString();
		// TODO: DO SOMETHING IF THE RESPONSE IS EMPTY
		return data;		
		
	}
	
	String list() throws Exception{
		return list(deviceOntologyName);
	}
	
	void register(String thingId) throws Exception{
		// TODO: INCLUDE ONTOLOGY INFORMATION
		String data = query(thingId);
		if (data.equals("[ ]")){
			// Entity does not exist. Call insert method
			// TODO: CREATE JSON OBJECT THAT REPRESENTS THE THING IN SOFIA2 AND CALL INSERT METHOD
			throw new Exception("Thing does not exist");
//			insert(ontName, data);
		}
	}
	
	void insert(String ontName, String data) throws Exception{
		// TODO: FORMAT DATA AS A JSON OBJECT
		
		String queryURL = url + "sib/services/api_ssap/v01/SSAPResource/";
		JsonObject ssapResource = new JsonObject();
		ssapResource.addProperty("sessionKey", sessionKey);
		ssapResource.addProperty("ontology",ontName);
		ssapResource.addProperty("data", data);
		
		invoke(queryURL, "POST", ssapResource);
	}
	
	String subscribe(String id, String callback) throws Exception{
		return subscribe(deviceOntologyName, deviceIdentifier, id, callback);
	}
	
	String subscribe(String ontName, String fieldName, String fieldValue, String callback) throws Exception{
		
		String subscriptionId = "";
		String queryUrl = url + "sib/services/api_ssap/v01/SSAPResource/subscribe";
		
		String query = "{\"" + ontName + "." + fieldName + "\":\"" + fieldValue + "\"}"; // TODO: check if this query is correct 
		
		String params = "?$sessionKey=" + sessionKey;
		params = params + "&$query=" + query;
		params = params + "&$queryType=NATIVE";
		params = params + "&$msRefresh=200"; // At least 0.2 second between notifications
		params = params + "&$endpoint=" + callback;
		
		String responseBody = invokeGet(queryUrl + params);
		
		// GET SUBSCRIPTION ID FROM RESPONSE BODY
		JsonParser parser = new JsonParser();
		JsonObject responseObject = parser.parse(responseBody).getAsJsonObject();
		subscriptionId = responseObject.get("data").getAsString(); // TODO: CHECK SERVER RESPONSE
		
		return subscriptionId;
	}
	
	
	String unsubscribe(String id) throws Exception{
		String queryUrl = url + "sib/services/api_ssap/v01/SSAPResource/unsubscribe";
		String params = "?$sessionKey=" + sessionKey;
		params = params + "&$subscriptionId=" + id;
		
		return invokeGet(queryUrl + params);
	}
	
		
	void update(String ontName, String data) throws Exception{
		// TODO: FORMAT DATA AS A JSON OBJECT
		String queryURL = url + "sib/services/api_ssap/v01/SSAPResource/";
		JsonObject ssapResource = new JsonObject();
		ssapResource.addProperty("sessionKey", sessionKey);
		ssapResource.addProperty("ontology",ontName);
		ssapResource.addProperty("data", data);
		
		invoke(queryURL, "PUT", ssapResource);
		
	}	
	
	void delete(String thingId) throws Exception{
		// PERHAPS THIS METHOD SHOULD CALL  DELETE /api_ssap/v01/SSAPResource/{oid} INSTEAD
		delete(deviceOntologyName, deviceIdentifier,thingId);
	}
	
	void delete(String ontName, String fieldName, String fieldValue) throws Exception{
				
		String data = query(ontName, fieldName, fieldValue);
		if (data.equals("[ ]")){
			throw new Exception("Thing does not exist");
		}
		
		JsonParser parser = new JsonParser();
		JsonArray array = parser.parse(data).getAsJsonArray(); 
		JsonObject thing = array.get(0).getAsJsonObject();
		
		String queryURL = url + "sib/services/api_ssap/v01/SSAPResource/";
		JsonObject ssapResource = new JsonObject();
		ssapResource.addProperty("sessionKey", sessionKey);
		ssapResource.addProperty("ontology",ontName);
		JsonObject objectId = thing.get("_id").getAsJsonObject();
		JsonObject dataObject = new JsonObject();
		dataObject.add("_id", objectId); 
		ssapResource.addProperty("data", dataObject.toString()); // Object or string?
//		ssapResource.addProperty("data", thing.toString());
		
		invoke(queryURL, "DELETE", ssapResource);
	}
	
	
}
