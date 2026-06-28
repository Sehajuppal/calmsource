package com.example.calmsource.feature.epg

import com.example.calmsource.core.model.Channel
import com.example.calmsource.feature.iptv.EpgNowNext

fun mapGuideToEpgGrid(
    channels: List<Channel>,
    nowNextMap: Map<String, EpgNowNext>,
    windowStartEpochMs: Long,
    windowEndEpochMs: Long,
): Pair<List<EpgChannel>, Map<String, List<EpgProgramme>>> {
    val epgChannels = channels.map { channel ->
        EpgChannel(id = channel.id, name = channel.name, logoUrl = channel.logoUrl)
    }
    val programmes = channels.associate { channel ->
        val nowNext = nowNextMap[channel.id]
        val list = buildList {
            nowNext?.currentProgram?.let { program ->
                if (program.endTimeMs > windowStartEpochMs && program.startTimeMs < windowEndEpochMs) {
                    add(
                        EpgProgramme(
                            id = program.id,
                            channelId = channel.id,
                            title = program.title,
                            startEpochMs = program.startTimeMs,
                            endEpochMs = program.endTimeMs,
                            description = program.description,
                        ),
                    )
                }
            }
            nowNext?.nextProgram?.let { program ->
                if (program.endTimeMs > windowStartEpochMs && program.startTimeMs < windowEndEpochMs) {
                    add(
                        EpgProgramme(
                            id = program.id,
                            channelId = channel.id,
                            title = program.title,
                            startEpochMs = program.startTimeMs,
                            endEpochMs = program.endTimeMs,
                            description = program.description,
                        ),
                    )
                }
            }
        }.sortedBy { it.startEpochMs }
        channel.id to list
    }
    return epgChannels to programmes
}
