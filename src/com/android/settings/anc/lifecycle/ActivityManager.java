package com.android.settings.anc.lifecycle;

import android.app.Activity;
import java.util.LinkedList;

public class ActivityManager {
    private LinkedList<Activity> mActivityList = new LinkedList<>();
    private static ActivityManager mActivityManager;

    private ActivityManager() {
    }

    public static ActivityManager getInstance() {
        if (mActivityManager == null) {
            mActivityManager = new ActivityManager();
        }
        return mActivityManager;
    }

    public Activity getCurrentActivity() {
        if (mActivityList.isEmpty()) {
            return null;
        } else {
            return mActivityList.getLast();
        }
    }

    public void pushActivity(Activity activity) {
        if(activity != null && "UnlockActivity".equals(activity.getClass().getSimpleName())){
            return;
        }
        if (mActivityList.contains(activity)) {
            if (mActivityList.getLast() != activity) {
                mActivityList.remove(activity);
                mActivityList.add(activity);
            }
        } else {
            mActivityList.add(activity);
        }
    }

    public void popActivity(Activity activity) {
        if(activity != null && "UnlockActivity".equals(activity.getClass().getSimpleName())){
            return;
        }
        mActivityList.remove(activity);
    }

    public int getActivityListSize() {
        return mActivityList.size();
    }

    public boolean containActivity(String activityName){
        if(!mActivityList.isEmpty() && activityName != null){
            for(Activity activity : mActivityList){
                if(activityName.equals(activity.getClass().getSimpleName())){
                    return true;
                }
            }
        }
        return false;
    }
}
