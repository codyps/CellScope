package app.cellscope.battery;

interface IPrivilegedSysfsService {
    void destroy() = 16777114;
    String readPowerSupplySnapshot() = 1;
}
