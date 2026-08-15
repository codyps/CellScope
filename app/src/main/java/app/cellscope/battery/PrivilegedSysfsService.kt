package app.cellscope.battery

import androidx.annotation.Keep
import android.util.Log

@Keep
class PrivilegedSysfsService : IPrivilegedSysfsService.Stub() {
    override fun readPowerSupplySnapshot(): String = buildString {
        val snapshot = PowerSupplySnapshotIo.readDirect(SysfsProvider.SHIZUKU)
        Log.d("CellScopeSysfsService", "Read ${snapshot?.supplies?.size ?: 0} power supplies")
        snapshot?.supplies?.forEach { (name, values) ->
            append(PowerSupplySnapshot.SECTION_PREFIX).appendLine(name)
            values.forEach { (key, value) -> append(key).append('=').appendLine(value) }
        }
    }

    override fun destroy() {
        System.exit(0)
    }
}
