package app.cellscope

import android.app.Application
import app.cellscope.battery.PrivilegedSysfsAccess
import app.cellscope.data.CellScopeDatabase
import app.cellscope.update.UpdateManager
import com.topjohnwu.superuser.Shell

class CellScopeApplication : Application() {
    val database: CellScopeDatabase by lazy { CellScopeDatabase.create(this) }
    val sysfsAccess: PrivilegedSysfsAccess by lazy { PrivilegedSysfsAccess(this) }
    val updates: UpdateManager by lazy { UpdateManager(this) }

    override fun onCreate() {
        super.onCreate()
        Shell.setDefaultBuilder(Shell.Builder.create().setTimeout(10))
        updates.start()
    }
}
