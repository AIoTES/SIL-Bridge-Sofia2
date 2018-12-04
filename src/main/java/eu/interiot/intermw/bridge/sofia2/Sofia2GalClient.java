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

import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.TimeZone;
import java.util.UUID;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
//import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import apisofia2.OperacionesSofia;

public class Sofia2GalClient {
	private String url;
//	private String KP;
//	private String TOKEN;
//	private String sofiaUser, sofiaPassword;
//	private int msSubscriptionRefresh;
//	Thread sessionRefresh;
	private final Logger logger = LoggerFactory.getLogger(Sofia2GalClient.class);
	private String trustStore;
	private String trustStorePass;
	private OperacionesSofia api;
    private Map<String, Subscription> subscriptions = new HashMap<>();
    private Map<String, Thread> subscriptionThreads = new HashMap<>();
	
	Sofia2GalClient(Properties properties, String baseUrl) throws Exception{
		try {
//            sofiaUser = properties.getProperty(Sofia2Bridge.PROPERTIES_PREFIX + "user"); // USER + PASSWORD OR TOKEN?
//            sofiaPassword = properties.getProperty(Sofia2Bridge.PROPERTIES_PREFIX + "password");
//            TOKEN = properties.getProperty(Sofia2Bridge.PROPERTIES_PREFIX + "token");
            url = baseUrl;
//            KP = properties.getProperty("KP");
            trustStore = properties.getProperty("certificate"); // For self-signed certificates
            trustStorePass = properties.getProperty("certificate-password"); // For self-signed certificates
            
//            if(KP == null){
//            	throw new Exception("Error in bridge configuration: no KP");
//            }
            
            
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
				
//		if (Strings.isNullOrEmpty(TOKEN) && (Strings.isNullOrEmpty(sofiaUser) || Strings.isNullOrEmpty(sofiaPassword))) {
//            throw new BridgeException("Invalid SOFIA2 bridge configuration.");
//        }else if(Strings.isNullOrEmpty(TOKEN)){
//			String authUrl = url + "console/api/rest/kps/" + KP + "/tokens"; 
//			getToken(authUrl, sofiaUser, sofiaPassword);
//		}
	}
	
	
	
	
	String query(String type, String device) throws Exception{
		// TODO: TEST QUERY METHOD
		    	
    	int limit = 1;
    	// TODO: SET INITIAL TIME FOR QUERY
    	String time = "20180918000103";
//    	SimpleDateFormat f = new SimpleDateFormat("yyyyMMddHHmmss");
//		f.setTimeZone(TimeZone.getTimeZone("UTC"));
//    	Calendar calendar = Calendar.getInstance();
//    	calendar.setTime(new java.util.Date());
//    	calendar.add(Calendar.MINUTE, -3);
//    	String time = f.format(calendar.getTime());
    	String fechaActividad = "";
    	String idPaciente = "";
    	JSONObject data = null;
		JSONObject indication = null;
		JSONObject body = null;				
							
		JSONArray responses = new JSONArray();
							
		logger.debug("Tipo medida: "+ type);
		logger.debug("Sensor: "+ device);
		logger.debug("Time: " + time);
					
		if(type.equals("scale")){ 
			responses.put(0, api.obtenerBiomedidas("concentrador", device, "", "PESO", time, limit)); // weight measurement
		}else if(type.equals("bloodPressureMonitor")){
			// Three measurements from the blood pressure monitor
			responses.put(0, api.obtenerBiomedidas("concentrador", device, "", "TAS", time, limit)); // Systolic
			responses.put(1, api.obtenerBiomedidas("concentrador", device, "", "TAD", time, limit)); // Diastolic
			responses.put(2, api.obtenerBiomedidas("concentrador", device, "", "PPM", time, limit)); // Heart rate
		}else if(type.equals("coagulometer")){
			// Coagulometer
			responses.put(0, api.obtenerBiomedidas("concentrador", device, "", "INR", time, limit)); // INR measurement
		}else{
			// ERROR
			throw new Exception("Unrecognized device type: " + type);
		}							
									
		// Send data to bridge class
		for (int i=0; i<responses.length(); i++){
			int rescode = responses.getJSONObject(i).getInt("codigo");
			if (rescode == 0){			
				JSONArray resultado = responses.getJSONObject(i).getJSONArray("resultado");
						
				logger.debug(resultado.toString());
				logger.debug("Result length: " + resultado.length());
					
							
				if(resultado.length()>0){										
					JSONObject biomedida = resultado.getJSONObject(0);			
							
					fechaActividad = biomedida.getString("fechaActividad");
					idPaciente = biomedida.getString("idPaciente");
					String contextData = "{ \"session_key\" : \"7b8a7e79-8003-4446-af97-78bc01a3c4c7\" , \"user\" : \"activage\" , \"kp\" : \"ActivageKP\" , \"kp_instancia\" : \"Pruebas\" , \"timestamp\" : { \"$date\" : \""+ time +"\"}}";											
					data = new JSONObject();
					data.put("_id", new JSONObject("{ \"$oid\" : \"5ad46394e4b0ffd95dce1277\"}"));
					data.put("contextData", new JSONObject(contextData));
					data.put("Biomedida", biomedida);
																								
					indication = new JSONObject();
					body = new JSONObject();
					body.put("data", data.toString());
					indication.put("body", body);
					indication.put("version","LEGACY");
							
					logger.debug("Datos enviados" + indication.toString());							
				}
			}else{
				// Error? Do something.
				// Code 106 means no new data
				if (rescode != 106){
					logger.error("Could not get observations from the sensor. " + rescode);
					logger.error(responses.getJSONObject(i).getString("descripcion"));
				} 
			}
		}	
		
		if (indication != null) return indication.toString();	
		else return "[ ]";
	}
	
	
	String list(String ontName) throws Exception{
		// List all instances of an ontology
		//query db.OntologyName.find().limit(X)
		
//		String queryUrl = url + "sib/services/api_ssap/v01/SSAPResource";
//		String params = "?$sessionKey=" + sessionKey;
//		params = params + "&$ontology=" + ontName;
//		params = params + "&$query={\"" + ontName + ".find()\"}";
//		params = params + "&$queryType=NATIVE";
//		String response = invokeGet(queryUrl + params);
//		JsonParser parser = new JsonParser();
//		JsonObject ssapObject = parser.parse(response.toString()).getAsJsonObject();
//		String data = ssapObject.get("data").getAsString();
//		if(data == null) data ="[ ]"; // An empty SOFIA2 response. Should return null instead?
//		return data;
		return "[ ]";
		
	}
	
	String list() throws Exception{
		// TODO: list all devices
		return "[ ]";
	}
	
			
	void sendObservation(String data) throws Exception{
		
		JSONArray biomedidas = new JSONArray();
		JSONObject medida = new JSONObject(data);
		biomedidas.put(medida.get("Biomedida"));
		
		logger.debug("Enviando datos...");
		logger.debug(biomedidas.toString());
		logger.debug("**********************");
		JSONArray responses = api.insertarBiomedidas(biomedidas);
		
		for (int i=0; i<responses.length(); i++){
			int rescode = responses.getJSONObject(i).getInt("codigo");
			if (rescode != 200 && rescode !=0){
				// Error
				logger.error("Could not send observation to SOFIA2. " + rescode);
				logger.error(responses.getJSONObject(i).getString("descripcion"));
			} else logger.info("Response code: " + rescode);
		}
		
	}	
	

	String subscribe(String device, String type, String callback) throws Exception{
				
		String subscriptionId = UUID.randomUUID().toString(); // random identifier
        Subscription subscription = new Subscription(device, type, callback);
        subscriptions.put(subscriptionId, subscription);

        ObservationsPublisher obsPublisher = new ObservationsPublisher(subscription);
        Thread obsPublisherThread = new Thread(obsPublisher);
        subscriptionThreads.put(subscriptionId, obsPublisherThread);
        obsPublisherThread.start();
        
        return subscriptionId;
	}
	
	
	void unsubscribe(String subscriptionId) throws Exception{
        subscriptions.remove(subscriptionId);
        Thread obsPublisherThread = subscriptionThreads.get(subscriptionId);        
        obsPublisherThread.interrupt();
        subscriptionThreads.remove(subscriptionId);
	}
	
	class ObservationsPublisher implements Runnable {
	        private Subscription subscription;

	        public ObservationsPublisher(Subscription subscription) throws Exception {
	            this.subscription = subscription;
	        }

	        @Override
	        public void run() {	            
	        	HttpClient httpClient = HttpClientBuilder.create().build();
	        	SimpleDateFormat f = new SimpleDateFormat("yyyyMMddHHmmss");
				f.setTimeZone(TimeZone.getTimeZone("UTC"));
	        	int limit = 1;
	        	Calendar calendar = Calendar.getInstance();
	        	calendar.setTime(new java.util.Date());
	        	calendar.add(Calendar.MINUTE, -3);
	        	String time = f.format(calendar.getTime());
	        	String fechaActividad = "";
	        	String idPaciente = "";
		        				
				while(!Thread.interrupted()){ 
					try {						
						JSONArray responses = new JSONArray();
						String type = subscription.getType();
						String device = subscription.getDevice();
						boolean nuevo = true;					
							
						logger.debug("Tipo medida: "+ type);
						logger.debug("Sensor: "+ device);
						logger.debug("Time: " + time);
							
						if(type.equals("scale")){ 
							responses.put(0, api.obtenerBiomedidas("concentrador", device, "", "PESO", time, limit)); // weight measurement
						}else if(type.equals("bloodPressureMonitor")){
							// Three measurements from the blood pressure monitor
							responses.put(0, api.obtenerBiomedidas("concentrador", device, "", "TAS", time, limit)); // Systolic
							responses.put(1, api.obtenerBiomedidas("concentrador", device, "", "TAD", time, limit)); // Diastolic
							responses.put(2, api.obtenerBiomedidas("concentrador", device, "", "PPM", time, limit)); // Heart rate
						}else if(type.equals("coagulometer")){
							// Coagulometer
							responses.put(0, api.obtenerBiomedidas("concentrador", device, "", "INR", time, limit)); // INR measurement
						}else{
							// ERROR
							throw new Exception("Unrecognized device type: " + type);
						}							
											
						// Send data to bridge class
						for (int i=0; i<responses.length(); i++){
							int rescode = responses.getJSONObject(i).getInt("codigo");
							if (rescode == 0){
							
								JSONArray resultado = responses.getJSONObject(i).getJSONArray("resultado");
								
								logger.debug(resultado.toString());
								logger.debug("Result length: " + resultado.length());
									
								JSONObject data;
								JSONObject indication;
								JSONObject body;
									
					//				for(int j=0; j<resultado.length(); j++){
								if(resultado.length()>0){										
									JSONObject biomedida = resultado.getJSONObject(0); //j																		
									if(i==0 && !fechaActividad.equals(biomedida.getString("fechaActividad")) && !idPaciente.equals(biomedida.getString("idPaciente"))){
										nuevo = true;
									}else{
									nuevo = false;
									}
									if(nuevo){
										fechaActividad = biomedida.getString("fechaActividad");
										idPaciente = biomedida.getString("idPaciente");
										String contextData = "{ \"session_key\" : \"7b8a7e79-8003-4446-af97-78bc01a3c4c7\" , \"user\" : \"activage\" , \"kp\" : \"ActivageKP\" , \"kp_instancia\" : \"Pruebas\" , \"timestamp\" : { \"$date\" : \""+ time +"\"}}";											
										data = new JSONObject();
										data.put("_id", new JSONObject("{ \"$oid\" : \"5ad46394e4b0ffd95dce1277\"}"));
										data.put("contextData", new JSONObject(contextData));
										data.put("Biomedida", biomedida);
																										
										indication = new JSONObject();
										body = new JSONObject();
										body.put("data", data.toString());
										indication.put("body", body);
										indication.put("version","LEGACY");
											
										logger.debug("Datos enviados: " + indication.toString());
																				
										// Send data to the listener
										HttpPost httpPost = new HttpPost(subscription.callbackUrl);
										HttpEntity httpEntity = new StringEntity(indication.toString(), ContentType.APPLICATION_JSON);
										httpPost.setEntity(httpEntity);
										HttpResponse httpResponse = httpClient.execute(httpPost);
										// Do something with the response code?
										if(httpResponse.getStatusLine().getStatusCode() == 200){
					                    	logger.debug("Observation has been sent to the bridge: " + indication);
					                    }
									}								
									
								}
							}else{
								// Error? Do something.
								// Code 106 means no new data
								if (rescode != 106){
									logger.error("Could not get observations from the sensor. " + rescode);
									logger.error(responses.getJSONObject(i).getString("descripcion"));
								} 
							}
						}
																 
						Thread.sleep(2000); // 2 seconds
			        	calendar.setTime(new java.util.Date());
			        	calendar.add(Calendar.MINUTE, -3);
			        	time = f.format(calendar.getTime());
							
						limit = 1;
						
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					}catch (Exception ex) {
						ex.printStackTrace();
						Thread.currentThread().interrupt();
					} 
					
				}
	        	
	        }
	    }

	    static class Subscription {
	        private String callbackUrl;
	        private String device;
	        private String type;
	        
	        public Subscription(String device, String type, String callbackUrl) {
	            this.device = device;
	            this.callbackUrl = callbackUrl;
	            this.type = type;
	        }

	        public String getDevice() {
	            return device;
	        }
	        
	        public String getType() {
	            return type;
	        }

	        public String getCallbackUrl() {
	            return callbackUrl;
	        }
	    }
	    
}
