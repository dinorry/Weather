package com.example.weatherapp.models

import java.io.Serializable

data class WeatherResponse (
    val coordinates: Coordinates,
    val weather : List<Weather>,
    val base: String,
    val main : Main,
    val visibility : Int,
    val wind: Wind,
    val rain: Rain,
    val clouds: Clouds,
    val dt: Int,
    val sys: Sys,
    val timezone: Int,
    val id: Int,
    val name: String,
    val cod: Int

    ): Serializable