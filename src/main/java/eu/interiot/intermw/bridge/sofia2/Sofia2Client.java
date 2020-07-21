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
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Properties;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import eu.interiot.intermw.bridge.exceptions.BridgeException;


public class Sofia2Client {
	private String url;
	private String KpInstance;
	private String sessionKey;
	private String KP;
	private String deviceOntologyName;
//	private String deviceIdentifier;
	private String identifierType;
	private final String STRING_TYPE = "string"; 
	private String TOKEN;
	private String sofiaUser, sofiaPassword;
	private int msSubscriptionRefresh;
	private int msSessionRefresh;
	Thread sessionRefresh;
	private final Logger logger = LoggerFactory.getLogger(Sofia2Client.class);
	private String trustStore;
	private String trustStorePass;
	
	Sofia2Client(Properties properties, String baseUrl) throws Exception{
		try {
            sofiaUser = properties.getProperty("user"); // USER + PASSWORD OR TOKEN?
            sofiaPassword = properties.getProperty("password");
            TOKEN = properties.getProperty("token");
            url = baseUrl;
            if (!url.endsWith("/")) url = url + "/"; // Just in case
            KP = properties.getProperty("KP");
            KpInstance = properties.getProperty("KP-instance", "sofia2Bridge");
            deviceOntologyName = properties.getProperty("device-class");
            identifierType = properties.getProperty("device-identifier-type", STRING_TYPE);
            trustStore = properties.getProperty("certificate"); // For self-signed certificates
            trustStorePass = properties.getProperty("certificate-password"); // For self-signed certificates
            msSubscriptionRefresh = Integer.valueOf(properties.getProperty("subscription-refresh", "0")); // Subscription refresh parameter
            msSessionRefresh = Integer.valueOf(properties.getProperty("session-refresh", "600000"));
            
            sessionKey = null;
            
            if(KP == null){
            	throw new Exception("Error in bridge configuration: no KP");
            }
            
            sessionRefresh = new Thread(){
				public void run(){
					
					while(!this.isInterrupted()){
						 try {
							Thread.sleep(msSessionRefresh);
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
            
			if(url.startsWith("https") && trustStore != null){
	    		// TO AVOID PROBLEMS WITH SSL SELF-SIGNED CERTIFICATES
	    		TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
	    			// Using null here initialises the TMF with the default trust store.
	    			tmf.init((KeyStore) null);

	    			// Get hold of the default trust manager
	    			X509TrustManager defaultTm = null;
	    			for (TrustManager tm : tmf.getTrustManagers()) {
	    			    if (tm instanceof X509TrustManager) {
	    			        defaultTm = (X509TrustManager) tm;
	    			        break;
	    			    }
	    			}

	    			FileInputStream myKeys = new FileInputStream(trustStore);

	    			// Custom trust store
	    			KeyStore myTrustStore = KeyStore.getInstance("JKS");
	    			myTrustStore.load(myKeys, trustStorePass.toCharArray());
	    			myKeys.close();
	    			tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
	    			tmf.init(myTrustStore);
	    			
	    			// Get hold of the default trust manager
	    			X509TrustManager myTm = null;
	    			for (TrustManager tm : tmf.getTrustManagers()) {
	    			    if (tm instanceof X509TrustManager) {
	    			        myTm = (X509TrustManager) tm;
	    			        break;
	    			    }
	    			}

	    			// Wrap it in your own class.
	    			final X509TrustManager finalDefaultTm = defaultTm;
	    			final X509TrustManager finalMyTm = myTm;
	    			X509TrustManager customTm = new X509TrustManager() {
	    			    @Override
	    			    public X509Certificate[] getAcceptedIssuers() {
	    			        // If you're planning to use client-cert auth,
	    			        // merge results from "defaultTm" and "myTm".
	    			        return finalDefaultTm.getAcceptedIssuers();
	    			    }

	    			    @Override
	    			    public void checkServerTrusted(X509Certificate[] chain,
	    			            String authType) throws CertificateException {
	    			        try {
	    			            finalMyTm.checkServerTrusted(chain, authType);
	    			        } catch (CertificateException e) {
	    			            // This will throw another CertificateException if this fails too.
	    			            finalDefaultTm.checkServerTrusted(chain, authType);
	    			        }
	    			    }

	    			    @Override
	    			    public void checkClientTrusted(X509Certificate[] chain,
	    			            String authType) throws CertificateException {
	    			        // If you're planning to use client-cert auth,
	    			        // do the same as checking the server.
	    			        finalDefaultTm.checkClientTrusted(chain, authType);
	    			    }
	    			};
	    			
	    			SSLContext sslContext = SSLContext.getInstance("SSL");
	    			sslContext.init(null, new TrustManager[] { customTm }, null);
	    			HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
	    		
			}
			
            
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
		con.disconnect();
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
		String queryUrl = url + "sib/services/api_ssap/v01/SSAPResource";
		String query;
		String data;
		
		String params = "?$sessionKey=" + sessionKey;
		if(fieldName.equals("_id")){
			query = "db." + ontName + ".find({\"" + fieldName + "\":{\"$oid\":\"" + fieldValue + "\"}})"; // Query by unique id
		}else{
//			if(identifierType.equals(STRING_TYPE)) query =  "{\"" + ontName + "." + fieldName + "\":\"" + fieldValue + "\"}"; // String id
//			else query = "{\"" + ontName + "." + fieldName + "\":" + fieldValue + "}"; // Numeric id
			// Get only the most recent value
			if(identifierType.equals(STRING_TYPE)) query =  "db." + ontName + ".find({\"" + fieldName + "\":\"" + fieldValue + "\"}).sort({\"contextData.timestamp\":-1}).limit(1)"; // String id
			else query = "db." + ontName + ".find({\"" + fieldName + "\":" + fieldValue + "}).sort({\"contextData.timestamp\":-1}).limit(1)"; // Numeric id
			params = params + "&$ontology=" + ontName;
		}
		params = params + "&$query=" + URLEncoder.encode(query, "UTF-8"); 
		params = params + "&$queryType=NATIVE";
		
		logger.debug("Query: " + queryUrl + params);
		String response = invokeGet(queryUrl + params);
//		logger.debug("Response: " + response);
		if (response != null){
			JsonParser parser = new JsonParser();
			JsonObject ssapObject = parser.parse(response.toString()).getAsJsonObject();
			data = ssapObject.get("data").getAsString();
		}else data ="[ ]"; // An empty SOFIA2 response. Should return null instead?
		return data;		
		
	}
	
	
	String list(String ontName) throws Exception{
		// List all instances of an ontology
		//query db.OntologyName.find().limit(X)
		
		String queryUrl = url + "sib/services/api_ssap/v01/SSAPResource";
		
		String params = "?$sessionKey=" + sessionKey;
		params = params + "&$ontology=" + ontName;
		params = params + "&$query=db."+ ontName +".find()";
		params = params + "&$queryType=NATIVE";
		String response = invokeGet(queryUrl + params);
		JsonParser parser = new JsonParser();
		JsonObject ssapObject = parser.parse(response.toString()).getAsJsonObject();
		String data = ssapObject.get("data").getAsString();
		if(data == null) data ="[ ]"; // An empty SOFIA2 response. Should return null instead?
		return data;		
		
	}
	
	String list() throws Exception{
		return list(deviceOntologyName);
	}
	
	void register(String ontName, String fieldName, String thingId) throws Exception{
		String res = query(ontName, fieldName, thingId);
		if (res.equals("[ ]")){
			// Entity does not exist. Call insert method to create a virtual device
			// Only id and name are provided by the input message
			JsonObject deviceId = new JsonObject();
			if(identifierType.equals(STRING_TYPE)) deviceId.addProperty(fieldName, thingId); 
			else deviceId.addProperty(fieldName, Long.valueOf(thingId)); // Or use Float instead?
			JsonObject device = new JsonObject();
			device.add(ontName, deviceId);
			String data = device.toString();
			insert(ontName, data);
		}
	}
	
	void insert(String ontName, String data) throws Exception{
		String queryURL = url + "sib/services/api_ssap/v01/SSAPResource/";
		JsonObject ssapResource = new JsonObject();
		ssapResource.addProperty("sessionKey", sessionKey);
		ssapResource.addProperty("ontology",ontName);
		ssapResource.addProperty("data", data);
		
		invoke(queryURL, "POST", ssapResource);
	}
	
	
	String subscribe(String ontName, String fieldName, String fieldValue, String callback) throws Exception{
		String query;
		String subscriptionId = "";
		String queryUrl = url + "sib/services/api_ssap/v01/SSAPResource/subscribe";
		String queryType;
		
		
		if(fieldName.equals("_id")){
			query = "db." + ontName + ".find({\"" + fieldName + "\":{\"$oid\":\"" + fieldValue + "\"}})"; // Native query by unique id. Should use SQLLIKE query?
			queryType="NATIVE";  // NATIVE
		}else{
			// NATIVE 
			// Add sort({"contextData.timestamp":-1}).limit(1) to get the most recent value only
//			if(identifierType.equals(STRING_TYPE)) query = "{\"" + ontName + "." + fieldName + "\":\"" + fieldValue + "\"}";
//			else query = "{\"" + ontName + "." + fieldName + "\":" + fieldValue + "}";
//			queryType="NATIVE";  
			
			// SQLLIKE
			// Add "order by contextData.timestamp DESC limit 1" to get only the most recent value.
			if(identifierType.equals(STRING_TYPE)) query = "select * from " + ontName + " where " + ontName + "." + fieldName + " = \"" + fieldValue + "\" order by contextData.timestamp DESC limit 1"; // string identifier
			else query = "select * from " + ontName + " where " + ontName + "." + fieldName + " = " + fieldValue + " order by contextData.timestamp DESC limit 1";  // numeric identifier
			queryType="SQLLIKE"; 
		}
		String params = "?$sessionKey=" + sessionKey;
		params = params + "&$msRefresh=" + msSubscriptionRefresh;
		params = params + "&$ontology=" + ontName;
		params = params + "&$query=" + URLEncoder.encode(query, "UTF-8");
		params = params + "&$queryType=" + queryType;
		params = params + "&$endpoint=" + callback; // URLencode?
		
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
		String queryURL = url + "sib/services/api_ssap/v01/SSAPResource/";
		JsonObject ssapResource = new JsonObject();
		ssapResource.addProperty("sessionKey", sessionKey);
		ssapResource.addProperty("ontology", ontName);
		ssapResource.addProperty("data", data);
		
		invoke(queryURL, "PUT", ssapResource);
		
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
