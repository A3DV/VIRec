# Visual-Inertial Recorder (VIRec)

Record camera frames at ~30fps from one or two camera sensors, Inertial Measurement Unit (IMU) measurements at ~100Hz and GPS locations data synced to one clock source on Android devices.

The app is released at [here](https://github.com/A3DV/VIRec/releases).

## Description

The app is developed from the [Grafika](https://github.com/google/grafika) project.

* Camera frames are saved into H.264/MP4 videos by using the Camera2 API (setRepeatingRequest, onCaptureCompleted), OpenGL ES (GLSurfaceView and GLSurfaceView.Renderer), and MediaCodec and MediaMuxer.
* The metadata for camera frames are saved to a csv.
* The timestamps for each camera frame are saved to a txt.
* IMU & GPS data are recorded on a background HandlerThread.

## Features

* ~30fps camera frames, 100Hz IMU measurements and GPS data
* The visual, inertial and location data are synchronized to one clock.
* The focal length in pixels and exposure duration are recorded.
* Dual camera recording at the same time.
* Select from multiple filters to apply it to the recorded video.

## Citing
Please adequately refer to the papers any time this Work is being used. If you do publish a paper where this Work helped your research, Please cite the following papers in your publications.

```
@misc{samadzadeh2024amirkabir,
    title={Amirkabir campus dataset: Real-world challenges and scenarios of Visual Inertial Odometry (VIO) for visually impaired people},
    author={Ali Samadzadeh and Mohammad Hassan Mojab and Heydar Soudani and Seyed Hesamoddin Mireshghollah and Ahmad Nickabadi},
    year={2024},
    eprint={2401.03604},
    archivePrefix={arXiv},
    primaryClass={cs.CV}
}
```
