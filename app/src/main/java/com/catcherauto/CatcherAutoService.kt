package com.catcherauto

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executor

class CatcherAutoService : AccessibilityService() {

    // Pedido que pasó todos los filtros (ciudad + distancia + whitelist)
    private data class PedidoCandidato(val goY: Float, val distancia: Float, val keyword: String)

    companion object {
        const val CATCHER_PACKAGE   = "webviewgold.catcher"
        const val COOLDOWN_MS       = 5000L
        const val SCAN_INTERVAL_MS  = 1000L  // escanear cada 1s, no cada 300ms

        // Columna X donde está el botón GO! (lado derecho, pantalla 1080px)
        const val GO_SCAN_X   = 940
        const val GO_SCAN_Y_MIN = 300   // inicio del rango de búsqueda vertical
        const val GO_SCAN_Y_MAX = 950   // fin del rango

        // El botón GO! es oscuro/navy Y el centro de pantalla es blanco (tarjeta)
        // El banner también es oscuro pero abarca todo el ancho → centro también oscuro

        // Botón ACEPTAR en pantalla de confirmación (WebView, no accesible por árbol)
        const val ACEPTAR_X = 540f
        const val ACEPTAR_Y = 2050f

        var instance: CatcherAutoService? = null

        // Solo se aceptan pedidos cuyo OCR contenga esta ciudad
        // Prefijo corto para tolerar errores de OCR (ej: "FUENLANBRADA")
        const val REQUIRED_CITY = "Fuenla"

        // Distancia máxima al restaurante (patrón OCR: "a 3.5km de ti")
        const val MAX_DISTANCE_KM = 3.0f
        val DISTANCE_REGEX = Regex("""a (\d+(?:[.,]\d+)?)km""")

        // Clave de prefs → lista de keywords OCR (cualquiera que aparezca = match)
        // Nota: el filtro REQUIRED_CITY ya garantiza que el pedido es de Fuenlabrada,
        // así que no se necesita "Fuenlabrada" como keyword aquí.
        // Restaurantes que no muestran ciudad en el OCR — se salta el filtro de ciudad
        val KEYWORDS_SIN_CIUDAD = setOf("El Dorado")

        val RESTAURANTES = mapOf(
            "pref_burger_king_loranca" to listOf("Loranca"),
            "pref_burger_king_naranjo" to listOf("Naranjo"),
            "pref_carlos"              to listOf("Carlos", "Pizzerias", "PIZZERÍA", "PIZZERIA"),
            "pref_papa_john"           to listOf("Papa John"),
            "pref_telepizza"           to listOf("Telepizza"),
            "pref_popeyes"             to listOf("PLK", "Popeyes"),
            "pref_kfc"                 to listOf("KFC"),
            "pref_lastmile"            to listOf("El Dorado")
        )
    }

    private val handler = Handler(Looper.getMainLooper())

    @Volatile var escaneoActivo = true
    @Volatile private var apagadoManualmente = false
    @Volatile private var currentPkg = ""

    private enum class State { IDLE, WAITING_CONFIRM }
    @Volatile private var state = State.IDLE
    private var lastActionTime  = 0L
    private var lastScanTime    = 0L
    private var lastHeartbeat   = 0L
    private var lastNoVisible   = 0L
    @Volatile private var scanEnProgreso = false

    // ──────────────────────────────────────────────────────────────
    // Ciclo de vida
    // ──────────────────────────────────────────────────────────────

    override fun onServiceConnected() {
        instance = this
        apagadoManualmente = false
        // Restaurar estado ON/OFF de la sesión anterior
        val prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
        escaneoActivo = prefs.getBoolean("pref_escaneo_activo", true)
        if (escaneoActivo) {
            handler.removeCallbacks(pollingRunnable)
            handler.postDelayed(pollingRunnable, 500)
        }
    }

    override fun onDestroy() {
        instance = null
        apagadoManualmente = true  // evita que callbacks async en vuelo reactiven el escaneo
        escaneoActivo = false
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            event.packageName?.toString()?.let { currentPkg = it }
        }
    }
    override fun onInterrupt() {
        apagadoManualmente = true  // ídem: corta cualquier cadena de clicks en vuelo
        escaneoActivo = false
        handler.removeCallbacksAndMessages(null)
        state = State.IDLE
    }

    // ──────────────────────────────────────────────────────────────
    // Polling principal (cada 300ms)
    // ──────────────────────────────────────────────────────────────

    private val pollingRunnable = object : Runnable {
        override fun run() {
            if (!escaneoActivo) return  // parado: no re-programar

            val now = System.currentTimeMillis()

            // Heartbeat cada 10s
            if (now - lastHeartbeat > 10_000L) {
                lastHeartbeat = now
                Log.d("CatcherAuto", "HEARTBEAT pkg=$currentPkg")
            }

            // Solo escanear si: activo + app correcta + IDLE + cooldown OK + intervalo OK
            if (escaneoActivo
                && currentPkg == CATCHER_PACKAGE
                && state == State.IDLE
                && now - lastActionTime >= COOLDOWN_MS
                && now - lastScanTime   >= SCAN_INTERVAL_MS
                && !scanEnProgreso
            ) {
                lastScanTime = now
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    escanearYClickar()
                } else {
                    // Fallback Android < 11: clic en coordenada fija
                    clickGoCoords(GO_SCAN_X.toFloat(), 650f)
                }
            }

            handler.postDelayed(this, 300)
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Escaneo de pantalla para detectar el botón GO!
    // ──────────────────────────────────────────────────────────────

    @RequiresApi(Build.VERSION_CODES.R)
    private fun escanearYClickar() {
        scanEnProgreso = true
        val executor = Executor { handler.post(it) }

        try {
        takeScreenshot(Display.DEFAULT_DISPLAY, executor, object : TakeScreenshotCallback {
            override fun onSuccess(result: ScreenshotResult) {
                try {
                    val hwBmp = Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                    result.hardwareBuffer.close()
                    val bmp = hwBmp?.copy(Bitmap.Config.ARGB_8888, false)
                    hwBmp?.recycle()

                    val goYs = encontrarBotonesGo(bmp)

                    if (goYs.isNotEmpty()) {
                        val label = if (goYs.size > 1)
                            "GO_DETECTADO x${goYs.size}: Ys=${goYs.map { it.toInt() }}"
                        else
                            "GO_DETECTADO Y=${goYs[0].toInt()}"
                        Log.d("CatcherAuto", "$label — OCR restaurante")
                        procesarOcrMultiple(bmp, goYs)
                    } else {
                        bmp?.recycle()
                        val now2 = System.currentTimeMillis()
                        if (now2 - lastNoVisible > 15_000L) {
                            lastNoVisible = now2
                            Log.d("CatcherAuto", "GO_NO_VISIBLE")
                        }
                        scanEnProgreso = false
                    }
                } catch (e: Exception) {
                    Log.e("CatcherAuto", "SCAN_ERROR: ${e.message}")
                    scanEnProgreso = false
                }
            }

            override fun onFailure(errorCode: Int) {
                Log.w("CatcherAuto", "SCREENSHOT_FAIL: $errorCode")
                scanEnProgreso = false
            }
        })
        } catch (e: Exception) {
            Log.e("CatcherAuto", "SCREENSHOT_CALL_ERROR: ${e.javaClass.simpleName}: ${e.message}")
            scanEnProgreso = false
        }
    }

    /**
     * Escanea la columna X=GO_SCAN_X buscando todos los botones GO! visibles.
     * Devuelve una lista con la coordenada Y del centro de cada botón encontrado.
     */
    private fun encontrarBotonesGo(bmp: Bitmap?): List<Float> {
        if (bmp == null) return emptyList()
        val x = GO_SCAN_X.coerceAtMost(bmp.width - 1)
        val resultados = mutableListOf<Float>()

        var y = GO_SCAN_Y_MIN
        var inicioOscuro = -1
        var contadorOscuro = 0

        while (y <= GO_SCAN_Y_MAX && y < bmp.height) {
            val pixel = bmp.getPixel(x, y)
            val esOscuro = Color.red(pixel) < 80
                && Color.green(pixel) < 80
                && Color.blue(pixel) < 110

            val centroBmp = bmp.getPixel(540.coerceAtMost(bmp.width - 1), y)
            val centroClaro = Color.red(centroBmp) > 180

            if (esOscuro && centroClaro) {
                if (inicioOscuro < 0) inicioOscuro = y
                contadorOscuro++
                if (contadorOscuro >= 30) {
                    val centro = (inicioOscuro + contadorOscuro / 2).toFloat()
                    resultados.add(centro.coerceAtMost(GO_SCAN_Y_MAX.toFloat()))
                    // Saltar el resto del botón actual (~80px) para buscar el siguiente
                    y = inicioOscuro + 100
                    inicioOscuro = -1
                    contadorOscuro = 0
                    continue
                }
            } else {
                inicioOscuro = -1
                contadorOscuro = 0
            }
            y++
        }
        return resultados
    }

    // ──────────────────────────────────────────────────────────────
    // Filtro por restaurante — OCR sobre el screenshot
    // ──────────────────────────────────────────────────────────────

    private fun obtenerWhitelist(): List<String> {
        val prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
        return RESTAURANTES.entries
            .filter  { prefs.getBoolean(it.key, true) }
            .flatMap { it.value }
    }

    /**
     * Lanza OCR en paralelo para cada GO! detectado.
     * Cuando todos los OCR terminan, llama a elegirYAceptar() con los candidatos válidos.
     * Si hay un solo GO! y el OCR falla → muestra botón ámbar para aprobación manual.
     */
    private fun procesarOcrMultiple(bmp: Bitmap?, goYs: List<Float>) {
        if (bmp == null || goYs.isEmpty()) { scanEnProgreso = false; return }

        val candidatos  = mutableListOf<PedidoCandidato>()
        val whitelist   = obtenerWhitelist()
        var pendientes  = goYs.size

        for (goY in goYs) {
            val cropTop    = 200.coerceAtMost(goY.toInt() - 50)
            val cropBottom = (goY.toInt() + 200).coerceAtMost(bmp.height - 1)
            val cropBmp    = Bitmap.createBitmap(bmp, 0, cropTop, bmp.width, cropBottom - cropTop)
            val image      = InputImage.fromBitmap(cropBmp, 0)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

            recognizer.process(image)
                .addOnSuccessListener { result ->
                    cropBmp.recycle()
                    if (!escaneoActivo) {
                        pendientes--
                        if (pendientes == 0) bmp.recycle()
                        scanEnProgreso = false
                        return@addOnSuccessListener
                    }
                    val text = result.text
                    Log.d("CatcherAuto", "OCR_TEXT[Y=${goY.toInt()}]: ${text.replace('\n', '|')}")

                    // Filtro 1 — ciudad (exento si el OCR matchea una keyword sin ciudad)
                    val exentoCiudad = KEYWORDS_SIN_CIUDAD.any { text.contains(it, ignoreCase = true) }
                    if (!exentoCiudad && !text.contains(REQUIRED_CITY, ignoreCase = true)) {
                        Log.w("CatcherAuto", "EXCLUIDO_CIUDAD[Y=${goY.toInt()}]")
                    } else {
                        // Filtro 2 — distancia
                        val dist = DISTANCE_REGEX.find(text)
                            ?.groupValues?.get(1)?.replace(',', '.')?.toFloatOrNull() ?: 0f
                        if (dist > MAX_DISTANCE_KM) {
                            Log.w("CatcherAuto", "EXCLUIDO_DISTANCIA[Y=${goY.toInt()}]: ${dist}km")
                        } else {
                            // Filtro 3 — whitelist
                            val match = whitelist.firstOrNull { text.contains(it, ignoreCase = true) }
                            if (match != null) {
                                Log.d("CatcherAuto", "CANDIDATO[Y=${goY.toInt()}]: $match dist=${dist}km")
                                candidatos.add(PedidoCandidato(goY, dist, match))
                            } else {
                                Log.w("CatcherAuto", "RESTAURANTE_SKIP[Y=${goY.toInt()}] — no en whitelist")
                            }
                        }
                    }

                    pendientes--
                    if (pendientes == 0) {
                        bmp.recycle()
                        elegirYAceptar(candidatos)
                    }
                }
                .addOnFailureListener { e ->
                    cropBmp.recycle()
                    Log.w("CatcherAuto", "OCR_FAIL[Y=${goY.toInt()}]: ${e.message}")
                    pendientes--
                    if (pendientes == 0) {
                        bmp.recycle()
                        elegirYAceptar(candidatos)
                    }
                }
        }
    }

    /**
     * Recibe los candidatos que pasaron todos los filtros.
     * Si hay más de uno, acepta el más cercano; el resto lo detectará el siguiente ciclo de scan.
     */
    private fun elegirYAceptar(candidatos: List<PedidoCandidato>) {
        scanEnProgreso = false
        if (candidatos.isEmpty()) {
            Log.w("CatcherAuto", "SIN_CANDIDATOS — todos descartados")
            return
        }
        if (currentPkg != CATCHER_PACKAGE) {
            Log.w("CatcherAuto", "ELEGIR_ABORTADO — Catcher no está en primer plano")
            return
        }
        val elegido = candidatos.minByOrNull { it.distancia }!!
        if (candidatos.size > 1) {
            val descartados = candidatos.filter { it != elegido }.joinToString { "${it.keyword} ${it.distancia}km" }
            Log.d("CatcherAuto", "PEDIDO_DOBLE — eligiendo más cercano: ${elegido.keyword} ${elegido.distancia}km | descartados: $descartados")
        } else {
            Log.d("CatcherAuto", "RESTAURANTE_OK: ${elegido.keyword} ${elegido.distancia}km — aceptando")
        }
        clickGoCoords(GO_SCAN_X.toFloat(), elegido.goY)
    }

    // ──────────────────────────────────────────────────────────────
    // Control ON/OFF (llamado desde MainActivity)
    // ──────────────────────────────────────────────────────────────

    fun toggleEscaneo() {
        escaneoActivo = !escaneoActivo
        getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
            .edit().putBoolean("pref_escaneo_activo", escaneoActivo).apply()
        if (escaneoActivo) {
            apagadoManualmente = false
            state = State.IDLE  // Bug 1: desbloquear state si quedó en WAITING_CONFIRM
            handler.removeCallbacks(pollingRunnable)
            handler.postDelayed(pollingRunnable, 300)
        } else {
            apagadoManualmente = true
            handler.removeCallbacksAndMessages(null)
        }
        Log.d("CatcherAuto", if (escaneoActivo) "ESCANEO_ACTIVADO" else "ESCANEO_PAUSADO")
    }

    // ──────────────────────────────────────────────────────────────
    // Gestos
    // ──────────────────────────────────────────────────────────────

    private fun clickGoCoords(x: Float, y: Float) {
        val path    = Path().apply { moveTo(x, y) }
        val stroke  = GestureDescription.StrokeDescription(path, 0, 1)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(g: GestureDescription) {
                state          = State.WAITING_CONFIRM
                lastActionTime = System.currentTimeMillis()
                scanEnProgreso = false
                // Auto-OFF: parar escaneo inmediatamente tras aceptar
                escaneoActivo  = false
                handler.removeCallbacksAndMessages(null) // Bug 2: cancela TODO, no solo polling
                getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
                    .edit().putBoolean("pref_escaneo_activo", false).apply()
                Log.d("CatcherAuto", "GO_PULSADO ($x,$y) — escaneo pausado automáticamente")
                vibrar()
                if (apagadoManualmente) {
                    Log.d("CatcherAuto", "ACEPTAR_ABORTADO — usuario apagó manualmente")
                    state = State.IDLE
                    return
                }
                val delay = (1000L..1200L).random()
                handler.postDelayed({ clickAceptar() }, delay)
            }
            override fun onCancelled(g: GestureDescription) {
                Log.w("CatcherAuto", "GESTO_CANCELADO")
                scanEnProgreso = false
            }
        }, handler)
    }

    // ──────────────────────────────────────────────────────────────
    // Pantalla de confirmación — botón "Aceptar"
    // ──────────────────────────────────────────────────────────────

    private fun clickAceptar() {
        if (apagadoManualmente) { state = State.IDLE; return }
        if (currentPkg != CATCHER_PACKAGE) {
            Log.w("CatcherAuto", "ACEPTAR_ABORTADO — Catcher no está en primer plano")
            state = State.IDLE
            return
        }
        val root = rootInActiveWindow
        var encontrado = false
        if (root != null) {
            try {
                val boton = buscarNodoClickable(root, "Aceptar")
                    ?: buscarNodoClickable(root, "ACEPTAR")
                if (boton != null) {
                    hacerClic(boton)
                    encontrado = true
                    Log.d("CatcherAuto", "ACEPTAR_PULSADO (árbol)")
                    handler.postDelayed({ programarVerificacion() }, 4000L)
                }
            } finally {
                root.recycle()
            }
        }
        if (!encontrado) {
            clickAceptarCoords()
        }
    }

    private fun clickAceptarCoords() {
        val path    = Path().apply { moveTo(ACEPTAR_X, ACEPTAR_Y) }
        val stroke  = GestureDescription.StrokeDescription(path, 0, 1)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(g: GestureDescription) {
                if (apagadoManualmente || currentPkg != CATCHER_PACKAGE) { state = State.IDLE; return }
                Log.d("CatcherAuto", "ACEPTAR_PULSADO (coords $ACEPTAR_X,$ACEPTAR_Y)")
                handler.postDelayed({ programarVerificacion() }, 4000L)
            }
            override fun onCancelled(g: GestureDescription) {
                Log.w("CatcherAuto", "ACEPTAR_GESTO_CANCELADO")
                state = State.IDLE
            }
        }, handler)
    }

    private fun programarVerificacion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            verificarPedidoAceptado()
        } else {
            reactivarEscaneo() // Android < 11: no hay takeScreenshot, reactivar por precaución
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Verificación post-ACEPTAR: ¿se aceptó el pedido o falló?
    // ──────────────────────────────────────────────────────────────

    @RequiresApi(Build.VERSION_CODES.R)
    private fun verificarPedidoAceptado() {
        val executor = Executor { handler.post(it) }
        takeScreenshot(Display.DEFAULT_DISPLAY, executor, object : TakeScreenshotCallback {
            override fun onSuccess(result: ScreenshotResult) {
                val hwBmp = Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                result.hardwareBuffer.close()
                val bmp = hwBmp?.copy(Bitmap.Config.ARGB_8888, false)
                hwBmp?.recycle()
                if (bmp == null) { state = State.IDLE; return }

                val image = InputImage.fromBitmap(bmp, 0)
                TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                    .process(image)
                    .addOnSuccessListener { ocr ->
                        bmp.recycle()
                        if (apagadoManualmente) {
                            // Servicio destruido/interrumpido o usuario pausó mientras verificábamos
                            state = State.IDLE
                            Log.d("CatcherAuto", "VERIF_ABORTADA — apagadoManualmente")
                            return@addOnSuccessListener
                        }
                        if (currentPkg != CATCHER_PACKAGE) {
                            Log.w("CatcherAuto", "VERIF_ABORTADA — Catcher no está en primer plano")
                            state = State.IDLE
                            return@addOnSuccessListener
                        }
                        val text = ocr.text
                        Log.d("CatcherAuto", "VERIF_OCR: ${text.replace('\n', '|')}")

                        val aceptado = text.contains("Felicidades", ignoreCase = true)
                        val fallo    = text.contains("Upps", ignoreCase = true)
                                    || text.contains("no disponible", ignoreCase = true)

                        when {
                            aceptado -> {
                                Log.d("CatcherAuto", "PEDIDO_CONFIRMADO — cerrando popup y escaneo OFF")
                                cerrarPopupFelicidades()
                            }
                            fallo -> {
                                Log.w("CatcherAuto", "PEDIDO_FALLIDO — cerrando popup y reactivando")
                                cerrarPopupOkYReactivar()
                            }
                            else -> {
                                Log.w("CatcherAuto", "VERIF_INCONCLUSA — reactivando por precaución")
                                reactivarEscaneo()
                            }
                        }
                    }
                    .addOnFailureListener { e ->
                        bmp.recycle()
                        Log.w("CatcherAuto", "VERIF_OCR_FAIL: ${e.message}")
                        if (!apagadoManualmente) reactivarEscaneo()
                    }
            }
            override fun onFailure(errorCode: Int) {
                Log.w("CatcherAuto", "VERIF_SCREENSHOT_FAIL: $errorCode — reactivando por precaución")
                if (!apagadoManualmente) reactivarEscaneo()
            }
        })
    }

    private fun cerrarPopupOkYReactivar() {
        // El popup "Upps / Pedido no disponible" es nativo → árbol accesible
        val root = rootInActiveWindow
        var cerrado = false
        if (root != null) {
            try {
                val boton = buscarNodoClickable(root, "OK")
                if (boton != null) {
                    hacerClic(boton)
                    cerrado = true
                    Log.d("CatcherAuto", "POPUP_OK_CERRADO (árbol)")
                }
            } finally {
                root.recycle()
            }
        }
        if (!cerrado) {
            // Fallback coordenadas: botón OK visible en el capture a ~(452, 747)
            val path    = Path().apply { moveTo(452f, 747f) }
            val stroke  = GestureDescription.StrokeDescription(path, 0, 1)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            dispatchGesture(gesture, null, handler)
            Log.d("CatcherAuto", "POPUP_OK_CERRADO (coords)")
        }
        handler.postDelayed({ reactivarEscaneo() }, 500L)
    }

    private fun cerrarPopupFelicidades() {
        val root = rootInActiveWindow
        var cerrado = false
        if (root != null) {
            try {
                val boton = buscarNodoClickable(root, "OK")
                if (boton != null) {
                    hacerClic(boton)
                    cerrado = true
                    Log.d("CatcherAuto", "FELICIDADES_OK_CERRADO (árbol)")
                }
            } finally {
                root.recycle()
            }
        }
        if (!cerrado) {
            val path    = Path().apply { moveTo(452f, 747f) }
            val stroke  = GestureDescription.StrokeDescription(path, 0, 1)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            dispatchGesture(gesture, null, handler)
            Log.d("CatcherAuto", "FELICIDADES_OK_CERRADO (coords)")
        }
        state = State.IDLE
        // escaneo permanece OFF — usuario reactiva al terminar la entrega
    }

    private fun reactivarEscaneo() {
        if (apagadoManualmente) {
            // El usuario apagó manualmente, o el servicio fue destruido/interrumpido
            state = State.IDLE
            Log.d("CatcherAuto", "REACTIVAR_BLOQUEADO — usuario apagó manualmente")
            return
        }
        escaneoActivo = true
        getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
            .edit().putBoolean("pref_escaneo_activo", true).apply()
        state = State.IDLE
        lastActionTime = 0L  // Bug 4: sin cooldown — el pedido falló, escanear de inmediato
        handler.removeCallbacks(pollingRunnable)
        handler.postDelayed(pollingRunnable, 300)
        Log.d("CatcherAuto", "ESCANEO_REACTIVADO — pedido no fue aceptado")
    }

    // ──────────────────────────────────────────────────────────────
    // Helpers de accesibilidad (para pantalla nativa de confirmación)
    // ──────────────────────────────────────────────────────────────

    private fun buscarNodoClickable(root: AccessibilityNodeInfo, texto: String): AccessibilityNodeInfo? {
        val nodos = root.findAccessibilityNodeInfosByText(texto)
        if (nodos.isEmpty()) return null
        for (nodo in nodos) {
            if (nodo.isClickable) return nodo
            var actual: AccessibilityNodeInfo? = nodo.parent
            repeat(5) {
                val padre = actual ?: return@repeat
                if (padre.isClickable) return padre
                actual = padre.parent
            }
        }
        return null
    }

    private fun hacerClic(nodo: AccessibilityNodeInfo) {
        if (!nodo.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            nodo.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Feedback táctil
    // ──────────────────────────────────────────────────────────────

    private fun vibrar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            val v = getSystemService(VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(120)
            }
        }
    }

}
