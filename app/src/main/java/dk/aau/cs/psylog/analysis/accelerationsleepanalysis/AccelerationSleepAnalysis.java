package dk.aau.cs.psylog.analysis.accelerationsleepanalysis;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;
import android.util.Pair;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import dk.aau.cs.psylog.module_lib.DBAccessContract;
import dk.aau.cs.psylog.module_lib.IScheduledTask;

public class AccelerationSleepAnalysis implements IScheduledTask {
    ContentResolver contentResolver;
    Uri accelerationSleepStateAnalysisUri = Uri.parse(DBAccessContract.DBACCESS_CONTENTPROVIDER + "ACCELERATIONSLEEPANALYSIS_state");
    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

    public AccelerationSleepAnalysis(Context context) {
        this.contentResolver = context.getContentResolver();
    }

    private Date loadTimeString() throws Exception {
        Uri uri = accelerationSleepStateAnalysisUri;
        Cursor cursor = contentResolver.query(uri, new String[]{"timeAcc"}, null, null, null);
        if (cursor.moveToFirst()) {
            String res = cursor.getString(cursor.getColumnIndex("timeAcc"));
            if (res == null || res == "") {
                throw new Exception("No Time located");
            }
            cursor.close();
            return convertTimeString(res);
        } else {
            throw new Exception("No Time located");
        }
    }

    private List<AccelerationData> loadData() {
        Uri uri = Uri.parse(DBAccessContract.DBACCESS_CONTENTPROVIDER + "accelerometer_accelerations");
        Cursor cursor = contentResolver.query(uri, new String[]{"_id", "accX", "accY", "accZ", "time"}, null, null, null);
        List<AccelerationData> returnList = new ArrayList<>();
        int lastPosition = getLastPosition();
        if ((lastPosition > 5 && cursor.moveToPosition(lastPosition - 5)) || cursor.moveToFirst()) {
            do {
                float accX = cursor.getFloat(cursor.getColumnIndex("accX"));
                float accY = cursor.getFloat(cursor.getColumnIndex("accY"));
                float accZ = cursor.getFloat(cursor.getColumnIndex("accZ"));
                String time = cursor.getString(cursor.getColumnIndex("time"));
                lastPos = cursor.getInt(cursor.getColumnIndex("_id"));
                returnList.add(new AccelerationData(accX, accY, accZ, time));
            } while (cursor.moveToNext());
            cursor.close();
        }
        return returnList;

    }

    private float probabilityFunc(float t) {
        float k = 1.0f;
        float res = (float) (1.0 / (1.0 + Math.exp(-k * (t - 4.0))));
        if (res > 1.0f)
            return 1.0f;
        return res;
    }

    public void Analyse() {

        Queue<AccelerationData> previousDataQueue = new LinkedList<>();
        List<AccelerationData> data = loadData();
        List<Pair<String, Float>> resultMap = new ArrayList<>();
        if (data.size() > 5) {
            for (int i = 0; i < 5; i++)
                previousDataQueue.add(data.get(i));
            data = makeMovingAverage(data.subList(5, data.size() - 1));
        } else {
            return;
        }

        Date oldTime;
        try {
            oldTime = loadTimeString();
        } catch (Exception e) {
            try {
                oldTime = convertTimeString(data.get(0).time);
            } catch (NullPointerException ne) {
                return;
            }
        }

        float probabilitySleeping = 0.0f;
        for (AccelerationData acc : data) {
            Date newTime = convertTimeString(acc.time);
            if (isStationary(acc, previousDataQueue)) {
                float timeElapsed = (float) ((newTime.getTime() - oldTime.getTime()) / (60.0 * 60.0 * 1000.0));
                probabilitySleeping = probabilityFunc(timeElapsed);
            } else {
                probabilitySleeping = 0.0f;
                oldTime = newTime;
            }
            resultMap.add(new Pair<>(acc.time, probabilitySleeping));

            previousDataQueue.remove();
            previousDataQueue.add(acc);
        }
        updatePosition(oldTime, probabilitySleeping);

        writeToDB(resultMap);
    }

    private void writeToDB(List<Pair<String, Float>> pairs) {
        Uri accelerationSleepStateAnalysisUri = Uri.parse(DBAccessContract.DBACCESS_CONTENTPROVIDER + "ACCELERATIONSLEEPANALYSIS_sleepcalc");
        for (Pair<String, Float> pair : pairs) {
            ContentValues values = new ContentValues();
            values.put("prob", pair.second);
            values.put("time", pair.first);
            contentResolver.insert(accelerationSleepStateAnalysisUri, values);
        }
    }

    private Date convertTimeString(String s) {

        Date convertedTime = new Date();
        try {
            convertedTime = dateFormat.parse(s);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return convertedTime;
    }

    private boolean isStationary(AccelerationData accelerationData, Queue<AccelerationData> previousDataQueue) {
        for (AccelerationData toConsider : previousDataQueue) {
            if (outOfThreshHold(accelerationData, toConsider))
                return false;
        }
        return true;
    }

    private boolean outOfThreshHold(AccelerationData acc1, AccelerationData acc2) {
        float threshold = 0.5f;
        return Math.abs(acc1.accX - acc2.accX) > threshold ||
                Math.abs(acc1.accY - acc2.accY) > threshold ||
                Math.abs(acc1.accZ - acc2.accZ) > threshold;
    }

    private List<AccelerationData> makeMovingAverage(List<AccelerationData> data) {
        if (data.size() == 0)
            return null;
        AccelerationData MAOld = data.get(0);
        float alpha = 0.1f;
        for (AccelerationData MAnew : data) {
            MAnew.accX = alpha * MAnew.accX + (1 - alpha) * MAOld.accX;
            MAnew.accY = alpha * MAnew.accY + (1 - alpha) * MAOld.accY;
            MAnew.accZ = alpha * MAnew.accZ + (1 - alpha) * MAOld.accZ;
            MAOld = MAnew;
        }
        return data;
    }

    private int lastPos = -1;

    private int getLastPosition() {
        Uri uri = accelerationSleepStateAnalysisUri;
        Cursor cursor = contentResolver.query(uri, new String[]{"positionAcc"}, null, null, null);
        if (cursor.moveToFirst()) {
            int res = cursor.getInt(cursor.getColumnIndex("positionAcc"));
            cursor.close();
            return res;
        } else {
            cursor.close();
            return 0;
        }
    }

    private void updatePosition(Date oldTime, float lastProb) {
        Uri uri = accelerationSleepStateAnalysisUri;
        ContentValues values = new ContentValues();
        values.put("positionAcc", lastPos);

        values.put("timeAcc", dateFormat.format(oldTime));
        values.put("probAcc", lastProb);
        Cursor cursor = contentResolver.query(uri, new String[]{"_id", "positionAcc", "timeAcc"}, null, null, null);
        if (cursor.getCount() > 0) {
            contentResolver.update(uri, values, "1=1", null);
        } else {
            contentResolver.insert(uri, values);
        }
        cursor.close();
    }

    @Override
    public void doTask() {
        Log.i("AccSleepAnalysis", "analysen startet");
        this.Analyse();
        Log.i("AccSleepAnalysis", "analysen færdig");
    }

    @Override
    public void setParameters(Intent i) {

    }
}

