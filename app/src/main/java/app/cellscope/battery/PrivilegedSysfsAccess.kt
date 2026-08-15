package app.cellscope.battery

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import app.cellscope.BuildConfig
import com.topjohnwu.superuser.Shell
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku

data class SysfsAccessState(
    val activeProvider: SysfsProvider? = null,
    val shizukuRunning: Boolean = false,
    val shizukuPermissionGranted: Boolean = false,
    val rootEnabled: Boolean = false,
    val detail: String = "Android API only",
)

class PrivilegedSysfsAccess(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences("sysfs_access", Context.MODE_PRIVATE)
    private val directUnavailable = AtomicBoolean(false)
    private val readLock = Any()
    @Volatile
    private var shizukuService: IPrivilegedSysfsService? = null
    @Volatile
    private var cachedSnapshot: PowerSupplySnapshot? = null
    @Volatile
    private var cachedAtMs: Long = Long.MIN_VALUE
    private val _state = MutableStateFlow(SysfsAccessState(rootEnabled = rootEnabled))
    val state: StateFlow<SysfsAccessState> = _state.asStateFlow()

    private val userServiceArgs = Shizuku.UserServiceArgs(
        ComponentName(BuildConfig.APPLICATION_ID, PrivilegedSysfsService::class.java.name),
    ).daemon(false)
        .processNameSuffix("sysfs")
        .debuggable(BuildConfig.DEBUG)
        .version(BuildConfig.VERSION_CODE)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            shizukuService = IPrivilegedSysfsService.Stub.asInterface(binder)
            refreshState("Shizuku sysfs reader connected")
        }

        override fun onServiceDisconnected(name: ComponentName) {
            shizukuService = null
            refreshState("Shizuku sysfs reader disconnected")
        }
    }

    private val binderReceived = Shizuku.OnBinderReceivedListener {
        refreshState()
        bindShizukuIfAuthorized()
    }
    private val binderDead = Shizuku.OnBinderDeadListener {
        shizukuService = null
        refreshState("Shizuku service stopped")
    }
    private val permissionResult = Shizuku.OnRequestPermissionResultListener { requestCode, result ->
        if (requestCode != SHIZUKU_PERMISSION_REQUEST) return@OnRequestPermissionResultListener
        if (result == PackageManager.PERMISSION_GRANTED) {
            bindShizukuIfAuthorized()
        } else {
            refreshState("Shizuku permission denied")
        }
    }

    init {
        Shizuku.addBinderReceivedListenerSticky(binderReceived)
        Shizuku.addBinderDeadListener(binderDead)
        Shizuku.addRequestPermissionResultListener(permissionResult)
        refreshState()
        bindShizukuIfAuthorized()
    }

    fun requestShizukuPermission(): Boolean {
        if (!shizukuRunning()) {
            refreshState("Start Shizuku or Sui first")
            return false
        }
        return runCatching {
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                bindShizukuIfAuthorized()
                true
            } else if (Shizuku.shouldShowRequestPermissionRationale()) {
                refreshState("Shizuku permission was denied; allow CellScope in Shizuku")
                false
            } else {
                Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST)
                true
            }
        }.getOrElse {
            refreshState("Could not request Shizuku permission")
            false
        }
    }

    fun setRootEnabled(enabled: Boolean): Boolean {
        if (!enabled) {
            preferences.edit().putBoolean(KEY_ROOT_ENABLED, false).apply()
            refreshState("Root sysfs access disabled")
            return true
        }
        val granted = runCatching { Shell.getShell().isRoot }.getOrDefault(false)
        preferences.edit().putBoolean(KEY_ROOT_ENABLED, granted).apply()
        refreshState(if (granted) "Root sysfs reader enabled" else "Root access unavailable or denied")
        return granted
    }

    fun readSnapshot(): PowerSupplySnapshot? = synchronized(readLock) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (cachedAtMs != Long.MIN_VALUE && now - cachedAtMs < SNAPSHOT_CACHE_MS) {
            return@synchronized cachedSnapshot
        }

        val snapshot = readFreshSnapshot()
        cachedSnapshot = snapshot
        cachedAtMs = now
        snapshot
    }

    private fun readFreshSnapshot(): PowerSupplySnapshot? {
        if (!directUnavailable.get()) {
            val direct = PowerSupplySnapshotIo.readDirect()
            if (direct != null) {
                updateActiveProvider(SysfsProvider.DIRECT)
                return direct
            }
            directUnavailable.set(true)
        }

        shizukuService?.let { service ->
            val response = runCatching { service.readPowerSupplySnapshot() }
                .onFailure { Log.w(TAG, "Shizuku sysfs read failed", it) }
                .getOrNull()
            val snapshot = response
                ?.takeIf(String::isNotBlank)
                ?.let { PowerSupplySnapshot.parse(it, SysfsProvider.SHIZUKU) }
            if (snapshot != null) {
                Log.d(TAG, "Shizuku read ${snapshot.supplies.size} power supplies")
                updateActiveProvider(SysfsProvider.SHIZUKU)
                return snapshot
            }
            if (response != null) Log.w(TAG, "Shizuku returned no readable power supplies")
        }

        if (rootEnabled) {
            val result = runCatching { Shell.cmd(PowerSupplySnapshotIo.shellScript()).exec() }.getOrNull()
            val output = result?.takeIf { it.isSuccess }?.out?.joinToString("\n")
            if (!output.isNullOrBlank()) {
                updateActiveProvider(SysfsProvider.ROOT)
                return PowerSupplySnapshot.parse(output, SysfsProvider.ROOT)
            }
        }
        updateActiveProvider(null)
        return null
    }

    private val rootEnabled: Boolean
        get() = preferences.getBoolean(KEY_ROOT_ENABLED, false)

    private fun bindShizukuIfAuthorized() {
        if (!shizukuRunning() || !shizukuPermissionGranted() || shizukuService != null) return
        runCatching { Shizuku.bindUserService(userServiceArgs, connection) }
            .onFailure { refreshState("Could not connect Shizuku sysfs reader") }
    }

    private fun shizukuRunning(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    private fun shizukuPermissionGranted(): Boolean = shizukuRunning() && runCatching {
        !Shizuku.isPreV11() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    private fun updateActiveProvider(provider: SysfsProvider?) {
        if (_state.value.activeProvider == provider) return
        refreshState(provider = provider)
    }

    private fun refreshState(detail: String? = null, provider: SysfsProvider? = _state.value.activeProvider) {
        val running = shizukuRunning()
        val granted = shizukuPermissionGranted()
        val effectiveDetail = detail ?: when {
            provider != null -> "Reading extended battery data through ${provider.label}"
            granted -> "Waiting for Shizuku sysfs reader"
            running -> "Shizuku permission not granted"
            rootEnabled -> "Root enabled; waiting for a readable power supply"
            else -> "Android API only"
        }
        _state.value = SysfsAccessState(provider, running, granted, rootEnabled, effectiveDetail)
    }

    companion object {
        private const val SHIZUKU_PERMISSION_REQUEST = 8301
        private const val KEY_ROOT_ENABLED = "root_enabled"
        private const val SNAPSHOT_CACHE_MS = 500L
        private const val TAG = "CellScopeSysfs"
    }
}
