package it.smg.hu.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import it.smg.hu.R;
import it.smg.hu.projection.InputDevice;
import it.smg.hu.ui.settings.AdvancedFragment;
import it.smg.hu.ui.settings.CarFragment;
import it.smg.hu.ui.settings.ConnectivityFragment;
import it.smg.hu.ui.settings.KeymapFragment;
import it.smg.hu.ui.settings.VideoFragment;
import it.smg.libs.common.Log;

public class SettingsActivity extends FragmentActivity implements InputDevice.OnKeyHolder {
    private static final String TAG = "SettingsActivity";
    private View.OnKeyListener keyListener_;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        findViewById(R.id.settingsBackBtn).setOnClickListener(v -> onBackPressed());
        findViewById(R.id.car_settings).setOnClickListener(v -> showSettings(new CarFragment(), R.string.car_settings));
        findViewById(R.id.video_settings).setOnClickListener(v -> showSettings(new VideoFragment(), R.string.video_settings));
        findViewById(R.id.conn_settings).setOnClickListener(v -> showSettings(new ConnectivityFragment(), R.string.conn_settings));
        findViewById(R.id.keymap_settings).setOnClickListener(v -> showSettings(new KeymapFragment(), R.string.keymap_settings));
        findViewById(R.id.advanced_settings).setOnClickListener(v -> showSettings(new AdvancedFragment(), R.string.advanced_settings));

        if (savedInstanceState == null) {
            showSettings(new CarFragment(), R.string.car_settings);
        }
    }

    private void showSettings(Fragment fragment, int titleResource) {
        ((TextView) findViewById(R.id.settingsTitle)).setText(titleResource);
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.main_content, fragment)
                .commit();
    }

    @Override
    public void onBackPressed() {
        setResult(Activity.RESULT_OK, new Intent());
        super.onBackPressed();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (Log.isDebug()) Log.d(TAG, "onKeyDown: " + keyCode);
        return keyListener_ != null ? keyListener_.onKey(null, keyCode, event) : super.onKeyDown(keyCode, event);
    }

    @Override
    public void setOnKeyListener(View.OnKeyListener listener) {
        keyListener_ = listener;
    }
}
