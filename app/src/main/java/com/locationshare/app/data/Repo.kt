package com.locationshare.app.data

import android.location.Location
import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener

object Repo {
    private const val TAG = "Repo"
    private const val TRACK_MAX = 200

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
        val track: List<Pt>,
    ) {
        val online: Boolean
            get() = current != null && System.currentTimeMillis() - current.ts < 15 * 60_000
    }

    private fun devicesRef(code: String) = db.getReference("groups").child(code).child("devices")

    fun publishCurrent(code: String, id: String, name: String, loc: Location, battery: Int?) {
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
    }

    fun addTrackPoint(code: String, id: String, loc: Location) {
        val track = devicesRef(code).child(id).child("track")
        track.push().setValue(
            mapOf("lat" to loc.latitude, "lon" to loc.longitude, "ts" to ServerValue.TIMESTAMP)
        )
        // подрезаем хвост, чтобы Firebase не разрастался
        track.orderByKey().limitToLast(TRACK_MAX + 60)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(s: DataSnapshot) {
                    val extra = s.childrenCount - TRACK_MAX
                    if (extra > 0) s.children.take(extra.toInt()).forEach { it.ref.removeValue() }
                }

                override fun onCancelled(e: DatabaseError) {}
            })
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
                    val track = dev.child("track").children.mapNotNull { p ->
                        val la = p.child("lat").getValue(Double::class.java) ?: return@mapNotNull null
                        val lo = p.child("lon").getValue(Double::class.java) ?: return@mapNotNull null
                        Pt(la, lo, p.child("ts").getValue(Long::class.java) ?: 0L)
                    }
                    list.add(
                        DeviceState(
                            id = id,
                            name = cur.child("name").getValue(String::class.java) ?: id,
                            current = Pt(lat, lon, ts),
                            accuracy = (cur.child("acc").getValue(Double::class.java) ?: 50.0).toFloat(),
                            battery = cur.child("bat").getValue(Int::class.java),
                            track = track,
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
