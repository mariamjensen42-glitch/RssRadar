package com.cycling.rssradar.core.data.rsshub

import android.content.Context
import com.cycling.rssradar.core.domain.rss.HttpFetcher
import com.cycling.rssradar.core.model.rsshub.CatalogSource
import com.cycling.rssradar.core.model.rsshub.RouteCatalog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 路由目录的唯一入口：装载、缓存、在线更新。
 *
 * 装载优先级：更新过的缓存 > 内置快照。内置快照随包发布（assets，~1.1MB），
 * 保证首次安装离线可用；用户手动更新后写本地缓存，之后一直用缓存。
 *
 * 数据全量常驻内存（3800 条，几百 KB 对象），检索是纯内存线性打分，
 * 不建索引、不进 Room——一次全量扫描比维护一张表简单得多。
 * 见 ADR-0010。
 */
@Singleton
class RouteCatalogStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val http: HttpFetcher,
) {

    private val _catalog = MutableStateFlow<RouteCatalog?>(null)
    val catalog: StateFlow<RouteCatalog?> = _catalog.asStateFlow()

    private val mutex = Mutex()

    /**
     * 装载目录。重复调用只解析一次；缓存损坏自动回落到内置快照。
     * 解析 1.1MB JSON 是百毫秒级，必须在 IO 线程调。
     */
    suspend fun load(): RouteCatalog = withContext(Dispatchers.IO) {
        _catalog.value?.let { return@withContext it }
        mutex.withLock {
            _catalog.value?.let { return@withContext it }
            val catalog = readCached() ?: readBundled()
            _catalog.value = catalog
            catalog
        }
    }

    /**
     * 从 RSSHub 官方拉取全量路由元数据，精简后覆盖本地缓存。
     *
     * @return 更新后的路由条数；失败时是异常（网络 / 解析 / 写入）。
     */
    suspend fun refresh(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val remote = http.fetch(SOURCE_URL).use { stream -> decodeRemoteCatalog(stream) }
            val slim = RouteCatalogSlimmer.slim(remote, System.currentTimeMillis())
            require(slim.namespaces.isNotEmpty()) { "路由元数据为空" }

            val target = cacheFile()
            val temp = File(target.parentFile, target.name + ".tmp")
            temp.outputStream().buffered().use { out ->
                out.write(CATALOG_JSON.encodeToString(slim).toByteArray(Charsets.UTF_8))
            }
            // 先写临时文件再改名：更新中途失败不会把还能用的缓存写坏
            check(temp.renameTo(target)) { "路由目录缓存写入失败" }

            val catalog = slim.toCatalog(CatalogSource.UPDATED)
            _catalog.value = catalog
            catalog.routes.size
        }
    }

    /** 丢弃更新过的缓存，回到内置快照。 */
    suspend fun resetToBundled(): RouteCatalog = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching { cacheFile().delete() }
            val catalog = readBundled()
            _catalog.value = catalog
            catalog
        }
    }

    private fun readCached(): RouteCatalog? {
        val file = cacheFile()
        if (!file.exists()) return null
        return runCatching {
            file.inputStream().buffered().use { decodeCatalogFile(it) }.toCatalog(CatalogSource.UPDATED)
        }.onFailure {
            // 缓存坏了就删掉，下次走内置快照，别让用户卡在打不开的目录上
            runCatching { file.delete() }
        }.getOrNull()?.takeIf { !it.isEmpty }
    }

    private fun readBundled(): RouteCatalog =
        context.assets.open(ASSET_NAME).buffered().use { decodeCatalogFile(it) }
            .toCatalog(CatalogSource.BUILTIN)

    private fun cacheFile(): File = File(context.filesDir, CACHE_NAME)

    companion object {
        /** 官方路由元数据。实例侧的 /api/routes 实测不可用（403/503/404），只有文档站这份是全的。 */
        const val SOURCE_URL = "https://docs.rsshub.app/routes.json"

        const val ASSET_NAME = "rsshub-routes.json"
        private const val CACHE_NAME = "rsshub-routes.json"
    }
}
