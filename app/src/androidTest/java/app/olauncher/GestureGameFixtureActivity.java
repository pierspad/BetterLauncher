package app.olauncher;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;

/** Test APK only: game-category screen for end-to-end gesture and tap checks. */
@SuppressWarnings("deprecation")
@SuppressLint("SetTextI18n")
public final class GestureGameFixtureActivity extends Activity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        TextView count = new TextView(this);
        count.setId(android.R.id.text1);
        count.setTextColor(Color.WHITE);
        count.setTextSize(24);
        count.setText("Taps: 0");
        root.addView(count, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        final int[] taps = {0};
        Button button = new Button(this);
        button.setText("Edge tap test");
        button.setOnClickListener(view -> count.setText("Taps: " + (++taps[0])));
        root.addView(button, new FrameLayout.LayoutParams(-1,
            (int)(64 * getResources().getDisplayMetrics().density), Gravity.BOTTOM));
        setContentView(root);
    }
}
