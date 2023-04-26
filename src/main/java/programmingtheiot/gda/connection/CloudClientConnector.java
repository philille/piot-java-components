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

	// TODO: set to either 0 or 1, depending on which is preferred for your implementation
	private int qosLevel = 1;
	
	// constructors
	
	/**
	 * Default.
	 * 
	 */
	// Constructor for the CloudClientConnector class
	public CloudClientConnector()
	{
		// Obtain an instance of the ConfigUtil class
		ConfigUtil configUtil = ConfigUtil.getInstance();

		// Get the topic prefix from the configuration file
		this.topicPrefix =
				configUtil.getProperty(ConfigConst.CLOUD_GATEWAY_SERVICE, ConfigConst.BASE_TOPIC_KEY);

		// Set the topic prefix to a default value if not found
		if (topicPrefix == null) {
			topicPrefix = "/";
		} else {
			// Ensure the topic prefix ends with a '/'
			if (! topicPrefix.endsWith("/")) {
				topicPrefix += "/";
			}
		}
	}


	// public methods

	// Connect the client to the cloud service
	@Override
	public boolean connectClient()
	{
		// Create an MQTT client if not already created
		if (this.mqttClient == null) {
			// Use CloudGateway properties
			this.mqttClient = new MqttClientConnector(true);
			this.mqttClient.setConnectionListener(this);
		}

		// Connect the MQTT client
		this.mqttClient.connectClient();

		return true;
	}

	// Disconnect the client from the cloud service
	@Override
	public boolean disconnectClient()
	{
		if (this.mqttClient != null && this.mqttClient.isConnected()) {
			return this.mqttClient.disconnectClient();
		}

		return false;
	}

	// Set the data message listener for the cloud client (not implemented)
	@Override
	public boolean setDataMessageListener(IDataMessageListener listener)
	{
		return false;
	}

	// Send edge data (sensor data) to the cloud
	@Override
	public boolean sendEdgeDataToCloud(ResourceNameEnum resource, SensorData data)
	{
		// Check if the resource and data are not null
		if (resource != null && data != null) {
			// Convert the sensor data to JSON
			String payload = DataUtil.getInstance().sensorDataToJson(data);

			// Publish the message to the cloud
			return publishMessageToCloud(resource, data.getName(), payload);
		}

		return false;
	}

	// Send edge data (system performance data) to the cloud
	@Override
	public boolean sendEdgeDataToCloud(ResourceNameEnum resource, SystemPerformanceData data)
	{
		if (resource != null && data != null) {
			// Create and set up CPU utilization sensor data
			SensorData cpuData = new SensorData();
			cpuData.updateData(data);
			cpuData.setName(ConfigConst.CPU_UTIL_NAME);
			cpuData.setValue(data.getCpuUtil());

			// Send CPU utilization data to the cloud
			boolean cpuDataSuccess = sendEdgeDataToCloud(resource, cpuData);

			// Log a warning if sending CPU utilization data fails
			if (! cpuDataSuccess) {
				_Logger.warning("Failed to send CPU utilization data to cloud service.");
			}

			// Create and set up memory utilization sensor data
			SensorData memData = new SensorData();
			memData.updateData(data);
			memData.setName(ConfigConst.MEM_UTIL_NAME);
			memData.setValue(data.getMemUtil());

			// Send memory utilization data to the cloud
			boolean memDataSuccess = sendEdgeDataToCloud(resource, memData);

			// Log a warning if sending memory utilization data fails
			if (! memDataSuccess) {
				_Logger.warning("Failed to send memory utilization data to cloud service.");
			}

			// Return true if both CPU and memory utilization data were sent successfully
			return (cpuDataSuccess == memDataSuccess);
		}

		return false;
	}

	// Subscribe to cloud events
	@Override
	public boolean subscribeToCloudEvents(ResourceNameEnum resource)
	{
		boolean success = false;

		String topicName = null;

		// Check if the MQTT client is connected
		if (this.mqttClient != null && this.mqttClient.isConnected()) {
			// Create the topic name for the resource
			topicName = createTopicName(resource);

			// Subscribe to the topic with the specified QoS level
			this.mqttClient.subscribeToTopic(topicName, this.qosLevel);

			success = true;
		} else {
			// Log a warning if the MQTT client is not connected
			_Logger.warning("Subscription methods only available for MQTT. No MQTT connection to broker. Ignoring. Topic: " + topicName);
		}

		return success;
	}

	// Unsubscribe from cloud events
	@Override
	public boolean unsubscribeFromCloudEvents(ResourceNameEnum resource)
	{
		boolean success = false;

		String topicName = null;

		// Check if the MQTT client is connected
		if (this.mqttClient != null && this.mqttClient.isConnected()) {
			// Create the topic name for the resource
			topicName = createTopicName(resource);

			// Unsubscribe from the topic
			this.mqttClient.unsubscribeFromTopic(topicName);

			success = true;
		} else {
			// Log a warning if the MQTT client is not connected
			_Logger.warning("Unsubscribe method only available for MQTT. No MQTT connection to broker. Ignoring. Topic: " + topicName);
		}

		return success;
	}
	// Handle Cloud Service Provider (CSP) subscriptions and device topic provisioning on MQTT client connect
	@Override
	public void onConnect()
	{
		_Logger.info("Handling CSP subscriptions and device topic provisioning...");

		// Instantiate enablement message listeners for HVAC, LED, and Humidifier
		HvacEnablementMessageListener hvacListener = new HvacEnablementMessageListener(this.dataMsgListener);
		LEDEnablementMessageListener ledListener = new LEDEnablementMessageListener(this.dataMsgListener);
		HudimifierEnablementMessageListener humidifierListener = new HudimifierEnablementMessageListener(this.dataMsgListener);

		ActuatorData ad = new ActuatorData();

		// HVAC
		ad.setResponse(true);
		ad.setName(ConfigConst.HVAC_ACTUATOR_NAME);
		ad.setValue((float) -1.0); // NOTE: this just needs to be an invalid actuation value

		// Create topic for HVAC and send actuator data JSON
		String Topic = createTopicName(hvacListener.getResource().getDeviceName(), ad.getName());
		String adJson = DataUtil.getInstance().actuatorDataToJson(ad);

		this.publishMessageToCloud(Topic, adJson);

		// Subscribe to HVAC topic with the specified QoS level
		this.mqttClient.subscribeToTopic(Topic, this.qosLevel, hvacListener);

		// LED
		ad.setResponse(true);
		ad.setName(ConfigConst.LED_ACTUATOR_NAME);
		ad.setValue((float) -1.0); // NOTE: this just needs to be an invalid actuation value

		// Create topic for LED and send actuator data JSON
		Topic = createTopicName(ledListener.getResource().getDeviceName(), ad.getName());
		adJson = DataUtil.getInstance().actuatorDataToJson(ad);

		this.publishMessageToCloud(Topic, adJson);

		// Subscribe to LED topic with the specified QoS level
		this.mqttClient.subscribeToTopic(Topic, this.qosLevel, ledListener);

		// HUMIDIFIER
		ad.setResponse(true);
		ad.setName(ConfigConst.HUMIDIFIER_ACTUATOR_NAME);
		ad.setValue((float) -1.0);

		// Create topic for Humidifier and send actuator data JSON
		Topic = createTopicName(humidifierListener.getResource().getDeviceName(), ad.getName());
		adJson = DataUtil.getInstance().actuatorDataToJson(ad);

		this.publishMessageToCloud(Topic, adJson);

		// Subscribe to Humidifier topic with the specified QoS level
		this.mqttClient.subscribeToTopic(Topic, this.qosLevel, humidifierListener);

		// Humidity Sensor Data
		SensorData humiditySensor = new SensorData();
		humiditySensor.setName(ConfigConst.HUMIDITY_SENSOR_NAME);
		humiditySensor.setTypeID(ConfigConst.HUMIDITY_SENSOR_TYPE);

		// CPU MEM Sensor Data
		SystemPerformanceData sysPerfData = new SystemPerformanceData();

		// Send humidity sensor data and system performance data to the cloud
		this.sendEdgeDataToCloud(ResourceNameEnum.CDA_SENSOR_MSG_RESOURCE, humiditySensor);
		this.sendEdgeDataToCloud(ResourceNameEnum.CDA_SYSTEM_PERF_MSG_RESOURCE, sysPerfData);
	}
	// Handle MQTT client disconnect event
	@Override
	public void onDisconnect()
	{
		_Logger.info("MQTT client disconnected. Nothing else to do.");
	}

	// Create cloud resource and send actuator data to the cloud
	@Override
	public boolean createCloudResource(ResourceNameEnum resource, ActuatorData data) {
		if (resource != null && data != null) {
			String payload = DataUtil.getInstance().actuatorDataToJson(data);

			return publishMessageToCloud(resource, data.getName(), payload);
		}

		return false;
	}

	// private methods
	// Create topic name based on resource
	private String createTopicName(ResourceNameEnum resource)
	{
		return createTopicName(resource.getDeviceName(), resource.getResourceType());
	}

	// Create topic name based on resource and item name
	private String createTopicName(ResourceNameEnum resource, String itemName)
	{
		return (createTopicName(resource) + "-" + itemName).toLowerCase();
	}

	// Create topic name based on device name and resource type name
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

	// Publish message to cloud based on resource, item name, and payload
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

	// Publish message to cloud based on item name and payload
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

	// HvacEnablementMessageListener class definition
	private class HvacEnablementMessageListener implements IMqttMessageListener
	{
		private IDataMessageListener dataMsgListener = null;

		private ResourceNameEnum resource = ResourceNameEnum.CDA_ACTUATOR_CMD_RESOURCE;

		private int    typeID   = ConfigConst.HVAC_ACTUATOR_TYPE;
		private String itemName = ConfigConst.HVAC_ACTUATOR_NAME;

		// Constructor
		HvacEnablementMessageListener(IDataMessageListener dataMsgListener)
		{
			this.dataMsgListener = dataMsgListener;
		}

		// Get resource
		public ResourceNameEnum getResource()
		{
			return this.resource;
		}

		// This method is called when a message arrives from the server
		@Override
		public void messageArrived(String topic, MqttMessage message) throws Exception

	    {
	        try {
	            String jsonData = new String(message.getPayload());

	            _Logger.info("Received Actuator Response Message: " + jsonData);

	            ActuatorData actuatorData =
	                DataUtil.getInstance().jsonToActuatorData(jsonData);

	            // TODO: This will have to match the CDA's location ID, depending on the
	            // validation logic implemented within the CDA's ActuatorAdapterManager
	            actuatorData.setLocationID(ConfigConst.CONSTRAINED_DEVICE);
	            actuatorData.setTypeID(this.typeID);
	            actuatorData.setName(this.itemName);

	            int val = (int) actuatorData.getValue();

	            _Logger.info("current val is :" + val);

	            switch (val) {
	                case ConfigConst.ON_COMMAND:
	                    _Logger.info("Received Hvac enablement message [ON].");
	                    actuatorData.setStateData("Hvac switching ON");
	                    break;

	                case ConfigConst.OFF_COMMAND:
	                    _Logger.info("Received Hvac enablement message [OFF].");
	                    actuatorData.setStateData("Hvac switching OFF");
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

	private class LEDEnablementMessageListener implements IMqttMessageListener
    {
        private IDataMessageListener dataMsgListener = null;

        private ResourceNameEnum resource = ResourceNameEnum.CDA_ACTUATOR_CMD_RESOURCE;

        private int    typeID   = ConfigConst.LED_ACTUATOR_TYPE;
        private String itemName = ConfigConst.LED_ACTUATOR_NAME;

        LEDEnablementMessageListener(IDataMessageListener dataMsgListener)
        {
            this.dataMsgListener = dataMsgListener;
        }

        public ResourceNameEnum getResource()
        {
            return this.resource;
        }
        /*
         * This method is called when a message arrives from the server.
         * This method is invoked synchronously by the MQTT client. An
         * acknowledgment is not sent back to the server until this
         * method returns cleanly.
         */
        @Override
        public void messageArrived(String topic, MqttMessage message) throws Exception
        {
            try {
                String jsonData = new String(message.getPayload());

                _Logger.info("Received Actuator Response Message: " + jsonData);

                ActuatorData actuatorData =
                    DataUtil.getInstance().jsonToActuatorData(jsonData);

                // TODO: This will have to match the CDA's location ID, depending on the
                // validation logic implemented within the CDA's ActuatorAdapterManager
                actuatorData.setLocationID(ConfigConst.CONSTRAINED_DEVICE);
                actuatorData.setTypeID(this.typeID);
                actuatorData.setName(this.itemName);

                int val = (int) actuatorData.getValue();

                _Logger.info("current val is :" + val);

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

	private class HudimifierEnablementMessageListener implements IMqttMessageListener
    {
        private IDataMessageListener dataMsgListener = null;

        private ResourceNameEnum resource = ResourceNameEnum.CDA_ACTUATOR_CMD_RESOURCE;

        private int    typeID   = ConfigConst.HUMIDIFIER_ACTUATOR_TYPE;
        private String itemName = ConfigConst.HUMIDIFIER_ACTUATOR_NAME;

        HudimifierEnablementMessageListener(IDataMessageListener dataMsgListener)
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

                _Logger.info("Received Actuator Response Message: " + jsonData);

                ActuatorData actuatorData =
                    DataUtil.getInstance().jsonToActuatorData(jsonData);

                // TODO: This will have to match the CDA's location ID, depending on the
                // validation logic implemented within the CDA's ActuatorAdapterManager
                actuatorData.setLocationID(ConfigConst.CONSTRAINED_DEVICE);
                actuatorData.setTypeID(this.typeID);
                actuatorData.setName(this.itemName);

                int val = (int) actuatorData.getValue();

                _Logger.info("current val is :" + val);

                switch (val) {
                    case ConfigConst.ON_COMMAND:
                        _Logger.info("Received Humidifier enablement message [ON].");
                        actuatorData.setStateData("Humidifier switching ON");
                        break;

                    case ConfigConst.OFF_COMMAND:
                        _Logger.info("Received Humidifier enablement message [OFF].");
                        actuatorData.setStateData("Humidifier switching OFF");
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
