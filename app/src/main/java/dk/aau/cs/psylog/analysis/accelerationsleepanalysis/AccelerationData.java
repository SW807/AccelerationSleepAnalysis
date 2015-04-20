package dk.aau.cs.psylog.analysis.accelerationsleepanalysis;

/**
 * Created by Praetorian on 24-03-2015.
 */
public class AccelerationData {
    public float accX;
    public float accY;
    public float accZ;
    public String time;

    public AccelerationData(float accX, float accY, float accZ, String time) {
        this.accX = accX;
        this.accY = accY;
        this.accZ = accZ;
        this.time = time;
    }
}
