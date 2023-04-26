/**
 * This class is part of the Programming the Internet of Things project.
 * 
 * It is provided as a simple shell to guide the student and assist with
 * implementation for the Programming the Internet of Things exercises,
 * and designed to be modified by the student as needed.
 */ 

package programmingtheiot.data;

import java.io.Serializable;

import programmingtheiot.common.ConfigConst;

/**
 * Shell representation of class for student implementation.
 *
 */
public class ActuatorData extends BaseIotData implements Serializable
{
	// static
	
	
	// private var's
	private int     command      = ConfigConst.DEFAULT_COMMAND;
	private float   value        = ConfigConst.DEFAULT_VAL;
	private boolean isResponse   = false;
	private String  stateData    = "";
    
    
	// constructors
	
	/**
	 * Default.
	 * 
	 */
	public ActuatorData()
	{
		super();
	}
	
	
	// public methods
	
	public int getCommand() {
		return command;
	}


	public void setCommand(int command) {
		this.command = command;
	}


	public float getValue() {
		return value;
	}


	public void setValue(float value) {
		this.value = value;
	}


	public boolean isResponse() {
		return isResponse;
	}


	public void setResponse(boolean isResponse) {
		this.isResponse = isResponse;
	}


	public String getStateData() {
		return stateData;
	}


	public void setStateData(String stateData) {
		this.stateData = stateData;
	}
	
	/**
	 * Returns a string representation of this instance. This will invoke the base class
	 * {@link #toString()} method, then append the output from this call.
	 * 
	 * @return String The string representing this instance, returned in CSV 'key=value' format.
	 */
	public String toString()
	{
		StringBuilder sb = new StringBuilder(super.toString());
		
		sb.append(',');
		sb.append(ConfigConst.COMMAND_PROP).append('=').append(this.getCommand()).append(',');
		sb.append(ConfigConst.IS_RESPONSE_PROP).append('=').append(this.isResponse()).append(',');
		sb.append(ConfigConst.VALUE_PROP).append('=').append(this.getValue());
		
		return sb.toString();
	}
	
	
	// protected methods



	/* (non-Javadoc)
	 * @see programmingtheiot.data.BaseIotData#handleUpdateData(programmingtheiot.data.BaseIotData)
	 */
	protected void handleUpdateData(BaseIotData data)
	{
		if (data instanceof ActuatorData) {
			ActuatorData ad = (ActuatorData) data;
			this.setCommand(ad.getCommand());
			this.setStateData(ad.getStateData());
			this.setValue(ad.getValue());
			if (ad.isResponse()) {
				this.isResponse = true;
			}
		}
	}
	
}
