package com.locationshare.app.data

import android.location.Location
import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.ServerValue
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener

object Repo {
    private const val TAG = "Repo"

    // «Затухающий» трек: глубина истории и правила записи
    internal const val HIST_WINDOW_MS = 2 * 3_600_000L // точки старше не хранятся

    private const val HIST_MIN_INTERVAL_MS = 30_000L // не чаще раза в 30 с (кап на машине)
    private const val HIST_CLEANUP_EVERY_MS = 10 * 60_000L // чистка старого — раз в 10 мин
    private var lastHistMs = 0L
    private var lastHistCleanupMs = 0L

    // База создана в регионе europe-west1 — нужен явный URL, иначе SDK идёт в us-central1
    internal const val DB_URL =
        "https://locationshare-e897f-default-rtdb.europe-west1.firebasedatabase.app"

    val db: FirebaseDatabase by lazy { FirebaseDatabase.getInstance(DB_URL) }

    data class Pt(val lat: Double, val lon: Double, val ts: Long)

    data class DeviceState(
        val id: String,
        val name: String,
        val current: Pt?,
        val accuracy: Float,
        val battery: Int?,
        // Кастомный цвет устройства "#RRGGBB" (null = авто-назначение палитрой)
        val color: String? = null,
    ) {
        val online: Boolean
            get() = current != null && System.currentTimeMillis() - current.ts < 15 * 60_000
    }

    private fun groupRef(code: String) = db.getReference("groups").child(code)
    private fun devicesRef(code: String) = groupRef(code).child("devices")

    // История трека — вне devices: листенер списка устройств не перекачивает её при каждой записи
    private fun histRef(code: String, id: String) = groupRef(code).child("hist").child(id)

    fun publishCurrent(code: String, id: String, name: String, loc: Location, battery: Int?, moved: Boolean = false) {
        // Цвет живёт отдельным полем devices/{id}/color: publishCurrent перезаписывает
        // current целиком, а setColor не гоняется с ним за поле в одном узле
        devicesRef(code).child(id).child("current")
            .setValue(
                mapOf(
                    "lat" to loc.latitude,
                    "lon" to loc.longitude,
                    "acc" to loc.accuracy.toDouble(),
                    "bat" to battery,
                    "name" to name,
                    "ts" to ServerValue.TIMESTAMP,
                )
            )
        // Трек пишем только от «движительных» публикаций: heartbeat и эко-режим
        // публикуют стоячую точку — иначе трек засоряется повторами на месте
        if (moved) appendHist(code, id, loc)
    }

    /** Немедленная публикация кастомного цвета (не ждёт следующей точки); null = сброс на авто */
    fun setColor(code: String, id: String, hex: String?) {
        val ref = devicesRef(code).child(id).child("color")
        if (hex == null) ref.removeValue() else ref.setValue(hex)
    }

    /** Точка трека: не чаще раза в 30 с; заодно изредка чистим старше окна */
    private fun appendHist(code: String, id: String, loc: Location) {
        val now = System.currentTimeMillis()
        if (now - lastHistMs < HIST_MIN_INTERVAL_MS) return
        lastHistMs = now
        histRef(code, id).push().setValue(
            mapOf(
                "lat" to loc.latitude,
                "lon" to loc.longitude,
                "ts" to ServerValue.TIMESTAMP,
            )
        )
        if (now - lastHistCleanupMs >= HIST_CLEANUP_EVERY_MS) {
            lastHistCleanupMs = now
            // Запрос работает и без .indexOn в правилах, Firebase лишь логирует предупреждение.
            // Query.removeValue() в SDK 21 нет — читаем срез и удаляем по ключам.
            histRef(code, id).orderByChild("ts").endAt((now - HIST_WINDOW_MS).toDouble()).get()
                .addOnSuccessListener { snap ->
                    for (child in snap.children) child.ref.removeValue()
                }
        }
    }

    fun observe(code: String, onUpdate: (List<DeviceState>) -> Unit): ValueEventListener {
        val l = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                val list = mutableListOf<DeviceState>()
                for (dev in s.children) {
                    val id = dev.key ?: continue
                    val cur = dev.child("current")
                    if (!cur.exists()) continue
                    val lat = cur.child("lat").getValue(Double::class.java) ?: continue
                    val lon = cur.child("lon").getValue(Double::class.java) ?: continue
                    val ts = cur.child("ts").getValue(Long::class.java) ?: 0L

                    // Самоочистка: записи старше 24 ч (переустановки, ушедшие устройства)
                    if (ts > 0 && System.currentTimeMillis() - ts > 24 * 3_600_000L) {
                        dev.ref.removeValue()
                        histRef(code, id).removeValue()
                        continue
                    }

                    list.add(
                        DeviceState(
                            id = id,
                            name = cur.child("name").getValue(String::class.java) ?: id,
                            current = Pt(lat, lon, ts),
                            accuracy = (cur.child("acc").getValue(Double::class.java) ?: 50.0).toFloat(),
                            battery = cur.child("bat").getValue(Int::class.java),
                            color = dev.child("color").getValue(String::class.java),
                        )
                    )
                }
                onUpdate(list.sortedBy { it.name })
            }

            override fun onCancelled(e: DatabaseError) {
                Log.e(TAG, "observe cancelled: ${e.message}")
            }
        }
        devicesRef(code).addValueEventListener(l)
        return l
    }

    fun stop(code: String, listener: ValueEventListener) {
        devicesRef(code).removeEventListener(listener)
    }

    // ---------- Затухающий трек ----------

    /** Живой листенер истории точек устройства (трек дорисовывается, пока выбрано) */
    fun observeTrack(code: String, id: String, onUpdate: (List<Pt>) -> Unit): ValueEventListener {
        val l = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                val pts = mutableListOf<Pt>()
                for (p in s.children) {
                    val lat = p.child("lat").getValue(Double::class.java) ?: continue
                    val lon = p.child("lon").getValue(Double::class.java) ?: continue
                    val ts = p.child("ts").getValue(Long::class.java) ?: 0L
                    pts.add(Pt(lat, lon, ts))
                }
                // push-ключи уже хронологичны; сортировка — страховка
                onUpdate(pts.sortedBy { it.ts })
            }

            override fun onCancelled(e: DatabaseError) {
                Log.e(TAG, "observeTrack cancelled: ${e.message}")
            }
        }
        histRef(code, id).addValueEventListener(l)
        return l
    }

    fun stopTrack(code: String, id: String, listener: ValueEventListener) {
        histRef(code, id).removeEventListener(listener)
    }

    // ---------- Администрирование ----------

    /** Заявка на администратора: занимает место, только если оно свободно */
    fun claimAdmin(code: String, id: String) {
        groupRef(code).child("admin").runTransaction(object : Transaction.Handler {
            override fun doTransaction(cur: MutableData): Transaction.Result {
                if (cur.value == null) cur.value = id
                return Transaction.success(cur)
            }

            override fun onComplete(e: DatabaseError?, committed: Boolean, cur: DataSnapshot?) {}
        })
    }

    fun observeAdmin(code: String, onUpdate: (String?) -> Unit): ValueEventListener {
        val l = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                onUpdate(s.getValue(String::class.java))
            }

            override fun onCancelled(e: DatabaseError) {}
        }
        groupRef(code).child("admin").addValueEventListener(l)
        return l
    }

    fun stopAdmin(code: String, listener: ValueEventListener) {
        groupRef(code).child("admin").removeEventListener(listener)
    }

    /** Удаление устройства из группы (кнопка администратора) */
    fun removeDevice(code: String, id: String) {
        devicesRef(code).child(id).removeValue()
        histRef(code, id).removeValue()
    }

    // ---------- «Найти телефон» ----------

    fun ring(code: String, id: String, byName: String) {
        devicesRef(code).child(id).child("ring")
            .setValue(mapOf("ts" to ServerValue.TIMESTAMP, "by" to byName))
    }

    fun observeRing(code: String, id: String, onRing: (String?) -> Unit): ValueEventListener {
        var last = 0L
        val l = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                if (!s.exists()) return
                val ts = s.child("ts").getValue(Long::class.java) ?: return
                if (ts > last) {
                    val fresh = System.currentTimeMillis() - ts < 120_000
                    val by = s.child("by").getValue(String::class.java)
                    last = ts
                    if (fresh) onRing(by)
                }
            }

            override fun onCancelled(e: DatabaseError) {}
        }
        devicesRef(code).child(id).child("ring").addValueEventListener(l)
        return l
    }

    fun stopRing(code: String, id: String, listener: ValueEventListener) {
        devicesRef(code).child(id).child("ring").removeEventListener(listener)
    }
}
