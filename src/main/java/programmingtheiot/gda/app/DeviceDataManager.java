/**
 * This class is part of the Programming the Internet of Things project.
 * 
 * It is provided as a simple shell to guide the student and assist with
 * implementation for the Programming the Internet of Things exercises,
 * and designed to be modified by the student as needed.
 */ 

package programmingtheiot.gda.app;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import programmingtheiot.common.ConfigConst;
import programmingtheiot.common.ConfigUtil;
import programmingtheiot.common.IActuatorDataListener;
import programmingtheiot.common.IDataMessageListener;
import programmingtheiot.common.ResourceNameEnum;

import programmingtheiot.data.ActuatorData;
import programmingtheiot.data.BaseIotData;
import programmingtheiot.data.DataUtil;
import programmingtheiot.data.SensorData;
import programmingtheiot.data.SystemPerformanceData;

import programmingtheiot.gda.connection.CloudClientConnector;
import programmingtheiot.gda.connection.CoapClientConnector;
import programmingtheiot.gda.connection.CoapServerGateway;
import programmingtheiot.gda.connection.ICloudClient;
import programmingtheiot.gda.connection.IPersistenceClient;
import programmingtheiot.gda.connection.IPubSubClient;
import programmingtheiot.gda.connection.IRequestResponseClient;
import programmingtheiot.gda.connection.MqttClientConnector;
import programmingtheiot.gda.connection.RedisPersistenceAdapter;
import programmingtheiot.gda.connection.SmtpClientConnector;
import programmingtheiot.gda.system.SystemPerformanceManager;

/**
 * Shell representation of class for student implementation.
 *
 */
public class DeviceDataManager implements IDataMessageListener
{
	// static
	
	private static final Logger _Logger =
		Logger.getLogger(DeviceDataManager.class.getName());
	
	// private var's
	
	private boolean enableMqttClient = true;
	private boolean enableCoapServer = false;
	private boolean enableCoapClient = false;
	private boolean enableCloudClient = false;
	//private boolean enableSmtpClient = false;
	private boolean enablePersistenceClient = false;
	private boolean enableSystemPerf = false;
	
	private IActuatorDataListener actuatorDataListener = null;
	private IPubSubClient mqttClient = null;
	private ICloudClient cloudClient = null;
	private IPersistenceClient persistenceClient = null;
	private IRequestResponseClient smtpClient = null;
	private CoapServerGateway coapServer = null;
	private CoapClientConnector coapClient = null;
	private SystemPerformanceManager sysPerfMgr = null;
	
	private ActuatorData   latestHumidiferActuatorData = null;
	private SensorData     latestHumiditySensorData = null;
	private OffsetDateTime latestHumiditySensorTimeStamp = null;

	private boolean handleHumidityChangeOnDevice = false; // optional
	private int     lastKnownHumidifierCommand   = ConfigConst.OFF_COMMAND;

	// TODO: Load these from PiotConfig.props
	private long    humidityMaxTimePastThreshold = 300; // seconds
	private float   nominalHumiditySetting   = 50.0f;
	private float   triggerHumidiferFloor    = 40.0f;
	private float   triggerHumidifierCeiling = 60.0f;
	
	// constructors
	
	public DeviceDataManager()
	{
		super();
		
		ConfigUtil configUtil = ConfigUtil.getInstance();
		
		this.enableMqttClient =
			configUtil.getBoolean(
				ConfigConst.GATEWAY_DEVICE, ConfigConst.ENABLE_MQTT_CLIENT_KEY);
		
		this.enableCoapServer =
			configUtil.getBoolean(
				ConfigConst.GATEWAY_DEVICE, ConfigConst.ENABLE_COAP_SERVER_KEY);
		
		this.enableCoapClient = 
		    configUtil.getBoolean(ConfigConst.GATEWAY_DEVICE, ConfigConst.ENABLE_COAP_CLIENT_KEY);
		
		this.enableCloudClient =
			configUtil.getBoolean(
				ConfigConst.GATEWAY_DEVICE, ConfigConst.ENABLE_CLOUD_CLIENT_KEY);
		
		this.enablePersistenceClient =
			configUtil.getBoolean(
				ConfigConst.GATEWAY_DEVICE, ConfigConst.ENABLE_PERSISTENCE_CLIENT_KEY);
		
		// parse config rules for local actuation events

		// TODO: add these to ConfigConst
		this.handleHumidityChangeOnDevice =
		    configUtil.getBoolean(
		        ConfigConst.GATEWAY_DEVICE, ConfigConst.HANDLE_HUMIDITY_CHANGE_ON_DEVICE);

		this.humidityMaxTimePastThreshold =
		    configUtil.getInteger(ConfigConst.GATEWAY_DEVICE, ConfigConst.HUMIDITY_MAX_TIME_PAST_THRESHOLD);

		this.nominalHumiditySetting =
		    configUtil.getFloat(
		        ConfigConst.GATEWAY_DEVICE, ConfigConst.NOMINAL_HUMIDITY_SETTING);

		this.triggerHumidiferFloor =
		    configUtil.getFloat(
		        ConfigConst.GATEWAY_DEVICE, ConfigConst.TRIGGER_HUMIDIFIER_FLOOR);

		this.triggerHumidifierCeiling =
		    configUtil.getFloat(
		        ConfigConst.GATEWAY_DEVICE, ConfigConst.TRIGGER_HUMIDIFIER_CEILING);

		// TODO: basic validation for timing - add other validators for remaining values
		if (this.humidityMaxTimePastThreshold < 10 || this.humidityMaxTimePastThreshold > 7200) {
		    this.humidityMaxTimePastThreshold = 300;
		}   
		
		initManager();
	}

	public DeviceDataManager(
		boolean enableMqttClient,
		boolean enableCoapClient,
		boolean enableCloudClient,
		boolean enablePersistenceClient)
	{
		super();
		this.enableCloudClient = enableCloudClient;
		this.enableCoapServer = enableCoapClient;
		this.enableMqttClient = enableMqttClient;
		this.enablePersistenceClient = enablePersistenceClient;
		initConnections();
	}
	
	
	// public methods
	// Handle actuator command response
	@Override
	public boolean handleActuatorCommandResponse(ResourceNameEnum resourceName, ActuatorData data)
	{
		// Check if data is not null
		if (data != null) {
			// Log information about handling actuator response
			_Logger.info("Handling actuator response: " + data.getName());

			// This next call is optional for now
			// Commented out: handleIncomingDataAnalysis(resourceName, data);

			// Check if there's an error in the ActuatorData instance
			if (data.hasError()) {
				_Logger.warning("Error flag set for ActuatorData instance.");
			}

			// Log JSON data of the ActuatorData instance
			_Logger.info("JSON [Actuator]" + DataUtil.getInstance().actuatorDataToJson(data));
			return true;
		} else {
			return false;
		}
	}

	// Handle incoming messages
	@Override
	public boolean handleIncomingMessage(ResourceNameEnum resourceName, String msg)
	{
		if (resourceName != null && msg != null) {
			try {
				// Check if the resourceName matches the expected value
				if (resourceName == ResourceNameEnum.CDA_ACTUATOR_CMD_RESOURCE) {
					// Log information about handling the incoming ActuatorData message
					_Logger.info("Handling incoming ActuatorData message: " + msg);

					// Convert JSON message to ActuatorData instance and back to JSON
					// This serves as a validation scheme to ensure the data is an 'ActuatorData' instance
					ActuatorData ad = DataUtil.getInstance().jsonToActuatorData(msg);
					String jsonData = DataUtil.getInstance().actuatorDataToJson(ad);

					// Check if the MQTT client is available
					if (this.mqttClient != null) {
						// Publish data to the MQTT broker
						_Logger.fine("Publishing data to MQTT broker: " + jsonData);
						return this.mqttClient.publishMessage(resourceName, jsonData, 0);
					}

					// If the GDA is hosting a CoAP server, add the logic here in place or in addition to the MQTT client
					if (this.coapServer != null) {
						_Logger.info("CDA will get this actuator message because of Observer: " + jsonData);

					}

				} else {
					// Log warning for unknown message type
					_Logger.warning("Failed to parse incoming message. Unknown type: " + msg);

					return false;
				}
			} catch (Exception e) {
				// Log warning for failed message processing
				_Logger.log(Level.WARNING, "Failed to process incoming message for resource: " + resourceName, e);
			}
		} else {
			// Log warning for incoming message with no data
			_Logger.warning("Incoming message has no data. Ignoring for resource: " + resourceName);
		}

		return false;
	}

	// Handle sensor messages
	@Override
	public boolean handleSensorMessage(ResourceNameEnum resourceName, SensorData data)
	{
		// Check if data is not null
		if (data != null) {
			// Log information about handling the sensor message
			_Logger.info("Handling sensor message: " + data.getName());

			// Check if there's an error in the SensorData instance
			if (data.hasError()) {
				_Logger.warning("Error flag set for SensorData instance.");
			}

			// Convert SensorData instance to JSON
			String jsonData = DataUtil.getInstance().sensorDataToJson(data);
	        _Logger.info("JSON [SensorData] -> " + jsonData);
	        if (this.cloudClient != null) {
	            // TODO: handle any failures
	            if (this.cloudClient.sendEdgeDataToCloud(resourceName, data)) {
	                _Logger.fine("Sent SensorData upstream to CSP.");
	            }
	        }

	        return true;
	    } else {
	        return false;
	    }
	}

	// Handle upstream transmission of data
	private void handleUpstreamTransmission(ResourceNameEnum resourceName, String jsonData, int qos) {
		// Check if MQTT client is enabled
		if (this.enableMqttClient) {
			// Publish the message and log the result
			if (this.mqttClient.publishMessage(resourceName, jsonData, qos)) {
				_Logger.info("Published incoming data to resource (MQTT):" + resourceName);
			} else {
				_Logger.info("Failed to publish incoming data to resource (MQTT):" + resourceName);
			}
		}
	}

	// Handle system performance messages
	@Override
	public boolean handleSystemPerformanceMessage(ResourceNameEnum resourceName, SystemPerformanceData data)
	{
		// Check if data is not null
		if (data != null) {
			// Log information about handling the system performance message
			_Logger.info("Handling system performance message: " + data.getName());

			// Check if there's an error in the SystemPerformanceData instance
			if (data.hasError()) {
				_Logger.warning("Error flag set for SystemPerformanceData instance.");
			}

			// Convert SystemPerformanceData instance to JSON
			String jsonData = DataUtil.getInstance().systemPerformanceDataToJson(data);

			// Log JSON data of the SystemPerformanceData instance
			_Logger.info("JSON [SystemPerformance Data] -> " + jsonData);

			// Check if the cloud client is available
			if (this.cloudClient != null) {
				// Send data to the cloud and log the result
				if (this.cloudClient.sendEdgeDataToCloud(resourceName, data)) {
					_Logger.fine("Sent SystemPerformanceData upstream to CSP.");

				}
			}

			return true;
		} else {
			return false;
		}
	}

	// Handle incoming data analysis
	private void handleIncomingDataAnalysis(ResourceNameEnum resource, SensorData data)
	{
		// Check either resource or SensorData for type
		if (data.getTypeID() == ConfigConst.HUMIDITY_SENSOR_TYPE) {
			// Handle humidity sensor analysis
			handleHumiditySensorAnalysis(resource, data);
		}
	}

	// Handle humidity sensor analysis
	private void handleHumiditySensorAnalysis(ResourceNameEnum resource, SensorData data)
	{
		// Log humidity data analysis information
		_Logger.fine("Analyzing humidity data from CDA: " + data.getLocationID() + ". Value: " + data.getValue());

		// Determine if humidity is too low or too high
		boolean isLow  = data.getValue() < this.triggerHumidiferFloor;
		boolean isHigh = data.getValue() > this.triggerHumidifierCeiling;

		// Check if humidity is out of the normal range
		if (isLow || isHigh) {
			_Logger.fine("Humidity data from CDA exceeds nominal range.");

			// Handle the case when there's no previous humidity sensor data
			if (this.latestHumiditySensorData == null) {
				// Set properties and exit - nothing more to do until the next sample
				this.latestHumiditySensorData = data;
				this.latestHumiditySensorTimeStamp = getDateTimeFromData(data);

				_Logger.fine(
						"Starting humidity nominal exception timer. Waiting for seconds: " +
								this.humidityMaxTimePastThreshold);

				return;
			} else {
				// Check if humidity is out of range for too long
				OffsetDateTime curHumiditySensorTimeStamp = getDateTimeFromData(data);

				long diffSeconds =


						ChronoUnit.SECONDS.between(
	                    this.latestHumiditySensorTimeStamp, curHumiditySensorTimeStamp);
	            
	            _Logger.fine("Checking Humidity value exception time delta: " + diffSeconds);
	            
	            if (diffSeconds >= this.humidityMaxTimePastThreshold) {
	                ActuatorData ad = new ActuatorData();
	                ad.setName(ConfigConst.HUMIDIFIER_ACTUATOR_NAME);
	                ad.setLocationID(data.getLocationID());
	                ad.setTypeID(ConfigConst.HUMIDIFIER_ACTUATOR_TYPE);
	                ad.setValue(this.nominalHumiditySetting);
	                
	                if (isLow) {
	                    ad.setCommand(ConfigConst.ON_COMMAND);
	                } else if (isHigh) {
	                    ad.setCommand(ConfigConst.OFF_COMMAND);
	                }
	                
	                _Logger.info(
	                    "Humidity exceptional value reached. Sending actuation event to CDA: " +
	                    ad);
	                
	                this.lastKnownHumidifierCommand = ad.getCommand();
	                sendActuatorCommandtoCda(ResourceNameEnum.CDA_ACTUATOR_CMD_RESOURCE, ad);
	                
	                // set ActuatorData and reset SensorData (and timestamp)
	                this.latestHumidiferActuatorData = ad;
	                this.latestHumiditySensorData = null;
	                this.latestHumiditySensorTimeStamp = null;
	            }
	        }
	    } else if (this.lastKnownHumidifierCommand == ConfigConst.ON_COMMAND) {
	        // check if we need to turn off the humidifer
	        if (this.latestHumidiferActuatorData != null) {
	            // check the value - if the humidifier is on, but not yet at nominal, keep it on
	            if (this.latestHumidiferActuatorData.getValue() >= this.nominalHumiditySetting) {
	                this.latestHumidiferActuatorData.setCommand(ConfigConst.OFF_COMMAND);
	                
	                _Logger.info(
	                    "Humidity nominal value reached. Sending OFF actuation event to CDA: " +
	                    this.latestHumidiferActuatorData);
	                
	                sendActuatorCommandtoCda(
	                    ResourceNameEnum.CDA_ACTUATOR_CMD_RESOURCE, this.latestHumidiferActuatorData);
	                
	                // reset ActuatorData and SensorData (and timestamp)
	                this.lastKnownHumidifierCommand = this.latestHumidiferActuatorData.getCommand();
	                this.latestHumidiferActuatorData = null;
	                this.latestHumiditySensorData = null;
	                this.latestHumiditySensorTimeStamp = null;
	            } else {
	                _Logger.fine("Humidifier is still on. Not yet at nominal levels (OK).");
	            }
	        } else {
	            // shouldn't happen, unless some other logic
	            // nullifies the class-scoped ActuatorData instance
	            _Logger.warning(
	                "ERROR: ActuatorData for humidifier is null (shouldn't be). Can't send command.");
	        }
	    }
	}

	// Send actuator command to the CDA
	private void sendActuatorCommandtoCda(ResourceNameEnum resource, ActuatorData data)
	{
		// Notify the actuator data listener
		if (this.actuatorDataListener != null) {
			this.actuatorDataListener.onActuatorDataUpdate(data);
		}

		// Publish the actuator command using MQTT, if enabled and client is available
		if (this.enableMqttClient && this.mqttClient != null) {
			// Convert actuator data to JSON
			String jsonData = DataUtil.getInstance().actuatorDataToJson(data);

			// Publish the actuator command and log the result
			if (this.mqttClient.publishMessage(resource, jsonData, ConfigConst.DEFAULT_QOS)) {
				_Logger.info(
						"Published ActuatorData humidifier command from GDA to CDA: " + data.getCommand());
			} else {
				_Logger.warning(
						"Failed to publish ActuatorData humidifier command from GDA to CDA: " + data.getCommand());
			}
		}

		// Publish the actuator command using CoAP, if enabled and server is available
		if (this.enableCoapServer && this.coapServer != null) {
			// Convert actuator data to JSON
			String jsonData = DataUtil.getInstance().actuatorDataToJson(data);

			// Log the message, waiting for the CDA CoAP client to get the data
			_Logger.info("Wait CDA CoAP Client GET: " + jsonData);
		}
	}

	// Get the OffsetDateTime from BaseIotData
	private OffsetDateTime getDateTimeFromData(BaseIotData data)
	{
		OffsetDateTime odt = null;

		// Attempt to parse the timestamp from the data
		try {
			odt = OffsetDateTime.parse(data.getTimeStamp());
		} catch (Exception e) {
			// Log a warning if timestamp extraction fails
			_Logger.warning(
					"Failed to extract ISO 8601 timestamp from IoT data. Using local current time.");

			// Use the current time as a fallback, even though it might not be accurate
			odt = OffsetDateTime.now();
		}

		return odt;
	}
	// Set an actuator data listener for the manager
	public void setActuatorDataListener(String name, IActuatorDataListener listener)
	{
		if (listener != null) {
			this.actuatorDataListener = listener;
		}
	}

	// Start the manager and its associated components
	public void startManager()
	{
		// Start the SystemPerformanceManager
		if (this.sysPerfMgr != null) {
			this.sysPerfMgr.startManager();
		}

		// Connect the MQTT client to the broker, if available
		if (this.mqttClient != null) {
			if (this.mqttClient.connectClient()) {
				_Logger.info("Successfully connected MQTT client to broker.");

				// Read QoS from the configuration file
				int qos = ConfigConst.DEFAULT_QOS;

				// Subscribe to topics and check the return value for each (not implemented in this code snippet)

			} else {
				_Logger.severe("Failed to connect MQTT client to broker.");

				// Take appropriate action (not implemented in this code snippet)
			}
		}

		// Start the CoAP server, if enabled and available
		if (this.enableCoapServer && this.coapServer != null) {
			if (this.coapServer.startServer()) {
				_Logger.info("CoAP server start");
			} else {
				_Logger.severe("Failed to start CoAP server. Check log file for details.");
			}
		}

		// Connect the cloud client, if enabled and available
		if (this.enableCloudClient && this.cloudClient != null) {
			if (this.cloudClient.connectClient()) {
				_Logger.info("Cloud Client Connected");
			} else {
				_Logger.severe("Failed to Connect Cloud Client. Check log file for details.");
			}
		}
	}

	// Stop the manager and its associated components
	public void stopManager()
	{
		// Stop the SystemPerformanceManager
		if (this.sysPerfMgr != null) {
			this.sysPerfMgr.stopManager();
		}

		// Disconnect the MQTT client from the broker, if available
		if (this.mqttClient != null) {
			// Unsubscribe from topics (check the return value for each, not implemented in this code snippet)
			this.mqttClient.unsubscribeFromTopic(ResourceNameEnum.GDA_MGMT_STATUS_MSG_RESOURCE);
			this.mqttClient.unsubscribeFromTopic(ResourceNameEnum.CDA_ACTUATOR_RESPONSE_RESOURCE);
			this.mqttClient.unsubscribeFromTopic(ResourceNameEnum.CDA_SENSOR_MSG_RESOURCE);
			this.mqttClient.unsubscribeFromTopic(ResourceNameEnum.CDA_SYSTEM_PERF_MSG_RESOURCE);
			// Disconnect the MQTT client
			if (this.mqttClient.disconnectClient()) {
				_Logger.info("Successfully disconnected MQTT client from broker.");
			} else {
				_Logger.severe("Failed to disconnect MQTT client from broker.");

				// Take appropriate action (not implemented in this code snippet)
			}
		}

		// Stop the CoAP server, if enabled and available
		if (this.enableCoapServer && this.coapServer != null) {
			if (this.coapServer.stopServer()) {
				_Logger.info("CoAP server stopped.");
			} else {
				_Logger.severe("Failed to stop CoAP server. Check log file for details.");
			}
		}

		// Disconnect the cloud client, if enabled and available
		if (this.enableCloudClient && this.cloudClient != null) {
			if (this.cloudClient.disconnectClient()) {
				_Logger.info("Cloud Client Disconnected.");
			} else {
				_Logger.severe("Failed to disconnect Cloud Client. Check log file for details.");
			}
		}
	}

	
	// private methods
	
	/**
	 * Initializes the enabled connections. This will NOT start them, but only create the
	 * instances that will be used in the {@link #startManager() and #stopManager()) methods.
	 * 
	 */
	private void initConnections()
	{
	}

	private void initManager()
	{
		ConfigUtil configUtil = ConfigUtil.getInstance();
		
		this.enableSystemPerf =
			configUtil.getBoolean(ConfigConst.GATEWAY_DEVICE,  ConfigConst.ENABLE_SYSTEM_PERF_KEY);
		
		if (this.enableSystemPerf) {
			this.sysPerfMgr = new SystemPerformanceManager();
			this.sysPerfMgr.setDataMessageListener(this);
		}
		
		if (this.enableMqttClient) {
			// TODO: implement this in Lab Module 7
		    this.mqttClient = new MqttClientConnector();
	        
	        // NOTE: The next line isn't technically needed until Lab Module 10
	        this.mqttClient.setDataMessageListener(this);
		}
		
		if (this.enableCoapServer) {
			// TODO: implement this in Lab Module 8
		    this.coapServer = new CoapServerGateway(this);
		}
		
		if (this.enableCoapClient) {
		    this.coapClient = new CoapClientConnector();
		}
		
		if (this.enableCloudClient) {
			// TODO: implement this in Lab Module 10
		    this.cloudClient = new CloudClientConnector();
		}
		
		if (this.enablePersistenceClient) {
			// TODO: implement this as an optional exercise in Lab Module 5
		}
	}
	
}
