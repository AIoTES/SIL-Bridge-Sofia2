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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Service;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class Sofia2PlatformEmulator {
    private final Logger logger = LoggerFactory.getLogger(Sofia2Bridge.class);
    private Map<String, Subscription> subscriptions = new HashMap<>();
    private Map<String, Thread> subscriptionThreads = new HashMap<>();
    private ObjectMapper objectMapper = new ObjectMapper();
    private int port;
    private int observationsDelay;
    private Service spark;

    public Sofia2PlatformEmulator(int port, int observationsDelay) {
        this.port = port;
        this.observationsDelay = observationsDelay;
    }

    public void start() throws Exception {
        logger.debug("Sofia2PlatformEmulator is initializing...");

        spark = Service.ignite().port(port);
        
        spark.post("sib/services/api_ssap/v01/SSAPResource/", (request, response) -> {
            logger.debug("Received POST sib/services/api_ssap/v01/SSAPResource/ request.");
            SsapInput input;
            try {
                input = objectMapper.readValue(request.body(), SsapInput.class);
                System.out.println("HTTP POST received");
                System.out.println("***** BODY ****");
                System.out.println(request.body());
                System.out.println("*********");
            } catch (Exception e) {
                response.status(400);
                return e.getMessage();
            }
            
            String platformResponse="";
            
            if(input.join==true){
            	logger.debug("JOIN request.");
            	 URL url1 = Resources.getResource("observations/response-join.json");
            	 platformResponse = Resources.toString(url1, Charsets.UTF_8);
            }else if(input.leave==true){
            	logger.debug("LEAVE request.");
            }else{
            	logger.debug("INSERT request.");
            	URL url1 = Resources.getResource("observations/response-insert.json");
           	 	String platformResponseTemplate = Resources.toString(url1, Charsets.UTF_8);
           	 	platformResponse = platformResponseTemplate.replace("%ONTOLOGY%", input.ontology); 	
            }
            response.header("Content-Type", "application/json;charset=UTF-8");
            response.status(200);
            return platformResponse;
        });
        
        spark.put("sib/services/api_ssap/v01/SSAPResource/", (request, response) -> {
            logger.debug("Received a UPDATE request.");
            SsapInput input;
            try {
                input = objectMapper.readValue(request.body(), SsapInput.class);
                System.out.println("HTTP PUT received");
                System.out.println("***** BODY ****");
                System.out.println(request.body());
                System.out.println("*********");
            } catch (Exception e) {
                response.status(400);
                return e.getMessage();
            }
            
            String platformResponse="";
            
            logger.debug("UPDATE request.");
            URL url1 = Resources.getResource("observations/response-insert.json");
           	String platformResponseTemplate = Resources.toString(url1, Charsets.UTF_8);
           	platformResponse = platformResponseTemplate.replace("%ONTOLOGY%", input.ontology); 	
            
            response.header("Content-Type", "application/json;charset=UTF-8");
            response.status(200);
            return platformResponse;
        });
        
        spark.get("sib/services/api_ssap/v01/SSAPResource", (request, response) -> {
            logger.debug("Received QUERY request.");
            String query;
            String ontology;
            String sessionKey;
            String platformResponse = "";
            
            try {
                sessionKey = request.queryParams("$sessionKey");
            	query = request.queryParams("$query");
            	ontology = request.queryParams("$ontology");
            	System.out.println("**** RECEIVED QUERY REQUEST ****");
            	System.out.println("Query parameters");
                System.out.println("Ontology: " + ontology);
                System.out.println("Query: " + query);
                System.out.println("SessionKey: " + sessionKey);
                System.out.println("*********");
            } catch (Exception e) {
                response.status(400);
                return e.getMessage();
            }
            
            if(sessionKey == null || query == null){
            	response.status(400);
            }else{ 	
           	 	URL url1 = Resources.getResource("observations/response-query.json");
           	 	platformResponse = Resources.toString(url1, Charsets.UTF_8);
           	 	response.header("Content-Type", "application/json;charset=UTF-8");
           	 	response.status(200);
            }
            
            return platformResponse;
        });
        
        spark.get("sib/services/api_ssap/v01/SSAPResource/subscribe", (request, response) -> {
            logger.debug("Received SUBSCRIBE request.");
            String sessionKey;
            String subscriptionQuery;
            String callbackUrl;
            try {
            	sessionKey = request.queryParams("$sessionKey");
            	subscriptionQuery = request.queryParams("$query");
            	callbackUrl = request.queryParams("$endpoint");
            	System.out.println("Subscription parameters");
                System.out.println("Callback URL: " + callbackUrl);
                System.out.println("Query: " + subscriptionQuery);
                System.out.println("SessionKey: " + sessionKey);
                System.out.println("*********");
            } catch (Exception e) {
                response.status(400);
                e.printStackTrace();
                return e.getMessage();
            }
            
//            if (subscriptions.containsKey(subscriptionQuery)) {
            if (subscriptions.containsKey(sessionKey)) {
                response.status(409);
                return "Already subscribed.";
            }

            Subscription subscription = new Subscription(subscriptionQuery, callbackUrl, sessionKey);
 //           subscriptions.put(subscriptionQuery, subscription);
            subscriptions.put(sessionKey, subscription);

            ObservationsPublisher obsPublisher = new ObservationsPublisher(subscription);
            Thread obsPublisherThread = new Thread(obsPublisher);
            obsPublisherThread.start();
 //           subscriptionThreads.put(subscriptionQuery, obsPublisherThread);
            subscriptionThreads.put(sessionKey, obsPublisherThread);

            logger.debug("Subscribed to thing '{}' with callback URL {}.", subscriptionQuery, callbackUrl);
            
            URL url1 = Resources.getResource("observations/response-subscribe.json");
       	 	String  platformResponse = Resources.toString(url1, Charsets.UTF_8);
       	 	response.header("Content-Type", "application/json;charset=UTF-8");
            response.status(200);
            return platformResponse;
        });
        
        spark.get("sib/services/api_ssap/v01/SSAPResource/unsubscribe", (request, response) -> {
            logger.debug("Received UNSUBSCRIBE request.");
            String sessionKey;
            String subscriptionId;
            try {
            	sessionKey = request.queryParams("$sessionKey");
            	subscriptionId = request.queryParams("$subscriptionId");
            	System.out.println("Subscription parameters");
                System.out.println("SubscriptionID: " + subscriptionId);
                System.out.println("SessionKey: " + sessionKey);
                System.out.println("*********");
            } catch (Exception e) {
                response.status(400);
                return e.getMessage();
            }
            
            subscriptions.remove(sessionKey);

            Thread obsPublisherThread = subscriptionThreads.get(sessionKey);
            
            obsPublisherThread.interrupt();
            subscriptionThreads.remove(sessionKey);

            logger.debug("Unubscribed from '{}' ", subscriptionId);
            
            URL url1 = Resources.getResource("observations/response-subscribe.json");
       	 	String  platformResponse = Resources.toString(url1, Charsets.UTF_8);
       	 	response.header("Content-Type", "application/json;charset=UTF-8");
            response.status(200);
            return platformResponse;
        });
    }

    public void stop() {
        spark.stop();
        for (Thread thread : subscriptionThreads.values()) {
            thread.interrupt();
        }
        logger.debug("Sofia2PlatformEmulator has stoped.");
    }

    public static void main(String[] args) throws Exception {
        new Sofia2PlatformEmulator(4568, 10).start();
    }

    class ObservationsPublisher implements Runnable {
    	String observation;
        private Subscription subscription;

        public ObservationsPublisher(Subscription subscription) throws Exception {
            this.subscription = subscription;
            URL url = Resources.getResource("observations/example-indication.json");
            observation = Resources.toString(url, Charsets.UTF_8);
        }

        @Override
        public void run() {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                return;
            }
            HttpClient httpClient = HttpClientBuilder.create().build();

            while (!Thread.interrupted()) {
                try {
//                	HttpPut httpPut = new HttpPut(subscription.getCallbackUrl());
                	HttpPost httpPost = new HttpPost(subscription.getCallbackUrl());
                    HttpEntity httpEntity = new StringEntity(observation, ContentType.getByMimeType("application/json"));
                    httpPost.setEntity(httpEntity);
                    HttpResponse response = httpClient.execute(httpPost);
                    logger.debug("Observation for thing {} has been sent to {}.", subscription.getSubscriptionQuery(),
                            subscription.getCallbackUrl());
                    if(response.getStatusLine().getStatusCode() == 200){
                    	System.out.println("Observation has been sent to the bridge:");
                    	System.out.println(observation);
                    }
                } catch (Exception e) {
                    logger.error("Failed to send observation to {}.", subscription.getCallbackUrl());
                    e.printStackTrace();
                }
                try {
                    Thread.sleep(observationsDelay * 1000);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }
    }

    static class Subscription {
        private String query;
        private String callbackUrl;
        private String sessionKey;

        public Subscription(String query, String callbackUrl, String sessionKey) {
            this.query = query;
            this.callbackUrl = callbackUrl;
            this.sessionKey = sessionKey;
        }

        public String getSubscriptionQuery() {
            return query;
        }

        public String getCallbackUrl() {
            return callbackUrl;
        }

        public String getSessionKey() {
            return sessionKey;
        }
    }

    static class ThingRegisterInput {
        public String thingId;
    }

    static class ThingSubscribeInput {
        public String thingId;
        public String callbackUrl;
        public String conversationId;
    }
    
    static class SsapInput{
    	public boolean join;
    	public boolean leave;
    	public String sessionKey;
    	public String ontology;
    	public String token;
    	public String instanceKP;
    	public String data;
    	public String version;
    }
}
