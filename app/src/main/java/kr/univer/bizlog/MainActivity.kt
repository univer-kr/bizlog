package kr.univer.bizlog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import kr.univer.bizlog.data.AppDatabase
import kr.univer.bizlog.data.AppSettings
import kr.univer.bizlog.data.LedgerRepository
import kr.univer.bizlog.service.RecordingService
import kr.univer.bizlog.ui.Bz
import kr.univer.bizlog.ui.BizLogScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RecordingService.start(this) // 화면이 보일 때만 포그라운드 서비스 시작이 허용된다(Android 14)
        val repo = LedgerRepository(AppDatabase.get(this))
        val settings = AppSettings(this)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(surface = Bz.raisedHi, onSurface = Bz.ink, primary = Bz.ink, background = Bz.page)) {
                BizLogScreen(repo, settings)
            }
        }
    }
}
