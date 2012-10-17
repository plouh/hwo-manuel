package fi.hwo.manuel;

import android.app.Activity;
import android.content.res.AssetManager;
import android.graphics.Typeface;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import de.tavendo.autobahn.WebSocketConnection;
import de.tavendo.autobahn.WebSocketException;
import de.tavendo.autobahn.WebSocketHandler;
import org.json.JSONArray;
import org.json.JSONObject;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.Timer;
import java.util.TimerTask;

public class Manuel extends Activity
        implements SensorEventListener, TCPClient.OnMessageReceived, Handler.Callback {

  private static final String TAG = "Manuel";

  private SensorManager mSensorManager = null;

  // angular speeds from gyro
  private float[] gyro = new float[3];

  // rotation matrix from gyro data
  private float[] gyroMatrix = new float[9];

  // orientation angles from gyro matrix
  private float[] gyroOrientation = new float[3];

  // magnetic field vector
  private float[] magnet = new float[3];

  // accelerometer vector
  private float[] accel = new float[3];

  // orientation angles from accel and magnet
  private float[] accMagOrientation = new float[3];

  // final orientation angles from sensor fusion
  private float[] fusedOrientation = new float[3];

  // accelerometer and magnetometer based rotation matrix
  private float[] rotationMatrix = new float[9];

  public static final float EPSILON = 0.000000001f;
  private static final float NS2S = 1.0f / 1000000000.0f;
  private float timestamp;
  private boolean initState = true;

  public static final int TIME_CONSTANT = 30;
  public static final float FILTER_COEFFICIENT = 0.98f;
  private Timer fuseTimer = new Timer();

  // The following members are only for displaying the sensor output.
  public Handler mHandler;
  private TextView left;
  private TextView leftScore;
  private int leftWins = 0;
  private TextView right;
  private TextView rightScore;
  private int rightWins = 0;

  DecimalFormat d = new DecimalFormat("#.##");
  private TCPClient client;
  private WebSocketConnection connection =  new WebSocketConnection();

  private boolean gameIsOn = false;

  private int missilesReady = 0;
  private boolean fired = false;

//  private MediaPlayer player;
//  private MediaPlayer effects;
  private Typeface font;
  private String leftUser = "";
  private String rightUser = "  ";

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.main);

    font = Typeface.createFromAsset(this.getAssets(), "PressStart2P-Regular.ttf");
//    player = MediaPlayer.create(getApplicationContext(), R.raw.game);

    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

    gyroOrientation[0] = 0.0f;
    gyroOrientation[1] = 0.0f;
    gyroOrientation[2] = 0.0f;

    // initialise gyroMatrix with identity matrix
    gyroMatrix[0] = 1.0f; gyroMatrix[1] = 0.0f; gyroMatrix[2] = 0.0f;
    gyroMatrix[3] = 0.0f; gyroMatrix[4] = 1.0f; gyroMatrix[5] = 0.0f;
    gyroMatrix[6] = 0.0f; gyroMatrix[7] = 0.0f; gyroMatrix[8] = 1.0f;

    // get sensorManager and initialise sensor listeners
    mSensorManager = (SensorManager) this.getSystemService(SENSOR_SERVICE);
    initListeners();

    // wait for one second until gyroscope and magnetometer/accelerometer
    // data is initialised then scedule the complementary filter task
    fuseTimer.scheduleAtFixedRate(new calculateFusedOrientationTask(),
            1000, TIME_CONSTANT);

    // GUI stuff
    mHandler = new Handler(this);
    d.setRoundingMode(RoundingMode.HALF_UP);
    d.setMaximumFractionDigits(3);
    d.setMinimumFractionDigits(3);
    left = (TextView)findViewById(R.id.playerLeft);
    leftScore = (TextView)findViewById(R.id.leftScore);
    right = (TextView)findViewById(R.id.playerRight);
    rightScore = (TextView)findViewById(R.id.rightScore);

    setFont(R.id.playerLeft, R.id.leftScore, R.id.playerRight, R.id.rightScore);

    Button b = (Button) findViewById(R.id.tulta);
    b.setOnTouchListener(new View.OnTouchListener() {
      @Override
      public boolean onTouch(View view, MotionEvent motionEvent) {
        fired = (motionEvent.getAction() != 1);
        return false;
      }
    });

    final String server = getIntent().getStringExtra("server");
    final String username = getIntent().getStringExtra("username");
    final String opponent = getIntent().getStringExtra("opponent");
    final String visuServer = getIntent().getStringExtra("visuServer");

    Log.i(TAG, "connecting " + username + " to ws://" + server);
    client = new TCPClient("10.0.1.5", 9090, this);

    try {
      if (visuServer == null || visuServer.trim().equals("")) {
        connect(username, opponent);
      } else {
        connection.connect("ws://" + visuServer, new WebSocketHandler() {
          @Override
          public void onOpen() {
            Log.d(TAG, "connected visualizer");
            connect(username, opponent);
          }

          @Override
          public void onTextMessage(String payload) {
            Log.d(TAG, "Received message " + payload);
          }

          @Override
          public void onClose(int code, String reason) {
            super.onClose(code, reason);
            Log.d(TAG, "closed because " + code + ": " + reason);
          }
        });
      }
    } catch (WebSocketException e) {
      Log.e(TAG, "Visu connection error", e);
    }

  }

  private void connect(String username, String opponent) {
    try {
      new Thread(client).start();
      Thread.sleep(1000);
      mHandler.sendEmptyMessageDelayed(SEND_MESSAGE, 60);
//      player.start();
    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    if (opponent == null || opponent.trim().equals(""))
      client.sendMessage("{\"msgType\":\"join\",\"data\":\"" + username + "\"}");
    else
      client.sendMessage("{\"msgType\":\"requestDuel\",\"data\":[\"" + username + "\", \"" + opponent + "\"]}");
  }

  private static final int SEND_MESSAGE = 100;

  @Override
  public boolean handleMessage(Message message) {
    switch (message.what) {
      case SEND_MESSAGE:
        sendMessage();
        return true;
      default:
        return false;
    }
  }

  private void sendMessage() {
    if (gameIsOn) {
      Log.d(TAG, "SendMessage!");
      if (missilesReady > 0 && fired) {
        missilesReady--;
        fired = false;
        client.sendMessage("{\"msgType\":\"launchMissile\"}");
      } else {
        client.sendMessage("{\"msgType\":\"changeDir\",\"data\":" + formatActionJson() + "}");
      }

      mHandler.removeMessages(SEND_MESSAGE);
      mHandler.sendEmptyMessageDelayed(SEND_MESSAGE, 60);
    }
  }

  @Override
  public void messageReceived(String message) {
    Log.i(TAG, message);
    try {
      final JSONObject object = new JSONObject(message);
      String msgType = object.getString("msgType");
      if (msgType.equals("gameStarted")) {
        Log.v(TAG, "Game started");
        gameIsOn = true;
        JSONArray players = object.getJSONArray("data");
        leftUser = players.getString(0);
        rightUser = players.getString(1);
        mHandler.post(new Runnable() {
          @Override
          public void run() {
            updatePlayernames(leftUser, rightUser);
          }
        });
        mHandler.removeMessages(SEND_MESSAGE);
        mHandler.sendEmptyMessage(SEND_MESSAGE);
      } else if (msgType.equals("gameIsOver")) {
        gameIsOn = false;
        final String winner = object.getString("data");
        mHandler.post(new Runnable() {
          @Override
          public void run() {
            updatePlayerScores(winner);
          }
        });
      }
      else if (msgType.equals("missileReady")) {
        missilesReady++;
      }
      else if (msgType.equals("joined")) {
        Log.d(TAG, "Visulator at " + object.getString("data"));
        if (connection.isConnected())
          connection.sendTextMessage(object.getString("data"));
      }
    } catch (Exception e) {
      Log.e(TAG, "error", e);
    }
  }

  @Override
  public void disconnected() {
    Log.v(TAG, "disconnected");
    gameIsOn = false;
    finish();
  }

  private void updatePlayerScores(String winner) {
    if (leftUser.equals(winner)) leftWins++;
    if (rightUser.equals(winner)) rightWins++;
    leftScore.setText("" + leftWins);
    rightScore.setText("" + rightWins);
  }

  private void updatePlayernames(String leftUser, String rightUser) {
    left.setText(leftUser);
    right.setText(rightUser);
  }

  @Override
  public void onStop() {
    super.onStop();
    Log.v(TAG, "pstoped");
    // unregister sensor listeners to prevent the activity from draining the device's battery.
    mSensorManager.unregisterListener(this);
  }

  @Override
  protected void onPause() {
    super.onPause();
    Log.v(TAG, "pasued");
    client.stopClient();
    connection.disconnect();
//    player.stop();
    // unregister sensor listeners to prevent the activity from draining the device's battery.
    mSensorManager.unregisterListener(this);
  }

  @Override
  public void onResume() {
    super.onResume();
    // restore the sensor listeners when user resumes the application.
    initListeners();
  }

  // This function registers sensor listeners for the accelerometer, magnetometer and gyroscope.
  public void initListeners(){
    mSensorManager.registerListener(this,
            mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
            SensorManager.SENSOR_DELAY_GAME);

    mSensorManager.registerListener(this,
            mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE),
            SensorManager.SENSOR_DELAY_GAME);

    mSensorManager.registerListener(this,
            mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD),
            SensorManager.SENSOR_DELAY_GAME);
  }

  @Override
  public void onAccuracyChanged(Sensor sensor, int accuracy) {}

  @Override
  public void onSensorChanged(SensorEvent event) {
    switch(event.sensor.getType()) {
      case Sensor.TYPE_ACCELEROMETER:
        // copy new accelerometer data into accel array and calculate orientation
        System.arraycopy(event.values, 0, accel, 0, 3);
        calculateAccMagOrientation();
        break;

      case Sensor.TYPE_GYROSCOPE:
        // process gyro data
        gyroFunction(event);
        break;

      case Sensor.TYPE_MAGNETIC_FIELD:
        // copy new magnetometer data into magnet array
        System.arraycopy(event.values, 0, magnet, 0, 3);
        break;
    }
  }

  // calculates orientation angles from accelerometer and magnetometer output
  public void calculateAccMagOrientation() {
    if(SensorManager.getRotationMatrix(rotationMatrix, null, accel, magnet)) {
      SensorManager.getOrientation(rotationMatrix, accMagOrientation);
    }
  }

  // This function is borrowed from the Android reference
  // at http://developer.android.com/reference/android/hardware/SensorEvent.html#values
  // It calculates a rotation vector from the gyroscope angular speed values.
  private void getRotationVectorFromGyro(float[] gyroValues,
                                         float[] deltaRotationVector,
                                         float timeFactor)
  {
    float[] normValues = new float[3];

    // Calculate the angular speed of the sample
    float omegaMagnitude =
            (float)Math.sqrt(gyroValues[0] * gyroValues[0] +
                    gyroValues[1] * gyroValues[1] +
                    gyroValues[2] * gyroValues[2]);

    // Normalize the rotation vector if it's big enough to get the axis
    if(omegaMagnitude > EPSILON) {
      normValues[0] = gyroValues[0] / omegaMagnitude;
      normValues[1] = gyroValues[1] / omegaMagnitude;
      normValues[2] = gyroValues[2] / omegaMagnitude;
    }

    // Integrate around this axis with the angular speed by the timestep
    // in order to get a delta rotation from this sample over the timestep
    // We will convert this axis-angle representation of the delta rotation
    // into a quaternion before turning it into the rotation matrix.
    float thetaOverTwo = omegaMagnitude * timeFactor;
    float sinThetaOverTwo = (float)Math.sin(thetaOverTwo);
    float cosThetaOverTwo = (float)Math.cos(thetaOverTwo);
    deltaRotationVector[0] = sinThetaOverTwo * normValues[0];
    deltaRotationVector[1] = sinThetaOverTwo * normValues[1];
    deltaRotationVector[2] = sinThetaOverTwo * normValues[2];
    deltaRotationVector[3] = cosThetaOverTwo;
  }

  // This function performs the integration of the gyroscope data.
  // It writes the gyroscope based orientation into gyroOrientation.
  public void gyroFunction(SensorEvent event) {
    // don't start until first accelerometer/magnetometer orientation has been acquired
    if (accMagOrientation == null)
      return;

    // initialisation of the gyroscope based rotation matrix
    if(initState) {
      float[] initMatrix = new float[9];
      initMatrix = getRotationMatrixFromOrientation(accMagOrientation);
      float[] test = new float[3];
      SensorManager.getOrientation(initMatrix, test);
      gyroMatrix = matrixMultiplication(gyroMatrix, initMatrix);
      initState = false;
    }

    // copy the new gyro values into the gyro array
    // convert the raw gyro data into a rotation vector
    float[] deltaVector = new float[4];
    if(timestamp != 0) {
      final float dT = (event.timestamp - timestamp) * NS2S;
      System.arraycopy(event.values, 0, gyro, 0, 3);
      getRotationVectorFromGyro(gyro, deltaVector, dT / 2.0f);
    }

    // measurement done, save current time for next interval
    timestamp = event.timestamp;

    // convert rotation vector into rotation matrix
    float[] deltaMatrix = new float[9];
    SensorManager.getRotationMatrixFromVector(deltaMatrix, deltaVector);

    // apply the new rotation interval on the gyroscope based rotation matrix
    gyroMatrix = matrixMultiplication(gyroMatrix, deltaMatrix);

    // get the gyroscope based orientation from the rotation matrix
    SensorManager.getOrientation(gyroMatrix, gyroOrientation);
  }

  private float[] getRotationMatrixFromOrientation(float[] o) {
    float[] xM = new float[9];
    float[] yM = new float[9];
    float[] zM = new float[9];

    float sinX = (float)Math.sin(o[1]);
    float cosX = (float)Math.cos(o[1]);
    float sinY = (float)Math.sin(o[2]);
    float cosY = (float)Math.cos(o[2]);
    float sinZ = (float)Math.sin(o[0]);
    float cosZ = (float)Math.cos(o[0]);

    // rotation about x-axis (pitch)
    xM[0] = 1.0f; xM[1] = 0.0f; xM[2] = 0.0f;
    xM[3] = 0.0f; xM[4] = cosX; xM[5] = sinX;
    xM[6] = 0.0f; xM[7] = -sinX; xM[8] = cosX;

    // rotation about y-axis (roll)
    yM[0] = cosY; yM[1] = 0.0f; yM[2] = sinY;
    yM[3] = 0.0f; yM[4] = 1.0f; yM[5] = 0.0f;
    yM[6] = -sinY; yM[7] = 0.0f; yM[8] = cosY;

    // rotation about z-axis (azimuth)
    zM[0] = cosZ; zM[1] = sinZ; zM[2] = 0.0f;
    zM[3] = -sinZ; zM[4] = cosZ; zM[5] = 0.0f;
    zM[6] = 0.0f; zM[7] = 0.0f; zM[8] = 1.0f;

    // rotation order is y, x, z (roll, pitch, azimuth)
    float[] resultMatrix = matrixMultiplication(xM, yM);
    resultMatrix = matrixMultiplication(zM, resultMatrix);
    return resultMatrix;
  }

  private float[] matrixMultiplication(float[] A, float[] B) {
    float[] result = new float[9];

    result[0] = A[0] * B[0] + A[1] * B[3] + A[2] * B[6];
    result[1] = A[0] * B[1] + A[1] * B[4] + A[2] * B[7];
    result[2] = A[0] * B[2] + A[1] * B[5] + A[2] * B[8];

    result[3] = A[3] * B[0] + A[4] * B[3] + A[5] * B[6];
    result[4] = A[3] * B[1] + A[4] * B[4] + A[5] * B[7];
    result[5] = A[3] * B[2] + A[4] * B[5] + A[5] * B[8];

    result[6] = A[6] * B[0] + A[7] * B[3] + A[8] * B[6];
    result[7] = A[6] * B[1] + A[7] * B[4] + A[8] * B[7];
    result[8] = A[6] * B[2] + A[7] * B[5] + A[8] * B[8];

    return result;
  }

  class calculateFusedOrientationTask extends TimerTask {
    public void run() {
      float oneMinusCoeff = 1.0f - FILTER_COEFFICIENT;
      fusedOrientation[0] =
              FILTER_COEFFICIENT * gyroOrientation[0]
                      + oneMinusCoeff * accMagOrientation[0];

      fusedOrientation[1] =
              FILTER_COEFFICIENT * gyroOrientation[1]
                      + oneMinusCoeff * accMagOrientation[1];

      fusedOrientation[2] =
              FILTER_COEFFICIENT * gyroOrientation[2]
                      + oneMinusCoeff * accMagOrientation[2];

      // overwrite gyro matrix and orientation with fused orientation
      // to comensate gyro drift
      gyroMatrix = getRotationMatrixFromOrientation(fusedOrientation);
      System.arraycopy(fusedOrientation, 0, gyroOrientation, 0, 3);
    }
  }


  // **************************** GUI FUNCTIONS *********************************

  public String formatActionJson() {
    double value = -Math.max(Math.min(fusedOrientation[1] * 3, 1.0), -1.0);
    if (Math.abs(value) < 0.1)
      value = 0.0;
    return Double.toString(value);
  }

  private void setFont(int... resources) {
    for (int res: resources)
      ((TextView) findViewById(res)).setTypeface(font);
  }

}
