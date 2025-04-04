package com.example.app.b

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class WeatherService : Service() {

    companion object {
        const val REQUEST_WEATHER = 1
        const val RESPONSE_WEATHER = 2
        const val RESPONSE_ERROR = 3
        const val EXTRA_WEATHER_DATA = "weatherJson"
    }

    private val messenger = Messenger(WeatherHandler(this))
    private val httpClient = HttpClient(Android)
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onBind(intent: Intent): IBinder = messenger.binder

    override fun onDestroy() {
        scope.cancel()
        httpClient.close()
        super.onDestroy()
    }

    private suspend fun fetchWeatherData(): String {
        val response: HttpResponse = httpClient.get("https://api.weather.gov/gridpoints/OKX/34,37/forecast")
        return response.body()
    }

    private class WeatherHandler(
        private val service: WeatherService,
        looper: Looper = Looper.getMainLooper()
    ) : Handler(looper) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                REQUEST_WEATHER -> {
                    val replyTo = msg.replyTo // msg will be recycled before data is ready, store replyTo.
                    service.scope.launch {
                        try {
                            val weatherData = service.fetchWeatherData()
                            val replyMessage = Message.obtain(null, RESPONSE_WEATHER).apply {
                                data = Bundle().apply { putString(EXTRA_WEATHER_DATA, weatherData) }
                            }
                            replyTo.send(replyMessage)
                        } catch (e: Exception) {
                            val errorMessage = Message.obtain(null, RESPONSE_ERROR).apply {
                                data = Bundle().apply { putString(EXTRA_WEATHER_DATA, e.localizedMessage) }
                            }
                            replyTo.send(errorMessage)
                        }
                    }
                }
            }
        }
    }
}