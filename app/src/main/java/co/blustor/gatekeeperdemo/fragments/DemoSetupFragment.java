package co.blustor.gatekeeperdemo.fragments;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import com.neurotec.biometrics.NSubject;

import java.io.IOException;
import java.util.List;

import co.blustor.gatekeeper.biometrics.GKFaceExtractor;
import co.blustor.gatekeeper.scopes.GKAuthentication;
import co.blustor.gatekeeperdemo.R;
import co.blustor.gatekeeperdemo.scopes.DemoAuthentication;

public class DemoSetupFragment extends DemoFragment {
    public static final String TAG = DemoSetupFragment.class.getSimpleName();

    private static final int REQUEST_CAMERA_FOR_ENROLLMENT = 1;

    private final Object mSyncObject = new Object();

    private boolean mEnrollmentSynced;
    private boolean mCardReady;
    private boolean mHasCapturedTemplate;
    private boolean mHasDemoTemplate;

    private Button mCaptureNewTemplate;
    private Button mRemoveCapturedTemplate;
    private Button mAddDemoTemplate;
    private Button mRemoveDemoTemplate;

    private GKFaceExtractor mFaceExtractor;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_demo_setup, container, false);
        initializeAuthActions(view);
        return view;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        syncEnrollmentState();
        preloadFaceExtractor();
    }

    private void initializeAuthActions(View view) {
        mCaptureNewTemplate = (Button) view.findViewById(R.id.capture_new_template);
        mCaptureNewTemplate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestFacePhoto();
            }
        });
        mRemoveCapturedTemplate = (Button) view.findViewById(R.id.remove_captured_template);
        mRemoveCapturedTemplate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                deleteCapturedTemplate();
            }
        });
        mAddDemoTemplate = (Button) view.findViewById(R.id.add_demo_template);
        mAddDemoTemplate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addDemoTemplate();
            }
        });
        mRemoveDemoTemplate = (Button) view.findViewById(R.id.remove_demo_template);
        mRemoveDemoTemplate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                deleteDemoTemplate();
            }
        });
    }

    private void checkInitialization() {
        synchronized (mSyncObject) {
            if (mEnrollmentSynced && mCardReady && mFaceExtractor != null) {
                updateUI();
            }
        }
    }

    private void preloadFaceExtractor() {
        new AsyncTask<Void, Void, GKFaceExtractor>() {
            @Override
            protected GKFaceExtractor doInBackground(Void... params) {
                return new GKFaceExtractor();
            }

            @Override
            protected void onPostExecute(GKFaceExtractor faceExtractor) {
                synchronized (mSyncObject) {
                    mFaceExtractor = faceExtractor;
                }
                checkInitialization();
            }
        }.execute();
    }

    private void syncEnrollmentState() {
        synchronized (mSyncObject) {
            mHasDemoTemplate = false;
            mHasCapturedTemplate = false;
        }
        new AsyncTask<Void, Void, GKAuthentication.ListTemplatesResult>() {
            private IOException ioException;
            private final DemoAuthentication auth = new DemoAuthentication(mCard, getContext());

            @Override
            protected GKAuthentication.ListTemplatesResult doInBackground(Void... params) {
                try {
                    return auth.listTemplates();
                } catch (IOException e) {
                    ioException = e;
                    return null;
                }
            }

            @Override
            protected void onPostExecute(GKAuthentication.ListTemplatesResult result) {
                if (ioException != null) {
                    showRetryConnectDialog();
                } else {
                    mEnrollmentSynced = true;
                    List<Object> templates = result.getTemplates();
                    mHasDemoTemplate = templates.size() > 0;
                    for (Object template : templates) {
                        if (template.equals("face000")) {
                            mHasCapturedTemplate = true;
                        }
                    }
                }
                ensureCardAccess();
            }
        }.execute();
    }

    private void ensureCardAccess() {
        if (mHasDemoTemplate) {
            new AuthTask() {
                @Override
                protected GKAuthentication.Status perform() throws IOException {
                    return auth.signInWithDemoFace().getStatus();
                }

                @Override
                protected void onPostExecute(GKAuthentication.Status status) {
                    if (mIOException != null) {
                        showRetryConnectDialog();
                    } else {
                        mCardReady = true;
                        checkInitialization();
                    }
                }
            }.execute();
        } else {
            mCardReady = true;
            checkInitialization();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CAMERA_FOR_ENROLLMENT:
                if (resultCode == Activity.RESULT_OK) {
                    extractFaceData(data);
                } else {
                    updateUI();
                }
                break;
            default:
                super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void extractFaceData(final Intent data) {
        final Bundle extras = data.getExtras();

        disableUI();
        new AuthTask() {
            private final Bitmap bitmap = (Bitmap) extras.get("data");

            @Override
            protected GKAuthentication.Status perform() throws IOException {
                try {
                    NSubject subject = mFaceExtractor.getSubjectFromBitmap(bitmap);
                    if (subject != null) {
                        return auth.enrollWithFace(subject).getStatus();
                    } else {
                        return GKAuthentication.Status.BAD_TEMPLATE;
                    }
                } finally {
                    bitmap.recycle();
                }
            }

            @Override
            protected void onPostExecute(GKAuthentication.Status status) {
                super.onPostExecute(status);
                if (mIOException == null && status.equals(GKAuthentication.Status.SUCCESS)) {
                    mHasCapturedTemplate = true;
                }
                updateUI();
            }
        }.execute();
    }

    private void requestFacePhoto() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getActivity().getPackageManager()) != null) {
            startActivityForResult(takePictureIntent, REQUEST_CAMERA_FOR_ENROLLMENT);
        }
    }

    private void deleteCapturedTemplate() {
        disableUI();
        new AuthTask() {
            @Override
            protected GKAuthentication.Status perform() throws IOException {
                return auth.revokeFace();
            }

            @Override
            protected void onPostExecute(GKAuthentication.Status status) {
                super.onPostExecute(status);
                if (mIOException == null && status.equals(GKAuthentication.Status.SUCCESS)) {
                    mHasCapturedTemplate = false;
                }
                updateUI();
            }
        }.execute();
    }

    private void addDemoTemplate() {
        disableUI();
        new AuthTask() {
            @Override
            protected GKAuthentication.Status perform() throws IOException {
                return auth.enrollWithDemoFace().getStatus();
            }

            @Override
            protected void onPostExecute(GKAuthentication.Status status) {
                super.onPostExecute(status);
                if (mIOException == null && status.equals(GKAuthentication.Status.SUCCESS)) {
                    mHasDemoTemplate = true;
                }
                updateUI();
            }
        }.execute();
    }

    private void deleteDemoTemplate() {
        disableUI();
        new AuthTask() {
            @Override
            protected GKAuthentication.Status perform() throws IOException {
                return auth.revokeDemoFace();
            }

            @Override
            protected void onPostExecute(GKAuthentication.Status status) {
                super.onPostExecute(status);
                if (mIOException == null && status.equals(GKAuthentication.Status.SUCCESS)) {
                    mHasDemoTemplate = false;
                }
                updateUI();
            }
        }.execute();
    }

    private void updateUI() {
        mCaptureNewTemplate.setEnabled(!mHasCapturedTemplate && mHasDemoTemplate);
        mRemoveCapturedTemplate.setEnabled(mHasCapturedTemplate && mHasDemoTemplate);
        mAddDemoTemplate.setEnabled(!mHasDemoTemplate);
        mRemoveDemoTemplate.setEnabled(!mHasCapturedTemplate && mHasDemoTemplate);
    }

    private void disableUI() {
        mCaptureNewTemplate.setEnabled(false);
        mRemoveCapturedTemplate.setEnabled(false);
        mAddDemoTemplate.setEnabled(false);
        mRemoveDemoTemplate.setEnabled(false);
    }
}
