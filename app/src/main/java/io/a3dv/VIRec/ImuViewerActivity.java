package io.a3dv.VIRec;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

public class ImuViewerActivity extends AppCompatActivity implements ImuViewFragment.OnListFragmentInteractionListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.menu_intent_activity);
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.menu_intent, new ImuViewFragment())
                    .commit();
        }
    }

    @Override
    public void onListFragmentInteraction(ImuViewContent.SingleAxis item) {

    }
}