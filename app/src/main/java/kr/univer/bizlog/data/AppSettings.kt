package kr.univer.bizlog.data

import android.content.Context
import kr.univer.bizlog.export.CsvHeader

/** 운전자 프로필(P0: 한 명)과 서식 기본정보. 값이 적어 SharedPreferences로 충분하다. */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var driverName: String
        get() = prefs.getString("driverName", "사용자 A")!!
        set(v) = prefs.edit().putString("driverName", v).apply()
    var driverDept: String
        get() = prefs.getString("driverDept", "영업 1팀")!!
        set(v) = prefs.edit().putString("driverDept", v).apply()
    var companyName: String
        get() = prefs.getString("companyName", "")!!
        set(v) = prefs.edit().putString("companyName", v).apply()
    var bizRegNo: String
        get() = prefs.getString("bizRegNo", "")!!
        set(v) = prefs.edit().putString("bizRegNo", v).apply()
    var carModel: String
        get() = prefs.getString("carModel", "")!!
        set(v) = prefs.edit().putString("carModel", v).apply()
    var plateNo: String
        get() = prefs.getString("plateNo", "")!!
        set(v) = prefs.edit().putString("plateNo", v).apply()

    fun csvHeader(year: Int) = CsvHeader(year, companyName, bizRegNo, carModel, plateNo)
}
