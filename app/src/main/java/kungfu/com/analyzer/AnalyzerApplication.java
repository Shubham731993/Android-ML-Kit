package kungfu.com.analyzer;

import android.app.Application;

import com.google.firebase.FirebaseApp;

/**
 * Created by shubham.srivastava on 25/05/18.
 */

public class AnalyzerApplication extends Application {
  @Override
  public void onCreate() {
    super.onCreate();
    FirebaseApp.initializeApp(getApplicationContext());
  }
}
