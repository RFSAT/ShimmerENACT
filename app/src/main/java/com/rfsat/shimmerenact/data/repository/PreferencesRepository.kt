package com.rfsat.shimmerenact.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.rfsat.shimmerenact.data.models.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "shimmer_prefs")

class PreferencesRepository(private val context: Context) {

    companion object {
        val KEY_GSR_BT_ID       = stringPreferencesKey("gsr_bt_id")
        val KEY_EXG_BT_ID       = stringPreferencesKey("exg_bt_id")
        val KEY_CUSTOM_BT_ID    = stringPreferencesKey("custom_bt_id")
        val KEY_CUSTOM_NAME     = stringPreferencesKey("custom_name")
        val KEY_IMU_BT_ID       = stringPreferencesKey("imu_bt_id")
        val KEY_EMG_BT_ID       = stringPreferencesKey("emg_bt_id")
        val KEY_SAMPLING_RATE   = intPreferencesKey("sampling_rate")
        val KEY_LAST_SENSOR     = stringPreferencesKey("last_sensor_type")
        val KEY_LAST_ADDRESS    = stringPreferencesKey("last_bt_address")
        val KEY_CHART_SIGNALS   = stringPreferencesKey("chart_signals")  // comma-separated keys
    }

    val gsrBtId: Flow<String> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_GSR_BT_ID] ?: SensorType.GSR_PLUS.defaultBtSuffix }

    val exgBtId: Flow<String> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_EXG_BT_ID] ?: SensorType.EXG.defaultBtSuffix }

    val imuBtId: Flow<String> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_IMU_BT_ID] ?: SensorType.IMU.defaultBtSuffix }

    val emgBtId: Flow<String> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_EMG_BT_ID] ?: SensorType.EMG.defaultBtSuffix }

    val customBtId: Flow<String> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_CUSTOM_BT_ID] ?: "" }

    val samplingRate: Flow<Int> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_SAMPLING_RATE] ?: 51 }

    val lastBtAddress: Flow<String> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_LAST_ADDRESS] ?: "" }

    suspend fun saveGsrBtId(id: String) = context.dataStore.edit { it[KEY_GSR_BT_ID] = id }
    suspend fun saveExgBtId(id: String) = context.dataStore.edit { it[KEY_EXG_BT_ID] = id }
    suspend fun saveCustomBtId(id: String) = context.dataStore.edit { it[KEY_CUSTOM_BT_ID] = id }
    suspend fun saveImuBtId(id: String)    = context.dataStore.edit { it[KEY_IMU_BT_ID] = id }
    suspend fun saveEmgBtId(id: String)    = context.dataStore.edit { it[KEY_EMG_BT_ID] = id }
    suspend fun saveCustomName(name: String) = context.dataStore.edit { it[KEY_CUSTOM_NAME] = name }
    suspend fun saveSamplingRate(rate: Int) = context.dataStore.edit { it[KEY_SAMPLING_RATE] = rate }
    suspend fun saveLastAddress(addr: String) = context.dataStore.edit { it[KEY_LAST_ADDRESS] = addr }
    suspend fun saveLastSensorType(type: String) = context.dataStore.edit { it[KEY_LAST_SENSOR] = type }
}
