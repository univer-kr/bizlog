package kr.univer.bizlog.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/** 시동(부팅) 때 기록 서비스 자동 시작. BOOT_COMPLETED 수신 중에는 포그라운드 서비스 시작이 허용된다. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        runCatching { RecordingService.start(context) }
            .onFailure { Log.e("BizLog", "부팅 후 기록 서비스 시작 실패", it) }
    }
}
