package com.example.weatherapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import com.example.weatherapp.models.WeatherResponse
import com.example.weatherapp.network.WeatherService
import com.google.android.gms.location.*
import com.google.gson.Gson
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import kotlinx.android.synthetic.main.activity_main.*
import retrofit2.*
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var mFusedLocationClient : FusedLocationProviderClient

    private var mProgressDialog : Dialog? = null

    private var mLatitude = 0.0

    private var mLongitude = 0.0

    private lateinit var mSharedPreferences: SharedPreferences



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        mSharedPreferences = getSharedPreferences(Constants.PREFERENCE_NAME,  Context.MODE_PRIVATE)

        setupUI()

        if(!isLocationEnabled()){
            Toast.makeText(this, "Your location provider is turned off. Please turn it on.", Toast.LENGTH_SHORT).show()

            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        } else {
            Dexter.withContext(this).withPermissions(
                Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION).withListener(object:
                MultiplePermissionsListener {
                override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                    if(report!!.areAllPermissionsGranted()){

                        requestLocationData()

                    }

                    if(report.isAnyPermissionPermanentlyDenied) {
                        Toast.makeText(this@MainActivity, "You have denied location permission. Please enable them as it is mandatory for the app to work",Toast.LENGTH_SHORT).show()
                    }
                }
                override fun onPermissionRationaleShouldBeShown(
                    permissions: MutableList<PermissionRequest>?,
                    p1: PermissionToken?
                ) {
                    showRationalDialogForPermissions()
                }

            }).onSameThread().check()
        }

    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)


        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when(item.itemId){
            R.id.action_refresh -> {
                Log.i("refresh", "pressed")
                getLocationWeatherDetails(mLatitude,mLongitude)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showRationalDialogForPermissions() {
        AlertDialog.Builder(this).setMessage("It looks like you have turned off permission required for this feature. It can be enabled under the Application Settings").setPositiveButton("GO TO SETTINGS" ) {

                _,_, ->
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package", packageName, null)
                intent.data = uri
                startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                e.printStackTrace()
            }
        }.setNegativeButton("Cancel"){dialog,_ ->
            dialog.dismiss()
        }.show()
    }



    private fun isLocationEnabled(): Boolean {
        val locationManager: LocationManager =
            getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationData() {

        val mLocationRequest = LocationRequest()
        mLocationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        mFusedLocationClient.requestLocationUpdates(
            mLocationRequest, mLocationCallback,
            Looper.myLooper()
        )
    }

    private val mLocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {

            val mLastLocation: Location = locationResult.lastLocation
            mLatitude = mLastLocation.latitude
            Log.e("Current Latitude", "$mLatitude")
            mLongitude = mLastLocation.longitude
            Log.e("Current Longitude", "$mLongitude")
            getLocationWeatherDetails(mLatitude, mLongitude)

        }
    }

    private fun getLocationWeatherDetails(latitude: Double, longitude: Double){
        if(Constants.isNetworkAvailable(this)){
            val retrofit : Retrofit = Retrofit.Builder()
                .baseUrl(Constants.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            val service: WeatherService = retrofit.create<WeatherService>(WeatherService::class.java)

            val listCall: Call<WeatherResponse> = service.getWeather(latitude, longitude, Constants.METRIC_UNIT, Constants.APP_ID)

            showProgressDialog()
            listCall.enqueue(object : Callback<WeatherResponse> {
                @SuppressLint("SetTextI18n")
                override fun onResponse(
                    call: Call<WeatherResponse>,
                    response: Response<WeatherResponse>
                ) {
                    if(response.isSuccessful){

                        hideProgressDialog()

                        val weatherList: WeatherResponse? = response.body()

                        val weatherResponseJsonString = Gson().toJson(weatherList)
                        val editor = mSharedPreferences.edit()
                        editor.putString(Constants.WEATHER_RESPONSE_DATA, weatherResponseJsonString)
                        editor.apply()

                        setupUI()

                        Log.i("Response Result", "$weatherList")

                    }else{
                        val rc = response.code()
                        when(rc){
                            400 ->{
                                Log.e("Error 400", "Bad Connection")
                            }
                            404 ->{
                                Log.e("Error 404", "Not Found")
                            }else->{
                                Log.e("Error", "Generic Error")
                            }
                        }
                    }
                }

                override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                    hideProgressDialog()
                    Log.e("Errorrrrrrrrrrrrrrrr", t!!.message.toString())
                }

            })

        } else {
            Toast.makeText(this@MainActivity, "No internet connection available", Toast.LENGTH_SHORT).show()
        }
    }


    private fun showProgressDialog(){
        mProgressDialog = Dialog(this)

        mProgressDialog!!.setContentView(R.layout.dialog_custom_progress)

        mProgressDialog!!.show()
    }



    private fun hideProgressDialog(){
        if(mProgressDialog != null){
            mProgressDialog!!.dismiss()
        }
    }


    private fun setupUI() {
        val weatherResponseJsonString = mSharedPreferences.getString(Constants.WEATHER_RESPONSE_DATA, "")

        if(!weatherResponseJsonString.isNullOrEmpty()){
            val weatherList = Gson().fromJson(weatherResponseJsonString, WeatherResponse::class.java)

                for (i in weatherList.weather.indices) {
                    Log.i("Weather Name", weatherList.weather.toString())

                    tv_main.text = weatherList.weather[i].main
                    tv_main_description.text = weatherList.weather[i].description
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        tv_temp.text = weatherList.main.temp.toString() + getUnit(application.resources.configuration.locales.toString())
                    }else{
                        tv_temp.text = weatherList.main.temp.toString() + getUnit(application.resources.configuration.toString())
                    }
                    tv_sunrise_time.text = unixTime(weatherList.sys.sunrise)
                    tv_sunset_time.text = unixTime(weatherList.sys.sunset)
                    tv_humidity.text = weatherList.main.humidity.toString() + "%"
                    tv_max.text = weatherList.main.temp_max.toString() + "max"
                    tv_min.text = weatherList.main.temp_min.toString() + "min"
                    tv_name.text = weatherList.name
                    tv_country.text = getCountryName(weatherList.sys.country)
                    tv_speed.text = weatherList.wind.speed.toString()


                    when(weatherList.weather[i].icon){
                        "01d" -> iv_main.setImageResource(R.drawable.sunny)
                        "02d" -> iv_main.setImageResource(R.drawable.cloud)
                        "03d" -> iv_main.setImageResource(R.drawable.cloud)
                        "04d" -> iv_main.setImageResource(R.drawable.cloud)
                        "09d" -> iv_main.setImageResource(R.drawable.rain)
                        "10d" -> iv_main.setImageResource(R.drawable.rain)
                        "11d" -> iv_main.setImageResource(R.drawable.storm)
                        "13d" -> iv_main.setImageResource(R.drawable.snowflake)
                        "50d" -> iv_main.setImageResource(R.drawable.cloud)
                        "01n" -> iv_main.setImageResource(R.drawable.sunny)
                        "02n" -> iv_main.setImageResource(R.drawable.sunny)
                        "03n" -> iv_main.setImageResource(R.drawable.cloud)
                        "04n" -> iv_main.setImageResource(R.drawable.cloud)
                        "09n" -> iv_main.setImageResource(R.drawable.rain)
                        "10n" -> iv_main.setImageResource(R.drawable.rain)
                        "11n" -> iv_main.setImageResource(R.drawable.storm)
                        "13n" -> iv_main.setImageResource(R.drawable.snowflake)
                        "50n" -> iv_main.setImageResource(R.drawable.cloud)




                    }
                }
        }

    }

    private fun getUnit(value: String): String?{
        var value = "°C"
        if("US" == value || "LR" == value || "MM" == value){
            value = "°F"
        }
        return value
    }


    private fun unixTime(timex: Long): String?{
        val date = Date(timex*1000L)
        val sdf = SimpleDateFormat("HH:mm")
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(date)

    }

    private fun getCountryName(code: String): String{
        val loc = Locale("", code)
        return loc.displayCountry
    }



}