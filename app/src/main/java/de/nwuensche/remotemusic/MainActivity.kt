package de.nwuensche.remotemusic

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.SpannableStringBuilder
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.InterstitialAd
import com.google.android.gms.ads.MobileAds
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.IOException


class MainActivity : AppCompatActivity() {
    private var session: Session? = null
    private val PLAYER="mplayer"
    private val TMUX_SESSION_NAME = "SSH_SESSION"
    private val dbName = "SETTINGS"
    private lateinit var mInterstitialAd: InterstitialAd

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setLastValues()

        //Ads
        MobileAds.initialize(this) {}
        mInterstitialAd = InterstitialAd(this)

        val real = "ca-app-pub-8653539039842404/7386853395"
        mInterstitialAd.adUnitId = real
        //TODO Need to reload ad after every show?
        mInterstitialAd.loadAd(AdRequest.Builder().build())


        connectButton.setOnClickListener {
            toast("Connecting...")

            //INFO Do this so I can use .join() in initSession to wait with "Connected" Toast until really connected
            GlobalScope.launch(Dispatchers.Main) {

                val wasError = initSession()
                if (wasError) {
                    session = null
                    toast("Error while Connecting!")
                } else {
                    toast("Connected!")
                }

                saveValues()
            }
        }

        shutdownButton.setOnClickListener {
            doIfConnected ("Turning off!") {shutdown()}
            mInterstitialAd.show()
            mInterstitialAd.loadAd(AdRequest.Builder().build())
        }

        startButton.setOnClickListener {
            doIfConnected ("Started!") {startMusic()}
            saveValues()
        }

        stopButton.setOnClickListener {
            doIfConnected ("Stopped!") {stopMusic()}
            mInterstitialAd.show()
            mInterstitialAd.loadAd(AdRequest.Builder().build())
        }

        increaseButton.setOnClickListener {
            doIfConnected { increaseVolume() }
        }

        decreaseButton.setOnClickListener {
            doIfConnected { decreaseVolume() }
        }

    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.test -> showDialog()
            else -> throw IOException()
        }

        return super.onOptionsItemSelected(item)
    }

    fun showDialog() {
        AlertDialog
            .Builder(this)
            .apply {
                setMessage("$SETUP\n\n$LICENSESSH")
                setPositiveButton("CLOSE") { dialog, _ -> dialog.dismiss() }
            }
            .create()
            .run {
                setTitle("Setup + Licenses")
                show()
            }
    }

    //TODO toast send aximer volume - Problem with coroutines

    fun doIfConnected(message: String = "", doStuff: Session.() -> (Unit)) {
        if (session == null) {
            toast("Connect first!")
            return
        }

        if(message.isNotBlank()) toast(message)

        GlobalScope.launch (Dispatchers.IO) {
            session?.doStuff()
        }

    }

    fun setLastValues() {
        iPEditText.text = getSharedPreference("IP", "").toEditable()
        usernameEditText.text = getSharedPreference("USERNAME", "").toEditable()
        passwordEditText.text = getSharedPreference("PASSWORD", "").toEditable()
        pathEditText.text = getSharedPreference("PATH", "").toEditable()
    }

    fun saveValues() {
        setSharedPreference("IP", iPEditText.text.toString())
        setSharedPreference("USERNAME", usernameEditText.text.toString())
        setSharedPreference("PASSWORD", passwordEditText.text.toString())
        setSharedPreference("PATH", pathEditText.text.toString())
    }

    fun String.toEditable(): Editable {
        return  SpannableStringBuilder(this)
    }

    fun getSharedPreference(key: String, defaultValue: String): String {
        val sp = getSharedPreferences(dbName, 0)
        return sp.getString(key, defaultValue)!!
    }

    fun setSharedPreference(key: String, value: String) {
        val spEdit = getSharedPreferences(dbName, 0).edit()
        spEdit.putString(key, value)
        spEdit.apply()
    }

    suspend fun initSession(): Boolean{
        //TODO Error if already connnected
        val jsch = JSch()
        val port = 22

        val session = jsch
            .getSession(usernameEditText.text.toString(), iPEditText.text.toString(), port)
            .apply {
                setPassword(passwordEditText.text.toString())
                setConfig("StrictHostKeyChecking", "no")
            }

        var errorConnecting = false
        GlobalScope.launch (Dispatchers.IO) {
            //Coroutines don't lift exception through
            try {
                session?.connect()
            } catch(e: Exception) {
                errorConnecting = true
            }
        }.join()

        if (!errorConnecting) {
            this.session = session
        }

        return errorConnecting
    }


    override fun onStop() {
        super.onStop()
        GlobalScope.launch (Dispatchers.IO) {
            session?.disconnect()
        }
        session = null

        saveValues()
    }

    //TODO toast if File not exists

    fun Session.shutdown() {
        execCommand("sudo shutdown -h now")
    }

    fun Session.startMusic() {
        stopMusic() //Kill everything before
        execCommand("tmux new-session -d -s $TMUX_SESSION_NAME '$PLAYER ${pathEditText.text}'")
    }

    fun Session.stopMusic() {
        execCommand("tmux kill-session -t $TMUX_SESSION_NAME")
    }

    fun Session.increaseVolume() {
        execCommand("amixer set PCM 5dB+")
    }

    fun Session.decreaseVolume() {
        execCommand("amixer set PCM 5dB-")
    }

    fun Session.execCommand(command: String) {
        val channelssh = openChannel("exec") as ChannelExec
        val stdout = java.io.ByteArrayOutputStream()
        channelssh.outputStream = stdout

        channelssh.setCommand(command)
        channelssh.connect()
        channelssh.disconnect()
    }
}

fun Context.toast(message: CharSequence) =
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()