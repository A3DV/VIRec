package io.a3dv.VIRec;

import android.util.Size;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import timber.log.Timber;

/**
 * Camera-related utility functions.
 */
public class CameraUtils {
    private static final float BPP = 0.25f;

    /**
     * In this sample, we choose a video size with 3x4 aspect ratio. Also, we don't use sizes
     * larger than 1080p, since MediaRecorder cannot handle such a high-resolution video.
     *
     * @param choices The list of available sizes
     * @return The video size
     */
    public static Size chooseVideoSize(
            Size[] choices, int wScale, int hScale, int maxWidth) {
        for (Size size : choices) {
            if (size.getWidth() == size.getHeight() * wScale / hScale &&
                    size.getWidth() <= maxWidth) {
                return size;
            }
        }
        Timber.e("Couldn't find any suitable video size");
        return choices[choices.length - 1];
    }

    /**
     * Compares two {@code Size}s based on their areas.
     */
    static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }

    }

    /**
     * Given {@code choices} of {@code Size}s supported by a camera, chooses the smallest one whose
     * width and height are at least as large as the respective requested values, and whose aspect
     * ratio matches with the specified value.
     *
     * @param choices     The list of sizes that the camera supports for the intended output class
     * @param width       The minimum desired width
     * @param height      The minimum desired height
     * @param aspectRatio The aspect ratio
     * @return The optimal {@code Size}, or an arbitrary one if none were big enough
     */
    public static Size chooseOptimalSize(Size[] choices, int width, int height, Size aspectRatio) {
        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getHeight() == option.getWidth() * h / w &&
                    option.getWidth() >= width && option.getHeight() >= height) {
                bigEnough.add(option);
            }
        }

        // Pick the smallest of those, assuming we found any
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else {
            Timber.e("Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    public static int calcBitRate(int width, int height, int frame_rate) {
        final int bitrate = (int) (BPP * frame_rate * width * height);
        Timber.i("bitrate=%d", bitrate);
        return bitrate;
    }
}
