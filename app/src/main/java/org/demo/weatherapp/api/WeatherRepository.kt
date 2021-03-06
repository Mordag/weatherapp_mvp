package org.demo.weatherapp.api

import android.util.Log
import com.google.gson.Gson
import org.demo.weatherapp.BuildConfig
import org.demo.weatherapp.WeatherModelContract
import org.demo.weatherapp.model.WeatherModel
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

open class WeatherRepository: WeatherModelContract.Repository {

    private val fetchingData = AtomicBoolean(false)
    private val gson: Gson = Gson()
    private val appExecutor = AppExecutor()
    private val cacheController: WeatherModelContract.Cache = CacheController()
    private val networkInterface = NetworkUtil.provideNetworkInterface()

    private lateinit var presenter: WeatherModelContract.Presenter

    /**
     * Checks the current cached weather model data. If the cache is outdated or not existing,
     * the class will try to retrieve new weather data from the server.
     */
    override fun getWeatherData(presenter: WeatherModelContract.Presenter) {
        this.presenter = presenter

        if (fetchingData.compareAndSet(false, true)) {
            appExecutor.workerThread.execute {
                val oldData = cacheController.getCache()
                if (oldData == null || cacheController.isCacheTooOld()) {
                    fetchFromNetwork()
                } else {
                    prepareData(oldData)
                    fetchingData.set(false)
                }
            }
        }
    }

    /**
     * Fetches weather data from the server. In case the response was successful, the data will be
     * cached and send to the presenter, else the class will call the presenter to show an error
     * message.
     */
    private fun fetchFromNetwork() {
        try {
            val networkResponse = networkInterface.getWeatherData(BuildConfig.apiKey)
            val response = networkResponse.execute()

            if (response?.isSuccessful == true) {
                response.body()?.let {
                    cacheController.updateCacheTime()
                    cacheController.saveCache(it)
                    prepareData(it)
                } ?: showError()
            } else showError()
        } catch (e: IOException) {
            Log.e(WeatherRepository::class.java.name, e.message)
            showError()
        }

        fetchingData.set(false)
    }

    /**
     * Converters the server response or cached data to the weather data object which can be used
     * by the presenter. If the conversion was not successful, the call will call the presenter to
     * show an error. Else it will send the converted object to the presenter.
     */
    private fun prepareData(data: String) {
        try {
            gson.fromJson<WeatherModel>(data, WeatherModel::class.java)?.let {
                appExecutor.mainThread.execute {
                    presenter.presentData(it)
                }
            } ?: showError()
        } catch (e: Exception) {
            Log.e(WeatherRepository::class.java.name, e.message)
            showError()
        }
    }

    /**
     * Calls the presenter to let this class know that an error occurred and the underlying view
     * should display an error.
     */
    private fun showError() {
        appExecutor.mainThread.execute {
            presenter.presentError()
        }
    }
}