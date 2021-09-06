package io.a3dv.VIRec;

import android.os.Bundle;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class AboutActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.about_activity);

        TextView hyperlink = findViewById(R.id.linkTextView);
        String linkText = getResources().getString(R.string.link_foreword);
        Spanned text = FileHelper.fromHtml(linkText + " " +
                "<a href='https://github.com/A3DV/VIRec'>GitHub</a>.");
        hyperlink.setMovementMethod(LinkMovementMethod.getInstance());
        hyperlink.setText(text);

        TextView versionName = findViewById(R.id.versionText);
        versionName.setText(getString(R.string.versionName, BuildConfig.VERSION_NAME));
    }
}
