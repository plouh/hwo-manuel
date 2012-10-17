package fi.hwo.manuel;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.MotionEvent;

public class HelloWorld extends Activity {

  protected int _splashTime = 5000;

  private Thread splashTread;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.splash);

    splashTread = new Thread() {
      @Override
      public void run() {
        try {
          synchronized(this){
            wait(_splashTime);
          }
        } catch(InterruptedException e) {}
        finally {
          finish();
          startActivity(new Intent(HelloWorld.this, Login.class));
        }
      }
    };

    splashTread.start();
  }
}