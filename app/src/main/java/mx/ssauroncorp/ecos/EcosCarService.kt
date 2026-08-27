package mx.ssauroncorp.ecos

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

/**
 * EcosCarService — punto de entrada para Android Auto.
 *
 * Cuando el usuario conecta el teléfono al auto y abre Ecos,
 * Android Auto llama a este servicio para mostrar la lista de recordatorios
 * en la pantalla del auto.
 *
 * Requiere en AndroidManifest.xml:
 *   <service android:name=".EcosCarService" android:exported="true">
 *     <intent-filter>
 *       <action android:name="androidx.car.app.CarAppService" />
 *     </intent-filter>
 *   </service>
 */
class EcosCarService : CarAppService() {

    override fun createHostValidator(): HostValidator = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    override fun onCreateSession(): Session {
        return object : Session() {
            override fun onCreateScreen(intent: Intent): androidx.car.app.Screen {
                return ReminderListScreen(carContext)
            }
        }
    }
}
