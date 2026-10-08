package com.cycling.rssradar.core.model

import kotlin.enums.enumEntries

inline fun <reified T : Enum<T>> enumValueOrNull(name: String?): T? =
    name?.let { n -> enumEntries<T>().firstOrNull { it.name == n } }
