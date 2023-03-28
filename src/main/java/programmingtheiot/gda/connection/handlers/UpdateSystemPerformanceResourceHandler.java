package programmingtheiot.gda.connection.handlers;

import java.util.logging.Logger;

import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.server.resources.CoapExchange;

import programmingtheiot.common.IDataMessageListener;
import programmingtheiot.common.ResourceNameEnum;
import programmingtheiot.data.DataUtil;
import programmingtheiot.data.SystemPerformanceData;
import programmingtheiot.data.ActuatorData;   

import java.util.logging.Logger;

import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.server.resources.CoapExchange;

import programmingtheiot.common.ConfigConst;
import programmingtheiot.common.ConfigUtil;
import programmingtheiot.common.IDataMessageListener;
import programmingtheiot.common.ResourceNameEnum;

public class UpdateSystemPerformanceResourceHandler extends CoapResource
{
	private IDataMessageListener dataMsgListener = null;
	private static final Logger _Logger =
            Logger.getLogger(UpdateSystemPerformanceResourceHandler.class.getName());


	//Constructor that takes the resource name as the parameter and passes it to the super-class.
	public UpdateSystemPerformanceResourceHandler(String resourceName)
	{
		super(resourceName);
	}
	
	//Method to handle these callbacks
	public void setDataMessageListener(IDataMessageListener listener)
	{
		if (listener != null) {
			this.dataMsgListener = listener;
		}
	}
	
	//Implement PUT method
	@Override
	public void handlePUT(CoapExchange context)
	{
		ResponseCode code = ResponseCode.NOT_ACCEPTABLE;
		
		context.accept();
		
		if (this.dataMsgListener != null) {
			try {
				String jsonData = new String(context.getRequestPayload());
				
				SystemPerformanceData sysPerfData =
					DataUtil.getInstance().jsonToSystemPerformanceData(jsonData); 
				
				this.dataMsgListener.handleSystemPerformanceMessage(
					ResourceNameEnum.CDA_SYSTEM_PERF_MSG_RESOURCE, sysPerfData);
				
				code = ResponseCode.CHANGED;
			} catch (Exception e) {
				_Logger.warning(
					"Failed to handle PUT request. Message: " +
						e.getMessage());
				
				code = ResponseCode.BAD_REQUEST;
			}
		} else {
			_Logger.info(
				"No callback listener for request. Ignoring PUT.");
			
			code = ResponseCode.CONTINUE;
		}
		
		String msg =
			"Update system perf data request handled: " + super.getName();
		
		context.respond(code, msg);
	}
	//Implement POST method
	public void handlePOST(CoapExchange context) {
        // TODO Auto-generated method stub
        ResponseCode code = ResponseCode.NOT_ACCEPTABLE;
        
        context.accept();
        
        if (this.dataMsgListener != null) {
            try {
                String jsonData = new String(context.getRequestPayload());
                SystemPerformanceData sysPerfData = DataUtil.getInstance().jsonToSystemPerformanceData(jsonData);
                this.dataMsgListener.handleSystemPerformanceMessage(
                		ResourceNameEnum.CDA_SENSOR_MSG_RESOURCE, sysPerfData);
                code = ResponseCode.CREATED;
            }catch (Exception e) {
                _Logger.warning(
                        "Failed to handle POST request. Message: " +
                            e.getMessage()); 
                code = ResponseCode.BAD_REQUEST;
            }
        }else {
            _Logger.info(
                    "No callback listener for request. Ignoring POST."); 
                code = ResponseCode.CONTINUE;
        }
        String msg =
                "Update telemetry data request handled: " + super.getName(); 
        context.respond(code, msg);
    }
	
	//Implement GET method
    @Override
    public void handleGET(CoapExchange exchange) {
        ResponseCode code = ResponseCode.CONTENT;
        exchange.accept();
        
        SystemPerformanceData spd = new SystemPerformanceData();
        String spdStr = DataUtil.getInstance().systemPerformanceDataToJson(spd);
        
        _Logger.info("Got a request from the client.");
        
        exchange.respond(code, spdStr);
    }
    
	//Implement DELETE method    
	public void handleDELETE(CoapExchange exchange) {
        // TODO Auto-generated method stub
        super.handleDELETE(exchange);
    }



}