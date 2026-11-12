package kr.univer.bizlog

import ai.pleos.playground.vehicle.Vehicle
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.onEach
import kr.univer.bizlog.domain.model.Gear
import kr.univer.bizlog.gateway.VehicleGateway
import kr.univer.bizlog.gateway.pleos.PleosVehicleGateway

// shortcut: Vehicle을 Activity 수명에 묶음. 기록 서비스(P0-7) 단계에서 서비스로 옮긴다.
class MainActivity : ComponentActivity() {
    private lateinit var vehicle: Vehicle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vehicle = Vehicle(applicationContext).apply { initialize() }
        val gateway = PleosVehicleGateway(vehicle)
        setContent { SignalScreen(gateway) }
    }

    override fun onDestroy() {
        vehicle.release()
        super.onDestroy()
    }
}

@Composable
private fun SignalScreen(gateway: VehicleGateway) {
    val odo = remember(gateway) { gateway.odometerKm.onEach { Log.i("BizLog", "ODO=$it km") } }
        .collectAsState(initial = null).value
    val gear = remember(gateway) { gateway.gear.onEach { Log.i("BizLog", "GEAR=$it") } }
        .collectAsState(initial = Gear.UNKNOWN).value
    Column(
        Modifier.fillMaxSize().background(Color(0xFF14171B)).padding(48.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text("비즈로그 · 차량 신호", color = Color(0xFFF1F3F5), fontSize = 28.sp)
        Text("ODO ${odo?.let { "%,.1f km".format(it) } ?: "—"}", color = Color(0xFF38C6F6), fontSize = 80.sp)
        Text("기어 $gear", color = Color(0xFFD6FD70), fontSize = 48.sp)
    }
}
