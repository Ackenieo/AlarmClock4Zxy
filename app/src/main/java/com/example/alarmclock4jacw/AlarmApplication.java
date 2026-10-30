package com.example.alarmclock4jacw;

import android.app.Application;
import android.content.Context;

/** 进程级 Context 也套用语言，服务/通知取到的字符串才会跟着切换。 */
public class AlarmApplication extends Application {

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(AppLanguage.wrap(base));
    }
}
