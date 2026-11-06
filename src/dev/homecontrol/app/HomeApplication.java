package dev.homecontrol.app;
import android.app.*;
import android.os.Bundle;
import java.util.*;
/** Only app-visible activities may retain an idle camera connection. */
public final class HomeApplication extends Application implements Application.ActivityLifecycleCallbacks {
 private final Set<Activity> started=Collections.newSetFromMap(new IdentityHashMap<Activity,Boolean>());
 @Override public void onCreate(){super.onCreate();registerActivityLifecycleCallbacks(this);}
 public void onActivityStarted(Activity a){started.add(a);CameraPool.foreground(true);}
 public void onActivityStopped(Activity a){started.remove(a);if(started.isEmpty())CameraPool.foreground(false);}
 public void onActivityCreated(Activity a,Bundle b){}public void onActivityResumed(Activity a){}public void onActivityPaused(Activity a){}public void onActivitySaveInstanceState(Activity a,Bundle b){}public void onActivityDestroyed(Activity a){started.remove(a);}
}
