package ideum.com.megamovie.Java.CameraControl;


import android.location.Location;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import ideum.com.megamovie.Java.LocationAndTiming.EclipseTimeCalculator;
import ideum.com.megamovie.Java.LocationAndTiming.LocationProvider;

public class EclipseCaptureSequenceBuilder {

    public final static String TAG = "SequenceBuilder";
    private LocationProvider mLocationProvider;
    private ConfigParser mConfig;
    private EclipseTimeCalculator mEclipseTimeCalculator;
    private static final long INTERVAL_LENGTH = 30 * 1000;
    /**
     * How long before each contact to start recording images, in milliseconds
     */
    private static final long INTERVAL_STARTING_OFFSET = 15 * 1000;

    public EclipseCaptureSequenceBuilder(LocationProvider provider, ConfigParser config, EclipseTimeCalculator eclipseTimeCalculator) {
        mLocationProvider = provider;
        mConfig = config;
        mEclipseTimeCalculator = eclipseTimeCalculator;
    }

    public CaptureSequence buildSequence() throws IOException, XmlPullParserException {
        List<CaptureSequence.IntervalProperties> iProperties = mConfig.getIntervalProperties();

        List<CaptureSequence.CaptureInterval> intervals = new ArrayList<>();

        Location currentLocation = mLocationProvider.getLocation();

        Long contact2 = null;//  mEclipseTimeCalculator.getEclipseTime(EclipseTimingMap.Event.CONTACT2);
        if (contact2 != null) {
            Long startingTime2 = contact2 - INTERVAL_STARTING_OFFSET;
            intervals.add(new CaptureSequence.CaptureInterval(iProperties.get(0), startingTime2, INTERVAL_LENGTH));
        }
        Long contact3 = null;// mEclipseTimeCalculator.getEclipseTime(EclipseTimingMap.Event.CONTACT3);
        /**
         * Make sure intervals don't overlap
         */
        if (contact2 != null) {
            contact3 = Math.max(contact3,contact2 + INTERVAL_LENGTH);
        }

        if (contact3 != null) {
            long startingTime3 = contact3 - INTERVAL_STARTING_OFFSET;
            intervals.add(new CaptureSequence.CaptureInterval(iProperties.get(1), startingTime3, INTERVAL_LENGTH));
        }

        return new CaptureSequence(intervals);
    }

    /**
     * method used for testing
     */
    public CaptureSequence buildSequenceAtTime(Long contact2) throws IOException, XmlPullParserException {
        List<CaptureSequence.IntervalProperties> iProperties = mConfig.getIntervalProperties();

        List<CaptureSequence.CaptureInterval> intervals = new ArrayList<>();

        if (contact2 != null) {
            intervals.add(new CaptureSequence.CaptureInterval(iProperties.get(0), contact2, INTERVAL_LENGTH));
        }

        return new CaptureSequence(intervals);
    }

    /**
     * Helper functions used for debugging
     */

    private String dateFromMills(Long mills) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(mills);

        DateFormat formatter = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss_SSS", Locale.US);

        return formatter.format(calendar.getTime());
    }

    private String timeString(Long mills) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(mills);

        DateFormat formatter = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss_SSS", Locale.US);

        return formatter.format(calendar.getTime());

    }

}
