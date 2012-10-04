package fi.hwo.manuel;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.nfc.Tag;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.*;

public class Login extends Activity {
  private Typeface font;

  private static final String TAG = "Manuel.Login";

  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.login);

    font = Typeface.createFromAsset(this.getAssets(), "PressStart2P-Regular.ttf");

    final EditText server = (EditText) findViewById(R.id.serveri);
    final EditText text = (EditText) findViewById(R.id.eskonNimi);
    final EditText opponent = (EditText) findViewById(R.id.vastustaja);
    final EditText visu = (EditText) findViewById(R.id.visuServeri);

    final SharedPreferences prefs = getPreferences(MODE_PRIVATE);
    setFromPreferences(server, "server", prefs);
    setFromPreferences(text, "name", prefs);
    setFromPreferences(opponent, "opponent", prefs);
    setFromPreferences(visu, "visu", prefs);

    setFont(R.id.eskonNimiLabel, R.id.serveriLabel, R.id.vastustajaLabel, R.id.visuServeriLabel,
            R.id.eskonNimi, R.id.serveri, R.id.vastustaja, R.id.visuServeri, R.id.ruiluta);

    Button go = (Button) findViewById(R.id.ruiluta);

    go.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        SharedPreferences.Editor editor = getPreferences(MODE_PRIVATE).edit();
        editor.putString("server", server.getText().toString());
        editor.putString("username", text.getText().toString());
        editor.putString("opponent", opponent.getText().toString());
        editor.putString("visu", visu.getText().toString());
        editor.commit();

        Log.e(TAG, prefs.getString("visuServer", ""));

        Intent intent = new Intent(getApplicationContext(), Manuel.class);
        intent.putExtra("server", server.getText().toString());
        intent.putExtra("username", text.getText().toString());
        intent.putExtra("opponent", opponent.getText().toString());
        intent.putExtra("visuServer", visu.getText().toString());
        startActivity(intent);
      }
    });
  }

  private void setFromPreferences(EditText field, String key, SharedPreferences values) {
    if (values.contains(key)) {
      Log.d(TAG, "Set " + key + " to " + values.getString(key, ""));
      field.setText(values.getString(key, ""));
    }
  }

  private void setFont(int... resources) {
    for (int res: resources)
      ((TextView) findViewById(res)).setTypeface(font);
  }
}