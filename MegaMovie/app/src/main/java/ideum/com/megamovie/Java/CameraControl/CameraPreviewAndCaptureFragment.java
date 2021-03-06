/**
 * This activity provides a camera preview together with the ability to
 * save images in RAW or JPEG.
 */

package ideum.com.megamovie.Java.CameraControl;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.DngCreator;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.location.Location;
import android.media.ExifInterface;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v13.app.FragmentCompat;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

import ideum.com.megamovie.Java.LocationAndTiming.GPS;
import ideum.com.megamovie.Java.LocationAndTiming.LocationProvider;
import ideum.com.megamovie.Java.PatagoniaTest.MetadataWriter;
import ideum.com.megamovie.Java.Util.FTPUtil;
import ideum.com.megamovie.R;


public class CameraPreviewAndCaptureFragment extends android.app.Fragment
        implements FragmentCompat.OnRequestPermissionsResultCallback,
        ManualCamera {

    public static final boolean ALLOWS_RAW = true;
    public static final boolean ALLOWS_JPEG = true;
    public static final String TAG = "PreviewCapture";

    private static final String RAW_METADATA_FILENAME = "metadata_raw.txt";
    private static final String JPEG_METADATA_FILENAME = "metadata_jpeg.txt";
    private static final String DATA_DIRECTORY_NAME = "MegaMovie";
    private String data_directory_name = "MegaMovieTestImages";


    @Override
    public void setDirectoryName(String directoryName) {
        data_directory_name = directoryName;
    }

    private List<CameraFragment.CaptureListener> listeners = new ArrayList<>();

    private LocationProvider mLocationProvider;

    private Location getLocation() {
        if (mLocationProvider == null) {
            return null;
        }
        return mLocationProvider.getLocation();
    }

    @Override
    public void setLocationProvider(LocationProvider provider) {
        mLocationProvider = provider;
    }

    public void addCaptureListener(CameraFragment.CaptureListener listener) {
        listeners.add(listener);
    }


    public void setCameraSettings(CaptureSequence.CaptureSettings settings) {
        mSensorSensitivity = settings.sensitivity;
        mFocusDistance = settings.focusDistance;
        mDuration = settings.exposureTime;
    }

    public int mSensorSensitivity = 60;
    public float mFocusDistance = 0;
    public long mDuration = 5000000; //nanoseconds

    // Argument is in milliseconds
    public void incrementDuration(long deltaDuration) {
        mDuration += deltaDuration * 100000;
        setPreviewRequest();
    }

    public void decrementDuration(long deltaDuration) {
        if (mDuration > 0) {
            mDuration -= deltaDuration * 100000;
        }
        setPreviewRequest();
    }

    public void incrementFocusDistance(float deltaDistance) {
        mFocusDistance += deltaDistance;
        setPreviewRequest();
    }

    public void decrementFocusDistance(float deltaDistance) {
        if (mFocusDistance > 0) {
            mFocusDistance -= deltaDistance;
            setPreviewRequest();
        }
    }

    public void incrementSensitivity(int deltaSensitivity) {
        mSensorSensitivity += deltaSensitivity;
        setPreviewRequest();
    }

    public void decrementSensitivity(int deltaSensitivity) {
        if (mSensorSensitivity > 60) {
            mSensorSensitivity -= deltaSensitivity;
            setPreviewRequest();
        }
    }

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    private int getOrientation(int rotation) {
        return (ORIENTATIONS.get(rotation) + mSensorOrientation + 270) % 360;
    }

    private Size mPreviewSize;

    private String mCameraId;
    private TextureView mTextureView;
    private Surface mPreviewSurface;
    /**
     * Request code for camera permissions
     */
    private static final int REQUEST_CAMERA_PERMISSIONS = 1;

    /**
     * Permissions required to take a picture.
     */
    private static final String[] CAMERA_PERMISSIONS = {
            Manifest.permission.CAMERA,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
    };
    private TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            setupCamera(width, height);
            openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {

        }
    };

    private CameraDevice mCameraDevice;
    private CameraCharacteristics mCharacteristics;
    private final AtomicInteger mRequestCounter = new AtomicInteger();

    private CameraDevice.StateCallback mCameraDeviceStateCallback
            = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            mCameraDevice = camera;
            createSession();
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            camera.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            camera.close();
            mCameraDevice = null;
        }
    };
    private CaptureRequest mPreviewCaptureRequest;
    private CaptureRequest.Builder mPreviewCaptureRequestBuilder;
    private CameraCaptureSession mCameraCaptureSession;
    private CameraCaptureSession.CaptureCallback mPreviewSessionCallback
            = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureStarted(CameraCaptureSession session, CaptureRequest request, long timestamp, long frameNumber) {
            super.onCaptureStarted(session, request, timestamp, frameNumber);
        }

        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);

        }

        @Override
        public void onCaptureFailed(CameraCaptureSession session, CaptureRequest request, CaptureFailure failure) {
            super.onCaptureFailed(session, request, failure);

            Toast.makeText(getActivity(), "Focus lock failed", Toast.LENGTH_SHORT).show();
        }
    };

    private CameraCaptureSession.CaptureCallback mCaptureSessionCallback =
            new CameraCaptureSession.CaptureCallback() {

                @Override
                public void onCaptureStarted(CameraCaptureSession session, CaptureRequest request, long timestamp, long frameNumber) {
                    super.onCaptureStarted(session, request, timestamp, frameNumber);
                    int requestId = (int) request.getTag();

                    String currentDateTime = generateTimeStamp();

                    if (ALLOWS_JPEG) {
                        ImageSaver.ImageSaverBuilder jpegBuilder = mJpegResultQueue.get(requestId);

                        File jpegRootPath = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), data_directory_name);
                        if (!jpegRootPath.exists()) {
                            jpegRootPath.mkdirs();
                        }

                        File jpegFile = new File(jpegRootPath,
                                "JPEG_" + currentDateTime + ".jpg");

                        if (jpegBuilder != null) {
                            jpegBuilder.setFile(jpegFile);
                        }
                    }

                    if (ALLOWS_RAW) {
                        File rawRootPath = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), data_directory_name);
                        if (!rawRootPath.exists()) {
                            rawRootPath.mkdirs();
                        }
                        File rawFile = new File(rawRootPath,
                                "RAW_" + currentDateTime + ".dng");
                        ImageSaver.ImageSaverBuilder rawBuilder;
                        rawBuilder = mRawResultQueue.get(requestId);

                        if (rawBuilder != null) {
                            rawBuilder.setFile(rawFile);
                        }

                    }

                }

                @Override
                public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);


                    int requestId = (int) request.getTag();
                    ImageSaver.ImageSaverBuilder jpegBuilder = mJpegResultQueue.get(requestId);
                    if (jpegBuilder != null) {
                        jpegBuilder.setResult(result);
                        /**
                         * Write metadata to file
                         */
//                        String fileName = jpegBuilder.getFileName();
//                        MetadataWriter writer = new MetadataWriter(result, fileName);
//                        File rootPath = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), data_directory_name);
//                        if(!rootPath.exists()) {
//                            rootPath.mkdir();
//                        }
//                        File metadataFile = new File(rootPath, JPEG_METADATA_FILENAME);
//                        try {
//                            FileOutputStream stream = new FileOutputStream(metadataFile, true);
//                            byte[] bytes = writer.getXMLString().getBytes();
//                            stream.write(bytes);
//                            Log.i(TAG,"writing metadata");
//
//                            MediaScannerConnection.scanFile(getActivity(), new String[]{metadataFile.getPath()},
//                                    null, new MediaScannerConnection.MediaScannerConnectionClient() {
//                                        @Override
//                                        public void onMediaScannerConnected() {
//                                            // Do nothing
//                                        }
//
//                                        @Override
//                                        public void onScanCompleted(String path, Uri uri) {
//                                            Log.i(TAG, "Scanned" + path + ":");
//                                            Log.i(TAG, "-> uri=" + uri);
//                                        }
//                                    });
//
//                        } catch (IOException e) {
//                            e.printStackTrace();
//                        }
                    }
                    if (ALLOWS_RAW) {
                        ImageSaver.ImageSaverBuilder rawBuilder = mRawResultQueue.get(requestId);
                        if (rawBuilder != null) {
                            rawBuilder.setResult(result);
//                            MetadataWriter writer = new MetadataWriter(result, rawBuilder.getFileName());
//                            File rootPath = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "MegaMovieTest");
//                            File metadataFile = new File(rootPath, "metadata.txt");
//                            try {
//                                FileOutputStream stream = new FileOutputStream(metadataFile, true);
//                                byte[] bytes = writer.getXMLString().getBytes();
//                                stream.write(bytes);
//                            } catch (IOException e) {
//                                e.printStackTrace();
//                            }
                        }
                    }
                }
            };

    private Size mJpegImageSize;
    private Size mRawImageSize;
    private int mSensorOrientation;

    private final TreeMap<Integer, ImageSaver.ImageSaverBuilder> mJpegResultQueue = new TreeMap<>();
    private final TreeMap<Integer, ImageSaver.ImageSaverBuilder> mRawResultQueue = new TreeMap<>();


    private RefCountedAutoCloseable<ImageReader> mJpegImageReader;
    private final ImageReader.OnImageAvailableListener mOnJpegImageAvailableListener =
            new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    dequeueAndSaveImage(mJpegResultQueue, mJpegImageReader);
                }
            };
    private RefCountedAutoCloseable<ImageReader> mRawImageReader;
    private final ImageReader.OnImageAvailableListener mOnRawImageAvailableListener =
            new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    dequeueAndSaveImage(mRawResultQueue, mRawImageReader);
                }
            };

    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!hasAllPermissionsGranted()) {
            requestAllPermissions();
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_camera_preview, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mTextureView = (TextureView) view.findViewById(R.id.preview_texture_view);
    }

    @Override
    public void onResume() {
        super.onResume();
        openBackgroundThread();

        if (mTextureView.isAvailable()) {
            setupCamera(mTextureView.getWidth(), mTextureView.getHeight());
            openCamera();
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }

    @Override
    public void onPause() {
        closeCamera();
        closeBackgroundThread();
        super.onPause();
    }

    private void setupCamera(int width, int height) {
        CameraManager cameraManager = (CameraManager) getActivity().getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String cameraID : cameraManager.getCameraIdList()) {
                CameraCharacteristics cc = cameraManager.getCameraCharacteristics(cameraID);
                if (cc.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }
                mCharacteristics = cc;
                StreamConfigurationMap map = cc.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

                mPreviewSize = getPreferredPreviewSize(map.getOutputSizes(SurfaceTexture.class), width, height);
                mCameraId = cameraID;
                mSensorOrientation = cc.get(CameraCharacteristics.SENSOR_ORIENTATION);

                List<Size> sortedSizes = Arrays.asList(map.getOutputSizes(ImageFormat.JPEG));

                Collections.sort(sortedSizes, new Comparator<Size>() {
                            @Override
                            public int compare(Size lhs, Size rhs) {
                                return Long.signum(lhs.getWidth() * lhs.getHeight() -
                                        rhs.getWidth() * rhs.getHeight());
                            }
                        }
                );

                int numberOfSizes = sortedSizes.size();
                // Pick out the middle size
                mJpegImageSize = sortedSizes.get(numberOfSizes / 2);

                if (ALLOWS_RAW) {
                    mRawImageSize = Collections.max(
                            Arrays.asList(map.getOutputSizes(ImageFormat.RAW_SENSOR)),
                            new Comparator<Size>() {
                                @Override
                                public int compare(Size lhs, Size rhs) {
                                    return Long.signum(lhs.getWidth() * lhs.getHeight() -
                                            rhs.getWidth() * rhs.getHeight());
                                }
                            }
                    );
                }

                if (mJpegImageReader == null || mJpegImageReader.getAndRetain() == null) {
                    mJpegImageReader = new RefCountedAutoCloseable<>(
                            ImageReader.newInstance(mJpegImageSize.getWidth(),
                                    mJpegImageSize.getHeight(),
                                    ImageFormat.JPEG,
                        /*max images */5));

                }
                if (ALLOWS_RAW) {
                    if (mRawImageReader == null || mRawImageReader.getAndRetain() == null) {
                        mRawImageReader = new RefCountedAutoCloseable<>(
                                ImageReader.newInstance(mRawImageSize.getWidth(),
                                        mRawImageSize.getHeight(),
                                        ImageFormat.RAW_SENSOR,
                        /*max images */5));

                    }
                    mRawImageReader.get().setOnImageAvailableListener(mOnRawImageAvailableListener, mBackgroundHandler);
                }

                mJpegImageReader.get().setOnImageAvailableListener(mOnJpegImageAvailableListener, mBackgroundHandler);

                return;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    private Size getPreferredPreviewSize(Size[] mapSizes, int width, int height) {
        List<Size> collectorSizes = new ArrayList<Size>();
        for (Size option : mapSizes) {
            //landscape
            if (width < height) {
                if (option.getWidth() > width && option.getHeight() > height) {
                    collectorSizes.add(option);
                }
            }
            //portrait
            if (width < height) {
                if (option.getWidth() > height && option.getHeight() > width) {
                    collectorSizes.add(option);
                }
            }
        }
        if (collectorSizes.size() > 0) {
            return Collections.min(collectorSizes, new Comparator<Size>() {
                @Override
                public int compare(Size lhs, Size rhs) {
                    return Long.signum(lhs.getWidth() * lhs.getHeight() - rhs.getWidth() * rhs.getHeight());
                }
            });
        }
        return mapSizes[0];
    }

    private void requestAllPermissions() {
        FragmentCompat.requestPermissions(this,
                CAMERA_PERMISSIONS,
                REQUEST_CAMERA_PERMISSIONS);
    }

    private boolean hasAllPermissionsGranted() {
        for (String permission : CAMERA_PERMISSIONS) {
            if (ActivityCompat.checkSelfPermission(getActivity(), permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSIONS) {
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    showMissingPermissionError();
                    return;
                }
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private void showMissingPermissionError() {
        Activity activity = getActivity();
        if (activity != null) {
            Toast.makeText(activity, "This app needs camera permissions.", Toast.LENGTH_SHORT).show();
        }
    }


    private void openCamera() {
        CameraManager cameraManager = (CameraManager) getActivity().getSystemService(Context.CAMERA_SERVICE);
        if (!hasAllPermissionsGranted()) {
            requestAllPermissions();
            return;
        }
        try {
            try {
                cameraManager.openCamera(mCameraId, mCameraDeviceStateCallback, mBackgroundHandler);
            } catch (SecurityException e) {
                e.printStackTrace();
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void closeCamera() {
        if (mCameraCaptureSession != null) {
            mCameraCaptureSession.close();
            mCameraCaptureSession = null;
        }
        if (mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
    }

    public void captureStillImage() {
        CaptureSequence.CaptureSettings settings = new CaptureSequence.CaptureSettings(mDuration,mSensorSensitivity,mFocusDistance,true,false);
    }

    public void captureRawImage() {
        CaptureSequence.CaptureSettings settings = new CaptureSequence.CaptureSettings(mDuration,mSensorSensitivity,mFocusDistance,true,false);
        takePhotoWithSettings(settings);
    }

    @Override
    public void takePhotoWithSettings(CaptureSequence.CaptureSettings settings) {
        try {
            final CaptureRequest.Builder captureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_MANUAL);
            if (settings.shouldSaveJpeg) {
                captureRequestBuilder.addTarget(mJpegImageReader.get().getSurface());
            }
            if (settings.shouldSaveRaw) {
                captureRequestBuilder.addTarget(mRawImageReader.get().getSurface());
            }

            int rotation = getActivity().getWindowManager().getDefaultDisplay().getRotation();
            captureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, getOrientation(rotation));
            captureRequestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, settings.sensitivity);
            captureRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, settings.exposureTime);
            captureRequestBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, settings.focusDistance);


//            if (mLocationProvider != null) {
//                captureRequestBuilder.set(CaptureRequest.JPEG_GPS_LOCATION, mLocationProvider.getLocation());
//            }

            captureRequestBuilder.setTag(mRequestCounter.getAndIncrement());

            CaptureRequest request = captureRequestBuilder.build();

            if (settings.shouldSaveJpeg) {
                ImageSaver.ImageSaverBuilder jpegBuilder = new ImageSaver.ImageSaverBuilder(getActivity()).setCharacteristics(mCharacteristics);
                mJpegResultQueue.put((int) request.getTag(), jpegBuilder);
            }
            if (settings.shouldSaveRaw) {
                ImageSaver.ImageSaverBuilder rawBuilder = new ImageSaver.ImageSaverBuilder(getActivity()).setCharacteristics(mCharacteristics);
                mRawResultQueue.put((int) request.getTag(), rawBuilder);
            }

            for (CameraFragment.CaptureListener listener : listeners) {
                listener.onCapture();
            }


            mCameraCaptureSession.capture(request,
                    mCaptureSessionCallback,
                    mBackgroundHandler);


        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void createSession() {
        try {
            SurfaceTexture surfaceTexture = mTextureView.getSurfaceTexture();
            surfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            mPreviewSurface = new Surface(surfaceTexture);
            List<Surface> surfaces = new ArrayList<>();
            surfaces.add(mPreviewSurface);
            if (ALLOWS_JPEG) {
                surfaces.add(mJpegImageReader.get().getSurface());
            }
            if (ALLOWS_RAW) {
                surfaces.add(mRawImageReader.get().getSurface());
            }
            mCameraDevice.createCaptureSession(surfaces,
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(CameraCaptureSession session) {
                            if (mCameraDevice == null) {
                                return;
                            }
                            mCameraCaptureSession = session;
                            setPreviewRequest();
                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession session) {
                            Toast.makeText(getActivity(), "create camera session failed", Toast.LENGTH_SHORT).show();
                        }
                    }, null);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void setPreviewRequest() {
        try {
            mPreviewCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewCaptureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_OFF);
            mPreviewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
            mPreviewCaptureRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, mDuration);
            mPreviewCaptureRequestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, mSensorSensitivity);
            mPreviewCaptureRequestBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, mFocusDistance);
            mPreviewCaptureRequestBuilder.addTarget(mPreviewSurface);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        try {
            mPreviewCaptureRequest = mPreviewCaptureRequestBuilder.build();
            mCameraCaptureSession.stopRepeating();
            mCameraCaptureSession.setRepeatingRequest(mPreviewCaptureRequest,
                    mPreviewSessionCallback,
                    mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void openBackgroundThread() {
        mBackgroundThread = new HandlerThread("Camera2 background thread");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    private void closeBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        mBackgroundThread = null;
        mBackgroundHandler = null;
    }

    private void dequeueAndSaveImage(TreeMap<Integer, ImageSaver.ImageSaverBuilder> pendingQueue,
                                     RefCountedAutoCloseable<ImageReader> reader) {
        Map.Entry<Integer, ImageSaver.ImageSaverBuilder> entry = pendingQueue.firstEntry();
        ImageSaver.ImageSaverBuilder builder = entry.getValue();

        // Increment reference count to prevent ImageReader from being closed while we
        // are saving its Images in a background thread (otherwise their resources may
        // be freed while we are writing to a file).
        if (reader == null || reader.getAndRetain() == null) {
            Log.e(TAG, "Paused the activity before we could save the image," +
                    " ImageReader already closed.");
            pendingQueue.remove(entry.getKey());
            return;
        }

        Image image;
        try {
            image = reader.get().acquireNextImage();
        } catch (IllegalStateException e) {
            e.printStackTrace();
            return;
        }
        builder.setRefCountedReader(reader).setImage(image);

        Location loc = getLocation();
        if(loc != null) {
            builder.setLocation(loc);
        }


        handleCompletionLocked(entry.getKey(), builder, pendingQueue);
    }

    private void handleCompletionLocked(int requestId, ImageSaver.ImageSaverBuilder builder,
                                        TreeMap<Integer, ImageSaver.ImageSaverBuilder> queue) {
        if (builder == null) return;
        ImageSaver saver = builder.buildIfComplete();
        if (saver != null) {
            queue.remove(requestId);
            AsyncTask.THREAD_POOL_EXECUTOR.execute(saver);
        }
    }

    private static class ImageSaver implements Runnable {

        private final Image mImage;
        private final File mFile;
        private final CaptureResult mCaptureResult;
        private final CameraCharacteristics mCharacteristics;
        private final Context mContext;
        private final RefCountedAutoCloseable<ImageReader> mReader;
        private final Location mLocation;

        private ImageSaver(Location location, Image image, File file, CaptureResult captureResult,
                           CameraCharacteristics characteristics, Context context, RefCountedAutoCloseable<ImageReader> reader) {
            mLocation = location;
            mImage = image;
            mFile = file;
            mCaptureResult = captureResult;
            mCharacteristics = characteristics;
            mContext = context;
            mReader = reader;
        }

        @Override
        public void run() {
            boolean success = false;
            int format = mImage.getFormat();
            switch (format) {
                case ImageFormat.JPEG: {
                    ByteBuffer byteBuffer = mImage.getPlanes()[0].getBuffer();
                    byte[] bytes = new byte[byteBuffer.remaining()];
                    byteBuffer.get(bytes);

                    FileOutputStream fileOutputStream = null;

                    try {
                        fileOutputStream = new FileOutputStream(mFile);
                        fileOutputStream.write(bytes);
                        success = true;
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        mImage.close();
                        closeOutput(fileOutputStream);
                    }

                    if (false) {
                        Location location = mLocation;
                        try {


                            ExifInterface exif = new ExifInterface((mFile.getAbsolutePath()));

                            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, GPS.latitudeRef(location.getLatitude()));
                            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, GPS.convert(location.getLatitude()));

                            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, GPS.longitudeRef(-location.getLongitude()));

                            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, GPS.convert(-location.getLongitude()));

                            SimpleDateFormat fmt_Exif = new SimpleDateFormat("yyyy:MM:dd HH:mm:ss");
                            exif.setAttribute(ExifInterface.TAG_DATETIME,fmt_Exif.format(new Date(location.getTime())));



                            exif.saveAttributes();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                }
                break;
                case ImageFormat.RAW_SENSOR: {


                    DngCreator dngCreator = new DngCreator(mCharacteristics, mCaptureResult);
                    if (mLocation != null) {
                        dngCreator.setLocation(mLocation);
                    }


                    FileOutputStream output = null;
                    try {
                        output = new FileOutputStream(mFile);
                        dngCreator.writeImage(output, mImage);
                        success = true;

                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        mImage.close();
                        closeOutput(output);
                    }



                }
                break;
                default: {
                    Log.e(TAG, "Cannot save image, unexpected image format:" + format);
                    break;
                }
            }

            if (success) {
                MediaScannerConnection.scanFile(mContext, new String[]{mFile.getPath()},
                        null, new MediaScannerConnection.MediaScannerConnectionClient() {
                            @Override
                            public void onMediaScannerConnected() {
                                // Do nothing
                            }

                            @Override
                            public void onScanCompleted(String path, Uri uri) {
                                Log.i(TAG, "Scanned" + path + ":");
                                Log.i(TAG, "-> uri=" + uri);
                            }
                        });
            }
        }

        public static class ImageSaverBuilder {
            private Image mImage;
            private File mFile;
            private CaptureResult mCaptureResult;
            private CameraCharacteristics mCharacteristics;
            private RefCountedAutoCloseable<ImageReader> mReader;
            private Context mContext;
            private Location mLocation;

            public ImageSaverBuilder(final Context context) {
                mContext = context;
            }

            public synchronized ImageSaver.ImageSaverBuilder setRefCountedReader(RefCountedAutoCloseable<ImageReader> reader) {
                if (reader == null) throw new NullPointerException();
                mReader = reader;
                return this;
            }

            public synchronized ImageSaver.ImageSaverBuilder setLocation(final Location location) {
                mLocation = location;
                return this;
            }

            public synchronized ImageSaver.ImageSaverBuilder setImage(final Image image) {
                if (image == null) throw new NullPointerException();
                mImage = image;
                return this;
            }

            public synchronized ImageSaver.ImageSaverBuilder setFile(final File file) {
                if (file == null) throw new NullPointerException();
                mFile = file;
                return this;
            }

            public synchronized ImageSaver.ImageSaverBuilder setResult(final CaptureResult result) {
                if (result == null) throw new NullPointerException();
                mCaptureResult = result;
                return this;
            }

            public synchronized ImageSaver.ImageSaverBuilder setCharacteristics(final CameraCharacteristics characteristics) {
                if (characteristics == null) throw new NullPointerException();
                mCharacteristics = characteristics;
                return this;
            }

            public synchronized ImageSaver buildIfComplete() {
                if (!isComplete()) {
                    return null;
                }
                return new ImageSaver(mLocation, mImage, mFile, mCaptureResult, mCharacteristics, mContext, mReader);
            }

            public synchronized String getSaveLocation() {
                return (mFile == null) ? "Unknown" : mFile.toString();
            }

            public synchronized String getFileName() {
                return (mFile == null) ? "Unknown" : mFile.getName();
            }

            public boolean isComplete() {
                return mImage != null && mFile != null && mCaptureResult != null
                        && mCharacteristics != null;
            }

        }
    }

    private static void closeOutput(OutputStream outputStream) {
        if (null != outputStream) {
            try {
                outputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static String generateTimeStamp() {
        SimpleDateFormat formatter = new SimpleDateFormat("MMM_dd_HH_mm_ss_SSS");
//        java.text.SimpleDateFormat formatter = new java.text.SimpleDateFormat("yyy_MM_dd_HH_mm_ss_SSS", Locale.US);
        Calendar c = Calendar.getInstance();
        formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
        return formatter.format(c.getTime()) + "_UTC";
    }

    /**
     * A wrapper for an {@link AutoCloseable} object that implements reference counting to allow
     * for resource management.
     */
    public static class RefCountedAutoCloseable<T extends AutoCloseable> implements AutoCloseable {
        private T mObject;
        private long mRefCount = 0;

        /**
         * Wrap the given object.
         *
         * @param object an object to wrap.
         */
        public RefCountedAutoCloseable(T object) {
            if (object == null) throw new NullPointerException();
            mObject = object;
        }

        /**
         * Increment the reference count and return the wrapped object.
         *
         * @return the wrapped object, or null if the object has been released.
         */
        public synchronized T getAndRetain() {
            if (mRefCount < 0) {
                return null;
            }
            mRefCount++;
            return mObject;
        }

        /**
         * Return the wrapped object.
         *
         * @return the wrapped object, or null if the object has been released.
         */
        public synchronized T get() {
            return mObject;
        }

        /**
         * Decrement the reference count and release the wrapped object if there are no other
         * users retaining this object.
         */
        @Override
        public synchronized void close() {
            if (mRefCount >= 0) {
                mRefCount--;
                if (mRefCount < 0) {
                    try {
                        mObject.close();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    } finally {
                        mObject = null;
                    }
                }
            }
        }
    }
}






