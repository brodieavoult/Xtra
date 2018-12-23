package com.github.exact7.xtra.repository.datasource

import androidx.paging.DataSource
import androidx.paging.PositionalDataSource
import com.github.exact7.xtra.api.KrakenApi
import com.github.exact7.xtra.model.kraken.video.BroadcastType
import com.github.exact7.xtra.model.kraken.video.Period
import com.github.exact7.xtra.model.kraken.video.Sort
import com.github.exact7.xtra.model.kraken.video.Video
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import java.util.concurrent.Executor

class VideosDataSource private constructor(
        private val game: String?,
        private val period: Period,
        private val broadcastTypes: BroadcastType,
        private val language: String?,
        private val sort: Sort,
        private val api: KrakenApi,
        retryExecutor: Executor,
        private val compositeDisposable: CompositeDisposable) : BasePositionalDataSource<Video>(retryExecutor) {

    override fun loadInitial(params: PositionalDataSource.LoadInitialParams, callback: PositionalDataSource.LoadInitialCallback<Video>) {
        super.loadInitial(params, callback)
        api.getTopVideos(game, period, broadcastTypes, language, sort, params.requestedLoadSize, 0)
                .subscribe({ callback.onSuccess(it.videos) }, { callback.onFailure(it, params) })
                .addTo(compositeDisposable)
    }

    override fun loadRange(params: PositionalDataSource.LoadRangeParams, callback: PositionalDataSource.LoadRangeCallback<Video>) {
        super.loadRange(params, callback)
        api.getTopVideos(game, period, broadcastTypes, language, sort, params.loadSize, params.startPosition)
                .subscribe({ callback.onSuccess(it.videos) }, { callback.onFailure(it, params) })
                .addTo(compositeDisposable)
    }

    class Factory (
            private val game: String?,
            private val period: Period,
            private val broadcastTypes: BroadcastType,
            private val language: String?,
            private val sort: Sort,
            private val api: KrakenApi,
            private val networkExecutor: Executor,
            private val compositeDisposable: CompositeDisposable) : BaseDataSourceFactory<Int, Video, VideosDataSource>() {

        override fun create(): DataSource<Int, Video> =
            VideosDataSource(game, period, broadcastTypes, language, sort, api, networkExecutor, compositeDisposable).also(sourceLiveData::postValue)
    }
}