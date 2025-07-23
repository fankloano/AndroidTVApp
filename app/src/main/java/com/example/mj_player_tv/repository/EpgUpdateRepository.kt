package com.example.mj_player_tv.repository

import android.util.Log
import com.example.mj_player_tv.database.ObjectBox
import com.example.mj_player_tv.database.entity.EpgDataOB
import com.example.mj_player_tv.database.entity.EpgDataOB_
import com.example.mj_player_tv.database.entity.EpgSource
import com.example.mj_player_tv.database.entity.EpgSourceChannel
import com.example.mj_player_tv.database.entity.EpgSourceChannel_
import com.example.mj_player_tv.network.RetrofitInstance
import com.example.mj_player_tv.network.externalepg.Channel
import com.example.mj_player_tv.utils.Resource
import io.objectbox.Box
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.threeten.bp.Instant
import org.threeten.bp.ZoneId
import org.threeten.bp.ZonedDateTime
import org.threeten.bp.format.DateTimeFormatter
import org.tukaani.xz.XZInputStream
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import retrofit2.Response
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

object EpgUpdateRepository {

    private val channelsService = RetrofitInstance

    private val epgSourceBox: Box<EpgSource>
    private val epgChannelBox: Box<EpgSourceChannel>
    private val epgDataBox: Box<EpgDataOB>

    private lateinit var existingEpgDataIds: Set<String>
    private lateinit var existingEpgChannelIds: Set<String>
    private lateinit var existingEpgChannels: List<EpgSourceChannel>
    private var parsedChannelIds: MutableSet<String> = mutableSetOf()

    init {
        val store = ObjectBox.store
        epgSourceBox = store.boxFor(EpgSource::class.java)
        epgChannelBox = store.boxFor(EpgSourceChannel::class.java)
        epgDataBox = store.boxFor(EpgDataOB::class.java)
    }

    private val _epgUpdateProcessState = MutableStateFlow<Map<String, EpgUpdateProcessState>>(
        emptyMap()
    )
    val epgUpdateProcessState: StateFlow<Map<String,EpgUpdateProcessState>> get() = _epgUpdateProcessState

    fun removeEpgSource(epgName: String) {
        _epgUpdateProcessState.value = _epgUpdateProcessState.value - epgName
    }

    fun updateState(epgName: String, state: EpgUpdateProcessState) {
        _epgUpdateProcessState.value += (epgName to state)
    }

    fun resetEpgProcessState() {
        _epgUpdateProcessState.value = emptyMap()
    }

    suspend fun downloadEpgFromExternalSource(url: String, epgSource: EpgSource): Resource<String> {
        return try {
            Log.e("EPGSOURCE UPDATEN", "START")
            updateState(epgSource.name, EpgUpdateProcessState.Loading)
            val response = channelsService.getXmlInstance("http://example.com/").downloadEpgXml(url)

            if (response.isSuccessful) {
                Log.d("EPGSOURCE UPDATEN", "SOURCE OK")
                val inputStream = getResponseInputStream(response, url)
                if (inputStream != null) {
                    parseXmlStream(inputStream, epgSource)
                    Resource.Success("OK")
                } else {
                    Resource.Error("Empty response body")
                }
            } else {
                Log.d("EPGSOURCE UPDATEN", "SOURCE FEHLER: ${response.code()} / ${response.message()}")
                Resource.Error("Network error: ${response.code()} ${response.message()}")
            }
        } catch (e: IOException) {
            Log.d("EPGSOURCE UPDATEN", "SOURCE IO EXCEP.: ${e.message}")
            Resource.Error("Network error: ${e.message}")
        } catch (e: Exception) {
            Log.d("EPGSOURCE UPDATEN", "SOURCE ALLG. EXCEP.: ${e.message}")
            Resource.Error("Unknown error: ${e.message}")
        }
    }

    private suspend fun getResponseInputStream(
        response: Response<ResponseBody>,
        url: String
    ): InputStream? = withContext(Dispatchers.IO) {
        val contentEncoding = response.headers()["Content-Encoding"]
        val bodyStream = response.body()?.byteStream() ?: return@withContext null

        // Check if URL or headers indicate GZIP
        if (url.endsWith(".gz", true) || contentEncoding?.contains("gzip") == true) {
            Log.d("EPGSOURCE UPDATEN", "IST GZ DATEI")
            return@withContext GzipCompressorInputStream(bodyStream)
        }

        // Check if URL or headers indicate XZ
        if (url.endsWith(".xz", true)) {
            Log.d("EPGSOURCE UPDATEN", "IST XZ DATEI")
            return@withContext XZInputStream(bodyStream)
        }

        // Perform magic number detection if no clear hint exists
        val bufferedStream = BufferedInputStream(bodyStream)
        bufferedStream.mark(6) // Mark the stream for reset after reading magic numbers

        val headerBytes = ByteArray(6)
        val readBytes = bufferedStream.read(headerBytes)
        bufferedStream.reset()

        // GZIP magic number check (0x1F, 0x8B)
        if (readBytes >= 2 && headerBytes[0] == 0x1F.toByte() && headerBytes[1] == 0x8B.toByte()) {
            Log.d("EPGSOURCE UPDATEN", "MAGIC: IST GZ DATEI")
            return@withContext GzipCompressorInputStream(bufferedStream)
        }

        // XZ magic number check (0xFD, '7', 'z', 'X', 'Z', 0x00)
        if (readBytes >= 6 && headerBytes[0] == 0xFD.toByte() &&
            headerBytes[1] == '7'.code.toByte() && headerBytes[2] == 'z'.toByte() &&
            headerBytes[3] == 'X'.code.toByte() && headerBytes[4] == 'Z'.toByte() &&
            headerBytes[5] == 0x00.toByte()
        ) {
            Log.d("EPGSOURCE UPDATEN", "MAGIC: IST XZ DATEI")
            return@withContext XZInputStream(bufferedStream)
        }
        Log.d("EPGSOURCE UPDATEN", "IST XML")
        // Default to returning the uncompressed stream
        return@withContext bufferedStream
    }


    suspend fun parseXmlStream(inputStream: InputStream, epgSource: EpgSource): Resource<String> =
        withContext(Dispatchers.IO) {
            try {
                Log.e("EPGSOURCE UPDATEN", "START PARSEN")
                existingEpgDataIds = epgDataBox.query(EpgDataOB_.epgSourceId.equal(epgSource.id)).build().find()
                    .map { it.idByAccountData } // Annahme: "idByAccountData" ist die Unique ID der Programmdaten
                    .toSet()
                existingEpgChannels = epgChannelBox.query(EpgSourceChannel_.relatedepgSourceId.equal(epgSource.id)).build().find()
                // Holen der bestehenden EPG-Kanäle und -Daten der Quelle
                existingEpgChannelIds = existingEpgChannels.map { it.chEpgId }.toSet()
                val factory = XmlPullParserFactory.newInstance()
                val parser = factory.newPullParser()
                parser.setInput(inputStream, null)
                var eventType = parser.eventType
                val epgDataList = mutableListOf<EpgDataOB>()
                val epgChannelList = mutableListOf<EpgSourceChannel>()

                while (eventType != XmlPullParser.END_DOCUMENT) {
                    when (eventType) {
                        XmlPullParser.START_TAG -> {
                            when (parser.name) {
                                "channel" -> {
                                    val channel = parseChannel(parser, epgSource)
                                    if (channel != null) epgChannelList.add(channel)
                                }
                                "programme" -> {
                                    val program = parseProgram(parser, epgSource)
                                    if (program != null) epgDataList.add(program)
                                }
                            }
                        }
                    }
                    eventType = parser.next()
                }
                Log.e("EPGSOURCE UPDATEN", "EPCH: ${epgChannelList.size} /// EPGDATA: ${epgDataList.size}")
                deleteRemovedEpgChannels(epgSource.id)
                storeEpgData(epgSource, epgChannelList, epgDataList)
                updateState(epgSource.name, EpgUpdateProcessState.Success)
                Resource.Success("OK")
            } catch (e: Exception) {
                Log.e("EPGSOURCE UPDATEN", "Error parsing XML: ${e.message}")
                updateState(epgSource.name, EpgUpdateProcessState.Success)
                Resource.Error("Error parsing XML: ${e.message}")
            } finally {
                inputStream.close()
            }
        }



    private fun parseChannel(parser: XmlPullParser, epgSource: EpgSource): EpgSourceChannel? {
        val id = parser.getAttributeValue(null, "id").lowercase()
        val displayNames = mutableListOf<String>()
        val icons = mutableListOf<String>()
        val chEpgId = "${epgSource.id}_${id.lowercase()}"
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "display-name" -> displayNames.add(parser.nextText())
                    "icon" -> icons.add(parser.getAttributeValue(null, "src") ?: "")
                }
            }
        }
        parsedChannelIds.add(chEpgId)
        val existingChannel = existingEpgChannels.find { it.chEpgId == chEpgId }

        return if (existingChannel != null) {
                existingChannel.apply {
                    name = displayNames.firstOrNull() ?: ""
                    icon = icons
                    display_name = displayNames
                    epgsource.target = epgSource
                }
            Log.e("EPGSOURCE UPDATEN", "EXISTS: ${existingChannel.name}")
                epgChannelBox.put(existingChannel)
            null
        } else {
            EpgSourceChannel(
                id = 0,
                chEpgId = chEpgId,
                chId = id.lowercase(),
                icon = icons,
                name = displayNames.firstOrNull() ?: "",
                relatedepgSourceId = epgSource.id,
                display_name = displayNames,
                isExternalEpg = true
            ).apply { epgsource.target = epgSource }
        }
    }

    private fun parseProgram(parser: XmlPullParser, epgSource: EpgSource): EpgDataOB? {

        val start = parser.getAttributeValue(null, "start")?.let {
            ZonedDateTime.parse(it, DateTimeFormatter.ofPattern("yyyyMMddHHmmss Z")).toEpochSecond()
        } ?: return null

        val stop = parser.getAttributeValue(null, "stop")?.let {
            ZonedDateTime.parse(it, DateTimeFormatter.ofPattern("yyyyMMddHHmmss Z")).toEpochSecond()
        } ?: return null

        val channel = parser.getAttributeValue(null, "channel") ?: return null

        val idByAccountData = "${channel}_${start}_${epgSource.id}"
// Überprüfen, ob die Programmdaten bereits existieren
        if (existingEpgDataIds.contains(idByAccountData)) {
            // Wenn die Programmdaten bereits existieren, überspringen
            return null
        }

        var title = ""
        var subTitle = ""
        var description = ""
        var iconUrl = ""
        var episodeNum = ""
        var rating = ""

        val listMap = mutableMapOf(
            "category" to mutableListOf(),
            "director" to mutableListOf(),
            "actor" to mutableListOf(),
            "country" to mutableListOf<String>()
        )

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType == XmlPullParser.START_TAG) {
                val tagName = parser.name
                val textValue = parser.nextText()
                when (tagName) {
                    "title" -> title = textValue
                    "sub-title" -> subTitle = textValue
                    "desc" -> description = textValue
                    "icon" -> iconUrl = parser.getAttributeValue(null, "src") ?: ""
                    "episode-num" -> episodeNum = textValue
                    "rating" -> rating = textValue
                    else -> listMap[tagName]?.add(textValue)
                }
            }
        }

        return EpgDataOB(
            id = 0,
            idByAccountData = "${channel}_${start}_${epgSource.id}",
            epgId = channel,
            chId = channel,
            datum = Instant.ofEpochSecond(start).atZone(ZoneId.systemDefault()).toLocalDate().toString(),
            name = title,
            sub_title = subTitle,
            descr = description,
            category = listMap["category"].takeIf { it?.isNotEmpty() == true },
            director = listMap["director"].takeIf { it?.isNotEmpty() == true },
            actor = listMap["actor"].takeIf { it?.isNotEmpty() == true },
            country = listMap["country"].takeIf { it?.isNotEmpty() == true },
            showIcon = iconUrl,
            episode_num = episodeNum,
            rating = rating,
            startTimestamp = start,
            stopTimestamp = stop,
            epgSourceId = epgSource.id.toInt(),
            epgChId = "${epgSource.id}_$channel"
        )
    }


    private suspend fun storeEpgData(
        epgSource: EpgSource,
        epgChannelList: List<EpgSourceChannel>,
        epgDataList: List<EpgDataOB>
    ) {
        withContext(Dispatchers.IO) {
            try {
                epgDataBox.store.runInTx {
                    Log.e("EPGSOURCE UPDATEN", "EPGDATEN: ${epgDataList.size}")
                    if (epgDataList.isNotEmpty()) {
                        epgDataList.chunked(5000).forEach { chunk -> epgDataBox.put(chunk) }
                    }
                    Log.e("EPGSOURCE UPDATEN", "EPGCHANNELS: ${epgChannelList.size}")
                    if (epgChannelList.isNotEmpty()) {
                        epgChannelBox.put(epgChannelList)
                    }
                }
                epgSource.lastUpdatedDate = System.currentTimeMillis() / 1000
                epgSourceBox.put(epgSource)
                Log.e("EPGSOURCE UPDATEN", "ENDE")
            } catch (e: Exception) {
                Log.e("DatabaseError", "Error inserting data", e)
            }
        }
    }

    private suspend fun deleteRemovedEpgChannels(epgSourceId: Long) {
        withContext(Dispatchers.IO) {
        // Holen der bestehenden EPG-Kanäle der Quelle
            val existingEpgChannels = epgChannelBox.query(EpgSourceChannel_.relatedepgSourceId.equal(epgSourceId)).build().find()

            // Kanäle, die gelöscht werden müssen (d.h. die nicht mehr vorhanden sind)
            val channelsToDelete = existingEpgChannels.filterNot { it.chEpgId in parsedChannelIds }

            // Löschen der veralteten Kanäle
            if (channelsToDelete.isNotEmpty()) {
                epgChannelBox.remove(channelsToDelete)
            }
        }
    }
}