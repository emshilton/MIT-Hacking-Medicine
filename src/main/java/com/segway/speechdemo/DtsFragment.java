package com.segway.speechdemo;

import android.app.Activity;
import android.app.Fragment;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.support.v13.app.FragmentCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.segway.speechdemo.comtroller.SimpleController;
import com.segway.robot.algo.dts.DTSPerson;
import com.segway.robot.algo.dts.PersonTrackingListener;
import com.segway.robot.sdk.base.bind.ServiceBinder;
import com.segway.robot.sdk.locomotion.head.Head;
import com.segway.robot.sdk.locomotion.sbv.Base;
import com.segway.robot.sdk.vision.DTS;
import com.segway.robot.sdk.vision.Vision;

public class DtsFragment extends Fragment
        implements View.OnClickListener, FragmentCompat.OnRequestPermissionsResultCallback {

    private static final String TAG = "DtsFragment";

    private static final int PREVIEW_WIDTH = 640;
    private static final int PREVIEW_HEIGHT = 480;

    private Vision mVision;
    private DTS mDTS;

    private Head mHead;
    private boolean mHeadBind;

    private Base mBase;
    private boolean mBaseBind;

    //private AutoFitDrawableView mTextureView;

    enum DtsState{
        STOP,
        DETECTING,
        TRACKING
    }

    boolean mHeadFollow;
    boolean mBaseFollow;

    DtsState mDtsState;
    SimpleController mController;

    private void showToast(final String text) {
        final Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(activity, text, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    public static DtsFragment newInstance() {
        return new DtsFragment();
    }

    private final TextureView.SurfaceTextureListener mSurfaceTextureListener
            = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
            bindServices();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture texture) {
        }

    };
/*
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        //return inflater.inflate(R.layout.fragment_camera2_basic, container, false);
        return;
    }
    */

    @Override
    public void onViewCreated(final View view, Bundle savedInstanceState) {
        view.findViewById(R.id.detect).setOnClickListener(this);
        view.findViewById(R.id.track).setOnClickListener(this);
        view.findViewById(R.id.head_follow).setOnClickListener(this);
        view.findViewById(R.id.base_follow).setOnClickListener(this);
       // mTextureView = (AutoFitDrawableView) view.findViewById(R.id.texture);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mVision = Vision.getInstance();
        mHead = Head.getInstance();
        mBase = Base.getInstance();
    }

    @Override
    public void onResume() {
        super.onResume();

//        if (mTextureView.getPreview().isAvailable()) {
//            bindServices();
//        } else {
//            int rotation = getActivity().getWindowManager().getDefaultDisplay().getRotation();
//            mTextureView.setPreviewSizeAndRotation(PREVIEW_WIDTH, PREVIEW_HEIGHT, rotation);
//            mTextureView.setSurfaceTextureListenerForPerview(mSurfaceTextureListener);
//        }
    }

    @Override
    public void onPause() {
        unbindServices();
        super.onPause();
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.move:{
                SimpleController controller = mController;
                mController = new SimpleController(new SimpleController.StateListener() {
                    @Override
                    public void onFinish() {
                        mController = null;
                    }
                }, mBase);
                controller = mController;
                controller.setTargetRobotPose(10, 0, 0);
                controller.updatePoseAndDistance();
                controller.startProcess();
            }
            case R.id.detect: {
                if (mDtsState != DtsState.DETECTING) {
                    mDtsState = DtsState.DETECTING;
                    showToast("Detecting person...");
                    new Thread() {
                        @Override
                        public void run() {
                            try {
                                Thread.sleep(1500);
                            } catch (InterruptedException ignored) {
                            }
                            DTSPerson[] dtsPersons = mDTS.detectPersons(5 * 1000 * 1000);
                            mDtsState = DtsState.STOP;
                            Rect[] rects = new Rect[dtsPersons.length];
                            for (int i = 0; i < dtsPersons.length; i++) {
                                rects[i] = dtsPersons[i].getDrawingRect();
                            }
                           // mTextureView.drawRect(rects);
                            showToast("Detect finish, " + dtsPersons.length + " person detected.");
                        }
                    }.start();
                }
                break;
            }
            case R.id.track: {
                if (mDtsState != DtsState.TRACKING) {
                    mDTS.startPersonTracking(null, 300 * 1000 * 100, mPersonTrackingListener);
                    mDtsState = DtsState.TRACKING;
                    showToast("Tracking...");
                } else {
                    mDTS.stopPersonTracking();
                    mDtsState = DtsState.STOP;
                    showToast("Stop Tracking");
                }
                break;
            }
            case R.id.head_follow: {
                if (!mHeadBind) {
                    showToast("Connect to Head First...");
                    return;
                }
                if (!mHeadFollow) {
                    showToast("Enable Head Follow");
                    mHeadFollow = true;
                } else {
                    showToast("Disable Head Follow");
                    mHeadFollow = false;
                }
                break;
            }
            case R.id.base_follow: {
                if (!mBaseBind) {
                    showToast("Connect to Base First...");
                    return;
                }
                if (!mBaseFollow) {
                    showToast("Enable Base Follow");
                    mBaseFollow = true;
                } else {
                    showToast("Disable Base Follow");
                    mBaseFollow = false;
                    mController = null;
                    mBase.stop();
                }
                break;
            }
        }
    }

    private void bindServices() {
        mVision.bindService(this.getActivity(), mVisionBindStateListener);
        mHead.bindService(this.getActivity(), mHeadBindStateListener);
        mBase.bindService(this.getActivity(), mBaseBindStateListener);
    }

    private void unbindServices() {
        if (mDTS != null) {
            mDTS.stop();
            mDTS = null;
        }
        mVision.unbindService();
        mHead.unbindService();
        mHeadBind = false;
        mBase.unbindService();
    }



    ServiceBinder.BindStateListener mVisionBindStateListener = new ServiceBinder.BindStateListener() {
        @Override
        public void onBind() {
            mDTS =  mVision.getDTS();
            mDTS.setVideoSource(DTS.VideoSource.CAMERA);
           // Surface surface = new Surface(mTextureView.getPreview().getSurfaceTexture());
           // mDTS.setPreviewDisplay(surface);
            mDTS.start();
        }

        @Override
        public void onUnbind(String reason) {
            mDTS = null;
        }
    };

    private ServiceBinder.BindStateListener mHeadBindStateListener = new ServiceBinder.BindStateListener() {
        @Override
        public void onBind() {
            mHeadBind = true;
            mHead.setMode(Head.MODE_SMOOTH_TACKING);
            mHead.setWorldPitch(0.3f);
        }

        @Override
        public void onUnbind(String reason) {
            mHeadBind = false;
        }
    };

    private ServiceBinder.BindStateListener mBaseBindStateListener = new ServiceBinder.BindStateListener() {
        @Override
        public void onBind() {
            mBaseBind = true;
        }

        @Override
        public void onUnbind(String reason) {
            mBaseBind = false;
        }
    };


    PersonTrackingListener mPersonTrackingListener = new PersonTrackingListener() {
        @Override
        public void onPersonTracking(final DTSPerson person) {
            Log.d(TAG, "onPersonTracking: " + person);
            if (person == null) {
                return;
            }
           // mTextureView.drawRect(person.getDrawingRect());
            if (mHeadFollow) {
                mHead.setMode(Head.MODE_SMOOTH_TACKING);
                mHead.setWorldYaw(person.getTheta()/2);
                mHead.setWorldPitch(person.getPitch());
            }
            if (mBaseFollow) {
                SimpleController controller = mController;
                if (controller == null) {
                    mController = new SimpleController(new SimpleController.StateListener() {
                        @Override
                        public void onFinish() {
                            mController = null;
                        }
                    }, mBase);
                    controller = mController;
                    controller.setTargetRobotPose(person.getX(), person.getY(), person.getTheta());
                    controller.updatePoseAndDistance();
                    controller.startProcess();
                } else {
                    controller.setTargetRobotPose(person.getX(), person.getY(), person.getTheta());
                    controller.updatePoseAndDistance();
                }
            }
        }

        @Override
        public void onPersonTrackingError(int errorCode, String message) {
            showToast("Person tracking error: code=" + errorCode + " message=" + message);
            mDtsState = DtsState.STOP;
        }


    };



}
