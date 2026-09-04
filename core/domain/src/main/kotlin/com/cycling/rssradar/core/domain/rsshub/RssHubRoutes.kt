package com.cycling.rssradar.core.domain.rsshub

import com.cycling.rssradar.core.model.rsshub.RssHubRoute

/** 路由目录的装配与 URL 拼装。 */
object RssHubRoutes {

    /** 官方公共实例；实际使用以 RssHubInstanceStore 的探测 / 自定义结果为准。 */
    const val DEFAULT_HOST = "https://rsshub.app"

    /**
     * 把参数值填进 path 模板，拼成完整订阅地址。
     * 必填参数缺失时返回 null——与手填 URL 不同，路由拼不出来就是拼不出来。
     */
    fun buildUrl(route: RssHubRoute, values: Map<String, String>, host: String): String? =
        RoutePath.build(route.path, values)?.let { host.trimEnd('/') + it }

    /** 填了所有必填参数才能生成预览。 */
    fun canBuild(route: RssHubRoute, values: Map<String, String>): Boolean =
        route.requiredParams.all { values[it.key]?.isNotBlank() == true }
}
