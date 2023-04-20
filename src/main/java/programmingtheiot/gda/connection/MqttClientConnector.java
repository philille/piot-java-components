/**
 * This class is part of the Programming the Internet of Things project.
 * 
 * It is provided as a simple shell to guide the student and assist with
 * implementation for the Programming the Internet of Things exercises,
 * and designed to be modified by the student as needed.
 */ 

package programmingtheiot.gda.connection;

import java.util.Properties;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
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

import java.io.File;
import javax.net.ssl.SSLSocketFactory;

import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;

import programmingtheiot.common.SimpleCertManagementUtil;
import programmingtheiot.data.ActuatorData;
import programmingtheiot.data.DataUtil;

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
	
	
	// Declare class-scoped variables to store the reference to the MQTT client and associated properties
	
	//private MqttClient           mqttClient = null;
	//MqttAsyncClient in place of MqttClient
	private MqttAsyncClient      mqttClient = null;
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
	private IConnectionListener  connListener = null;
	
	public MqttClientConnector()
	{
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
	        this.useCloudGatewayConfig = false;
	        
	        initClientParameters(ConfigConst.MQTT_GATEWAY_SERVICE);
	    }
	}
	
	
	// Logic to connect to the broker if not already connected, and log an info message indicating connector was started. 
	@Override
	public boolean connectClient()
	{
	    try {
	        if (this.mqttClient == null) {
//	            this.mqttClient = new MqttClient(this.brokerAddr, this.clientID, this.persistence);
	            this.mqttClient = new MqttAsyncClient(this.brokerAddr, this.clientID, this.persistence);
	            this.mqttClient.setCallback(this);
	        }
	        
	        if (! this.mqttClient.isConnected()) {
	            _Logger.info("MQTT client connecting to broker: " + this.brokerAddr);
	            this.mqttClient.connect(this.connOpts);
	            // wait 2s for connecting MQTT broker completely
	            try {
                    Thread.sleep(2000);
                }catch(Exception e) { 
                }
	            return true;
	        } else {
	            _Logger.warning("MQTT client already connected to broker: " + this.brokerAddr);
	        }
	    } catch (MqttException e) { 
	        _Logger.log(Level.SEVERE, "Failed to connect MQTT client to broker.", e);
	    }
	    
	    return false;
	}
    
    //Logic to disconnect from the broker if currently connected, and log an info message indicating the connector was stopped. 
	@Override
	public boolean disconnectClient()
	{
	    try {
	        if (this.mqttClient != null) {
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

	public boolean isConnected()
	{
		// TODO: this logic for use with the synchronous `MqttClient` instance only
		return (this.mqttClient != null && this.mqttClient.isConnected());
	}
	
	//Method to handle all publish functionality. It will accept the topic name, message content, and requested QoS level for parameters. 

	@Override
	public boolean publishMessage(ResourceNameEnum topicName, String msg, int qos)
	{
	    if (topicName == null) {
	        _Logger.warning("Resource is null. Unable to publish message: " + this.brokerAddr);
	        
	        return false;
	    }
	    
	    if (msg == null || msg.length() == 0) {
	        _Logger.warning("Message is null or empty. Unable to publish message: " + this.brokerAddr);
	        
	        return false;
	    }
	    
	    return publishMessage(topicName.getResourceName(), msg.getBytes(), qos);
	}
	// protected method
	protected boolean publishMessage(String topicName, byte[] payload, int qos)
	{
		if (topicName == null) {
			_Logger.warning("Resource is null. Unable to publish message: " + this.brokerAddr);
			
			return false;
		}
		
		if (payload == null || payload.length == 0) {
			_Logger.warning("Message is null or empty. Unable to publish message: " + this.brokerAddr);
			
			return false;
		}
		
		if (qos < 0 || qos > 2) {
			_Logger.warning("Invalid QoS. Using default. QoS requested: " + qos);
			
			// TODO: retrieve default QoS from config file
			qos = ConfigConst.DEFAULT_QOS;
		}
		
		try {
			MqttMessage mqttMsg = new MqttMessage();
			mqttMsg.setQos(qos);
			mqttMsg.setPayload(payload);
			
			this.mqttClient.publish(topicName, mqttMsg);
			
			return true;
		} catch (Exception e) {
			_Logger.log(Level.SEVERE, "Failed to publish message to topic: " + topicName, e);
		}
		
		return false;
	}

    
	//Method to handle all subscribe functionality. It will accept the topic name and requested QoS level for parameters.
	@Override
	public boolean subscribeToTopic(ResourceNameEnum topicName, int qos)
	{
		if (topicName == null) {
			_Logger.warning("Resource is null. Unable to subscribe to topic: " + this.brokerAddr);
			
			return false;
		}
		
		return subscribeToTopic(topicName.getResourceName(), qos);
	}
	
	protected boolean subscribeToTopic(String topicName, int qos)
	{
		return subscribeToTopic(topicName, qos, null);
	}

	protected boolean subscribeToTopic(String topicName, int qos, IMqttMessageListener listener)
	{
		if (topicName == null) {
			_Logger.warning("Resource is null. Unable to subscribe to topic: " + this.brokerAddr);
			
			return false;
		}
		
		if (qos < 0 || qos > 2) {
			_Logger.warning("Invalid QoS. Using default. QoS requested: " + qos);
			
			// TODO: retrieve default QoS from config file
			qos = ConfigConst.DEFAULT_QOS;
		}
		
		try {
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

    //Method to handle all un-subscribe functionality. 	
	@Override
	public boolean unsubscribeFromTopic(ResourceNameEnum topicName)
	{
		if (topicName == null) {
			_Logger.warning("Resource is null. Unable to unsubscribe from topic: " + this.brokerAddr);
			
			return false;
		}
		
		return unsubscribeFromTopic(topicName.getResourceName());
	}
	
	
	protected boolean unsubscribeFromTopic(String topicName)
	{
		if (topicName == null) {
			_Logger.warning("Resource is null. Unable to unsubscribe from topic: " + this.brokerAddr);
			
			return false;
		}
		
		try {
			this.mqttClient.unsubscribe(topicName);
			
			_Logger.info("Successfully unsubscribed from topic: " + topicName);
			
			return true;
		} catch (Exception e) {
			_Logger.log(Level.SEVERE, "Failed to unsubscribe from topic: " + topicName, e);
		}
		
		return false;
	}

	@Override
	public boolean setDataMessageListener(IDataMessageListener listener)
	{
	    if (listener != null) {
	        this.dataMsgListener = listener;
	        return true;
	    }
	    
	    return false;
	}
	public void setConnectionListener(IConnectionListener listener)
	{
	    if (listener != null) {
	        _Logger.info("Setting connection listener.");
	        
	        this.connListener = listener;
	    } else {
	        _Logger.warning("No connection listener specified. Ignoring.");
	    }
	}
	
	// callbacks
	
	@Override
	public void connectComplete(boolean reconnect, String serverURI)
	{
		_Logger.info("MQTT connection successful (is reconnect = " + reconnect + "). Broker: " + serverURI);
		
		int qos = 1;
		
		// Option 2
		try {
			if (! this.useCloudGatewayConfig) {
				_Logger.info("Subscribing to topic: " + ResourceNameEnum.CDA_ACTUATOR_RESPONSE_RESOURCE.getResourceName());
				
				this.mqttClient.subscribe(
					ResourceNameEnum.CDA_ACTUATOR_RESPONSE_RESOURCE.getResourceName(),
					qos,
					new ActuatorResponseMessageListener(ResourceNameEnum.CDA_ACTUATOR_RESPONSE_RESOURCE, this.dataMsgListener));
			}
		} catch (MqttException e) {
			_Logger.warning("Failed to subscribe to CDA actuator response topic.");
		}
		
		if (this.connListener != null) {
			this.connListener.onConnect();
		}
	}

	@Override
	public void connectionLost(Throwable t)
	{
	}
	
	@Override
	public void deliveryComplete(IMqttDeliveryToken token)
	{
	}
	
	@Override
	public void messageArrived(String topic, MqttMessage msg) throws Exception
	{
	}

	
	// private methods
	
	/**
	 * Called by the constructor to set the MQTT client parameters to be used for the connection.
	 * 
	 * @param configSectionName The name of the configuration section to use for
	 * the MQTT client configuration parameters.
	 */
	private void initClientParameters(String configSectionName)
	{
		ConfigUtil configUtil = ConfigUtil.getInstance();
		
		this.host =
			configUtil.getProperty(
				configSectionName, ConfigConst.HOST_KEY, ConfigConst.DEFAULT_HOST);
		this.port =
			configUtil.getInteger(
				configSectionName, ConfigConst.PORT_KEY, ConfigConst.DEFAULT_MQTT_PORT);
		this.brokerKeepAlive =
			configUtil.getInteger(
				configSectionName, ConfigConst.KEEP_ALIVE_KEY, ConfigConst.DEFAULT_KEEP_ALIVE);
		this.enableEncryption =
			configUtil.getBoolean(
				configSectionName, ConfigConst.ENABLE_CRYPT_KEY);
		this.pemFileName =
			configUtil.getProperty(
				configSectionName, ConfigConst.CERT_FILE_KEY);
		
		// NOTE: updated from Lab Module 07 - attempt to load clientID from configuration file
		this.clientID =
			configUtil.getProperty(
				ConfigConst.GATEWAY_DEVICE, ConfigConst.DEVICE_LOCATION_ID_KEY, MqttClient.generateClientId());
		
		// these are specific to the MQTT connection which will be used during connect
		this.persistence = new MemoryPersistence();
		this.connOpts    = new MqttConnectOptions();
		
		this.connOpts.setKeepAliveInterval(this.brokerKeepAlive);
		this.connOpts.setCleanSession(this.useCleanSession);
		this.connOpts.setAutomaticReconnect(this.enableAutoReconnect);
		
		// if encryption is enabled, try to load and apply the cert(s)
		if (this.enableEncryption) {
			initSecureConnectionParameters(configSectionName);
		}
		
		// if there's a credential file, try to load and apply them
		if (configUtil.hasProperty(configSectionName, ConfigConst.CRED_FILE_KEY)) {
			initCredentialConnectionParameters(configSectionName);
		}
		
		// NOTE: URL does not have a protocol handler for "tcp" or "ssl",
		// so construct the URL manually
		this.brokerAddr  = this.protocol + "://" + this.host + ":" + this.port;
		
		_Logger.info("Using URL for broker conn: " + this.brokerAddr);
	}
	
	/**
	 * Called by {@link #initClientParameters(String)} to load credentials.
	 * 
	 * @param configSectionName The name of the configuration section to use for
	 * the MQTT client configuration parameters.
	 */
	

	private void initCredentialConnectionParameters(String configSectionName)
	{
		ConfigUtil configUtil = ConfigUtil.getInstance();
		
		try {
			_Logger.info("Checking if credentials file exists and is loadable...");
			
			Properties props = configUtil.getCredentials(configSectionName);
			
			if (props != null) {
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
	private void initSecureConnectionParameters(String configSectionName)
	{
		ConfigUtil configUtil = ConfigUtil.getInstance();
		
		try {
			_Logger.info("Configuring TLS...");
			
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
			
			SSLSocketFactory sslFactory =
				SimpleCertManagementUtil.getInstance().loadCertificate(this.pemFileName);
			
			this.connOpts.setSocketFactory(sslFactory);
			
			// override current config parameters
			this.port =
				configUtil.getInteger(
					configSectionName, ConfigConst.SECURE_PORT_KEY, ConfigConst.DEFAULT_MQTT_SECURE_PORT);
			
			this.protocol = ConfigConst.DEFAULT_MQTT_SECURE_PROTOCOL;
			
			_Logger.info("TLS enabled.");
		} catch (Exception e) {
			_Logger.log(Level.SEVERE, "Failed to initialize secure MQTT connection. Using insecure connection.", e);
			
			this.enableEncryption = false;
		}
	}
	
	//ActuatorData response messages
	private class ActuatorResponseMessageListener implements IMqttMessageListener
	{
		private ResourceNameEnum resource = null;
		private IDataMessageListener dataMsgListener = null;
		
		ActuatorResponseMessageListener(ResourceNameEnum resource, IDataMessageListener dataMsgListener)
		{
			this.resource = resource;
			this.dataMsgListener = dataMsgListener;
		}
		
		@Override
		public void messageArrived(String topic, MqttMessage message) throws Exception
		{
			try {
				ActuatorData actuatorData =
					DataUtil.getInstance().jsonToActuatorData(new String(message.getPayload()));
				
				// optionally, log a message indicating data was received
				_Logger.info("Received ActuatorData response: " + actuatorData.getValue());
					

				if (this.dataMsgListener != null) {
					this.dataMsgListener.handleActuatorCommandResponse(resource, actuatorData);
				}
			} catch (Exception e) {
				_Logger.warning("Failed to convert message payload to ActuatorData.");
			}
		}
		
	}
}
