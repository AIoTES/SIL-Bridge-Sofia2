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
import java.net.Socket;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Properties;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import eu.interiot.intermw.bridge.exceptions.BridgeException;


public class Sofia2Client {
	private String url;
	private String KpInstance =  "sofia2Bridge"; // TODO: add to bridge configuration (?)
	private String sessionKey;
	private String KP;
	private String deviceOntologyName;
//	private String deviceIdentifier;
	private String identifierType;
	private final String STRING_TYPE = "string"; 
	private String TOKEN;
	private String sofiaUser, sofiaPassword;
	private int msRefresh = 0; // TODO: Add to properties?
	Thread sessionRefresh;
	private final Logger logger = LoggerFactory.getLogger(Sofia2Client.class);
	
	Sofia2Client(Properties properties, String baseUrl) throws Exception{
		try {
            sofiaUser = properties.getProperty(Sofia2Bridge.PROPERTIES_PREFIX + "user"); // USER + PASSWORD OR TOKEN?
            sofiaPassword = properties.getProperty(Sofia2Bridge.PROPERTIES_PREFIX + "password");
            TOKEN = properties.getProperty(Sofia2Bridge.PROPERTIES_PREFIX + "token");
 //           url = properties.getProperty(Sofia2Bridge.PROPERTIES_PREFIX + "address", DEFAULT_URL);
            url = baseUrl;
            KP = properties.getProperty(Sofia2Bridge.PROPERTIES_PREFIX + "KP", "activage"); // TODO: CREATE KP IN SOFIA2 PLATFORM
            deviceOntologyName = properties.getProperty(Sofia2Bridge.PROPERTIES_PREFIX + "device-class");
//            deviceIdentifier = properties.getProperty(Sofia2Bridge.PROPERTIES_PREFIX + "device-identifier");
            identifierType = properties.getProperty(Sofia2Bridge.PROPERTIES_PREFIX + "device-identifier-type", STRING_TYPE);
            sessionKey = null;
            
            sessionRefresh = new Thread(){
				public void run(){
					
					while(!this.isInterrupted()){
						 try {
							Thread.sleep(600000);
							join();
						} catch (InterruptedException e) {
							Thread.currentThread().interrupt();
						}catch (Exception ex) {
							ex.printStackTrace();
							Thread.currentThread().interrupt();
						} 
					}
				}
			};
            
         // THIS IS A HACK TO AVOID PROBLEMS WITH SSL CERTIFICATE HOSTNAME VERIFICATION
    		javax.net.ssl.HttpsURLConnection.setDefaultHostnameVerifier(
    			    new javax.net.ssl.HostnameVerifier(){
    			 
    			        public boolean verify(String hostname,
    			                javax.net.ssl.SSLSession sslSession) {
    			            if (hostname.contains("sofia2.televes")) {
    			                return true;
    			            }
    			            else if (hostname.equals("sofia2.com")){
    			            	return true;
    			            }
    			            else return false;
    			        }
    			    });
    		// THIS IS A HACK TO AVOID PROBLEMS WITH SSL SELF-SIGNED CERTIFICATES
    		 TrustManager[] trustAllCerts = new TrustManager[]{
                     new X509ExtendedTrustManager()
                     {
                         @Override
                         public java.security.cert.X509Certificate[] getAcceptedIssuers(){
                             return null;
                         }

                         @Override
                         public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType){}

                         @Override
                         public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType){}
                       

                         @Override
                         public void checkClientTrusted(java.security.cert.X509Certificate[] xcs, String string, SSLEngine ssle) throws CertificateException{}

                         @Override
                         public void checkServerTrusted(java.security.cert.X509Certificate[] xcs, String string, SSLEngine ssle) throws CertificateException{}

						@Override
						public void checkClientTrusted(X509Certificate[] arg0, String arg1, Socket arg2)
								throws CertificateException {
						}

						@Override
						public void checkServerTrusted(X509Certificate[] arg0, String arg1, Socket arg2)
								throws CertificateException {
						}

                     }
             };
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
    		
            
        } catch (Exception e) {
            throw new Exception("Failed to read SOFIA2 bridge configuration: " + e.getMessage());
        }
		
//		if(Strings.isNullOrEmpty(deviceOntologyName) || Strings.isNullOrEmpty(deviceIdentifier)) {
//			throw new BridgeException("Invalid SOFIA2 bridge configuration.");
//		}
		
		if (Strings.isNullOrEmpty(TOKEN) && (Strings.isNullOrEmpty(sofiaUser) || Strings.isNullOrEmpty(sofiaPassword))) {
            throw new BridgeException("Invalid SOFIA2 bridge configuration.");
        }else if(Strings.isNullOrEmpty(TOKEN)){
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
		int responseCode = 0;
		try{
			con.setDoInput(true);
			con.setRequestMethod("GET");
			con.setRequestProperty("Accept", "application/json"); 
			con.connect();
			responseCode = con.getResponseCode();
		}catch(Exception ex){
			throw ex;
		}
		 
		
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
		ssapResource.addProperty("instanceKP", KP +":"+ KpInstance);
		ssapResource.addProperty("token", TOKEN);
		if ((sessionKey != null) && (!sessionKey.equals(""))) ssapResource.addProperty("sessionKey", sessionKey); // Refresh session
		
		String responseJoin = invoke(queryURL, "POST", ssapResource);
		JsonParser parser = new JsonParser();
		JsonObject ssapResponse = parser.parse(responseJoin).getAsJsonObject();
		sessionKey = ssapResponse.get("sessionKey").getAsString();

		if ((sessionKey == null) || (sessionKey.equals(""))) {
			throw new Exception("JOIN operation failed");
		}else{
			sessionRefresh.start();
		}
		
	}
	
	void leave() throws Exception{
		String queryURL = url + "sib/services/api_ssap/v01/SSAPResource/";
		JsonObject ssapResource = new JsonObject();
		ssapResource.addProperty("leave", true);
		ssapResource.addProperty("sessionKey", sessionKey);
		sessionKey = null;
		sessionRefresh.interrupt();
		invoke(queryURL, "POST", ssapResource);
	}
	
	
	String query(String ontName, String fieldName, String fieldValue) throws Exception{
		// TODO: ADD MORE QUERIES
		
		String queryUrl = url + "sib/services/api_ssap/v01/SSAPResource";
		String query;
		
		if(identifierType.equals(STRING_TYPE)) query = "\"" + ontName + "." + fieldName + "\":\"" + fieldValue + "\"}"; // String id
		else query = "{\"" + ontName + "." + fieldName + "\":" + fieldValue + "}"; // Numeric id
		
		String params = "?$sessionKey=" + sessionKey;
		params = params + "&$ontology=" + ontName;
				
//		if(identifierType.equals(STRING_TYPE)) params = params + "&$query={\"" + ontName + "." + fieldName + "\":\"" + fieldValue + "\"}"; // String id
//		else params = params + "&$query={\"" + ontName + "." + fieldName + "\":" + fieldValue + "}"; // Numeric id
		
		params = params + "&$query=" + URLEncoder.encode(query, "UTF-8"); // TODO: CHECK IF THIS WORKS ON THE SERVER
		params = params + "&$queryType=NATIVE";
		logger.debug("Query: " + queryUrl + params);
		String response = invokeGet(queryUrl + params);
		logger.debug("Response: " + response);
		JsonParser parser = new JsonParser();
		JsonObject ssapObject = parser.parse(response.toString()).getAsJsonObject();
		String data = ssapObject.get("data").getAsString();
		// TODO: DO SOMETHING IF THE RESPONSE IS EMPTY
		return data;		
		
	}
	
//	String query(String id) throws Exception{
//		// PERHAPS THIS METHOD SHOULD CALL  GET /api_ssap/v01/SSAPResource/{oid} INSTEAD
//		return query(deviceOntologyName, deviceIdentifier, id); // TODO: LOOK FOR A MORE APPROPRIATE QUERY
//	}
	
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
	
	void register(String ontName, String fieldName, String thingId) throws Exception{
		String data = query(ontName, fieldName, thingId);
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
	
//	String subscribe(String id, String callback) throws Exception{
//		return subscribe(deviceOntologyName, deviceIdentifier, id, callback);
//	}
	
	String subscribe(String ontName, String fieldName, String fieldValue, String callback) throws Exception{
		String query;
		String subscriptionId = "";
		String queryUrl = url + "sib/services/api_ssap/v01/SSAPResource/subscribe";
		
//		query = "{\"" + ontName + "." + fieldName + "\":\"" + fieldValue + "\"}"; // NATIVE 
		
		// NATIVE 
//		if(identifierType.equals(STRING_TYPE)) query = "{\"" + ontName + "." + fieldName + "\":\"" + fieldValue + "\"}";
//		else query = "{\"" + ontName + "." + fieldName + "\":" + fieldValue + "}";
		
		// SQLLIKE
		if(identifierType.equals(STRING_TYPE)) query = "select * from " + ontName + " where " + ontName + "." + fieldName + " = \"" + fieldValue + "\""; // string identifier
		else query = "select * from " + ontName + " where " + ontName + "." + fieldName + " = " + fieldValue;  // numeric identifier
		
		String params = "?$sessionKey=" + sessionKey;
		params = params + "&$msRefresh=" + msRefresh;
		params = params + "&$ontology=" + ontName;
		params = params + "&$query=" + URLEncoder.encode(query, "UTF-8"); // TODO: CHECK IF THIS WORKS ON THE SERVER
//		params = params + "&$query=" + query; // THIS SHOULD WORK ON THE SERVER
//		params = params + "&$queryType=NATIVE"; // NATIVE
		params = params + "&$queryType=SQLLIKE"; //SQLLIKE
		params = params + "&$endpoint=" + callback;
		
		logger.debug("Subscription query: " + queryUrl + params);
		
		String responseBody = invokeGet(queryUrl + params);
		
		// GET SUBSCRIPTION ID FROM RESPONSE BODY
		JsonParser parser = new JsonParser();
		JsonObject responseObject = parser.parse(responseBody).getAsJsonObject();
		subscriptionId = responseObject.get("data").getAsString();
		
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
		ssapResource.addProperty("ontology", ontName);
		ssapResource.addProperty("data", data);
		
		invoke(queryURL, "PUT", ssapResource);
		
	}	
	
//	void delete(String thingId) throws Exception{
//		// PERHAPS THIS METHOD SHOULD CALL  DELETE /api_ssap/v01/SSAPResource/{oid} INSTEAD
//		delete(deviceOntologyName, deviceIdentifier, thingId);
//	}
	
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
