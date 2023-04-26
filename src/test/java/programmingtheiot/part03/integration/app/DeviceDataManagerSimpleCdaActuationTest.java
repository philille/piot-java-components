package programmingtheiot.part03.integration.app;

import org.junit.Test;

import programmingtheiot.common.ConfigConst;
import programmingtheiot.common.ConfigUtil;
import programmingtheiot.common.ResourceNameEnum;
import programmingtheiot.data.SensorData;
import programmingtheiot.gda.app.DeviceDataManager;

public class DeviceDataManagerSimpleCdaActuationTest {
    /**
     * Test method for running the DeviceDataManager.
     * 
     */
    @Test
    public void testSendActuationEventsToCda()
    {
        DeviceDataManager devDataMgr = new DeviceDataManager();
        
        ConfigUtil cfgUtil = ConfigUtil.getInstance();
        
        // TODO: add these to ConfigConst
        float nominalVal = cfgUtil.getFloat(ConfigConst.GATEWAY_DEVICE,   "nominalHumiditySetting");
        float lowVal     = cfgUtil.getFloat(ConfigConst.GATEWAY_DEVICE,   "triggerHumidiferFloor");
        float highVal    = cfgUtil.getFloat(ConfigConst.GATEWAY_DEVICE,   "triggerHumidifierCeiling");
        int   delay      = cfgUtil.getInteger(ConfigConst.GATEWAY_DEVICE, "humidityMaxTimePastThreshold");
        
        // Test Sequence No. 1
        generateAndProcessHumiditySensorDataSequence(
            devDataMgr, nominalVal, lowVal, highVal, delay);
        
        // TODO: Add more test sequences if desired.
    }

    private void generateAndProcessHumiditySensorDataSequence(
        DeviceDataManager ddm, float nominalVal, float lowVal, float highVal, int delay)
    {
        SensorData sd = new SensorData();
        sd.setName("My Test Humidity Sensor");
        sd.setLocationID("constraineddevice001");
        sd.setTypeID(ConfigConst.HUMIDITY_SENSOR_TYPE);
        
        sd.setValue(nominalVal);
        ddm.handleSensorMessage(ResourceNameEnum.CDA_SENSOR_MSG_RESOURCE, sd);
        waitForSeconds(2);
        
        sd.setValue(nominalVal);
        ddm.handleSensorMessage(ResourceNameEnum.CDA_SENSOR_MSG_RESOURCE, sd);
        waitForSeconds(2);
        
        sd.setValue(lowVal - 2);
        ddm.handleSensorMessage(ResourceNameEnum.CDA_SENSOR_MSG_RESOURCE, sd);
        waitForSeconds(delay + 1);
        
        sd.setValue(lowVal - 1);
        ddm.handleSensorMessage(ResourceNameEnum.CDA_SENSOR_MSG_RESOURCE, sd);
        waitForSeconds(delay + 1);
        
        sd.setValue(lowVal + 1);
        ddm.handleSensorMessage(ResourceNameEnum.CDA_SENSOR_MSG_RESOURCE, sd);
        waitForSeconds(delay + 1);
        
        sd.setValue(nominalVal);
        ddm.handleSensorMessage(ResourceNameEnum.CDA_SENSOR_MSG_RESOURCE, sd);
        waitForSeconds(delay + 1);
    }

    private void waitForSeconds(int seconds)
    {
        try {
            Thread.sleep(seconds * 1000);
        } catch (InterruptedException e) {
            // ignore
        }
    }
}
