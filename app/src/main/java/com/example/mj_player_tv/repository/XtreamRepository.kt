package com.example.mj_player_tv.repository

import android.app.Application
import android.util.Log
import com.example.mj_player_tv.database.ObjectBox
import com.example.mj_player_tv.database.entity.Accounts
import com.example.mj_player_tv.database.entity.EpgDataOB
import com.example.mj_player_tv.database.entity.EpgDataOB_
import com.example.mj_player_tv.database.entity.EpgSource
import com.example.mj_player_tv.database.entity.EpgSourceChannel
import com.example.mj_player_tv.database.entity.EpgSourceChannel_
import com.example.mj_player_tv.database.entity.TvChannelOB
import com.example.mj_player_tv.database.entity.TvChannelOB_
import com.example.mj_player_tv.network.RetrofitInstance
import com.example.mj_player_tv.network.externalepg.Channel
import com.example.mj_player_tv.network.model.stalker.token.TokenResponse
import com.example.mj_player_tv.network.model.xtreamcodes.XtreamAuthentication
import com.example.mj_player_tv.network.model.xtreamcodes.allmovies.XtreamAllMovies
import com.example.mj_player_tv.network.model.xtreamcodes.allseries.XtreamAllSeries
import com.example.mj_player_tv.network.model.xtreamcodes.alltvchannels.XtreamAllChannels
import com.example.mj_player_tv.network.model.xtreamcodes.channelsbycategory.XtreamChannelsByCategory
import com.example.mj_player_tv.network.model.xtreamcodes.epgbychannel.XtreamEpgByChannel
import com.example.mj_player_tv.network.model.xtreamcodes.moviecategory.XtreamMovieCategory
import com.example.mj_player_tv.network.model.xtreamcodes.moviedetails.XtreamMovieDetails
import com.example.mj_player_tv.network.model.xtreamcodes.moviesbycategory.XtreamMoviesByCategory
import com.example.mj_player_tv.network.model.xtreamcodes.seriesbycategory.XtreamSeriesByCategory
import com.example.mj_player_tv.network.model.xtreamcodes.seriescategory.XtreamSeriesCategory
import com.example.mj_player_tv.network.model.xtreamcodes.seriesdetails.XtreamSeriesDetails
import com.example.mj_player_tv.network.model.xtreamcodes.shortepg.XtreamShortEpg
import com.example.mj_player_tv.network.model.xtreamcodes.tvcategory.XtreamTvCategory
import com.example.mj_player_tv.utils.Resource
import com.google.gson.JsonParseException
import com.google.gson.JsonSyntaxException
import io.objectbox.Box
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.threeten.bp.Instant
import org.threeten.bp.ZoneId
import org.threeten.bp.ZonedDateTime
import org.threeten.bp.format.DateTimeFormatter
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory
import retrofit2.HttpException
import java.io.IOException
import java.io.InputStream

class XtreamRepository() {
    private val channelsService = RetrofitInstance

    private val epgSourceBox: Box<EpgSource>
    private val epgChannelBox: Box<EpgSourceChannel>
    private val epgDataBox: Box<EpgDataOB>
    private val tvChannelBox: Box<TvChannelOB>

    var isUpdating = false

    init {
        val store = ObjectBox.store
        epgSourceBox = store.boxFor(EpgSource::class.java)
        epgChannelBox = store.boxFor(EpgSourceChannel::class.java)
        epgDataBox = store.boxFor(EpgDataOB::class.java)
        tvChannelBox = store.boxFor(TvChannelOB::class.java)
    }

    suspend fun getXtreamAuthentication(url: String, username: String, password: String, userAgent: String): Resource<XtreamAuthentication> {
        return try {
            val request = channelsService.getInstance(url).getXtreamAuthentication(userAgent, username, password)
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

    suspend fun getXtreamTvCategories(url: String, username: String, password: String, userAgent: String): Resource<List<XtreamTvCategory>> {
        return try {
            val request = channelsService.getInstance(url).getXtreamTvCategories(userAgent, username, password)
            if (request.isSuccessful) {
                val bodi = request.body().toString()
                Log.d("XTREAMTVAPI", "OK: $bodi")
                Resource.Success(request.body()!!)
            } else {
                val bodi = request.body().toString()
                Log.d("XTREAMTVAPI", "FALSE: $bodi")
                Log.e("XTREAM ERROR", "${request.message()}")
                Resource.Error(HttpException(request).message ?: "Unknown error")
            }
        } catch (e: IOException) {
            Log.e("XTREAM IO EXC","${e.message} + ${e.cause} + ${e.stackTrace}")
            Resource.Error("Network error: ${e.message} + ${e.cause} + ${e.stackTrace}")
        } catch (e: HttpException) {
            Resource.Error("Server error: ${e.code()} ${e.message()}")
        } catch (e: IllegalStateException) {
            Resource.Error("Illegal State Exc.: ${e.message}")
        } catch (e: JsonSyntaxException) {
            Resource.Error("Json Syntax error: ${e.message}")
        } catch (e: JsonParseException) {
            Resource.Error("Json Parsing error: ${e.message}")
        } catch (e: NullPointerException) {
            Resource.Error("Nullpointer error: ${e.message}")
        } catch (e: Exception) {
            Resource.Error("Unknown error: ${e.message}")
        }
    }

    suspend fun getXtreamMovieCategories(url: String, username: String, password: String, userAgent: String): Resource<List<XtreamMovieCategory>> {
        return try {
            val request = channelsService.getInstance(url).getXtreamMovieCategories(userAgent, username, password)
            if (request.isSuccessful) {
                val jsonString = request.body()?.toString()
                Log.d("JSON Response XTREAM", jsonString ?: "Empty response")
                Resource.Success(request.body()!!)
            } else {  Log.d("JSON Response XTREAM", "ERROR")
                Resource.Error(HttpException(request).message ?: "Unknown error")
            }
        } catch (e: IOException) {
            Resource.Error("Network error: ${e.message} + ${e.cause} + ${e.stackTrace}")
        } catch (e: HttpException) {
            Resource.Error("Server error: ${e.code()} ${e.message()}")
        } catch (e: IllegalStateException) {
            Resource.Error("Illegal State Exc.: ${e.message}")
        } catch (e: JsonSyntaxException) {
            Resource.Error("Json Syntax error: ${e.message}")
        } catch (e: JsonParseException) {
            Resource.Error("Json Parsing error: ${e.message}")
        } catch (e: NullPointerException) {
            Resource.Error("Nullpointer error: ${e.message}")
        } catch (e: Exception) {
            Resource.Error("Unknown error: ${e.message}")
        }
    }

    suspend fun getXtreamSeriesCategories(url: String, username: String, password: String, userAgent: String): Resource<List<XtreamSeriesCategory>> {
        return try {
            val request = channelsService.getInstance(url).getXtreamSeriesCategories(userAgent, username, password)
            if (request.isSuccessful) {
                Resource.Success(request.body()!!)
            } else {
                Resource.Error(HttpException(request).message ?: "Unknown error")
            }
        } catch (e: IOException) {
            Resource.Error("Network error: ${e.message} + ${e.cause} + ${e.stackTrace}")
        } catch (e: HttpException) {
            Resource.Error("Server error: ${e.code()} ${e.message()}")
        } catch (e: IllegalStateException) {
            Resource.Error("Illegal State Exc.: ${e.message}")
        } catch (e: JsonSyntaxException) {
            Resource.Error("Json Syntax error: ${e.message}")
        } catch (e: JsonParseException) {
            Resource.Error("Json Parsing error: ${e.message}")
        } catch (e: NullPointerException) {
            Resource.Error("Nullpointer error: ${e.message}")
        } catch (e: Exception) {
            Resource.Error("Unknown error: ${e.message}")
        }
    }

    suspend fun getXtreamAllChannels(url: String, username: String, password: String, userAgent: String): Resource<List<XtreamAllChannels>> {
        return try {
            val request = channelsService.getInstance(url).getXtreamAllChannels(userAgent, username, password)
            if (request.isSuccessful) {
                Resource.Success(request.body()!!)
            } else {
                Resource.Error(HttpException(request).message ?: "Unknown error")
            }
        } catch (e: IOException) {
            Resource.Error("Network error: ${e.message} + ${e.cause} + ${e.stackTrace}")
        } catch (e: HttpException) {
            Resource.Error("Server error: ${e.code()} ${e.message()}")
        } catch (e: IllegalStateException) {
            Resource.Error("Illegal State Exc.: ${e.message}")
        } catch (e: JsonSyntaxException) {
            Resource.Error("Json Syntax error: ${e.message}")
        } catch (e: JsonParseException) {
            Resource.Error("Json Parsing error: ${e.message}")
        } catch (e: NullPointerException) {
            Resource.Error("Nullpointer error: ${e.message}")
        } catch (e: Exception) {
            Resource.Error("Unknown error: ${e.message}")
        }
    }

    suspend fun getXtreamChannelsByCategory(url: String, username: String, password: String, userAgent: String, categoryId: String): Resource<List<XtreamChannelsByCategory>> {
        return try {
            val request = channelsService.getInstance(url).getXtreamChannelsByCategory(userAgent, username, password, categoryId)
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

    suspend fun getXtreamAllMovies(
        account: Accounts
    ): Resource<List<XtreamAllMovies>> {
        return try {
            val request = channelsService.getInstance(account.stalkerUrl).getXtreamAllMovies(account.userAgent, account.username, account.macAddress)
            if (request.isSuccessful) {
                Log.d("XTREAM GET ACCOUNTDATA", "ALL MOVIES: SUCCESS")

                Resource.Success(request.body()!!)
            } else {
                Resource.Error(HttpException(request).message ?: "Unknown error")
            }
        } catch (e: IOException) {
            Resource.Error("Network error: ${e.message} + ${e.cause} + ${e.stackTrace}")
        } catch (e: HttpException) {
            Resource.Error("Server error: ${e.code()} ${e.message()}")
        } catch (e: IllegalStateException) {
            Resource.Error("Illegal State Exc.: ${e.message}")
        } catch (e: JsonSyntaxException) {
            Resource.Error("Json Syntax error: ${e.message}")
        } catch (e: JsonParseException) {
            Resource.Error("Json Parsing error: ${e.message}")
        } catch (e: NullPointerException) {
            Resource.Error("Nullpointer error: ${e.message}")
        } catch (e: Exception) {
            Resource.Error("Unknown error: ${e.message}")
        }
    }

    suspend fun getXtreamAllSeries(
        account: Accounts
    ): Resource<List<XtreamAllSeries>> {
        return try {
            val request = channelsService.getInstance(account.stalkerUrl).getXtreamAllSeries(account.userAgent, account.username, account.macAddress)
            if (request.isSuccessful) {
                Resource.Success(request.body()!!)
            } else {
                Resource.Error(HttpException(request).message ?: "Unknown error")
            }
        } catch (e: IOException) {
            Resource.Error("Network error: ${e.message} + ${e.cause} + ${e.stackTrace}")
        } catch (e: HttpException) {
            Resource.Error("Server error: ${e.code()} ${e.message()}")
        } catch (e: IllegalStateException) {
            Resource.Error("Illegal State Exc.: ${e.message}")
        } catch (e: JsonSyntaxException) {
            Resource.Error("Json Syntax error: ${e.message}")
        } catch (e: JsonParseException) {
            Resource.Error("Json Parsing error: ${e.message}")
        } catch (e: NullPointerException) {
            Resource.Error("Nullpointer error: ${e.message}")
        } catch (e: Exception) {
            Resource.Error("Unknown error: ${e.message}")
        }
    }

    suspend fun getXtreamMoviesByCategory(url: String, username: String, password: String, userAgent: String, categoryId: String): Resource<List<XtreamMoviesByCategory>> {
        return try {
            val request = channelsService.getInstance(url).getXtreamMoviesByCategory(userAgent, username, password, categoryId)
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

    suspend fun getXtreamSeriesByCategory(url: String, username: String, password: String, userAgent: String, categoryId: String): Resource<List<XtreamSeriesByCategory>> {
        return try {
            val request = channelsService.getInstance(url).getXtreamSeriesByCategory(userAgent, username, password, categoryId)
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

    suspend fun getXtreamSeriesDetails(url: String, username: String, password: String, userAgent: String, categoryId: String): Resource<XtreamSeriesDetails> {
        return try {
            val request = channelsService.getInstance(url).getXtreamSeriesInfo(userAgent, username, password, categoryId)
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

    suspend fun getXtreamMovieDetails(url: String, username: String, password: String, userAgent: String, vodId: String): Resource<XtreamMovieDetails> {
        return try {
            val request = channelsService.getInstance(url).getXtreamMovieInfo(userAgent, username, password, vodId)
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

    suspend fun getXtreamShortEpg(url: String, username: String, password: String, userAgent: String, streamId: String): Resource<XtreamShortEpg> {
        return try {
            val request = channelsService.getInstance(url).getXtreamShortEpg(userAgent, username, password, streamId)
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

    suspend fun getXtreamEpgByChannel(url: String, username: String, password: String, userAgent: String, streamId: String): Resource<XtreamEpgByChannel> {
        Log.d("EPG XTREAM INFO", "START")
        return try {
            val request = channelsService.getInstance(url).getXtreamEpgByChannel(userAgent, username, password, streamId)
            if (request.isSuccessful) {
                Log.d("EPG XTREAM INFO", "END = ${request.body()}")
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

    suspend fun getXtreamEpg(url: String, username: String, password: String, epgSource: EpgSource, account: Accounts): Resource<String> {
        return try {
            val response =
                channelsService.getXmlInstance(url).downloadEpgXtream(username, password)
            if (response.isSuccessful) {
                val xmlString = withContext(Dispatchers.IO) {
                    response.body()?.byteStream()
                }
                parseXmlStream(xmlString!!, epgSource, account)
                Resource.Success(totalEpgData)
            } else {
                Log.d("XTREAM EPG", "ERROR: ${response.code()} ${response.message()}")
                Resource.Error("Network error: ${response.code()} ${response.message()}")
            }
        } catch (e: IOException) {
            Log.d("XTREAM EPG", "NETWORK ERROR: ${e.message}")
            Resource.Error("Network error: ${e.message}")
        } catch (e: Exception) {
            Log.d("XTREAM EPG", "UNKNOWN ERROR: ${e.message}")
            Resource.Error("Unknown error: ${e.message}")
        }
    }

    var currentChannel: Channel? = null
    var epgChannelBatch = mutableListOf<EpgSourceChannel>()
    var epgDataBatch = mutableListOf<EpgDataOB>()
    var totalEpgData = ""

    suspend fun parseXmlStream(
        inputStream: InputStream,
        epgSource: EpgSource,
        account: Accounts
    ) {
        withContext(Dispatchers.Default) {
            try {
                totalEpgData = ""
                val factory = XmlPullParserFactory.newInstance()
                val parser = factory.newPullParser()
                parser.setInput(inputStream, null)
                var eventType = parser.eventType
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    when (eventType) {
                        XmlPullParser.START_TAG -> {
                            when (parser.name) {
                                "channel" -> {
                                    parseChannel(parser, epgSource, account)
                                }
                                "programme" -> {
                                    parseProgram(parser, epgSource)
                                }
                            }
                        }
                    }
                    eventType = parser.next()
                }
                addEpgChannelToTvChannel(account)
                addEpgDataToDatabase(epgSource, account)
                Resource.Success("OK")
            } catch (e: XmlPullParserException) {
                Log.d("ERROR PARSING", "XML Parsing error: ${e.message}")
            } catch (e: IOException) {
                Log.d("ERROR PARSING", "IO error: ${e.message}")
            } catch (e: Exception) {
                Log.d("ERROR PARSING", "Unexpected error: ${e.message}")
            } finally {
                withContext(Dispatchers.IO) {
                    inputStream.close()
                }
            }
        }
    }

    private fun addEpgDataToDatabase(thisEpgSource: EpgSource, account: Accounts) {

        CoroutineScope(Dispatchers.IO).launch {
            try {
                totalEpgData = epgDataBatch.size.toString()
                if (isUpdating) {
                    val existingDataQuery = epgDataBox.query(EpgDataOB_.epgSourceId.equal(thisEpgSource.id)).build()
                    val existingDataIds = existingDataQuery.find()
                        .map { it.idByAccountData }.toSet()
                    existingDataQuery.close()

                    val iterator = epgDataBatch.iterator()
                    while (iterator.hasNext()) {
                        val epg = iterator.next()
                        if (existingDataIds.contains(epg.idByAccountData)) {
                            iterator.remove()
                        }
                    }
                }

                epgDataBox.store.runInTx {
                    epgDataBox.put(epgDataBatch)
                }
                addEpgDataToChannel()
            } catch (e: Exception) {
                Log.e("DatabaseError", "Error inserting data: ${e.message}")

            } finally {
                epgDataBox.closeThreadResources()
            }
        }
    }

    private fun addEpgChannelToTvChannel(account: Accounts) {
        Log.d("XTREAM EPG TV", "START EPGCH TO TVCH")
        epgChannelBatch.forEach { epgChannel ->
            val tvChannelQuery = tvChannelBox.query(TvChannelOB_.accountId.equal(account.id).and(TvChannelOB_.xmltv_id.equal(epgChannel.chId))).build()
            val tvChannel = tvChannelQuery.find()
            tvChannelQuery.close()
            tvChannel.forEach {
                it.epgChannel?.target = epgChannel
                Log.d("XTREAM EPG TV", "${it.showingName} = ${epgChannel.name} === ${it.epgChannel?.target?.name}")
                tvChannelBox.put(it)
            }
        }
    }

    private fun addEpgDataToChannel() {
        if (epgDataBatch.isNotEmpty()) {
            CoroutineScope(Dispatchers.IO).launch {

                try {

                    epgChannelBox.put(epgChannelBatch)

                } catch (e: Exception) {
                    Log.e("ChannelError", "Fehler beim Hinzufügen zu Channel: ${e.message}")

                } finally {
                    epgChannelBatch.clear()
                    epgDataBatch.clear()
                }
            }
        } else {
            epgChannelBatch.clear()
            epgDataBatch.clear()
        }
    }

    private fun resetChannel() {
        currentChannel = Channel("", mutableListOf(), mutableListOf(), "", "")
    }

    private fun parseChannel(parser: XmlPullParser, thisEpgSource: EpgSource, account: Accounts) {
        resetChannel()
        val id = parser.getAttributeValue(null, "id")?.trim() ?: ""

        // Überprüfe, ob die ID leer ist und überspringe die Verarbeitung, falls ja.
        if (id.isEmpty()) {
            // Optionale Log-Ausgabe für leere IDs, wenn gewünscht
            Log.d("XTREAM EPG", "Skipping channel with empty ID")
            return
        }
        val display_name = mutableListOf<String>()
        val icon = mutableListOf<String>()
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "display-name" -> display_name.add(parser.nextText())
                    "icon" -> icon.add(parser.getAttributeValue(null, "src"))
                }
            }
        }
        val name = display_name.firstOrNull() ?: ""
        val newEpgChannel = if (isUpdating) {
            val existEpgChannel =
                epgChannelBox.query(EpgSourceChannel_.chEpgId.equal("${thisEpgSource.id}_${id}"))
                    .build().findFirst()
            if (existEpgChannel != null) {
                existEpgChannel.name = name
                existEpgChannel.display_name = display_name
                existEpgChannel.icon = icon
                existEpgChannel.chId = id.lowercase()
                existEpgChannel
            } else {
                val newepgChannel = EpgSourceChannel(
                    id = 0,
                    "${thisEpgSource.id}_${id.lowercase()}",
                    id.lowercase(),
                    icon,
                    name,
                    thisEpgSource.id,
                    display_name,
                    true
                )
                newepgChannel.epgsource.target = thisEpgSource
                newepgChannel
            }
        } else {
            val newEpgChannel = EpgSourceChannel(
                id = 0,
                "${thisEpgSource.id}_${id.lowercase()}",
                id.lowercase(),
                icon,
                name,
                thisEpgSource.id,
                display_name,
                true
            )
            newEpgChannel.epgsource.target = thisEpgSource
            newEpgChannel
        }
        epgChannelBatch.add(newEpgChannel)
            val tvChannelQuery = tvChannelBox.query(
                TvChannelOB_.accountId.equal(account.id)
                    .and(TvChannelOB_.xmltv_id.equal(newEpgChannel.chId))
            ).build()
            val tvChannel = tvChannelQuery.find()
            tvChannelQuery.close()
            tvChannel.forEach {
                it.epgChannel?.target = newEpgChannel
                Log.d(
                    "XTREAM EPG TV",
                    "${it.showingName} = ${it.xmltv_id} EQUAL ${newEpgChannel.chId} === ${newEpgChannel.name}"
                )
                tvChannelBox.put(it)
        }
    }

    private fun parseProgram(parser: XmlPullParser, thisEpgSource: EpgSource) {
        val start = ZonedDateTime.parse(
            (parser.getAttributeValue(null, "start")).toString(),
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss Z")
        ).toEpochSecond()

        val stop = ZonedDateTime.parse(
            (parser.getAttributeValue(null, "stop")).toString(),
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss Z")
        ).toEpochSecond()
        val datum = Instant.ofEpochSecond(start).atZone(ZoneId.systemDefault()).toLocalDate().toString()
        val channel = parser.getAttributeValue(null, "channel")
        var name = ""
        var descr = ""
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "title" -> name = parser.nextText()
                    "desc" -> descr = parser.nextText()
                }
            }
        }
        val newEpgData = EpgDataOB(
            id = 0,
            idByAccountData = "${channel}_${start}_${thisEpgSource.id}",
            channel,
            channel,
            datum,
            name,
            "",
            descr,
            mutableListOf(),
            mutableListOf(),
            mutableListOf(),
            datum,
            mutableListOf(),
            "",
            "",
            "",
            "",
            "",
            start,
            stop,
            null,
            thisEpgSource.url,
            thisEpgSource.id.toInt(),
            "${thisEpgSource.id}_$channel"
        )
        epgDataBatch.add(newEpgData)
    }
}