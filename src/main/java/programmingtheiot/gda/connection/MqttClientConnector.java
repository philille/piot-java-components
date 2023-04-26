/**
 * This class is part of the Programming the Internet of Things project.
 * 
 * It is provided as a simple shell to guide the student and assist with
 * implementation for the Programming the Internet of Things exercises,
 * and designed to be modified by the student as needed.
 */ 

package programmingtheiot.gda.connection;

import java.io.File;

import javax.net.ssl.SSLSocketFactory;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.IMqttAsyncClient;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttPersistenceException;
import org.eclipse.paho.client.mqttv3.MqttSecurityException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import programmingtheiot.common.ConfigConst;
import programmingtheiot.common.ConfigUtil;
import programmingtheiot.common.IDataMessageListener;
import programmingtheiot.common.ResourceNameEnum;
import programmingtheiot.common.SimpleCertManagementUtil;

import programmingtheiot.data.ActuatorData;
import programmingtheiot.data.DataUtil;
import programmingtheiot.data.SensorData;
import programmingtheiot.data.SystemPerformanceData;

/**
 * Shell representation of class for student implementation.
 * 
 */
public class MqttClientConnector implements IPubSubClient, MqttCallbackExtended
{
	// static
	
	private static final Logger _Logger =
		Logger.getLogger(MqttClientConnector.class.getName());
	
	// params
	// NOTE: MQTT client updated to use async client vs sync client
	/*
	 * This class implements the non-blocking {@link IMqttAsyncClient} client
     * interface allowing applications to initiate MQTT actions and then carry on
     * working while the MQTT action completes on a background thread.
     * 
     * An application can connect to an MQTT server using:
     * A plain TCP socket
     * A secure SSL/TLS socket
     * 
     * To enable messages to be delivered even across network and client restarts
     * messages need to be safely stored until the message has been delivered at the
     * requested quality of service. A pluggable persistence mechanism is provided
     * to store the messages.
	 */
	private MqttAsyncClient      mqttClient = null;
//	private MqttClient           mqttClient = null;
	private MqttConnectOptions   connOpts = null;
	private MemoryPersistence    persistence = null;
	private IDataMessageListener dataMsgListener = null;

	private String  clientID = null;
	private String  brokerAddr = null;
	private String  host = ConfigConst.DEFAULT_HOST;
	private String  protocol = ConfigConst.DEFAULT_MQTT_PROTOCOL;
	private int     port = ConfigConst.DEFAULT_MQTT_PORT;
	private int     brokerKeepAlive = ConfigConst.DEFAULT_KEEP_ALIVE;
	
	private String pemFileName = null;
	private boolean enableEncryption = false;
	private boolean useCleanSession = false;
	private boolean enableAutoReconnect = true;
	
	private boolean useCloudGatewayConfig = false;
	// TODO: place this in the class-scoped variable declarations section
	private IConnectionListener  connListener = null;
	
	// constructors
	
	/**
	 * Default.
	 * 
	 */
	public MqttClientConnector()
	{
//	    whether connect to cloud or not
	    this(false);
	}

	public MqttClientConnector(boolean useCloudGatewayConfig)
	{
	    this(useCloudGatewayConfig ? ConfigConst.CLOUD_GATEWAY_SERVICE : ConfigConst.MQTT_GATEWAY_SERVICE);
	}

	public MqttClientConnector(String cloudGatewayConfigSectionName)
	{
	    super();
	    
	    if (cloudGatewayConfigSectionName != null && cloudGatewayConfigSectionName.trim().length() > 0) {
	        this.useCloudGatewayConfig = true;
	        
	        initClientParameters(cloudGatewayConfigSectionName);
	    } else {
//	        if name is empty, default use mqtt gateway service
	        this.useCloudGatewayConfig = false;
	        
	        initClientParameters(ConfigConst.MQTT_GATEWAY_SERVICE);
	    }
	}
	
	/*
     * In MqttClientConnector class, which implements IPubSubClient, We have to override these method:
     * connectClient(), disconnectClient(), publishMessage(), subscribeToTopic(), unsubscribeFromTopic(),
     * setDataMessageListener()
     */
	// public methods

	@Override
	public boolean connectClient() {
		try {
			// If the MQTT client is not instantiated, create a new instance.
			if (this.mqttClient == null) {
				this.mqttClient = new MqttAsyncClient(this.brokerAddr, this.clientID, this.persistence);
				this.mqttClient.setCallback(this);
			}

			// If the MQTT client is not connected, attempt to connect.
			if (!this.mqttClient.isConnected()) {
				_Logger.info("MQTT client connecting to broker: " + this.brokerAddr);
				this.mqttClient.connect(this.connOpts);

				return true;
			} else {
				_Logger.warning("MQTT client already connected to broker: " + this.brokerAddr);
			}
		} catch (MqttException e) {
			// TODO: handle this exception
			_Logger.log(Level.SEVERE, "Failed to connect MQTT client to broker.", e);
		}

		return false;
	}

	@Override
	public boolean disconnectClient() {
		try {
			// If the MQTT client exists, proceed with disconnection.
			if (this.mqttClient != null) {
				// If the MQTT client is connected, disconnect it from the broker.
				if (this.mqttClient.isConnected()) {
					_Logger.info("Disconnecting MQTT client from broker: " + this.brokerAddr);
					this.mqttClient.disconnect();
					return true;
				} else {
					_Logger.warning("MQTT client not connected to broker: " + this.brokerAddr);
				}
			}
		} catch (Exception e) {
			// TODO: handle this exception
			_Logger.log(Level.SEVERE, "Failed to disconnect MQTT client from broker: " + this.brokerAddr, e);
		}

		return false;
	}
	public boolean isConnected() {
		// Return true if the MQTT client exists and is connected to the broker, otherwise return false.
		return (this.mqttClient != null && this.mqttClient.isConnected());
	}

	@Override
	public boolean publishMessage(ResourceNameEnum topicName, String msg, int qos) {
		// Check if the topic name is null.
		if (topicName == null) {
			_Logger.warning("Resource is null. Unable to publish message: " + this.brokerAddr);

			return false;
		}

		// Check if the message is null or empty.
		if (msg == null || msg.length() == 0) {
			_Logger.warning("Message is null or empty. Unable to publish message: " + this.brokerAddr);

			return false;
		}

		// Call the protected publishMessage method with the resource name, payload, and QoS.
		return publishMessage(topicName.getResourceName(), msg.getBytes(), qos);
	}

	protected boolean publishMessage(String topicName, byte[] payload, int qos) {
		// Check if the topic name is null.
		if (topicName == null) {
			_Logger.warning("Resource is null. Unable to publish message: " + this.brokerAddr);

			return false;
		}

		// Check if the payload is null or empty.
		if (payload == null || payload.length == 0) {
			_Logger.warning("Message is null or empty. Unable to publish message: " + this.brokerAddr);

			return false;
		}

		// Check if the QoS value is valid (0, 1, or 2).
		if (qos < 0 || qos > 2) {
			_Logger.warning("Invalid QoS. Using default. QoS requested: " + qos);

			// TODO: retrieve default QoS from config file
			qos = ConfigConst.DEFAULT_QOS;
		}

		try {
			// Create a new MqttMessage object and set its QoS and payload.
			MqttMessage mqttMsg = new MqttMessage();
			mqttMsg.setQos(qos);
			mqttMsg.setPayload(payload);

			// Publish the message to the specified topic.
			this.mqttClient.publish(topicName, mqttMsg);

			return true;
		} catch (Exception e) {
			_Logger.log(Level.SEVERE, "Failed to publish message to topic: " + topicName, e);
		}

		return false;
	}
	@Override
	public boolean subscribeToTopic(ResourceNameEnum topicName, int qos) {
		// Check if the topic name is null.
		if (topicName == null) {
			_Logger.warning("Resource is null. Unable to subscribe to topic: " + this.brokerAddr);

			return false;
		}

		// Call the protected subscribeToTopic method with the resource name and QoS.
		return subscribeToTopic(topicName.getResourceName(), qos);
	}

	protected boolean subscribeToTopic(String topicName, int qos) {
		// Call the protected subscribeToTopic method with the topic name, QoS, and null listener.
		return subscribeToTopic(topicName, qos, null);
	}

	protected boolean subscribeToTopic(String topicName, int qos, IMqttMessageListener listener) {
		// Check if the topic name is null.
		if (topicName == null) {
			_Logger.warning("Resource is null. Unable to subscribe to topic: " + this.brokerAddr);

			return false;
		}

		// Check if the QoS value is valid (0, 1, or 2).
		if (qos < 0 || qos > 2) {
			_Logger.warning("Invalid QoS. Using default. QoS requested: " + qos);

			// TODO: retrieve default QoS from config file
			qos = ConfigConst.DEFAULT_QOS;
		}

		try {
			// Subscribe to the topic with the specified QoS and listener.
			if (listener != null) {
				this.mqttClient.subscribe(topicName, qos, listener);

				_Logger.info("Successfully subscribed to topic with listener: " + topicName);
			} else {
				this.mqttClient.subscribe(topicName, qos);

				_Logger.info("Successfully subscribed to topic: " + topicName);
			}

			return true;
		} catch (Exception e) {
			_Logger.log(Level.SEVERE, "Failed to subscribe to topic: " + topicName, e);
		}

		return false;
	}

	@Override
	public boolean unsubscribeFromTopic(ResourceNameEnum topicName) {
		// Check if the topic name is null.
		if (topicName == null) {
			_Logger.warning("Resource is null. Unable to unsubscribe from topic: " + this.brokerAddr);

			return false;
		}

		// Call the protected unsubscribeFromTopic method with the resource name.
		return unsubscribeFromTopic(topicName.getResourceName());
	}

	protected boolean unsubscribeFromTopic(String topicName) {
		// Check if the topic name is null.
		if (topicName == null) {
			_Logger.warning("Resource is null. Unable to unsubscribe from topic: " + this.brokerAddr);

			return false;
		}

		try {
			// Unsubscribe from the specified topic.
			this.mqttClient.unsubscribe(topicName);

			_Logger.info("Successfully unsubscribed from topic: " + topicName);

			return true;
		} catch (Exception e) {
			_Logger.log(Level.SEVERE, "Failed to unsubscribe from topic: " + topicName, e);
		}

		return false;
	}
	@Override
	public boolean setDataMessageListener(IDataMessageListener listener) {
		// Set the data message listener if it's not null.
		if (listener != null) {
			this.dataMsgListener = listener;
			return true;
		}

		return false;
	}

	public void setConnectionListener(IConnectionListener listener) {
		// Set the connection listener if it's not null.
		if (listener != null) {
			_Logger.info("Setting connection listener.");

			this.connListener = listener;
		} else {
			_Logger.warning("No connection listener specified. Ignoring.");
		}
	}

// Callbacks for MqttCallbackExtended

	@Override
	public void connectComplete(boolean reconnect, String serverURI) {
		_Logger.info("MQTT connection successful (is reconnect = " + reconnect + "). Broker: " + serverURI);

		int qos = 1;

		// Subscribe to the necessary topics if the useCloudGatewayConfig flag is set.
		try {
			if (this.useCloudGatewayConfig) {
				_Logger.info("Subscribing to topic: " + ResourceNameEnum.CDA_ACTUATOR_RESPONSE_RESOURCE.getResourceName());

				this.mqttClient.subscribe(
						ResourceNameEnum.CDA_ACTUATOR_RESPONSE_RESOURCE.getResourceName(),
						qos,
						new ActuatorResponseMessageListener(ResourceNameEnum.CDA_ACTUATOR_RESPONSE_RESOURCE, this.dataMsgListener));

				_Logger.info("Subscribing to topic: " + ResourceNameEnum.CDA_SENSOR_MSG_RESOURCE.getResourceName());
				this.mqttClient.subscribe(
						ResourceNameEnum.CDA_SENSOR_MSG_RESOURCE.getResourceName(),
						qos,
						new SensorResponseMessageListener(ResourceNameEnum.CDA_SENSOR_MSG_RESOURCE, this.dataMsgListener));

				_Logger.info("Subscribing to topic: " + ResourceNameEnum.CDA_SYSTEM_PERF_MSG_RESOURCE.getResourceName());
				this.mqttClient.subscribe(
						ResourceNameEnum.CDA_SYSTEM_PERF_MSG_RESOURCE.getResourceName(),
						qos,
						new SysPerfResponseMessageListener(ResourceNameEnum.CDA_SYSTEM_PERF_MSG_RESOURCE, this.dataMsgListener));
			}
		} catch (MqttException e) {
			_Logger.warning("Failed to subscribe to CDA actuator response topic.");
		}

		// Notify the connection listener that the client has connected.
		if (this.connListener != null) {
			this.connListener.onConnect();
		}
	}

	@Override
	public void connectionLost(Throwable t) {
		_Logger.info("connectionLost Method is called");
		// Pause for 2 seconds before attempting to reconnect.
		try {
			Thread.sleep(2000);
		} catch (Exception e) {
		}
	}

	@Override
	public void deliveryComplete(IMqttDeliveryToken token) {
		_Logger.info("deliveryComplete Method is called with message ID: " + token.getMessageId());
	}

	@Override
	public void messageArrived(String topic, MqttMessage msg) throws Exception {
		_Logger.info("messageArrived Method is called" + new String(msg.getPayload()));
	}


	
	// private methods
	
	/**
	 * Called by the constructor to set the MQTT client parameters to be used for the connection.
	 * 
	 * @param configSectionName The name of the configuration section to use for
	 * the MQTT client configuration parameters.
	 */
	private void initClientParameters(String configSectionName) {
		// Get an instance of the configuration utility.
		ConfigUtil configUtil = ConfigUtil.getInstance();

		// Load parameters from the configuration file.
		this.host = configUtil.getProperty(configSectionName, ConfigConst.HOST_KEY, ConfigConst.DEFAULT_HOST);
		this.port = configUtil.getInteger(configSectionName, ConfigConst.PORT_KEY, ConfigConst.DEFAULT_MQTT_PORT);
		this.brokerKeepAlive = configUtil.getInteger(configSectionName, ConfigConst.KEEP_ALIVE_KEY, ConfigConst.DEFAULT_KEEP_ALIVE);
		this.enableEncryption = configUtil.getBoolean(configSectionName, ConfigConst.ENABLE_CRYPT_KEY);
		this.pemFileName = configUtil.getProperty(configSectionName, ConfigConst.CERT_FILE_KEY);

		// Attempt to load clientID from the configuration file.
		this.clientID = configUtil.getProperty(ConfigConst.GATEWAY_DEVICE, ConfigConst.DEVICE_LOCATION_ID_KEY, MqttClient.generateClientId());

		// Set up MQTT connection options.
		this.persistence = new MemoryPersistence();
		this.connOpts = new MqttConnectOptions();
		this.connOpts.setKeepAliveInterval(this.brokerKeepAlive);
		this.connOpts.setCleanSession(this.useCleanSession);
		this.connOpts.setAutomaticReconnect(this.enableAutoReconnect);

		// If encryption is enabled, initialize secure connection parameters.
		if (this.enableEncryption) {
			initSecureConnectionParameters(configSectionName);
		}

		// If there's a credential file, initialize credential connection parameters.
		if (configUtil.hasProperty(configSectionName, ConfigConst.CRED_FILE_KEY)) {
			initCredentialConnectionParameters(configSectionName);
		}

		// Manually construct the URL for the broker connection since there's no protocol handler for "tcp" or "ssl".
		this.brokerAddr = this.protocol + "://" + this.host + ":" + this.port;

		_Logger.info("Using URL for broker conn: " + this.brokerAddr);
	}

	
	/**
	 * Called by {@link #initClientParameters(String)} to load credentials.
	 * 
	 * @param configSectionName The name of the configuration section to use for
	 * the MQTT client configuration parameters.
	 */
	private void initCredentialConnectionParameters(String configSectionName) {
		// Get an instance of the configuration utility.
		ConfigUtil configUtil = ConfigUtil.getInstance();

		try {
			_Logger.info("Checking if credentials file exists and is loadable...");

			// Load the properties from the credentials file.
			Properties props = configUtil.getCredentials(configSectionName);

			if (props != null) {
				// Set the username and password in the connection options.
				this.connOpts.setUserName(props.getProperty(ConfigConst.USER_NAME_TOKEN_KEY, ""));
				this.connOpts.setPassword(props.getProperty(ConfigConst.USER_AUTH_TOKEN_KEY, "").toCharArray());

				_Logger.info("Credentials now set.");
			} else {
				_Logger.warning("No credentials are set.");
			}
		} catch (Exception e) {
			_Logger.log(Level.WARNING, "Credential file non-existent. Disabling auth requirement.");
		}
	}

	
	/**
	 * Called by {@link #initClientParameters(String)} to enable encryption.
	 * 
	 * @param configSectionName The name of the configuration section to use for
	 * the MQTT client configuration parameters.
	 */
	private void initSecureConnectionParameters(String configSectionName) {
		// Get an instance of the configuration utility.
		ConfigUtil configUtil = ConfigUtil.getInstance();

		try {
			_Logger.info("Configuring TLS...");

			// Check if the PEM file is valid.
			if (this.pemFileName != null) {
				File file = new File(this.pemFileName);

				if (file.exists()) {
					_Logger.info("PEM file valid. Using secure connection: " + this.pemFileName);
				} else {
					this.enableEncryption = false;
					_Logger.log(Level.WARNING, "PEM file invalid. Using insecure connection: " + this.pemFileName, new Exception());

					return;
				}
			}

			// Load the certificate from the PEM file and create an SSLSocketFactory.
			SSLSocketFactory sslFactory = SimpleCertManagementUtil.getInstance().loadCertificate(this.pemFileName);

			// Set the SSLSocketFactory in the connection options.
			this.connOpts.setSocketFactory(sslFactory);

			// Override the current configuration parameters and get the secure protocol and port.
			this.port = configUtil.getInteger(configSectionName, ConfigConst.SECURE_PORT_KEY, ConfigConst.DEFAULT_MQTT_SECURE_PORT);
			this.protocol = ConfigConst.DEFAULT_MQTT_SECURE_PROTOCOL;

			_Logger.info("TLS enabled.");
		} catch (Exception e) {
			_Logger.log(Level.SEVERE, "Failed to initialize secure MQTT connection. Using insecure connection.", e);

			this.enableEncryption = false;
		}
	}

	/**
	 * IMqttMessageListener:
	 * Implementers of this interface will be notified when a message arrives.
	 * 
	 */
// Listener class for handling Actuator response messages
	private class ActuatorResponseMessageListener implements IMqttMessageListener {
		private ResourceNameEnum resource = null;
		private IDataMessageListener dataMsgListener = null;

		// Constructor
		ActuatorResponseMessageListener(ResourceNameEnum resource, IDataMessageListener dataMsgListener) {
			this.resource = resource;
			this.dataMsgListener = dataMsgListener;
		}

		// Override the messageArrived method to handle incoming actuator response messages
		@Override
		public void messageArrived(String topic, MqttMessage message) throws Exception {
			try {
				ActuatorData actuatorData = DataUtil.getInstance().jsonToActuatorData(new String(message.getPayload()));

				// Log the received actuator data response
				_Logger.info("Received ActuatorData response: " + actuatorData.getValue());

				// Invoke the handleActuatorCommandResponse method if the data message listener is set
				if (this.dataMsgListener != null) {
					this.dataMsgListener.handleActuatorCommandResponse(resource, actuatorData);
				}
			} catch (Exception e) {
				_Logger.warning("Failed to convert message payload to ActuatorData.");
			}
		}
	}

	// Listener class for handling Sensor response messages
	private class SensorResponseMessageListener implements IMqttMessageListener {
		private ResourceNameEnum resource = null;
		private IDataMessageListener dataMsgListener = null;

		// Constructor
		SensorResponseMessageListener(ResourceNameEnum resource, IDataMessageListener dataMsgListener) {
			this.resource = resource;
			this.dataMsgListener = dataMsgListener;
		}

		// Override the messageArrived method to handle incoming sensor response messages
		@Override
		public void messageArrived(String topic, MqttMessage message) throws Exception {
			try {
				SensorData sensorData = DataUtil.getInstance().jsonToSensorData(new String(message.getPayload()));

				// Log the received sensor data response
				_Logger.info("Received SensorData response: " + sensorData.getValue());

				// Invoke the handleSensorMessage method if the data message listener is set
				if (this.dataMsgListener != null) {
					this.dataMsgListener.handleSensorMessage(resource, sensorData);
				}
			} catch (Exception e) {
				_Logger.warning("Failed to convert message payload to SensorData.");
			}
		}
	}

	// Listener class for handling System Performance response messages
	private class SysPerfResponseMessageListener implements IMqttMessageListener {
		private ResourceNameEnum resource = null;
		private IDataMessageListener dataMsgListener = null;

		// Constructor
		SysPerfResponseMessageListener(ResourceNameEnum resource, IDataMessageListener dataMsgListener) {
			this.resource = resource;
			this.dataMsgListener = dataMsgListener;
		}

		// Override the messageArrived method to handle incoming system performance response messages
		@Override
		public void messageArrived(String topic, MqttMessage message) throws Exception {
			try {
				SystemPerformanceData sysPerfData = DataUtil.getInstance().jsonToSystemPerformanceData(new String(message.getPayload()));

				// Log the received system performance data
				_Logger.info("Received CPU response: " + sysPerfData.getCpuUtil());
				_Logger.info("Received MEM response: " + sysPerfData.getMemUtil());

				// Invoke the handleSystemPerformanceMessage method if the data message listener is set
				if (this.dataMsgListener != null) {
					this.dataMsgListener.handleSystemPerformanceMessage(resource, sysPerfData);
				}
			} catch (Exception e) {
                _Logger.warning("Failed to convert message payload to SystemPerformanceData.");
            }
        }
        
    }
}
