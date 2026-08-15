package app.cellscope

import android.app.Application
import app.cellscope.data.CellScopeDatabase

class CellScopeApplication : Application() {
    val database: CellScopeDatabase by lazy { CellScopeDatabase.create(this) }
}
