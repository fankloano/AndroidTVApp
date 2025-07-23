package com.example.mj_player_tv.repository

import android.app.Application
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.mj_player_tv.database.ObjectBox
import com.example.mj_player_tv.database.entity.EpgDataOB
import com.example.mj_player_tv.database.entity.EpgSource
import com.example.mj_player_tv.database.entity.EpgSourceChannel
import com.example.mj_player_tv.database.entity.Programme
import com.example.mj_player_tv.network.RetrofitInstance
import com.example.mj_player_tv.network.externalepg.Channel
import com.example.mj_player_tv.network.model.tmdb.TmdbMovieImageResponse
import com.example.mj_player_tv.network.model.tmdb.imdb_id.TMDB_imdb_id
import com.example.mj_player_tv.network.model.tmdb.moviedetails.TMDBMovieDetails
import com.example.mj_player_tv.network.model.tmdb.seasondetails.TMDBSeasonDetails
import com.example.mj_player_tv.network.model.tmdb.seriesdetails.TMDBSeriesDetails
import com.example.mj_player_tv.utils.Resource
import com.google.firebase.crashlytics.buildtools.reloc.org.apache.commons.io.input.BOMInputStream
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
import org.xml.sax.InputSource
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory
import retrofit2.HttpException
import retrofit2.Response
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.PushbackInputStream
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.xml.parsers.SAXParserFactory

class HelpRepository(private val application: Application) {
    private val channelsService = RetrofitInstance

    private val epgSourceBox: Box<EpgSource>
    private val epgChannelBox: Box<EpgSourceChannel>
    private val epgDataBox: Box<EpgDataOB>

    init {
        val store = ObjectBox.store
        epgSourceBox = store.boxFor(EpgSource::class.java)
        epgChannelBox = store.boxFor(EpgSourceChannel::class.java)
        epgDataBox = store.boxFor(EpgDataOB::class.java)
    }

    //EXTERNAL EPG
    private val _epgProcessState = MutableStateFlow<ExternEpgProcessState?>(null)
    val epgProcessState: StateFlow<ExternEpgProcessState?> = _epgProcessState

    fun resetEpgProcessState() {
        _epgProcessState.value = null
    }

    suspend fun downloadEpgFromExternalSource(url: String, epgSourceId: Long): Resource<String> {
        return try {
            _epgProcessState.value = ExternEpgProcessState.Loading
            val response = channelsService.getXmlInstance("http://example.com/").downloadEpgXml(url)

            if (response.isSuccessful) {
                val inputStream = getResponseInputStream(response, url)
                inputStream?.use { stream ->
                    parseXmlStream(stream, epgSourceId)
                }
                Resource.Success("OK")
            } else {
                Log.d("EPGSOURCEHINZUFÜGEN", "SOURCE FEHLER: ${response.code()} / ${response.message()}")
                Resource.Error("Network error: ${response.code()} ${response.message()}")
            }
        } catch (e: IOException) {
            Log.d("EPGSOURCEHINZUFÜGEN", "SOURCE IO EXCEP.: ${e.message}")
            _epgProcessState.value = ExternEpgProcessState.Error(e.message ?: "Error loading EPG from Url!")
            Resource.Error("Network error: ${e.message}")
        } catch (e: Exception) {
            Log.d("EPGSOURCEHINZUFÜGEN", "SOURCE ALLG. EXCEP.: ${e.message}")
            _epgProcessState.value = ExternEpgProcessState.Error(e.message ?: "Error loading EPG from Url!")
            Resource.Error("Unknown error: ${e.message}")
        }
    }

    private suspend fun getResponseInputStream(
        response: Response<ResponseBody>,
        url: String
    ): InputStream? = withContext(Dispatchers.IO) {
        val contentEncoding = response.headers()["Content-Encoding"]
        val bodyStream = response.body()?.byteStream() ?: return@withContext null

        // Check for GZIP or XZ encoding
        if (url.endsWith(".gz", true) || contentEncoding?.contains("gzip") == true) {
            return@withContext GzipCompressorInputStream(bodyStream)
        }

        if (url.endsWith(".xz", true)) {
            return@withContext XZInputStream(bodyStream)
        }

        // Magic number detection
        val bufferedStream = BufferedInputStream(bodyStream)
        bufferedStream.mark(6)

        val headerBytes = ByteArray(6)
        val readBytes = bufferedStream.read(headerBytes)
        bufferedStream.reset()

        // GZIP magic number check (0x1F, 0x8B)
        if (readBytes >= 2 && headerBytes[0] == 0x1F.toByte() && headerBytes[1] == 0x8B.toByte()) {
            return@withContext GzipCompressorInputStream(bufferedStream)
        }

        // XZ magic number check
        if (readBytes >= 6 && headerBytes[0] == 0xFD.toByte() &&
            headerBytes[1] == '7'.code.toByte() && headerBytes[2] == 'z'.toByte() &&
            headerBytes[3] == 'X'.code.toByte() && headerBytes[4] == 'Z'.toByte() &&
            headerBytes[5] == 0x00.toByte()
        ) {
            return@withContext XZInputStream(bufferedStream)
        }
        // Default to returning the uncompressed stream
        return@withContext bufferedStream
    }

    suspend fun parseXmlStream(inputStream: InputStream, epgSourceId: Long): Resource<String> =
        withContext(Dispatchers.IO) {
            try {
                val start = System.currentTimeMillis()
                _epgProcessState.value = ExternEpgProcessState.Parsing
                val epgSource = epgSourceBox.get(epgSourceId)
                val factory = XmlPullParserFactory.newInstance()
                val parser = factory.newPullParser()
                parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                val reader = BufferedReader(InputStreamReader(inputStream))
                parser.setInput(reader)

                _epgProcessState.value = ExternEpgProcessState.Parsing
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
                val end = System.currentTimeMillis()
                val duration = (end - start) / 1000.0
                _epgProcessState.value = ExternEpgProcessState.ParsingFinished(duration.toString())
                storeEpgData(epgSource, epgChannelList, epgDataList)
                scheduleNextEpgWorker(epgSource)
                _epgProcessState.value = ExternEpgProcessState.Success
                Resource.Success("OK")
            } catch (e: Exception) {
                Log.e("EPGSOURCEHINZUFÜGEN", "Error parsing XML: ${e.message}")
                _epgProcessState.value = ExternEpgProcessState.Error("Error parsing XML: ${e.message}")
                Resource.Error("Error parsing XML: ${e.message}")
            } catch(e: XmlPullParserException) {
                Log.e("EPGSOURCEHINZUFÜGEN", "XML Parse Error: ${e.message}")
                Resource.Error("Error parsing XML: ${e.message}")
            } finally {
                inputStream.close()
            }
        }

    private fun parseChannel(parser: XmlPullParser, epgSource: EpgSource): EpgSourceChannel? {
        val id = parser.getAttributeValue(null, "id") ?: return null
        val displayNames = mutableListOf<String>()
        val icons = mutableListOf<String>()

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "display-name" -> displayNames.add(parser.nextText())
                    "icon" -> icons.add(parser.getAttributeValue(null, "src") ?: "")
                }
            }
        }

        return EpgSourceChannel(
            id = 0,
            chEpgId = "${epgSource.id}_${id.lowercase()}",
            chId = id.lowercase(),
            icon = icons,
            name = displayNames.firstOrNull() ?: "",
            relatedepgSourceId = epgSource.id,
            display_name = displayNames,
            isExternalEpg = true
        ).apply { epgsource.target = epgSource }
    }

    private fun parseProgram(parser: XmlPullParser, epgSource: EpgSource): EpgDataOB? {
        try {
            // Start und Stop-Zeit
            val start = parser.getAttributeValue(null, "start")?.let {
                ZonedDateTime.parse(it, dateTimeFormatter).toEpochSecond()
            } ?: return null

            val stop = parser.getAttributeValue(null, "stop")?.let {
                ZonedDateTime.parse(it, dateTimeFormatter).toEpochSecond()
            } ?: return null

            val channel = parser.getAttributeValue(null, "channel") ?: return null

            // Variablen initialisieren
            var title = ""
            var subTitle = ""
            var description = ""
            var iconUrl = ""
            var episodeNum = ""
            var rating = ""
            val idByAccountData = StringBuilder().append(channel).append("_").append(start).append("_").append(epgSource.id).toString()
            // Tags als Listen
            val categories = ArrayList<String>()
            val directors = ArrayList<String>()
            val actors = ArrayList<String>()
            val countries = ArrayList<String>()

            // Parsing-Logik
            while (parser.next() != XmlPullParser.END_TAG) {
                if (parser.eventType == XmlPullParser.START_TAG) {
                    when (val tagName = parser.name) {
                        "title" -> title = parser.nextText() // Text extrahieren
                        "sub-title" -> subTitle = parser.nextText()
                        "desc" -> description = parser.nextText()
                        "icon" -> iconUrl = parser.getAttributeValue(null, "src") ?: ""
                        "episode-num" -> episodeNum = parser.nextText()
                        "rating" -> rating = parser.nextText()
                        "category" -> categories.add(parser.nextText())
                        "director" -> directors.add(parser.nextText())
                        "actor" -> actors.add(parser.nextText())
                        "country" -> countries.add(parser.nextText())
                        else -> {
                            Log.w("PARSE_PROGRAM", "Unknown tag: $tagName") // Logging unbekannter Tags
                        }
                    }
                } else if (parser.eventType == XmlPullParser.END_TAG && parser.name == "program") {
                    // Stelle sicher, dass wir bei einem "END_TAG" für das Program-Tag ankommen
                    break
                }
            }

            // Erstelle das Objekt
            return EpgDataOB(
                id = 0,
                idByAccountData = idByAccountData,
                epgId = channel,
                chId = channel,
                datum = Instant.ofEpochSecond(start).atZone(ZoneId.systemDefault()).toLocalDate().toString(),
                name = title,
                sub_title = subTitle,
                descr = description,
                category = if (categories.isNotEmpty()) categories else null,
                director = if (directors.isNotEmpty()) directors else null,
                actor = if (actors.isNotEmpty()) actors else null,
                country = if (countries.isNotEmpty()) countries else null,
                showIcon = iconUrl,
                episode_num = episodeNum,
                rating = rating,
                startTimestamp = start,
                stopTimestamp = stop,
                epgSourceId = epgSource.id.toInt(),
                epgChId = "${epgSource.id}_$channel"
            )
        } catch (e: Exception) {
            Log.e("PARSE_PROGRAM_ERROR", "Error during parsing: ${e.message}", e) // Detailierte Fehlermeldung
            return null
        }
    }


    private suspend fun storeEpgData(
        epgSource: EpgSource,
        epgChannelList: List<EpgSourceChannel>,
        epgDataList: List<EpgDataOB>
    ) {
        withContext(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            _epgProcessState.value = ExternEpgProcessState.EpgDataToDatabase

            try {
                epgDataBox.store.runInTx {
                    if (epgChannelList.isNotEmpty()) {
                        epgChannelBox.put(epgChannelList)
                    }
                    if (epgDataList.isNotEmpty()) {
                        epgDataList.chunked(2000).forEach { chunk -> epgDataBox.put(chunk) }
                    }
                }
                epgSource.lastUpdatedDate = System.currentTimeMillis() / 1000
                epgSourceBox.put(epgSource)
                val end = System.currentTimeMillis()
                val duration = (end - start) / 1000.0
                _epgProcessState.value = ExternEpgProcessState.EpgDataToDatabaseFinished(duration.toString())
            } catch (e: Exception) {
                Log.e("DatabaseError", "Error inserting data", e)
            }
        }
    }

    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss Z")

    private fun scheduleNextEpgWorker(epgSource: EpgSource) {
        val delay = epgSource.automaticUpdateDays * 3600000L
        val executionTimeMillis = System.currentTimeMillis() + delay

        // Formatierte Zeit (z.B. "HH:mm:ss")
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val executionTime = dateFormat.format(Date(executionTimeMillis))
        val nextWorkRequest = OneTimeWorkRequestBuilder<EpgUpdateWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf("epgSourceId" to epgSource.id))
            .addTag("autoupdate_${epgSource.name}")
            .build()
        WorkManager.getInstance(application).enqueueUniqueWork("autoupdateepg_${epgSource.id}",ExistingWorkPolicy.REPLACE, nextWorkRequest)
    }


    private fun isWithinTimeFrame(start: String, maxDays: Int, minDays: Int): Boolean {
        val currentDate = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        val threeDaysAgo = calculateDateMinusDays(currentDate, minDays)
        val sevenDaysLater = calculateDatePlusDays(currentDate, maxDays)
        return isWithinNext7Days(start, currentDate, sevenDaysLater) && !isOlderThan3Days(start, currentDate, threeDaysAgo)
    }


    fun isWithinNext7Days(date: String, currentDate: String, sevenDaysLater: String): Boolean {
        return date.substring(0, 8) in currentDate..sevenDaysLater
    }

    fun isOlderThan3Days(date: String, currentDate: String, threeDaysAgo: String): Boolean {
        return date.substring(0, 8) < currentDate && date.substring(0, 8) >= threeDaysAgo
    }

    fun calculateDatePlusDays(date: String, days: Int): String {
        val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())

        try {
            val parsedDate = dateFormat.parse(date)
            val calendar = Calendar.getInstance()
            if (parsedDate != null) {
                calendar.time = parsedDate
            }
            calendar.add(Calendar.DAY_OF_YEAR, days)
            return dateFormat.format(calendar.time)
        } catch (e: ParseException) {
            e.printStackTrace()
        }

        return "" // Fallback-Wert im Fehlerfall
    }

    fun calculateDateMinusDays(date: String, days: Int): String {
        val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())

        try {
            val parsedDate = dateFormat.parse(date)
            val calendar = Calendar.getInstance()
            if (parsedDate != null) {
                calendar.time = parsedDate
            }
            calendar.add(Calendar.DAY_OF_YEAR, -days)
            return dateFormat.format(calendar.time)
        } catch (e: ParseException) {
            e.printStackTrace()
        }

        return "" // Fallback-Wert im Fehlerfall
    }

    fun extractDateTimeAndUnixTimestamp(timestamp: String, timeOffset: String): Pair<String, Long> {
        try {
            // Extrahiere den Zeitstempel und den Zeitversatz aus der timestamp-Zeichenfolge
            val timestampPattern = "yyyyMMddHHmmss Z"
            val timestampFormat = SimpleDateFormat(timestampPattern, Locale.getDefault())
            val timestampDate = timestampFormat.parse(timestamp)
            val calendar = Calendar.getInstance().apply { time = timestampDate }

            // Extrahiere den Zeitversatz aus dem übergebenen Zeitversatzparameter, falls vorhanden
            if (timeOffset.isNotEmpty()) {
                val timezoneOffset = parseTimeOffset(timeOffset)
                val timezoneOffsetHours = timezoneOffset.first
                val timezoneOffsetMinutes = timezoneOffset.second

                // Füge den Zeitversatz vom Benutzer hinzu oder subtrahiere ihn von der Zeit
                calendar.add(Calendar.HOUR_OF_DAY, timezoneOffsetHours)
                calendar.add(Calendar.MINUTE, timezoneOffsetMinutes)
            }

            // Formatieren des Datum und der Zeit im gewünschten Format
            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
            val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(calendar.time)

            // Generiere den Unix-Zeitstempel
            val unixTimestamp = calendar.timeInMillis / 1000

            return Pair(dateStr, unixTimestamp)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return Pair("", -1L) // Fallback-Wert im Fehlerfall
    }

    fun parseTimeOffset(timeOffset: String): Pair<Int, Int> {
        if (timeOffset.isEmpty()) return Pair(0, 0)

        val sign = if (timeOffset.startsWith('-')) -1 else 1
        val parts = timeOffset.split(":").map { it.toInt() }

        val hours = parts[0] * sign
        val minutes = parts[1] * sign

        return Pair(hours, minutes)
    }


    ///TMDB///////////

    suspend fun getTmdbMovieImage(url: String, movieId: Int, apiKey: String): Resource<TmdbMovieImageResponse?> {
        return try {
            val request = channelsService.getInstance(url).getTmdbMovieImage(movieId, apiKey)
            if (request.isSuccessful) {
                Resource.Success(request.body()!!)
            } else {
                Resource.Error(HttpException(request).message ?: "Unknown error")
            }
        } catch (e: IOException) {
            Resource.Error("Network error: ${e.message}")
        } catch (e: HttpException) {
            Resource.Error("Server error: ${e.code()} ${e.message()}")
        } catch (e: Exception) {
            Resource.Error("Unknown error: ${e.message}")
        }
    }

    suspend fun getTmdbMovieDetails(url: String, movieId: Int, apiKey: String): Resource<TMDBMovieDetails?> {
        return try {
            val request = channelsService.getInstance(url).getTmdbMovieDetails(movieId, apiKey)
            if (request.isSuccessful) {
                Resource.Success(request.body()!!)
            } else {
                Resource.Error(HttpException(request).message ?: "Unknown error")
            }
        } catch (e: IOException) {
            Resource.Error("Network error: ${e.message}")
        } catch (e: HttpException) {
            Resource.Error("Server error: ${e.code()} ${e.message()}")
        } catch (e: Exception) {
            Resource.Error("Unknown error: ${e.message}")
        }
    }

    suspend fun getTmdbMovieDetailsByImdb(url: String, imdbId: String, apiKey: String): Resource<TMDB_imdb_id?> {
        return try {
            val request = channelsService.getInstance(url).getTmdbMovieDetailsWithImdbId(imdbId, apiKey)
            if (request.isSuccessful) {
                Resource.Success(request.body()!!)
            } else {
                Resource.Error(HttpException(request).message ?: "Unknown error")
            }
        } catch (e: IOException) {
            Resource.Error("Network error: ${e.message}")
        } catch (e: HttpException) {
            Resource.Error("Server error: ${e.code()} ${e.message()}")
        } catch (e: Exception) {
            Resource.Error("Unknown error: ${e.message}")
        }
    }

    suspend fun getTmdbSeriesDetails(url: String, seriesId: Int, apiKey: String): Resource<TMDBSeriesDetails?> {
        return try {
            val request = channelsService.getInstance(url).getTmdbSeriesDetails(seriesId, apiKey)
            if (request.isSuccessful) {
                Resource.Success(request.body()!!)
            } else {
                Resource.Error(HttpException(request).message ?: "Unknown error")
            }
        } catch (e: IOException) {
            Resource.Error("Network error: ${e.message}")
        } catch (e: HttpException) {
            Resource.Error("Server error: ${e.code()} ${e.message()}")
        } catch (e: Exception) {
            Resource.Error("Unknown error: ${e.message}")
        }
    }

    suspend fun getTmdbSeasonDetails(url: String, seriesId: Int, seasonNumber: Int, apiKey: String): Resource<TMDBSeasonDetails?> {
        return try {
            val request = channelsService.getInstance(url).getTmdbSeasonDetails(seriesId, seasonNumber, apiKey)
            if (request.isSuccessful) {
                Resource.Success(request.body()!!)
            } else {
                Resource.Error(HttpException(request).message ?: "Unknown error")
            }
        } catch (e: IOException) {
            Resource.Error("Network error: ${e.message}")
        } catch (e: HttpException) {
            Resource.Error("Server error: ${e.code()} ${e.message()}")
        } catch (e: Exception) {
            Resource.Error("Unknown error: ${e.message}")
        }
    }
}