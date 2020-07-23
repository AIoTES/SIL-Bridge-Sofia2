# SOFIA2 Bridge

Bridge for the SOFIA2 platform.


## Getting stated

The generic SOFIA2 bridge communicates with the SOFIA2 REST API v1.0. The bridge supports authentication and SSL/TLS encryption.


To configure the SIL with the SOFIA2 bridge properly the following files must be added inside a mounted docker volume:

* Sofia2Bridge.properties (bridge configuration file, available in /src/main/config)
* syntactic-translators-1.0.jar (library, available at https://git.activageproject.eu/Bridges_Binaries/Libraries)
* mw.bridge.sofia2-2.3.0.jar (bridge jar file)


Other relevant information:
* The bridge connects with SOFIA2 as an instance of an existing KP.
* Subscriptions are created by ontology instance.
* Authentication options supported by the bridge: authentication by user and password or tokens. 



### Bridge configuration

Create Sofia2Bridge.properties file with this content and save the file with this name. Modify the configuration following the indications below.

```
# SOFIA2
#user=user
#password=password
#token=example_token
#certificate=/etc/inter-iot/intermw/sofia2TrustStore.jks
#certificate-password=password
subscription-refresh=0
session-refresh=600000
###
KP=ExampleKP
KP-instance=sofia2Bridge
device-class=Device
#device-identifier=id
device-identifier-type=int

```


**What to change?**

* **User** and **password** or **token** for authentication (use only one type of authentication)
* **KP**: name of the KP to connect the bridge with the platform. The KP must be defined in the platform before using the bridge.
* **KP-instance**: name of the KP instance to include in the messages sent to the platform.
* **subscription-refresh**: validity of the created subscriptions (in ms). 0 indicates that subscriptions are valid until they are deleted.
* **session-refresh**: time interval (in ms) to renew the session with the platform using a JOIN request.
* **device-class**: name of the ontology representing a device.
* **device-identifier** and **device-identifier-type**: name and type of the property that represents the device identifier. (Default: the object id in SOFIA2).
* If you use self-signed certificates in your SOFIA2 platform, uncomment and add the security certificate and password.


```
certificate=/trustStore.jks
certificate-password=mysecretpassword
```



### Build bridge
Clone this repository and build with Maven using

`mvn clean package`



### Dependencies 

You have to copy into the INTER-MW library (i.e. lib directory) all runtime dependencies that are required by the bridge project and do not already exist there. Make sure that dependencies added to INTER-MW library do not cause dependency conflict. The mw.bridges.api dependency together with its sub-dependencies are already included in the INTER-MW library.

Dependencies required for SOFIA2: 
 
Syntactic-translators-1.0.jar
https://git.activageproject.eu/Bridges_Binaries/Libraries


You can get a list of all dependencies available in INTER-MW library by listing lib directory content:

`$ docker exec <intermw-container> ls -l /usr/local/tomcat/webapps/ROOT/WEB-INF/lib`


To display dependency tree for the bridge project (runtime dependencies), run the following command:

`$ mvn dependency:tree -Dscope=runtime`



## Testing
JUnit tests are provided with the bridge code. These tests can be adapted to test new functionalities.


## Further information

### Tutorial

**SOFIA2 platform registration**

Platforms are registered in the SIL using the POST /mw2mw/platforms operation. In the case of SOFIA2, the parameters to be provided are:

* **platformId**: the id that will be assigned to the registered platform (must be an URI)
* **type**: this is the bridge type to use (Check that the expected platform type is shown using GET platform-types in the API to confirm that the SOFIA2 bridge has been installed correctly). Generic SOFIA2 platform: http://inter-iot.eu/sofia2
* **baseEndpoint**: it refers to the machine (and TCP port) where SOFIA2 is available. It is an URL (e.g., "http://sofia2.com/" )
* **location**: internal attribute used by SIL to give the geographic location of the platform. This field is optional, but in case it is provided, it has to be an URI.
* **Name**: a label to identify the platform
* **downstreamXXX/upstreamXXX**: these fields are used to create semantic translation channels. These fields should be included (the ones not being used should have an empty string as value).

 
Example JSON object:
```
{
  "platformId": "http://inter-iot.eu/platforms/sofia",
  "type": "http://inter-iot.eu/sofia2",
  "baseEndpoint": "http://sofia2.com/",
  "location": "http://test.inter-iot.eu/Sofia2Location",
  "name": "My SOFIA2 Platform",
  "downstreamInputAlignmentName": "",
  "downstreamInputAlignmentVersion": "",
  "downstreamOutputAlignmentName": "",
  "downstreamOutputAlignmentVersion": "",
  "upstreamInputAlignmentName": "",
  "upstreamInputAlignmentVersion": "",
  "upstreamOutputAlignmentName": "",
  "upstreamOutputAlignmentVersion": ""
}

```


(If you have alignments, you should fill the name and version in this json following the indications from the general guide)


The REST API operation returns 202 (Accepted) response code. To make sure the process has executed successfully, check the response message and the logs.


**Registering devices**

When a platform is registered, the SIL performs automatically a device discovery process, provided it has been configured properly. However, the devices can be also manually registered in the SIL. This can be done using the operation POST /mw2mw/devices. This operation also allows the creation of virtual devices.

The convention used to generate device ids in SOFIA2 is the following:

“http://inter-iot.eu/dev/{ontName}/_id#{$oid}” 

Where:
* **ontName**: it is the name of the SOFIA2 ontology that represents the device type.
* **_id**: it denotes the unique identifier of a SOFIA2 ontology instance.
* **$oid**: represents the value of the unique identifier of the ontology instance.


An alternative way to construct the device id is also supported:

“http://inter-iot.eu/dev/{ontName}/{idName}#{id}”

Where:
* **ontName**: it is the name of the SOFIA2 ontology that represents the device type.
* **idName**: is the name of the attribute that acts as unique identifier for the device. The type of this attribute is defined in the properties file (device-identifier-type).
* **id**: represents the value of the unique identifier of the device (according to the data model defined in the SOFIA2 platform).



If the ontology instance given by the previous parameters already exists on SOFIA2, only the AIoTES metadata is generated. Otherwise, a new instance is created (all additional parameters must be defined as optional).

For example: 

"http://inter-iot.eu/dev/MyTemperatureSensor/id#1" 

In this case, we will create a new instance of the MyTemperatureSensor ontology with id 1.


Example of POST data:

```
{
  "devices": [
	{
  	"deviceId": "http://inter-iot.eu/dev/MyTemperatureSensor/id#1",
  	"hostedBy": "http://inter-iot.eu/platforms/sofia",
  	"location": "http://test.inter-iot.eu/Sofia2Location"
  	"name": "Temperature Sensor #1"
	},
	...
  ]
}

```

The REST API operation returns 202 (Accepted) response code. To make sure the process has been executed successfully, check the response messages.


## License
The SOFIA2 bridge is licensed under [Apache 2.0 license](https://www.apache.org/licenses/LICENSE-2.0).

