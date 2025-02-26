package com.weatherapp


import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.bumptech.glide.Glide
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.gson.Gson
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import weathermodels.WeatherResponse
import network.WeatherService
import retrofit.Call
import retrofit.Callback
import retrofit.GsonConverterFactory
import retrofit.Response
import retrofit.Retrofit
import utils.Constants
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone


class MainActivity : AppCompatActivity() {

    private lateinit var mFusedLocationClient: FusedLocationProviderClient
    private var mProgressDialog: Dialog? = null
    private var mLatitude: Double = 0.0
    private var mLongitude: Double = 0.0
    private lateinit var mSharedPreferences: SharedPreferences


    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize the Fused location variable
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        mSharedPreferences = getSharedPreferences(Constants.PREFERENCE_NAME, Context.MODE_PRIVATE)

        


        setupUI()

        if (!isLocationEnabled()) {
            Toast.makeText(
                this,
                "Konum servisleri kapalı. Lütfen açın.",
                Toast.LENGTH_SHORT
            ).show()
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        } else {
            Dexter.withActivity(this)
                .withPermissions(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
                .withListener(object : MultiplePermissionsListener {
                    override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                        if (report!!.areAllPermissionsGranted()) {
                            requestLocationData()
                        } else {
                            Toast.makeText(
                                this@MainActivity,
                                "Konum izinleri verilmedi.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                    override fun onPermissionRationaleShouldBeShown(
                        permissions: MutableList<PermissionRequest>?,
                        token: PermissionToken?
                    ) {
                        showRationalDialogForPermissions()
                    }
                }).onSameThread()
                .check()
        }
    }





    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {

            R.id.action_refresh -> {
                getLocationWeatherDetails()
                true
            }
            else -> super.onOptionsItemSelected(item)

        }
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager: LocationManager =
            getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun showRationalDialogForPermissions() {
        AlertDialog.Builder(this)
            .setMessage("It Looks like you have turned off permissions required for this feature. It can be enabled under Application Settings")
            .setPositiveButton(
                "GO TO SETTINGS"
            ) { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package", packageName, null)
                    intent.data = uri
                    startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    e.printStackTrace()
                }
            }
            .setNegativeButton("Cancel") { dialog,
                                           _ ->
                dialog.dismiss()
            }.show()
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationData() {
        val mLocationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
            .setWaitForAccurateLocation(false)
            .setMinUpdateIntervalMillis(5000)
            .setMaxUpdateDelayMillis(10000)
            .build()

        mFusedLocationClient.requestLocationUpdates(
            mLocationRequest, mLocationCallback, Looper.getMainLooper()
        )
    }


    private val mLocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val mLastLocation: Location? = locationResult.lastLocation
            if (mLastLocation != null) {
                mLatitude = mLastLocation.latitude
                mLongitude = mLastLocation.longitude
                Log.i("Location", "Latitude: $mLatitude, Longitude: $mLongitude")
                getLocationWeatherDetails()
            } else {
                Toast.makeText(this@MainActivity, "Konum bilgisi alınamadı.", Toast.LENGTH_SHORT).show()
            }
        }
    }


    private fun getLocationWeatherDetails() {

        if (Constants.isNetworkAvailable(this@MainActivity)) {

            val retrofit: Retrofit = Retrofit.Builder()

                .baseUrl(Constants.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()


            val service: WeatherService =
                retrofit.create<WeatherService>(WeatherService::class.java)

            val listCall: Call<WeatherResponse> = service.getWeather(
                mLatitude, mLongitude, Constants.METRIC_UNIT, Constants.APP_ID
            )

            showCustomProgressDialog() // Used to show the progress dialog

            listCall.enqueue(object : Callback<WeatherResponse> {
                @SuppressLint("SetTextI18n")
                override fun onResponse(
                    response: Response<WeatherResponse>,
                    retrofit: Retrofit
                ) {

                    // Check weather the response is success or not.
                    if (response.isSuccess) {

                        hideProgressDialog() // Hides the progress dialog

                        val weatherList: WeatherResponse = response.body()
                        Log.i("Response Result", "$weatherList")

                        val weatherResponseJsonString = Gson().toJson(weatherList)
                        val editor = mSharedPreferences.edit()
                        editor.putString(Constants.WEATHER_RESPONSE_DATA, weatherResponseJsonString)
                        editor.apply()

                        setupUI()
                    } else {
                        // If the response is not success then we check the response code.
                        val sc = response.code()
                        when (sc) {
                            400 -> {
                                Log.e("Error 400", "Bad Request")
                            }
                            404 -> {
                                Log.e("Error 404", "Not Found")
                            }
                            else -> {
                                Log.e("Error", "Generic Error")
                            }
                        }
                    }
                }

                override fun onFailure(t: Throwable) {
                    hideProgressDialog() // Hides the progress dialog
                    Log.e("Errorrrrr", t.message.toString())
                }
            })
        } else {
            Toast.makeText(
                this@MainActivity,
                "No internet connection available.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }


    private fun showCustomProgressDialog() {
        mProgressDialog = Dialog(this)
        mProgressDialog!!.setContentView(R.layout.dialog_custom_progress)
        mProgressDialog!!.show()
    }

    private fun hideProgressDialog() {
        if (mProgressDialog != null) {
            mProgressDialog!!.dismiss()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun setupUI() {

        val weatherResponseJsonString =
            mSharedPreferences.getString(Constants.WEATHER_RESPONSE_DATA, "")

        if (!weatherResponseJsonString.isNullOrEmpty()){
            val weatherList =
                Gson().fromJson(weatherResponseJsonString, WeatherResponse::class.java)

            for (z in weatherList.weather.indices){
                Log.i("Name", weatherList.weather[z].main)

                findViewById<TextView>(R.id.tv_main).text = weatherList.weather[z].main
                findViewById<TextView>(R.id.tv_main_description).text = weatherList.weather[z].description
                findViewById<TextView>(R.id.tv_temp).text = "${weatherList.main.temp} ${getUnit(Locale.getDefault().country)}"

                findViewById<TextView>(R.id.tv_humidity).text = weatherList.main.humidity.toString() + " per cent"
                findViewById<TextView>(R.id.tv_min).text = weatherList.main.temp_min.toString() + " min"
                findViewById<TextView>(R.id.tv_max).text = weatherList.main.temp_max.toString() + " max"
                findViewById<TextView>(R.id.tv_speed).text = weatherList.wind.speed.toString()
                findViewById<TextView>(R.id.tv_name).text = weatherList.name
                findViewById<TextView>(R.id.tv_country).text = weatherList.sys.country

                findViewById<TextView>(R.id.tv_sunrise_time).text = unixTime(weatherList.sys.sunrise)
                findViewById<TextView>(R.id.tv_sunset_time).text = unixTime(weatherList.sys.sunset)

                val imageViewHumidity = findViewById<ImageView>(R.id.iv_humidity)
                val imageViewWind = findViewById<ImageView>(R.id.iv_wind)
                val imageViewMinMax = findViewById<ImageView>(R.id.iv_min_max)
                val imageViewLocation = findViewById<ImageView>(R.id.iv_location)
                val imageViewSunrise = findViewById<ImageView>(R.id.iv_sunrise)
                val imageViewSunset = findViewById<ImageView>(R.id.iv_sunset)

                Glide.with(this).asGif().load(R.raw.humidity).into(imageViewHumidity)
                Glide.with(this).asGif().load(R.raw.wind).into(imageViewWind)
                Glide.with(this).asGif().load(R.raw.temparature).into(imageViewMinMax)
                Glide.with(this).asGif().load(R.raw.location).into(imageViewLocation)
                Glide.with(this).asGif().load(R.raw.sunrise).into(imageViewSunrise)
                Glide.with(this).asGif().load(R.raw.sunset).into(imageViewSunset)



                when(weatherList.weather[z].icon){
                    "01d" -> Glide.with(this).asGif().load(R.raw.sun).into(findViewById(R.id.iv_main))
                    "01n" -> Glide.with(this).asGif().load(R.raw.night).into(findViewById(R.id.iv_main))

                    "02d" -> Glide.with(this).asGif().load(R.raw.weather).into(findViewById(R.id.iv_main))
                    "02n" -> Glide.with(this).asGif().load(R.raw.cloud_night).into(findViewById(R.id.iv_main))

                    "03d" -> Glide.with(this).asGif().load(R.raw.clouds_broken).into(findViewById(R.id.iv_main))
                    "03n" -> Glide.with(this).asGif().load(R.raw.cloudy_night).into(findViewById(R.id.iv_main))

                    "04d","04n"-> Glide.with(this).asGif().load(R.raw.clouds).into(findViewById(R.id.iv_main))

                    "09d" , "09n" -> Glide.with(this).asGif().load(R.raw.rain).into(findViewById(R.id.iv_main))

                    "10d" -> Glide.with(this).asGif().load(R.raw.rain_sun).into(findViewById(R.id.iv_main))
                    "10n" -> Glide.with(this).asGif().load(R.raw.rain_night).into(findViewById(R.id.iv_main))

                    "11d" -> Glide.with(this).asGif().load(R.raw.storm).into(findViewById(R.id.iv_main))
                    "11n" -> Glide.with(this).asGif().load(R.raw.storm_night).into(findViewById(R.id.iv_main))

                    "13d" ,"13n" -> Glide.with(this).asGif().load(R.raw.snow).into(findViewById(R.id.iv_main))
                    "50d" , "50n" -> Glide.with(this).asGif().load(R.raw.foggy).into(findViewById(R.id.iv_main))

                    else -> Glide.with(this).load(R.raw.defaults).into(findViewById(R.id.iv_main))
                }

                Log.i("WeatherIcon", "Icon: ${weatherList.weather[z].icon}")



            }

        }
    }

    private fun getUnit(value: String): String? {
        Log.i("unitttttt", value)
        var value = "°C"
        if ("US" == value || "LR" == value || "MM" == value) {
            value = "°F"
        }
        return value
    }

    private fun unixTime(timex: Long): String? {
        val date = Date(timex * 1000L)
        @SuppressLint("SimpleDateFormat") val sdf =
            SimpleDateFormat("HH:mm:ss")
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(date)
    }
}