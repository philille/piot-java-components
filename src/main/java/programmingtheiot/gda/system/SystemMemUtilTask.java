/**
 * This class is part of the Programming the Internet of Things project.
 * 
 * It is provided as a simple shell to guide the student and assist with
 * implementation for the Programming the Internet of Things exercises,
 * and designed to be modified by the student as needed.
 */ 

package programmingtheiot.gda.system;

import java.lang.management.ManagementFactory;

import java.lang.management.MemoryUsage;

import programmingtheiot.common.ConfigConst;

import java.util.logging.Logger;

/**
 * Shell representation of class for student implementation.
 * 
 */
public class SystemMemUtilTask extends BaseSystemUtilTask
{
	// constructors
	private static final Logger _Logger =
			Logger.getLogger(SystemMemUtilTask.class.getName());
	
	/**
	 * Default.
	 * 
	 */
	public SystemMemUtilTask()
	{
		super(ConfigConst.NOT_SET, ConfigConst.DEFAULT_TYPE_ID);
	}

	@Override
	public float getTelemetryValue()
	{
		MemoryUsage memUsage = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
		double memUsed = (double) memUsage.getUsed();
		double memMax  = (double) memUsage.getMax();
		
		_Logger.fine("Mem used: " + memUsed + "; Mem Max: " + memMax);
		
		double memUtil = (memUsed / memMax) * 100.0d;
		
		return (float) memUtil;
	}
	
}
