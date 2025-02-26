package network

import weathermodels.WeatherResponse
import retrofit.Call
import retrofit.http.GET
import retrofit.http.Query
import utils.Constants


interface WeatherService {

    @GET("2.5/weather")
    fun getWeather(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("units") units: String = Constants.METRIC_UNIT,
        @Query("appid") appId: String = Constants.APP_ID
    ): Call<WeatherResponse>



}