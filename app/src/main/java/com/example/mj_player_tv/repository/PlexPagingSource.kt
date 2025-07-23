package com.example.mj_player_tv.repository

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.example.mj_player_tv.network.RetrofitInstance

class PlexPagingSource(
    private val apiService: RetrofitInstance, // dein Retrofit-Interface
    private val token: String,
    private val sectionKey: Int,
    private val url: String,
    private val updateTotalItems: (Int) -> Unit
) : PagingSource<Int, com.example.mj_player_tv.network.model.plex.items.Metadata>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, com.example.mj_player_tv.network.model.plex.items.Metadata> {
        val offset = params.key ?: 0
        val limit = params.loadSize

        return try {
            val response = apiService.getInstance(url).getPlexSectionItems(
                token = token,
                offset = offset,
                limit = limit,
                sectionKey = sectionKey
            )
            val items = response.body()?.MediaContainer?.Metadata ?: emptyList()

            if (offset == 1) {
                val totalItems = response.body()?.MediaContainer?.totalSize ?: 0
                updateTotalItems(totalItems) // Übergabe der Gesamtanzahl ans ViewModel
            }


            LoadResult.Page(
                data = items,
                prevKey = if (offset == 0) null else offset - limit,
                nextKey = if (items.isEmpty()) null else offset + limit
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, com.example.mj_player_tv.network.model.plex.items.Metadata>): Int? {
        return state.anchorPosition?.let { position ->
            val page = state.closestPageToPosition(position)
            page?.prevKey?.plus(state.config.pageSize) ?: page?.nextKey?.minus(state.config.pageSize)
        }
    }
}
