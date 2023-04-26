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
public class SystemPerformanceData extends BaseIotData implements Serializable
{
	// static
	
	
	// private var's
	private float cpuUtil  = ConfigConst.DEFAULT_VAL;
	private float diskUtil = ConfigConst.DEFAULT_VAL;
	private float memUtil  = ConfigConst.DEFAULT_VAL;
    
	// constructors
	
	public SystemPerformanceData()
	{
		super();
		super.setName(ConfigConst.SYS_PERF_DATA);
	}
	
	
	// public methods
	public float getCpuUtil() {
		return cpuUtil;
	}


	public void setCpuUtil(float cpuUtil) {
		this.cpuUtil = cpuUtil;
	}


	public float getDiskUtil() {
		return diskUtil;
	}


	public void setDiskUtil(float diskUtil) {
		this.diskUtil = diskUtil;
	}


	public float getMemUtil() {
		return memUtil;
	}


	public void setMemUtil(float memUtil) {
		this.memUtil = memUtil;
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
		sb.append(ConfigConst.CPU_UTIL_PROP).append('=').append(this.getCpuUtil()).append(',');
		sb.append(ConfigConst.DISK_UTIL_PROP).append('=').append(this.getDiskUtil()).append(',');
		sb.append(ConfigConst.MEM_UTIL_PROP).append('=').append(this.getMemUtil());
		
		return sb.toString();
	}
	
	
	// protected methods
	


	/* (non-Javadoc)
	 * @see programmingtheiot.data.BaseIotData#handleUpdateData(programmingtheiot.data.BaseIotData)
	 */
	protected void handleUpdateData(BaseIotData data)
	{
		if (data instanceof SystemPerformanceData) {
			SystemPerformanceData spd = (SystemPerformanceData)data;
			this.setCpuUtil(spd.getCpuUtil());
			this.setMemUtil(spd.getMemUtil());
			this.setDiskUtil(spd.getDiskUtil());
		}
	}
	
}
