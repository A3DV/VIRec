package io.a3dv.VIRec;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Size;
import android.view.Display;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.RequiresApi;

import java.io.File;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import io.a3dv.VIRec.gles.FullFrameRect;
import io.a3dv.VIRec.gles.Texture2dProgram;
import timber.log.Timber;

class DesiredCameraSetting {
    static final int mDesiredFrameWidth = 1280;
    static final int mDesiredFrameHeight = 720;
    static final Long mDesiredExposureTime = 5000000L; // nanoseconds
    static final String mDesiredFrameSize = mDesiredFrameWidth + "x" + mDesiredFrameHeight;
}

class CameraActivityBase extends Activity implements SurfaceTexture.OnFrameAvailableListener {
    protected static final boolean VERBOSE = false;

    // Camera filters; must match up with cameraFilterNames in strings.xml
    static final int FILTER_NONE = 0;
    static final int FILTER_BLACK_WHITE = 1;
    static final int FILTER_BLUR = 2;
    static final int FILTER_SHARPEN = 3;
    static final int FILTER_EDGE_DETECT = 4;
    static final int FILTER_EMBOSS = 5;

    protected TextView mKeyCameraParamsText;
    protected TextView mKeyCameraParamsText2;
    protected TextView mCaptureResultText;
    protected TextView mCaptureResultText2;

    protected int mCameraPreviewWidth, mCameraPreviewHeight;
    protected int mVideoFrameWidth, mVideoFrameHeight;
    protected int mVideoFrameWidth2, mVideoFrameHeight2;
    protected Camera2Proxy mCamera2Proxy = null;
    protected Camera2Proxy mCamera2Proxy2 = null;

    protected SampleGLView mGLView;
    protected SampleGLView mGLView2;
    protected TextureMovieEncoder sVideoEncoder = new TextureMovieEncoder();
    protected TextureMovieEncoder sVideoEncoder2 = new TextureMovieEncoder();

    /**
     * Connects the SurfaceTexture to the Camera preview output, and starts the preview.
     */
    public void handleSetSurfaceTexture(SurfaceTexture st) {
        st.setOnFrameAvailableListener(this);

        if (mCamera2Proxy != null) {
            mCamera2Proxy.setPreviewSurfaceTexture(st);
            mCamera2Proxy.openCamera();
        } else {
            throw new RuntimeException(
                    "Try to set surface texture while camera2proxy is null");
        }
    }

    public void handleSetSurfaceTexture2(SurfaceTexture st) {
        st.setOnFrameAvailableListener(surfaceTexture -> {
            if (VERBOSE) Timber.d("ST onFrameAvailable");
            mGLView2.requestRender();

            final String sFps = String.format(Locale.getDefault(), "%.1f FPS",
                    sVideoEncoder.mFrameRate);
            String previewFacts = "[2] " + mCameraPreviewWidth + "x" + mCameraPreviewHeight + "@" + sFps;

            mKeyCameraParamsText2.setText(previewFacts);
        });

        if (mCamera2Proxy2 != null) {
            mCamera2Proxy2.setPreviewSurfaceTexture(st);
            mCamera2Proxy2.openCamera();
        } else {
            throw new RuntimeException(
                    "Try to set surface texture while camera2proxy is null");
        }
    }

    public Camera2Proxy getmCamera2Proxy() {
        if (mCamera2Proxy == null) {
            throw new RuntimeException("Get a null Camera2Proxy");
        }
        return mCamera2Proxy;
    }

    protected String renewOutputDir() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss", Locale.US);
        String folderName = dateFormat.format(new Date());

        String dataDir = getExternalFilesDir(
                Environment.getDataDirectory().getAbsolutePath()).getAbsolutePath();
        String outputDir = dataDir + File.separator + folderName;

        (new File(outputDir)).mkdirs();
        return outputDir;
    }

    // updates mCameraPreviewWidth/Height
    protected void setLayoutAspectRatio(Size cameraPreviewSize) {
        AspectFrameLayout layout = findViewById(R.id.cameraPreview_afl);
        Display display = ((WindowManager) getSystemService(WINDOW_SERVICE)).getDefaultDisplay();
        mCameraPreviewWidth = cameraPreviewSize.getWidth();
        mCameraPreviewHeight = cameraPreviewSize.getHeight();
        if (display.getRotation() == Surface.ROTATION_0) {
            layout.setAspectRatio((double) mCameraPreviewHeight / mCameraPreviewWidth);
        } else if (display.getRotation() == Surface.ROTATION_180) {
            layout.setAspectRatio((double) mCameraPreviewHeight / mCameraPreviewWidth);
        } else {
            layout.setAspectRatio((double) mCameraPreviewWidth / mCameraPreviewHeight);
        }
    }

    public void updateCaptureResultPanel(
            final Float fl,
            final Long exposureTimeNs, final Integer afMode, boolean secondCamera) {
        final String sfl = String.format(Locale.getDefault(), "%.3f", fl);
        final String sExpoTime =
                exposureTimeNs == null ?
                        "null ms" :
                        String.format(Locale.getDefault(), "%.2f ms",
                                exposureTimeNs / 1000000.0);

        final String saf = "AF Mode: " + afMode.toString();

        if (secondCamera) {
            runOnUiThread(() -> mCaptureResultText2.setText(sfl + " " + sExpoTime + " " + saf));
        } else {
            runOnUiThread(() -> mCaptureResultText.setText(sfl + " " + sExpoTime + " " + saf));
        }

    }

    @Override
    public void onFrameAvailable(SurfaceTexture st) {
        // The SurfaceTexture uses this to signal the availability of a new frame.  The
        // thread that "owns" the external texture associated with the SurfaceTexture (which,
        // by virtue of the context being shared, *should* be either one) needs to call
        // updateTexImage() to latch the buffer.
        //
        // Once the buffer is latched, the GLSurfaceView thread can signal the encoder thread.
        // This feels backward -- we want recording to be prioritized over rendering -- but
        // since recording is only enabled some of the time it's easier to do it this way.
        //
        // Since GLSurfaceView doesn't establish a Looper, this will *probably* execute on
        // the main UI thread.  Fortunately, requestRender() can be called from any thread,
        // so it doesn't really matter.
        if (VERBOSE) Timber.d("ST onFrameAvailable");
        mGLView.requestRender();

        final String sFps = String.format(Locale.getDefault(), "%.1f FPS",
                sVideoEncoder.mFrameRate);
        String previewFacts = "[1] " + mCameraPreviewWidth + "x" + mCameraPreviewHeight + "@" + sFps;

        mKeyCameraParamsText.setText(previewFacts);
    }
}

public class CameraActivity extends CameraActivityBase
        implements PopupMenu.OnMenuItemClickListener {
    private CameraSurfaceRenderer mRenderer = null;
    private CameraSurfaceRenderer mRenderer2 = null;
    private TextView mOutputDirText;

    private CameraHandler mCameraHandler;
    private CameraHandler mCameraHandler2;
    private boolean mRecordingEnabled;      // controls button state

    private IMUManager mImuManager;
    private GPSManager mGpsManager;
    private TimeBaseManager mTimeBaseManager;

    @SuppressLint("SourceLockedOrientationActivity")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
        setContentView(R.layout.camera_activity);
        Spinner spinner = findViewById(R.id.cameraFilter_spinner);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.cameraFilterNames, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        // Apply the adapter to the spinner.
        spinner.setAdapter(adapter);
        spinner.setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                Spinner spinner = (Spinner) parent;
                final int filterNum = spinner.getSelectedItemPosition();
                TextView textView = (TextView) parent.getChildAt(0);
                textView.setTextColor(0xFFFFFFFF);
                textView.setGravity(Gravity.CENTER);

                mGLView.queueEvent(() -> {
                    // notify the renderer that we want to change the encoder's state
                    mRenderer.changeFilterMode(filterNum);
                });

                mGLView2.queueEvent(() -> {
                    // notify the renderer that we want to change the encoder's state
                    mRenderer2.changeFilterMode(filterNum);
                });
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        mCamera2Proxy = new Camera2Proxy(this, false);
        mCamera2Proxy2 = new Camera2Proxy(this, true);
        Size previewSize = mCamera2Proxy.configureCamera();
        mCamera2Proxy2.configureCamera();
        setLayoutAspectRatio(previewSize);  // updates mCameraPreviewWidth/Height
        Size videoSize = mCamera2Proxy.getmVideoSize();
        mVideoFrameWidth = videoSize.getWidth();
        mVideoFrameHeight = videoSize.getHeight();

        Size videoSize2 = mCamera2Proxy2.getmVideoSize();
        mVideoFrameWidth2 = videoSize2.getWidth();
        mVideoFrameHeight2 = videoSize2.getHeight();
        // Define a handler that receives camera-control messages from other threads.  All calls
        // to Camera must be made on the same thread.  Note we create this before the renderer
        // thread, so we know the fully-constructed object will be visible.
        mCameraHandler = new CameraHandler(this);
        mCameraHandler2 = new CameraHandler(this);

        mRecordingEnabled = sVideoEncoder.isRecording();

        // Configure the GLSurfaceView.  This will start the Renderer thread, with an
        // appropriate EGL context.
        mGLView = findViewById(R.id.cameraPreview_surfaceView);
        mGLView2 = findViewById(R.id.cameraPreview_surfaceView2);

        if (mRenderer == null) {
            mRenderer = new CameraSurfaceRenderer(mCameraHandler, sVideoEncoder, 0);
            mGLView.setEGLContextClientVersion(2);     // select GLES 2.0
            mGLView.setRenderer(mRenderer);
            mGLView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        }

        if (mRenderer2 == null) {
            mRenderer2 = new CameraSurfaceRenderer(mCameraHandler2, sVideoEncoder2, 1);
            mGLView2.setEGLContextClientVersion(2);     // select GLES 2.0
            mGLView2.setRenderer(mRenderer2);
            mGLView2.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        }

        mGLView.setTouchListener((event, width, height) -> {
            ManualFocusConfig focusConfig =
                    new ManualFocusConfig(event.getX(), event.getY(), width, height);
            Timber.d(focusConfig.toString());
            mCameraHandler.sendMessage(
                    mCameraHandler.obtainMessage(CameraHandler.MSG_MANUAL_FOCUS, focusConfig));
        });

        if (mImuManager == null) {
            mImuManager = new IMUManager(this);
            mTimeBaseManager = new TimeBaseManager();
        }

        if (mGpsManager == null) {
            mGpsManager = new GPSManager(this);
            mTimeBaseManager = new TimeBaseManager();
        }

        mKeyCameraParamsText = findViewById(R.id.cameraParams_text);
        mKeyCameraParamsText2 = findViewById(R.id.cameraParams_text2);
        mCaptureResultText = findViewById(R.id.captureResult_text);
        mCaptureResultText2 = findViewById(R.id.captureResult_text2);
        mOutputDirText = findViewById(R.id.cameraOutputDir_text);
    }

    @Override
    protected void onResume() {
        Timber.d("onResume -- acquiring camera");
        super.onResume();
        Timber.d("Keeping screen on for previewing recording.");
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        updateControls();

        if (mCamera2Proxy == null) {
            mCamera2Proxy = new Camera2Proxy(this, false);
            Size previewSize = mCamera2Proxy.configureCamera();
            setLayoutAspectRatio(previewSize);
            Size videoSize = mCamera2Proxy.getmVideoSize();
            mVideoFrameWidth = videoSize.getWidth();
            mVideoFrameHeight = videoSize.getHeight();
        }

        if (mCamera2Proxy2 == null) {
            mCamera2Proxy2 = new Camera2Proxy(this, true);
            mCamera2Proxy2.configureCamera();
            Size videoSize = mCamera2Proxy2.getmVideoSize();
            mVideoFrameWidth2 = videoSize.getWidth();
            mVideoFrameHeight2 = videoSize.getHeight();
        }

        mGLView.onResume();
        mGLView.queueEvent(() -> {
            mRenderer.setCameraPreviewSize(mCameraPreviewWidth, mCameraPreviewHeight);
            mRenderer.setVideoFrameSize(mVideoFrameWidth, mVideoFrameHeight);
        });

        mGLView2.onResume();
        mGLView2.queueEvent(() -> {
            mRenderer2.setCameraPreviewSize(mCameraPreviewWidth, mCameraPreviewHeight);
            mRenderer2.setVideoFrameSize(mVideoFrameWidth2, mVideoFrameHeight2);
        });

        mImuManager.register();
        mGpsManager.register();
    }

    @Override
    protected void onPause() {
        Timber.d("onPause -- releasing camera");
        super.onPause();
        // no more frame metadata will be saved during pause
        if (mCamera2Proxy != null) {
            mCamera2Proxy.releaseCamera();
            mCamera2Proxy = null;
        }

        if (mCamera2Proxy2 != null) {
            mCamera2Proxy2.releaseCamera();
            mCamera2Proxy2 = null;
        }

        mGLView.queueEvent(() -> {
            // Tell the renderer that it's about to be paused so it can clean up.
            mRenderer.notifyPausing();
        });
        mGLView.onPause();

        mGLView2.queueEvent(() -> {
            // Tell the renderer that it's about to be paused so it can clean up.
            mRenderer2.notifyPausing();
        });
        mGLView2.onPause();

        mImuManager.unregister();
        mGpsManager.unregister();
        Timber.d("onPause complete");
    }

    @Override
    protected void onDestroy() {
        Timber.d("onDestroy");
        super.onDestroy();
        mCameraHandler.invalidateHandler();
        mCameraHandler2.invalidateHandler();
    }

    
    public void clickToggleRecording(@SuppressWarnings("unused") View unused) {
        mRecordingEnabled = !mRecordingEnabled;
        if (mRecordingEnabled) {
            String outputDir = renewOutputDir();
            String outputFile = outputDir + File.separator + "movie.mp4";
            String outputFile2 = outputDir + File.separator + "movie2.mp4";
            String metaFile = outputDir + File.separator + "frame_timestamps.txt";
            String metaFile2 = outputDir + File.separator + "frame_timestamps2.txt";

            String basename = outputDir.substring(outputDir.lastIndexOf("/") + 1);
            mOutputDirText.setText(basename);
            mRenderer.resetOutputFiles(outputFile, metaFile); // this will not cause sync issues
            mRenderer2.resetOutputFiles(outputFile2, metaFile2);

            String inertialFile = outputDir + File.separator + "gyro_accel.csv";
            String locationFile = outputDir + File.separator + "location.csv";
            String edgeEpochFile = outputDir + File.separator + "edge_epochs.txt";

            mTimeBaseManager.startRecording(edgeEpochFile, mCamera2Proxy.getmTimeSourceValue());
            mImuManager.startRecording(inertialFile);
            mGpsManager.startRecording(locationFile);
            mCamera2Proxy.startRecordingCaptureResult(
                    outputDir + File.separator + "movie_metadata.csv");
            mCamera2Proxy2.startRecordingCaptureResult(
                    outputDir + File.separator + "movie_metadata2.csv");
        } else {
            mCamera2Proxy.stopRecordingCaptureResult();
            mCamera2Proxy2.stopRecordingCaptureResult();
            mImuManager.stopRecording();
            mGpsManager.stopRecording();
            mTimeBaseManager.stopRecording();
        }

        mGLView.queueEvent(() -> {
            // notify the renderer that we want to change the encoder's state
            mRenderer.changeRecordingState(mRecordingEnabled);
        });

        mGLView2.queueEvent(() -> {
            // notify the renderer that we want to change the encoder's state
            mRenderer2.changeRecordingState(mRecordingEnabled);
        });

        updateControls();
    }

    private void updateControls() {
        ImageButton toggleRecordingButton = findViewById(R.id.toggleRecordingButton);
        toggleRecordingButton.setContentDescription(mRecordingEnabled
                ? getString(R.string.stop)
                : getString(R.string.record));
        toggleRecordingButton.setImageResource(mRecordingEnabled
                ? R.drawable.ic_baseline_stop_24
                : R.drawable.ic_baseline_fiber_manual_record_24);

        Spinner filterSpinner = findViewById(R.id.cameraFilter_spinner);
        filterSpinner.setVisibility(mRecordingEnabled ? View.INVISIBLE : View.VISIBLE);
    }

    public void clickShowPopupMenu(View v) {
        PopupMenu popup = new PopupMenu(getApplicationContext(), v);
        popup.setOnMenuItemClickListener(this);
        popup.inflate(R.menu.popup_menu);
        popup.show();
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        if (item.getItemId() == R.id.menu_settings) {
            final Intent toSettings = new Intent(this, SettingsActivity.class);
            startActivity(toSettings);
        } else if (item.getItemId() == R.id.menu_imu) {
            final Intent toImuViewer = new Intent(this, ImuViewerActivity.class);
            startActivity(toImuViewer);
        } else if (item.getItemId() == R.id.menu_about) {
            final Intent toAbout = new Intent(this, AboutActivity.class);
            startActivity(toAbout);
        }

        return false;
    }
}


class CameraHandler extends Handler {
    public static final int MSG_SET_SURFACE_TEXTURE = 0;
    public static final int MSG_SET_SURFACE_TEXTURE2 = 2;
    public static final int MSG_MANUAL_FOCUS = 1;

    // Weak reference to the Activity; only access this from the UI thread.
    private final WeakReference<Activity> mWeakActivity;

    public CameraHandler(Activity activity) {
        mWeakActivity = new WeakReference<>(activity);
    }

    /**
     * Drop the reference to the activity. Useful as a paranoid measure to ensure that
     * attempts to access a stale Activity through a handler are caught.
     */
    public void invalidateHandler() {
        mWeakActivity.clear();
    }


    @Override  // runs on UI thread
    public void handleMessage(Message inputMessage) {
        int what = inputMessage.what;
        Object obj = inputMessage.obj;

        Timber.d("CameraHandler [%s]: what=%d", this.toString(), what);

        Activity activity = mWeakActivity.get();
        if (activity == null) {
            Timber.w("CameraHandler.handleMessage: activity is null");
            return;
        }

        switch (what) {
            case MSG_SET_SURFACE_TEXTURE:
                ((CameraActivityBase) activity).handleSetSurfaceTexture(
                        (SurfaceTexture) inputMessage.obj);
                break;
            case MSG_SET_SURFACE_TEXTURE2:
                ((CameraActivityBase) activity).handleSetSurfaceTexture2(
                        (SurfaceTexture) inputMessage.obj);
                break;
            case MSG_MANUAL_FOCUS:
                Camera2Proxy camera2proxy = ((CameraActivityBase) activity).getmCamera2Proxy();
                camera2proxy.changeManualFocusPoint((ManualFocusConfig) obj);
                break;
            default:
                throw new RuntimeException("unknown msg " + what);
        }
    }
}

class CameraSurfaceRenderer implements GLSurfaceView.Renderer {
    private static final boolean VERBOSE = false;

    private static final int RECORDING_OFF = 0;
    private static final int RECORDING_ON = 1;
    private static final int RECORDING_RESUMED = 2;

    private final CameraHandler mCameraHandler;
    private final TextureMovieEncoder mVideoEncoder;
    private String mOutputFile;
    private String mMetadataFile;

    private FullFrameRect mFullScreen;

    private final float[] mSTMatrix = new float[16];
    private int mTextureId;

    private SurfaceTexture mSurfaceTexture;
    private boolean mRecordingEnabled;
    private int mRecordingStatus;
    private int mFrameCount;

    // width/height of the incoming camera preview frames
    private boolean mIncomingSizeUpdated;
    private int mIncomingWidth;
    private int mIncomingHeight;

    private int mVideoFrameWidth;
    private int mVideoFrameHeight;

    private int mCurrentFilter;
    private int mNewFilter;

    private final int mCameraId;

    public CameraSurfaceRenderer(CameraHandler cameraHandler,
                                 TextureMovieEncoder movieEncoder, int cameraId) {
        mCameraHandler = cameraHandler;
        mVideoEncoder = movieEncoder;
        mTextureId = -1;

        mRecordingStatus = -1;
        mRecordingEnabled = false;
        mFrameCount = -1;

        mIncomingSizeUpdated = false;
        mIncomingWidth = mIncomingHeight = -1;
        mVideoFrameWidth = mVideoFrameHeight = -1;

        mCurrentFilter = -1;
        mNewFilter = CameraActivity.FILTER_NONE;

        mCameraId = cameraId;
    }

    public void resetOutputFiles(String outputFile, String metaFile) {
        mOutputFile = outputFile;
        mMetadataFile = metaFile;
    }

    /**
     * Notifies the renderer thread that the activity is pausing.
     * <p>
     * For best results, call this *after* disabling Camera preview.
     */
    public void notifyPausing() {
        if (mSurfaceTexture != null) {
            Timber.d("renderer pausing -- releasing SurfaceTexture");
            mSurfaceTexture.release();
            mSurfaceTexture = null;
        }
        if (mFullScreen != null) {
            mFullScreen.release(false);     // assume the GLSurfaceView EGL context is about
            mFullScreen = null;             //  to be destroyed
        }
        mIncomingWidth = mIncomingHeight = -1;
        mVideoFrameWidth = mVideoFrameHeight = -1;
    }

    /**
     * Notifies the renderer that we want to stop or start recording.
     */
    public void changeRecordingState(boolean isRecording) {
        Timber.d("changeRecordingState: was %b now %b", mRecordingEnabled, isRecording);
        mRecordingEnabled = isRecording;
    }

    /**
     * Changes the filter that we're applying to the camera preview.
     */
    public void changeFilterMode(int filter) {
        mNewFilter = filter;
    }

    /**
     * Updates the filter program.
     */
    public void updateFilter() {
        Texture2dProgram.ProgramType programType;
        float[] kernel = null;
        float colorAdj = 0.0f;

        Timber.d("Updating filter to %d", mNewFilter);
        switch (mNewFilter) {
            case CameraActivity.FILTER_NONE:
                programType = Texture2dProgram.ProgramType.TEXTURE_EXT;
                break;
            case CameraActivity.FILTER_BLACK_WHITE:
                programType = Texture2dProgram.ProgramType.TEXTURE_EXT_BW;
                break;
            case CameraActivity.FILTER_BLUR:
                programType = Texture2dProgram.ProgramType.TEXTURE_EXT_FILT_VIEW;
                kernel = new float[]{
                        1f / 16f, 2f / 16f, 1f / 16f,
                        2f / 16f, 4f / 16f, 2f / 16f,
                        1f / 16f, 2f / 16f, 1f / 16f};
                break;
            case CameraActivity.FILTER_SHARPEN:
                programType = Texture2dProgram.ProgramType.TEXTURE_EXT_FILT_VIEW;
                kernel = new float[]{
                        0f, -1f, 0f,
                        -1f, 5f, -1f,
                        0f, -1f, 0f};
                break;
            case CameraActivity.FILTER_EDGE_DETECT:
                programType = Texture2dProgram.ProgramType.TEXTURE_EXT_FILT_VIEW;
                kernel = new float[]{
                        -1f, -1f, -1f,
                        -1f, 8f, -1f,
                        -1f, -1f, -1f};
                break;
            case CameraActivity.FILTER_EMBOSS:
                programType = Texture2dProgram.ProgramType.TEXTURE_EXT_FILT_VIEW;
                kernel = new float[]{
                        2f, 0f, 0f,
                        0f, -1f, 0f,
                        0f, 0f, -1f};
                colorAdj = 0.5f;
                break;
            default:
                throw new RuntimeException("Unknown filter mode " + mNewFilter);
        }

        // Do we need a whole new program?  (We want to avoid doing this if we don't have
        // too -- compiling a program could be expensive.)
        if (programType != mFullScreen.getProgram().getProgramType()) {
            mFullScreen.changeProgram(new Texture2dProgram(programType));
            // If we created a new program, we need to initialize the texture width/height.
            mIncomingSizeUpdated = true;
        }

        // Update the filter kernel (if any).
        if (kernel != null) {
            mFullScreen.getProgram().setKernel(kernel, colorAdj);
        }

        mCurrentFilter = mNewFilter;
    }

    /**
     * Records the size of the incoming camera preview frames.
     * <p>
     * It's not clear whether this is guaranteed to execute before or after onSurfaceCreated(),
     * so we assume it could go either way.  (Fortunately they both run on the same thread,
     * so we at least know that they won't execute concurrently.)
     */
    public void setCameraPreviewSize(int width, int height) {
        Timber.d("setCameraPreviewSize");
        mIncomingWidth = width;
        mIncomingHeight = height;
        mIncomingSizeUpdated = true;
    }

    public void setVideoFrameSize(int width, int height) {
        mVideoFrameWidth = width;
        mVideoFrameHeight = height;
    }

    @Override
    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        Timber.d("onSurfaceCreated");

        // We're starting up or coming back. Either way we've got a new EGLContext that will
        // need to be shared with the video encoder, so figure out if a recording is already
        // in progress.
        mRecordingEnabled = mVideoEncoder.isRecording();
        if (mRecordingEnabled) {
            mRecordingStatus = RECORDING_RESUMED;
        } else {
            mRecordingStatus = RECORDING_OFF;
        }

        // Set up the texture glitter that will be used for on-screen display. This
        // is *not* applied to the recording, because that uses a separate shader.
        mFullScreen = new FullFrameRect(
                new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_EXT));

        mTextureId = mFullScreen.createTextureObject();

        // Create a SurfaceTexture, with an external texture, in this EGL context.  We don't
        // have a Looper in this thread -- GLSurfaceView doesn't create one -- so the frame
        // available messages will arrive on the main thread.
        mSurfaceTexture = new SurfaceTexture(mTextureId);

        // Tell the UI thread to enable the camera preview.
        if (mCameraId == 0) {
            mCameraHandler.sendMessage(mCameraHandler.obtainMessage(
                    CameraHandler.MSG_SET_SURFACE_TEXTURE, mSurfaceTexture));
        } else {
            mCameraHandler.sendMessage(mCameraHandler.obtainMessage(
                    CameraHandler.MSG_SET_SURFACE_TEXTURE2, mSurfaceTexture));
        }
    }

    @Override
    public void onSurfaceChanged(GL10 unused, int width, int height) {
        Timber.d("onSurfaceChanged %dx%d", width, height);
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR1)
    @Override
    public void onDrawFrame(GL10 unused) {
        if (VERBOSE) Timber.d("onDrawFrame tex=%d", mTextureId);
        boolean showBox;

        // Latch the latest frame.  If there isn't anything new, we'll just re-use whatever
        // was there before.
        mSurfaceTexture.updateTexImage();

        // If the recording state is changing, take care of it here.  Ideally we wouldn't
        // be doing all this in onDrawFrame(), but the EGLContext sharing with GLSurfaceView
        // makes it hard to do elsewhere.
        if (mRecordingEnabled) {
            switch (mRecordingStatus) {
                case RECORDING_OFF:
                    if (mVideoFrameWidth <= 0 || mVideoFrameHeight <= 0) {
                        Timber.i("Start recording before setting video frame size; skipping");
                        break;
                    }
                    Timber.d("Start recording outputFile: %s", mOutputFile);
                    // The output video has a size e.g., 720x1280. Video of the same size is recorded in
                    // the portrait mode of the complex CameraRecorder-android at
                    // https://github.com/MasayukiSuda/CameraRecorder-android.
                    mVideoEncoder.startRecording(
                            new TextureMovieEncoder.EncoderConfig(
                                    mOutputFile, mVideoFrameHeight, mVideoFrameWidth,
                                    CameraUtils.calcBitRate(mVideoFrameWidth, mVideoFrameHeight,
                                            VideoEncoderCore.FRAME_RATE),
                                    EGL14.eglGetCurrentContext(),
                                    mFullScreen.getProgram(), mMetadataFile));
                    mRecordingStatus = RECORDING_ON;
                    break;
                case RECORDING_RESUMED:
                    Timber.d("Resume recording");
                    mVideoEncoder.updateSharedContext(EGL14.eglGetCurrentContext());
                    mRecordingStatus = RECORDING_ON;
                    break;
                case RECORDING_ON:
                    break;
                default:
                    throw new RuntimeException("unknown status " + mRecordingStatus);
            }
        } else {
            switch (mRecordingStatus) {
                case RECORDING_ON:
                case RECORDING_RESUMED:
                    Timber.d("Stop recording");
                    mVideoEncoder.stopRecording();
                    mRecordingStatus = RECORDING_OFF;
                    break;
                case RECORDING_OFF:
                    break;
                default:
                    throw new RuntimeException("unknown status " + mRecordingStatus);
            }
        }

        // Set the video encoder's texture name.  We only need to do this once, but in the
        // current implementation it has to happen after the video encoder is started, so
        // we just do it here.
        mVideoEncoder.setTextureId(mTextureId);

        // Tell the video encoder thread that a new frame is available.
        // This will be ignored if we're not actually recording.
        mVideoEncoder.frameAvailable(mSurfaceTexture);

        if (mIncomingWidth <= 0 || mIncomingHeight <= 0) {
            // Texture size isn't set yet.  This is only used for the filters, but to be
            // safe we can just skip drawing while we wait for the various races to resolve.
            // (This seems to happen if you toggle the screen off/on with power button.)
            Timber.i("Drawing before incoming texture size set; skipping");
            return;
        }

        // Update the filter, if necessary.
        if (mCurrentFilter != mNewFilter) {
            updateFilter();
        }

        if (mIncomingSizeUpdated) {
            mFullScreen.getProgram().setTexSize(mIncomingWidth, mIncomingHeight);
            mIncomingSizeUpdated = false;
        }

        // Draw the video frame.
        mSurfaceTexture.getTransformMatrix(mSTMatrix);
        mFullScreen.drawFrame(mTextureId, mSTMatrix);

        // Draw a flashing box if we're recording. This only appears on screen.
        showBox = (mRecordingStatus == RECORDING_ON);
        if (showBox && (++mFrameCount & 0x04) == 0) {
            drawBox();
        }
    }

    /**
     * Draws a red box in the corner.
     */
    private void drawBox() {
        GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
        GLES20.glScissor(0, 0, 50, 50);
        GLES20.glClearColor(1.0f, 0.0f, 0.0f, 1.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
    }
}
