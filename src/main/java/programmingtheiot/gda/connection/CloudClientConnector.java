/**
 * This class is part of the Programming the Internet of Things project.
 * 
 * It is provided as a simple shell to guide the student and assist with
 * implementation for the Programming the Internet of Things exercises,
 * and designed to be modified by the student as needed.
 */ 

package programmingtheiot.gda.connection;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import programmingtheiot.common.ConfigConst;
import programmingtheiot.common.ConfigUtil;
import programmingtheiot.common.IDataMessageListener;
import programmingtheiot.common.ResourceNameEnum;
import programmingtheiot.data.ActuatorData;
import programmingtheiot.data.DataUtil;
import programmingtheiot.data.SensorData;
import programmingtheiot.data.SystemPerformanceData;

/**
 * Shell representation of class for student implementation.
 *
 */
public class CloudClientConnector implements ICloudClient,IConnectionListener
{
	// static
	
	private static final Logger _Logger =
		Logger.getLogger(CloudClientConnector.class.getName());
	
	// private var's
	
	private String topicPrefix = "";
	private MqttClientConnector mqttClient = null;
	private IDataMessageListener dataMsgListener = null;
	private int qosLevel = 1;
	
	
	// constructors
	
	/**
	 * Default.
	 * 
	 */
	public CloudClientConnector()
	{
	    ConfigUtil configUtil = ConfigUtil.getInstance();
	    
	    this.topicPrefix =
	        configUtil.getProperty(ConfigConst.CLOUD_GATEWAY_SERVICE, ConfigConst.BASE_TOPIC_KEY);
	    
	    // Depending on the cloud service, the topic names may or may not begin with a "/", so this code
	    // should be updated according to the cloud service provider's topic naming conventions
	    if (topicPrefix == null) {
	        topicPrefix = "/";
	    } else {
	        if (! topicPrefix.endsWith("/")) {
	            topicPrefix += "/";
	        }
	    }
	}
	
	
	// public methods
	
	@Override
	public boolean connectClient()
	{
		if (this.mqttClient == null) {
			this.mqttClient = new MqttClientConnector(true);
			this.mqttClient.setConnectionListener(this);
		}
		
		return this.mqttClient.connectClient();
	}

	@Override
	public boolean disconnectClient()
	{
	    if (this.mqttClient != null && this.mqttClient.isConnected()) {
	        return this.mqttClient.disconnectClient();
	    }
	    
	    return false;
	}

	@Override
	public boolean setDataMessageListener(IDataMessageListener listener)
	{
		return false;
	}

	@Override
	public boolean sendEdgeDataToCloud(ResourceNameEnum resource, SensorData data)
	{
	    if (resource != null && data != null) {
	        String payload = DataUtil.getInstance().sensorDataToJson(data);
	        
	        return publishMessageToCloud(resource, data.getName(), payload);
	    }
	    
	    return false;
	}

	@Override
	public boolean sendEdgeDataToCloud(ResourceNameEnum resource, SystemPerformanceData data)
	{
	    if (resource != null && data != null) {
	        SensorData cpuData = new SensorData();
	        cpuData.updateData(data);
	        cpuData.setName(ConfigConst.CPU_UTIL_NAME);
	        cpuData.setValue(data.getCpuUtilization());
	        
	        boolean cpuDataSuccess = sendEdgeDataToCloud(resource, cpuData);
	        
	        if (! cpuDataSuccess) {
	            _Logger.warning("Failed to send CPU utilization data to cloud service.");
	        }
	        
	        SensorData memData = new SensorData();
	        memData.updateData(data);
	        memData.setName(ConfigConst.MEM_UTIL_NAME);
	        memData.setValue(data.getMemoryUtilization());
	        
	        boolean memDataSuccess = sendEdgeDataToCloud(resource, memData);
	        
	        if (! memDataSuccess) {
	            _Logger.warning("Failed to send memory utilization data to cloud service.");
	        }
	        
	        return (cpuDataSuccess == memDataSuccess);
	    }
	    
	    return false;
	}

	@Override
	public boolean subscribeToCloudEvents(ResourceNameEnum resource)
	{
	    boolean success = false;
	    
	    String topicName = null;
	    
	    if (this.mqttClient != null && this.mqttClient.isConnected()) {
	        topicName = createTopicName(resource);
	        
	        this.mqttClient.subscribeToTopic(topicName, this.qosLevel);
	        
	        success = true;
	    } else {
	        _Logger.warning("Subscription methods only available for MQTT. No MQTT connection to broker. Ignoring. Topic: " + topicName);
	    }
	    
	    return success;
	}

	@Override
	public boolean unsubscribeFromCloudEvents(ResourceNameEnum resource)
	{
	    boolean success = false;
	    
	    String topicName = null;
	    
	    if (this.mqttClient != null && this.mqttClient.isConnected()) {
	        topicName = createTopicName(resource);
	        
	        this.mqttClient.unsubscribeFromTopic(topicName);
	        
	        success = true;
	    } else {
	        _Logger.warning("Unsubscribe method only available for MQTT. No MQTT connection to broker. Ignoring. Topic: " + topicName);
	    }
	    
	    return success;
	}
	
	@Override
	public void onConnect()
	{
		_Logger.info("Handling CSP subscriptions and device topic provisioninig...");
		
		LedEnablementMessageListener ledListener = new LedEnablementMessageListener(this.dataMsgListener);
		
		// topic may not exist yet, so create a 'response' actuation event with invalid value -
		// this will create the relevant topic if it doesn't yet exist, which ensures
		// the message listener (if coded correctly) will log a message but ignore the
		// actuation command and NOT pass it onto the IDataMessageListener instance
		ActuatorData ad = new ActuatorData();
		ad.setAsResponse();
		ad.setName(ConfigConst.LED_ACTUATOR_NAME);
		ad.setValue((float) -1.0); // NOTE: this just needs to be an invalid actuation value

		String ledTopic = createTopicName(ledListener.getResource().getDeviceName(), ad.getName());
		String adJson = DataUtil.getInstance().actuatorDataToJson(ad);

		this.publishMessageToCloud(ledTopic, adJson);
		
		this.mqttClient.subscribeToTopic(ledTopic, this.qosLevel, ledListener);
	}

	@Override
	public void onDisconnect()
	{
		_Logger.info("MQTT client disconnected. Nothing else to do.");
	}
	
	@Override
    public boolean createCloudResource(ResourceNameEnum resource, ActuatorData data) {
	    if (resource != null && data != null) {
            String payload = DataUtil.getInstance().actuatorDataToJson(data);
            
            return publishMessageToCloud(resource, data.getName(), payload);
        }
	    
        return false;
    }
	
	// private methods
	private String createTopicName(ResourceNameEnum resource)
	{
		return createTopicName(resource.getDeviceName(), resource.getResourceType());
	}

	private String createTopicName(ResourceNameEnum resource, String itemName)
	{
		return (createTopicName(resource) + "-" + itemName).toLowerCase();
	}

	private String createTopicName(String deviceName, String resourceTypeName)
	{
		StringBuilder buf = new StringBuilder();
		
		if (deviceName != null && deviceName.trim().length() > 0) {
			buf.append(topicPrefix).append(deviceName);
		}
		
		if (resourceTypeName != null && resourceTypeName.trim().length() > 0) {
			buf.append('/').append(resourceTypeName);
		}
		
		return buf.toString().toLowerCase();
	}

	private boolean publishMessageToCloud(ResourceNameEnum resource, String itemName, String payload)
	{
	    String topicName = createTopicName(resource) + "-" + itemName;
	    
	    try {
	        _Logger.finest("Publishing payload value(s) to CSP: " + topicName);
	        
	        this.mqttClient.publishMessage(topicName, payload.getBytes(), this.qosLevel);
	        
	        return true;
	    } catch (Exception e) {
	        _Logger.warning("Failed to publish message to CSP: " + topicName);
	    }
	    
	    return false;
	}
	
	private boolean publishMessageToCloud(String itemName, String payload)
    {
        String topicName = itemName;
        
        try {
            _Logger.finest("Publishing payload value(s) to CSP: " + topicName);
            
            this.mqttClient.publishMessage(topicName, payload.getBytes(), this.qosLevel);
            
            return true;
        } catch (Exception e) {
            _Logger.warning("Failed to publish message to CSP: " + topicName);
        }
        
        return false;
    }  
	
	private class LedEnablementMessageListener implements IMqttMessageListener
	{
		private IDataMessageListener dataMsgListener = null;
		
		private ResourceNameEnum resource = ResourceNameEnum.CDA_ACTUATOR_CMD_RESOURCE;
		
		private int    typeID   = ConfigConst.LED_ACTUATOR_TYPE;
		private String itemName = ConfigConst.LED_ACTUATOR_NAME;
		
		LedEnablementMessageListener(IDataMessageListener dataMsgListener)
		{
			this.dataMsgListener = dataMsgListener;
		}
		
		public ResourceNameEnum getResource()
		{
			return this.resource;
		}
		
		@Override
		public void messageArrived(String topic, MqttMessage message) throws Exception
		{
			try {
				String jsonData = new String(message.getPayload());
				
				ActuatorData actuatorData =
					DataUtil.getInstance().jsonToActuatorData(jsonData);
				
				// TODO: This will have to match the CDA's location ID, depending on the
				// validation logic implemented within the CDA's ActuatorAdapterManager
				actuatorData.setLocationID(ConfigConst.CONSTRAINED_DEVICE);
				actuatorData.setTypeID(this.typeID);
				actuatorData.setName(this.itemName);
				
				int val = (int) actuatorData.getValue();
				
				switch (val) {
					case ConfigConst.ON_COMMAND:
						_Logger.info("Received LED enablement message [ON].");
						actuatorData.setStateData("LED switching ON");
						break;
						
					case ConfigConst.OFF_COMMAND:
						_Logger.info("Received LED enablement message [OFF].");
						actuatorData.setStateData("LED switching OFF");
						break;
						
					default:
						return;
				}
				
				if (this.dataMsgListener != null) {
					jsonData = DataUtil.getInstance().actuatorDataToJson(actuatorData);
					
					this.dataMsgListener.handleIncomingMessage(
						ResourceNameEnum.CDA_ACTUATOR_CMD_RESOURCE, jsonData);
				}
			} catch (Exception e) {
				_Logger.warning("Failed to convert message payload to ActuatorData.");
			}
		}
	}
    
}
