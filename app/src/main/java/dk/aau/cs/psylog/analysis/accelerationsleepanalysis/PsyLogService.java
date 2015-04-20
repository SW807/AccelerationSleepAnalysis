package dk.aau.cs.psylog.analysis.accelerationsleepanalysis;
import dk.aau.cs.psylog.module_lib.ScheduledService;

public class PsyLogService extends ScheduledService{

    public PsyLogService(){
        super("AccelerationSleepAnalysis");
    }
    @Override
    public void setScheduledTask() {
        this.scheduledTask = new AccelerationSleepAnalysis(this);
    }
}
