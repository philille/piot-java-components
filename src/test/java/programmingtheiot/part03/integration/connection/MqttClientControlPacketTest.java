/**
 * 
 * This class is part of the Programming the Internet of Things
 * project, and is available via the MIT License, which can be
 * found in the LICENSE file at the top level of this repository.
 * 
 * Copyright (c) 2020 by Andrew D. King
 */ 

package programmingtheiot.part03.integration.connection;

import static org.junit.Assert.*;

import java.util.logging.Logger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import programmingtheiot.common.ConfigConst;
import programmingtheiot.common.ConfigUtil;
import programmingtheiot.common.IDataMessageListener;
import programmingtheiot.common.ResourceNameEnum;
import programmingtheiot.data.*;
import programmingtheiot.gda.connection.*;

/**
 * This test case class contains very basic integration tests for
 * MqttClientControlPacketTest. It should not be considered complete,
 * but serve as a starting point for the student implementing
 * additional functionality within their Programming the IoT
 * environment.
 *
 */
public class MqttClientControlPacketTest
{
	// static
	
	private static final Logger _Logger =
		Logger.getLogger(MqttClientControlPacketTest.class.getName());
	
	
	// member var's
	
	private MqttClientConnector mqttClient = null;
	
	
	// test setup methods
	
	@Before
	public void setUp() throws Exception
	{
		this.mqttClient = new MqttClientConnector();
	}
	
	@After
	public void tearDown() throws Exception
	{
	}
	
	// test methods
	
//	@Test
	public void testConnectAndDisconnect()
	{
		// TODO: implement this test
	    
	    assertTrue(this.mqttClient.connectClient());
	    assertFalse(this.mqttClient.connectClient());
	    
	    assertTrue(this.mqttClient.disconnectClient());
	    assertFalse(this.mqttClient.disconnectClient());
	}
	
	@Test
	public void testServerPing()
	{
		// TODO: implement this test
	    int keepAlive = ConfigUtil.getInstance().getInteger(ConfigConst.MQTT_GATEWAY_SERVICE, ConfigConst.KEEP_ALIVE_KEY, ConfigConst.DEFAULT_KEEP_ALIVE);
        
        assertTrue(this.mqttClient.connectClient());
        assertFalse(this.mqttClient.connectClient());
        
        try {
            Thread.sleep(keepAlive * 1000 + 5000);
        } catch (Exception e) {
            // ignore
        }
        
        assertTrue(this.mqttClient.disconnectClient());
        assertFalse(this.mqttClient.disconnectClient());
	}
	
//	@Test
	public void testPubSub()
	{
		// TODO: implement this test
		// 
		// IMPORTANT: be sure to use QoS 1 and 2 to see ALL control packets
	    int qos;
        int delay = ConfigUtil.getInstance().getInteger(ConfigConst.MQTT_GATEWAY_SERVICE, ConfigConst.KEEP_ALIVE_KEY, ConfigConst.DEFAULT_KEEP_ALIVE);
        
        assertTrue(this.mqttClient.connectClient());
        for (qos = 1; qos <= 2; qos++) {
            _Logger.info("This is MQTT under QoS:\t" + qos);
            assertTrue(this.mqttClient.subscribeToTopic(ResourceNameEnum.GDA_MGMT_STATUS_MSG_RESOURCE, qos));
            assertTrue(this.mqttClient.subscribeToTopic(ResourceNameEnum.CDA_ACTUATOR_RESPONSE_RESOURCE, qos));
            assertTrue(this.mqttClient.subscribeToTopic(ResourceNameEnum.CDA_SENSOR_MSG_RESOURCE, qos));
            assertTrue(this.mqttClient.subscribeToTopic(ResourceNameEnum.CDA_SYSTEM_PERF_MSG_RESOURCE, qos));
            
            try {
                Thread.sleep(5000);
            } catch (Exception e) {
                // ignore
            }
            
            assertTrue(this.mqttClient.publishMessage(ResourceNameEnum.GDA_MGMT_STATUS_MSG_RESOURCE, "TEST: This is the GDA message payload 1.", qos));
            assertTrue(this.mqttClient.publishMessage(ResourceNameEnum.CDA_ACTUATOR_RESPONSE_RESOURCE, "TEST: This is the GDA message payload 2.", qos));
            assertTrue(this.mqttClient.publishMessage(ResourceNameEnum.CDA_SENSOR_MSG_RESOURCE, "TEST: This is the GDA message payload 3.", qos));
            assertTrue(this.mqttClient.publishMessage(ResourceNameEnum.CDA_SYSTEM_PERF_MSG_RESOURCE, "TEST: This is the GDA message payload 4.", qos));
            
            try {
                Thread.sleep(25000);
            } catch (Exception e) {
                // ignore
            }
            
            assertTrue(this.mqttClient.unsubscribeFromTopic(ResourceNameEnum.GDA_MGMT_STATUS_MSG_RESOURCE));
            assertTrue(this.mqttClient.unsubscribeFromTopic(ResourceNameEnum.CDA_ACTUATOR_RESPONSE_RESOURCE));
            assertTrue(this.mqttClient.unsubscribeFromTopic(ResourceNameEnum.CDA_SENSOR_MSG_RESOURCE));
            assertTrue(this.mqttClient.unsubscribeFromTopic(ResourceNameEnum.CDA_SYSTEM_PERF_MSG_RESOURCE));
    
            try {
                Thread.sleep(5000);
            } catch (Exception e) {
                // ignore
            }
    
            try {
                Thread.sleep(delay * 1000);
            } catch (Exception e) {
                // ignore
            }
        }
        assertTrue(this.mqttClient.disconnectClient());
	}
	
}
