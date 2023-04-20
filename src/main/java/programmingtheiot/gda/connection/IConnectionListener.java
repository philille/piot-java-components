package programmingtheiot.gda.connection;

import programmingtheiot.common.ResourceNameEnum;
import programmingtheiot.data.ActuatorData;

public interface IConnectionListener {

    public void onConnect();
    
    public void onDisconnect();

	boolean createCloudResource(ResourceNameEnum resource, ActuatorData data);
}