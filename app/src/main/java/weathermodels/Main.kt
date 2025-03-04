package weathermodels

import java.io.Serializable

data class Main (
    val name: String,
    val temp: Double,
    val feels_like: Double,
    val temp_min: Double,
    val temp_max: Double,
    val pressure: Int,
    val humidity: Int,
    val sea_level: Double,
    val grnd_level: Int
):Serializable