package com.example.alarmclock4jacw;

import android.content.Context;

import androidx.appcompat.app.AppCompatActivity;

/** 所有页面的基类：在这里统一套用应用内语言。 */
public abstract class BaseActivity extends AppCompatActivity {

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(AppLanguage.wrap(newBase));
    }
}
