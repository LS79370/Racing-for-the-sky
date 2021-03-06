package com.cnam.enjmin.racingforthesky;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.ImageFormat;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.os.Build;
import android.support.annotation.NonNull;
import android.os.Bundle;
import android.util.Log;
import android.util.SparseArray;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import com.cnam.enjmin.racingforthesky.utils.PermissionsManager;
import com.cnam.enjmin.racingforthesky.utils.PythonLinker;
import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.MultiProcessor;
import com.google.android.gms.vision.Tracker;
import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.FaceDetector;
import com.google.android.gms.vision.face.Landmark;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.List;

import static android.graphics.Bitmap.createBitmap;
import static android.graphics.Bitmap.createScaledBitmap;
import static java.lang.Math.abs;

public class MainActivity extends Activity {

    protected static FaceDetector faceDetector = null;
    protected static GazeDetector gazeDetector = null;
    protected static Bitmap mBitmap;
    protected static Bitmap mEyeBitmap;
    protected static int[] mDebugArray;
    protected static byte[] mFrameArray;
    protected CameraSource mCameraSource = null;
    protected CameraSourcePreview mPreview;
    protected GraphicOverlay mGraphicOverlay;

    protected boolean DEBUG_MODE = true;
    protected int eyeRegionWidth = 80;
    protected int eyeRegionHeight = 60;
    protected int mDownSampleScale = 2;
    protected int mUpSampleScale = 4;
    protected int mDThresh = 10;
    protected double mGradThresh = 25.0;
    protected int iris_pixel = 0;
    protected int cx;
    protected int cy;
    protected int mUpThreshold = 8;
    protected int mDownThreshold = -4;
    protected int mLeftThreshold = 6;
    protected int mRightThreshold = -6;

    private PermissionsManager permManager;

    public static String IP = "192.168.43.67";
    public static int PORT = 8090;
    public static String REQUEST = "n";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // go full screen
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        // and hide the window title.
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        permManager = new PermissionsManager();
        permManager.getPermissions(this);   // NOTE: can *not* assume we actually have permissions after this call
        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        setContentView(R.layout.activity_main);

        // Face Tracking stuff
        //mPreview = new Preview(this, DrawOnTop); // TODO(fyordan): Probably need to create a better preview
        mPreview = (CameraSourcePreview) findViewById(R.id.preview);
        mGraphicOverlay = (GraphicOverlay) findViewById(R.id.faceOverlay);
        faceDetector = new FaceDetector.Builder(getApplicationContext())
                .setTrackingEnabled(true)
                .setLandmarkType(FaceDetector.ALL_LANDMARKS)
                .build();
        gazeDetector = new GazeDetector(faceDetector);
        gazeDetector.setProcessor(
                new MultiProcessor.Builder<>(new GraphicFaceTrackerFactory()).build());

        mCameraSource = new CameraSource.Builder(getApplicationContext(), gazeDetector)
               // .setRequestedPreviewSize(640, 480)
                .setFacing(CameraSource.CAMERA_FACING_FRONT)
                .setRequestedFps(30.0f)
                .build();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mPreview.stop();
    }

    // which means the CameraDevice has to be (re-)opened when the activity is (re-)started
    // (as long as we have permission to use the camera)

    @Override
    protected void onResume() {
        super.onResume();
        if (permManager.cameraPermissionGranted && (mCameraSource != null) && (mPreview != null)) {
            try {
                mPreview.start(mCameraSource, mGraphicOverlay);
            } catch(IOException e) {
                // WHO CARES
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mCameraSource.release();
    }

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }

    @Override
    // overrides method in android.app.Activity
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        String TAG = "onRequestPermitResult";
        //if (DBG) Log.w(TAG, "in onRequestPermissionsResult(...) (" + requestCode + ")");
        if (requestCode != permManager.REQ_PERMISSION_THISAPP) {    // check that this is a response to our request
            //Log.e(TAG, "Unexpected requestCode " + requestCode);    // can this happen?
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            return;
        }
        int n = grantResults.length;
        //if (DBG) Log.w(TAG, "requestCode=" + requestCode + " for " + n + " permissions");
        for (int i = 0; i < n; i++) {
            //if (DBG) Log.w(TAG, "permission " + permissions[i] + " " + grantResults[i]);
            switch (permissions[i]) {
                case Manifest.permission.CAMERA:
                    if (!(grantResults[i] == PackageManager.PERMISSION_GRANTED))
                    {
                        String str = "You must grant CAMERA permission to use the camera!";
                        Log.d(TAG, str);
                    }
                    break;
            }
        }
    }

    //==============================================================================================
    // Graphic Face Tracker (Stolen from google)
    //==============================================================================================

    /**
     * Factory for creating a face tracker to be associated with a new face.  The multiprocessor
     * uses this factory to create face trackers as needed -- one for each individual.
     */
    private class GraphicFaceTrackerFactory implements MultiProcessor.Factory<Face> {
        @Override
        public Tracker<Face> create(Face face) {
            return new GraphicFaceTracker(mGraphicOverlay);
        }
    }

    /**
     * Face tracker for each detected individual. This maintains a face graphic within the app's
     * associated face overlay.
     */
    private class GraphicFaceTracker extends Tracker<Face> {
        private GraphicOverlay mOverlay;
        private FaceGraphic mFaceGraphic;

        GraphicFaceTracker(GraphicOverlay overlay) {
            mOverlay = overlay;
            mFaceGraphic = new FaceGraphic(overlay);
        }


        /**
         * Update the position/characteristics of the face within the overlay.
         */
        @Override
        public void onUpdate(FaceDetector.Detections<Face> detectionResults, Face face) {
            mOverlay.add(mFaceGraphic);
            mFaceGraphic.updateFace(face);
        }

        /**
         * Hide the graphic when the corresponding face was not detected.  This can happen for
         * intermediate frames temporarily (e.g., if the face was momentarily blocked from
         * view).
         */
        @Override
        public void onMissing(FaceDetector.Detections<Face> detectionResults) {
            mOverlay.remove(mFaceGraphic);
        }

        /**
         * Called when the face is assumed to be gone for good. Remove the graphic annotation from
         * the overlay.
         */
        @Override
        public void onDone() {
            mOverlay.remove(mFaceGraphic);
        }
    }

    /**
     * Graphic instance for rendering face position, orientation, and landmarks within an associated
     * graphic overlay view.
     */
    class FaceGraphic extends GraphicOverlay.Graphic {
        private static final float ID_TEXT_SIZE = 40.0f;
        private static final float BOX_STROKE_WIDTH = 5.0f;

        private final int COLOR_CHOICES[] = {
                Color.BLUE,
                Color.CYAN,
                Color.GREEN,
                Color.MAGENTA,
                Color.RED,
                Color.WHITE,
                Color.YELLOW
        };
        private int mCurrentColorIndex = 0;

        private Paint mFacePositionPaint;
        private Paint mIdPaint;
        private Paint mBoxPaint;

        private volatile Face mFace;

        FaceGraphic(GraphicOverlay overlay) {
            super(overlay);

            mCurrentColorIndex = (mCurrentColorIndex + 1) % COLOR_CHOICES.length;
            final int selectedColor = COLOR_CHOICES[mCurrentColorIndex];

            mFacePositionPaint = new Paint();
            mFacePositionPaint.setColor(selectedColor);

            mIdPaint = new Paint();
            mIdPaint.setColor(selectedColor);
            mIdPaint.setTextSize(ID_TEXT_SIZE);

            mBoxPaint = new Paint();
            mBoxPaint.setColor(selectedColor);
            mBoxPaint.setStyle(Paint.Style.STROKE);
            mBoxPaint.setStrokeWidth(BOX_STROKE_WIDTH);
        }

        /**
         * Updates the face instance from the detection of the most recent frame.  Invalidates the
         * relevant portions of the overlay to trigger a redraw.
         */
        void updateFace(Face face) {
            mFace = face;
            postInvalidate();
        }

        /**
         * Draws the face annotations for position on the supplied canvas.
         */
        @Override
        public void draw(Canvas canvas)
        {
            Face face = mFace;
            if (face == null)
            {
                return;
            }

            float x = translateX(face.getPosition().x + face.getWidth() / 2);
            float y = translateY(face.getPosition().y + face.getHeight() / 2);


            if (mEyeBitmap != null && DEBUG_MODE)
            {
                Bitmap debugBitmap = createBitmap(mEyeBitmap.getWidth()-2, mEyeBitmap.getHeight()-2, Bitmap.Config.ARGB_8888);//BitmapFactory.decodeByteArray(mDebugArray, 0, mDebugArray.length);
                debugBitmap.copyPixelsFromBuffer(IntBuffer.wrap(mDebugArray));
                Bitmap resizedBitmap = Bitmap.createScaledBitmap(
//                        mEyeBitmap,
                        debugBitmap,
                        eyeRegionWidth*mUpSampleScale,
                        eyeRegionHeight*mUpSampleScale,
                        false);
                canvas.drawBitmap(resizedBitmap, 0, 0, mBoxPaint);
                int iris_x = iris_pixel%mEyeBitmap.getWidth()*mDownSampleScale*mUpSampleScale;
                int iris_y = iris_pixel/mEyeBitmap.getWidth()*mDownSampleScale*mUpSampleScale;
                canvas.drawCircle(iris_x, iris_y, mDownSampleScale*mUpSampleScale*mDThresh, mBoxPaint);
            }

            for (Landmark landmark : face.getLandmarks())
            {
                int landmark_type = landmark.getType();
                if (landmark_type == Landmark.LEFT_EYE)
                {
                    cx = (int) translateX(landmark.getPosition().x);
                    cy = (int) translateY(landmark.getPosition().y);
                    Paint paint = new Paint();
                    paint.setColor(Color.GREEN);
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(5);

                    int eye_region_left = (int)landmark.getPosition().x-eyeRegionWidth/2;
                    eye_region_left = abs(eye_region_left);
                    int eye_region_top = (int)landmark.getPosition().y-eyeRegionHeight/2;
                    eye_region_top = abs(eye_region_top);

                    if(eye_region_top + eyeRegionHeight > mBitmap.getHeight())
                    {
                        eye_region_top = eye_region_top + eyeRegionHeight - mBitmap.getHeight();
                    }

                    mEyeBitmap = toGrayscale(
                            createBitmap(mBitmap,
                                    eye_region_left,
                                    eye_region_top,
                                    eyeRegionWidth, eyeRegionHeight));

                    mEyeBitmap = createScaledBitmap(mEyeBitmap,
                            eyeRegionWidth/mDownSampleScale,
                            eyeRegionHeight/mDownSampleScale,
                            true);

                    iris_pixel = calculateEyeCenter(mEyeBitmap, mGradThresh, mDThresh);
                }
            }
            if (mEyeBitmap != null)
            {
                int x_gaze = iris_pixel%mEyeBitmap.getWidth() - mEyeBitmap.getWidth()/2;
                int y_gaze = mEyeBitmap.getHeight()/2 - iris_pixel/mEyeBitmap.getWidth();

                Log.d("Eyes Gaze", "X : " + x_gaze + ", Y : " + y_gaze);

                if(x_gaze < mRightThreshold)
                {
                    REQUEST = "r";
                    Log.d("Eye Tracking", "Right");
                }
                if (x_gaze > mLeftThreshold)
                {
                    REQUEST = "l";
                    Log.d("Eye Tracking", "Left");
                }
                if (y_gaze > mUpThreshold)
                {
                    REQUEST = "u";
                    Log.d("Eye Tracking", "Up");
                }
                if (y_gaze < mDownThreshold)
                {
                    REQUEST = "d";
                    Log.d("Eye Tracking", "Down");
                }
                PythonLinker linker = new PythonLinker();
                linker.execute();
            }
        }

        protected int calculateEyeCenter(Bitmap eyeMap, double gradientThreshold, int d_thresh) {
            // TODO(fyordan): Shouldn't use mImageWidth and mImageHeight, but grayData dimensions.
            // Calculate gradients.
            // Ignore edges of image to not deal with boundaries.

            //Log.d("CalculateEyeCenter", "Well it entered");
            int imageWidth = eyeMap.getWidth();
            int imageHeight = eyeMap.getHeight();
            int grayData[] = new int[imageWidth*imageHeight];
            double mags[] = new double[(imageWidth-2)*(imageHeight-2)];
            //Log.d("CalculateEyeCenter", "Size is : " + imageWidth*imageHeight);
            eyeMap.getPixels(grayData, 0, imageWidth, 0, 0, imageWidth, imageHeight);
            double[][] gradients = new double[(imageWidth-2)*(imageHeight-2)][2];
            int k = 0;
            int magCount = 0;
            mDebugArray = new int[(imageWidth-2)*(imageHeight-2)];
            for(int j=1; j < imageHeight-1; j++) {
                for (int i=1; i < imageWidth-1; i++) {
                    int n = j*imageWidth + i;
                    gradients[k][0] = (grayData[n+1] & 0xff) - (grayData[n] & 0xff);
                    gradients[k][1] = (grayData[n + imageWidth] & 0xff) - (grayData[n] & 0xff);
                    double mag = Math.sqrt(Math.pow(gradients[k][0],2) + Math.pow(gradients[k][1],2));
                    mags[k] = mag;
                    mDebugArray[k] = grayData[n];
                    if (mag > gradientThreshold) {
                        gradients[k][0] /= mag;
                        gradients[k][1] /= mag;
                        magCount++;
                        mDebugArray[k] = 0xffffffff;
                    } else {
                        gradients[k][0] = 0;
                        gradients[k][1] = 0;
                    }
                    k++;
                }
            }

            int c_n = gradients.length/2;
            double max_c = 0;
            for (int i=1; i < imageWidth-1; i++) {
                for (int j=1; j < imageHeight-1; j++) {
                    int n = j*imageWidth + i;
                    int k_left = Math.max(0, i - d_thresh - 1);
                    int k_right= Math.min(imageWidth-2, i+d_thresh-1);
                    int k_top = Math.max(0, j - d_thresh-1);
                    int k_bottom = Math.min(imageHeight-2, j+d_thresh-1);
                    double sumC = 0;
                    for (int k_h = k_top; k_h < k_bottom; ++k_h) {
                        for (int k_w = k_left; k_w < k_right; ++k_w) {
                            k = k_w + k_h*(imageWidth-2);
                            if ((gradients[k][0] == 0 && gradients[k][1] == 0)) continue;
                            double d_i = k_w - i;
                            double d_j = k_h - j;
                            if (abs(d_i) > d_thresh || abs(d_j) > d_thresh) continue;
                            double mag = Math.sqrt(Math.pow(d_i, 2) + Math.pow(d_j, 2));
                            if (mag > d_thresh) continue;
                            mag = mag == 0 ? 1 : mag;
                            d_i /= mag;
                            d_j /= mag;
                            sumC += Math.pow(d_i * gradients[k][0] + d_j * gradients[k][1], 2);
                        }
                    }
                    // TODO(fyordan): w_c should be the value in a gaussian filtered graydata
                    sumC /= (grayData[n] & 0xff);
                    if (sumC > max_c) {
                        c_n = n;
                        max_c = sumC;
                    }
                }
            }
            return c_n;
        }
    }

    protected Bitmap toGrayscale(Bitmap bmp){
        Bitmap grayscale = createBitmap(bmp.getWidth(), bmp.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(grayscale);
        Paint paint=new Paint();
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0);
        ColorMatrixColorFilter f = new ColorMatrixColorFilter(cm);
        paint.setColorFilter(f);
        c.drawBitmap(bmp, 0, 0, paint);
        return grayscale;
    }

    class GazeDetector extends Detector<Face> {
        private Detector<Face> mDelegate;

        GazeDetector(Detector<Face> delegate) {
            mDelegate = delegate;
        }

        public SparseArray<Face> detect(Frame frame) {
            int w = frame.getMetadata().getWidth();
            int h = frame.getMetadata().getHeight();
            YuvImage yuvimage=new YuvImage(frame.getGrayscaleImageData().array(), ImageFormat.NV21, w, h, null);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            yuvimage.compressToJpeg(
                    new Rect(0, 0, w, h), 100, baos); // Where 100 is the quality of the generated jpeg
            mFrameArray = baos.toByteArray();
            mBitmap = BitmapFactory.decodeByteArray(mFrameArray, 0, mFrameArray.length);
            return mDelegate.detect(frame);
        }

        public boolean isOperational() {
            return mDelegate.isOperational();
        }

        public boolean setFocus(int id) {
            return mDelegate.setFocus(id);
        }
    }
}
