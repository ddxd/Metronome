package pl.edu.uksw.metronome;

import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.media.AudioManager;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;




public class MainActivity extends AppCompatActivity implements View.OnClickListener {




    private static String LOG = "MetronomeApp";
    private static String BPM_NAME = "bpm";
    private static String WORKING_NAME = "working";
    private boolean work = false;
    TextView bpmTextView;
    TextView tempoTextView;
    TextView fab2text;

    private Handler buttonHandler = new Handler();                  //handler to continuous increase or decrease bpm
    private static int DELAY = 70;                                  //delay time between runnable repeat
    private boolean incrementing = false;
    private boolean decrementing = false;
    ImageButton incrementButton;
    ImageButton decrementButton;

    // Animations and layouts of Floating Action Buttons
    private Animation fab_open, fab_close, rotate_forward, rotate_backward;
    RelativeLayout fabMore, fab1, fab2, fab3;
    private boolean isFabOpened;

    // Layout to store dynamic dots
    LinearLayout dotsLayout;
    private ImageView[] iv;
    int dots = 3;

    // restrictions for max and min tempo
    private final static int maxBpm = 200;
    private final static int minBpm = 30;
    int bpm = 0;

    BeepService beepService = null;                                 //reference to service, initialized on connection to service
    boolean serviceConnected = false;                               //boolean variable if service is bounded

    // times to store in history
    long start;
    long stop;
    long time;

    long startBpm;
    long tapBpm;

    private SQLiteDatabase db;
    private DBOpenHelper dbhelp;

    DateFormat df = new SimpleDateFormat("dd-MM-yyyy HH:mm");
    String datestart = "";
    String lastedtime = "";
    Integer result = 0;

    AudioManager am;
    boolean isSilent = false;
    ImageView silentImage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        registerReceiver(broadcast, filter);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        SharedPreferences prefs = this.getSharedPreferences("pl.edu.uksw.metronome", Context.MODE_PRIVATE);

        // If this is the first run of the application ever
        if (!prefs.getBoolean("AppWasUsed", false)) {
            Toast.makeText(this, R.string.app_hint, Toast.LENGTH_LONG).show();
            prefs.edit().putBoolean("AppWasUsed", true).commit();
        }

        am = (AudioManager) getBaseContext().getSystemService(Context.AUDIO_SERVICE);

        silentImage = (ImageView)findViewById(R.id.silentMode);
        fab2text = (TextView)findViewById(R.id.fab2text);

        // set up toolbar
        Toolbar myToolbar = (Toolbar)findViewById(R.id.my_toolbar);
        setSupportActionBar(myToolbar);

        // set up layout with dots
        dotsLayout = (LinearLayout)findViewById(R.id.dotsLayout);
        setupDots(dots,false);

        //floating action buttons views
        fabMore = (RelativeLayout)findViewById(R.id.fab_more);
        fab1 = (RelativeLayout)findViewById(R.id.fab1);
        fab2 = (RelativeLayout)findViewById(R.id.fab2);
        fab3 = (RelativeLayout)findViewById(R.id.fab3);

        // fab listeners
        fabMore.setOnClickListener(this);
        fab1.setOnClickListener(this);
        fab2.setOnClickListener(this);
        fab3.setOnClickListener(this);

        // fab animations
        rotate_forward = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.rotate_forward);
        rotate_backward = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.rotate_backward);
        fab_open = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.fab_open);
        fab_close = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.fab_close);
        isFabOpened = false;

        // open DB
        dbhelp = new DBOpenHelper(this);
        db = dbhelp.getWritableDatabase();

        // BPM layout init
        bpmTextView = (TextView)findViewById(R.id.bpmTextView);
        bpmTextView.setText("" + (bpm));

        // Tempo layout init
        tempoTextView = (TextView)findViewById(R.id.tempo);
        tempoTextView.setText(assignTempo(bpm));

        /*
         * increment button listeners
         */
        incrementButton = (ImageButton)findViewById(R.id.incrementButton);
        // long press
        incrementButton.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                incrementing = true;
                buttonHandler.post(new ButtonsLongPressHandler());
                return false;
            }
        });
        // cancel press
        incrementButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if ((event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) && incrementing) {
                    incrementing = false;
                }
                return false;
            }
        });
        // click
        incrementButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                increment();
            }
        });

        /*
         * decrement button listeners
         */
        decrementButton = (ImageButton)findViewById(R.id.decrementButton);
        // long press
        decrementButton.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                decrementing = true;
                buttonHandler.post(new ButtonsLongPressHandler());
                return false;
            }
        });
        // cancel press
        decrementButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if ((event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) && decrementing) {
                    decrementing = false;
                }
                return false;
            }
        });
        // click
        decrementButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                decrement();
            }
        });
    }

    private IntentFilter filter = new IntentFilter("pl.edu.uksw.metronome.Broadcast");

    public BroadcastReceiver broadcast = new BroadcastReceiver(){
        @Override
        public void onReceive(Context context, Intent intent) {
            result = intent.getIntExtra("result",result);
            Log.i("Broadcast", Integer.toString(result));
            highlightDot(result);
        }
    };

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.history:
                checkHistory();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        bindService(new Intent(this, BeepService.class), connection, Context.BIND_AUTO_CREATE);
        startService(new Intent(this, BeepService.class));

        Log.d(LOG, "onStart");
    }

    @Override
    protected void onStop() {
        am.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
        super.onStop();
        if (serviceConnected) {
            unbindService(connection);
            serviceConnected = false;
        }
        Log.d(LOG, "onStop");
    }

    @Override
    protected void onDestroy() {
        am.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
        if (serviceConnected) {
            unbindService(connection);
            stopService(new Intent(this, BeepService.class));           // service is stopped only when application is completely closed
            serviceConnected = false;
        }
        super.onDestroy();
    }

    public int getDotsNumber(){ return dots; }

    private void updateBpmViewAndService(int beatsPerMinute){
        Log.i("bpm", Integer.toString(beatsPerMinute));
        bpmTextView.setText("" + (beatsPerMinute));
        tempoTextView.setText(assignTempo(beatsPerMinute));
        beepService.setBpm(beatsPerMinute);
    }

    /*
     * Faster bpm button
     */
    public void increment(){
        if(bpm >= 30 && bpm < 200) {
            bpm++;
            updateBpmViewAndService(bpm);
        }
    }

    /*
     * Slower bpm button
     */
    public void decrement(){
        if(bpm > 30 && bpm <= 200) {
            bpm--;
            updateBpmViewAndService(bpm);
        }
    }

    // setup dots layout
    public void setupDots(int dotsnum, boolean isCreated){
        dotsLayout.removeAllViews();
        dotsLayout.setWeightSum(dotsnum);
        iv = new ImageView[dotsnum];
        for (int i = 0; i < dotsnum; i++){
            iv[i] = new ImageView(this);
            iv[i].setImageResource(R.drawable.dot_base);
            iv[i].setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1.0f
            ));
            iv[i].setId(i);
            dotsLayout.addView(iv[i]);
        }
        /*
         * if layout has been already created, modify service
         * if not, that means the service isn't binded yet
         */
        if(isCreated) {
            beepService.setDotsNumber(dotsnum);
            beepService.setDotNumber(0);
        }
    }

    // highlight one dot at a time
    public void highlightDot(int num){
            Log.i("highlightDot",Integer.toString(num));
            iv[num].setColorFilter(Color.RED);
            if(num!=0) iv[num-1].setColorFilter(null);
            else iv[getDotsNumber()-1].setColorFilter(null);
    }

    /*
     * Start/Stop bpm button
     */
    public void start_stop(View view){
        if(beepService != null){
            // if metronome is not ticking, start
            if(!work) {
                work = true;
                start = System.currentTimeMillis();
                datestart = df.format(Calendar.getInstance().getTime());
                beepService.playBeep(work, bpm);
            }
            else {
                stop = System.currentTimeMillis();
                time = stop - start;
                long sec = time/1000;
                long min = sec/60;
                long hour = min/60;
                sec = sec%60;
                min = min%60;

                if(hour > 0)
                    lastedtime += Long.toString(hour)+"h"+" ";
                if(min > 0)
                    lastedtime += Long.toString(min)+"min"+" ";
                if(sec > 0)
                    lastedtime += Long.toString(sec)+"s"+" ";

                insertEntry();
                lastedtime = "";
                work = false;
                beepService.setWork(work);
            }
        }
    }

    public void onClick(View v){
        int id = v.getId();
        switch (id) {
            case R.id.fab_more:
                animateFAB();
                break;
            case R.id.fab1:
                if (startBpm == 0) {
                    Toast.makeText(this, R.string.tap_tempo, Toast.LENGTH_SHORT).show();
                }
                tapBpm();
                break;
            case R.id.fab2:
                DialogFragment dialogFragment = new PickMetrumDialogFragment();
                dialogFragment.show(getSupportFragmentManager(), "Picker");
                beepService.setDotNumber(0);
                break;
            case R.id.fab3:
                if(isSilent)
                {
                    am.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                    isSilent = false;
                    silentImage.setImageResource(R.drawable.ic_notifications_off);
                    Toast.makeText(this, R.string.silent_off, Toast.LENGTH_LONG).show();
                }
                else
                {
                    am.setRingerMode(AudioManager.RINGER_MODE_SILENT);
                    isSilent = true;
                    silentImage.setImageResource(R.drawable.ic_notifications);
                    Toast.makeText(this, R.string.silent_on, Toast.LENGTH_LONG).show();
                }
                break;
        }
    }

    private void animateFAB(){
        if(isFabOpened) {
            fabMore.startAnimation(rotate_backward);
            fab1.startAnimation(fab_close);
            fab2.startAnimation(fab_close);
            fab3.startAnimation(fab_close);
            fab1.setClickable(false);
            fab2.setClickable(false);
            fab3.setClickable(false);
            isFabOpened = false;
        }
        else {
            fabMore.startAnimation(rotate_forward);
            fab1.startAnimation(fab_open);
            fab2.startAnimation(fab_open);
            fab3.startAnimation(fab_open);
            fab1.setClickable(true);
            fab2.setClickable(true);
            fab3.setClickable(true);
            isFabOpened = true;
        }
    }

    private void tapBpm(){

        if(startBpm == 0) {
            startBpm = System.currentTimeMillis();
        }
        else if ((System.currentTimeMillis() - startBpm)/1000 > 2){
            startBpm = System.currentTimeMillis();
        }
        else {
            tapBpm = 60000/(System.currentTimeMillis() - startBpm);
            startBpm = System.currentTimeMillis();
            if (tapBpm >= minBpm && tapBpm <= maxBpm) {
                bpm = (int)tapBpm;
                updateBpmViewAndService(bpm);
            }
            else if (tapBpm > maxBpm){
                bpm = maxBpm;
                updateBpmViewAndService(bpm);
            }
            else if (tapBpm < minBpm){
                bpm = minBpm;
                updateBpmViewAndService(bpm);
            }
        }
    }

    public void setBpmManually(View view){
        DialogFragment dialog = new PickBpmDialogFragment();
        dialog.show(getSupportFragmentManager(), "Dialog");
    }

    private String assignTempo(int bpm){
        // italian constant names of tempo
        if(bpm >= 30 && bpm < 40) return "Grave";
        else if(bpm >= 40 && bpm < 50) return "Largo";
        else if(bpm >= 50 && bpm < 60) return "Lento";
        else if(bpm >= 60 && bpm < 66) return "Larghetto";
        else if(bpm >= 66 && bpm < 76) return "Adagio";
        else if(bpm >= 76 && bpm < 108) return "Andante";
        else if(bpm >= 108 && bpm < 120) return "Moderato";
        else if(bpm >= 120 && bpm < 168) return "Allegro";
        else if(bpm >= 168 && bpm < 176) return "Vivace";
        else if(bpm >= 176 && bpm <= 200) return "Presto";
        else return "null";
    }

    public void insertEntry() {
        ContentValues cv = new ContentValues();
        // check if date is not empty
        if(datestart.length() > 9 && time > 1000) {
            cv.put(DBOpenHelper.date, datestart);
            cv.put(DBOpenHelper.lasted, lastedtime);
            cv.put(DBOpenHelper.lastedseconds, time);
            db.insert(DBOpenHelper.TABLE_NAME, null, cv);
            Log.d(LOG, "a new entry was inserted:");
        }
    }

    public void checkHistory() {
        Intent myIntent = new Intent(this, BeepHistory.class);
        startActivity(myIntent);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putLong("start", start);
        outState.putString("dateStart", datestart);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        start = savedInstanceState.getLong("start");
        datestart = savedInstanceState.getString("dateStart");
    }

    private class ButtonsLongPressHandler implements Runnable {
        @Override
        public void run() {
            if (incrementing){
                increment();
                buttonHandler.postDelayed(new ButtonsLongPressHandler(), DELAY);
            }
            else if (decrementing){
                decrement();
                buttonHandler.postDelayed(new ButtonsLongPressHandler(), DELAY);
            }
        }
    }

    /*
     * class to interact with service
     */
    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(LOG, "Service connected");
            beepService = ((BeepService.MetronomeBinder)service).getService();
            serviceConnected = true;
            bpm = beepService.getBpm();                                                             //get bpm from service on connection with service
            bpmTextView.setText("" + (bpm));                                                        //set textView
            tempoTextView.setText(assignTempo(bpm));
            work = beepService.getWork();                                                           //get boolean work from service
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(LOG, "Service disconnected");
            beepService = null;
            serviceConnected = false;
        }
    };

    public class PickMetrumDialogFragment extends DialogFragment{
        NumberPicker numberPicker;

        public Dialog onCreateDialog(Bundle savedInstanceState){
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            LayoutInflater layoutInflater = getActivity().getLayoutInflater();
            final View view = layoutInflater.inflate(R.layout.dialog_metrum_picker, null);
            numberPicker = (NumberPicker)view.findViewById(R.id.numberPicker);
            numberPicker.setMaxValue(6);
            numberPicker.setMinValue(1);
            numberPicker.setValue(getDotsNumber());
            Log.d("PickMetrumDialog", "On Create Dialog method...");
            builder.setTitle(R.string.metrum_dialog)
                    .setView(view)
                    .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener(){
                        @Override
                        public void onClick(DialogInterface dialog, int id) {
                            dots = numberPicker.getValue();
                            setupDots(dots, true);
                            fab2text.setText(Integer.toString(dots));
                        }
                    })
                    .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            // User cancelled the dialog
                        }
                    });
            return builder.create();
        }
    }

    public class PickBpmDialogFragment extends DialogFragment{
        EditText bpmPicker;
        int temp;

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {

            // Use the Builder class for convenient dialog construction
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            LayoutInflater layoutInflater = getActivity().getLayoutInflater();
            final View view = layoutInflater.inflate(R.layout.dialog_bpm_picker, null);

            builder.setTitle(R.string.bpm_dialog)
                    .setView(view)
                    .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            bpmPicker = (EditText)view.findViewById(R.id.dialog_bpm_picker_editText);
                            temp = Integer.valueOf(bpmPicker.getText().toString());

                            if (temp > 200) {
                                bpm = 200;
                                Toast.makeText(getApplicationContext(), R.string.high_bpm, Toast.LENGTH_SHORT).show();
                            }
                            else if (temp < 30) {
                                bpm = 30;
                                Toast.makeText(getApplicationContext(), R.string.low_bpm, Toast.LENGTH_SHORT).show();
                            }
                            else bpm = temp;
                            updateBpmViewAndService(bpm);
                        }
                    })
                    .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            // User cancelled the dialog
                        }
                    });
            // Create the AlertDialog object and return it
            return builder.create();
        }
    }
}
