// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.gva.watch

import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.tiles.Material3TileService
import androidx.wear.tiles.RequestBuilders.TileRequest
import androidx.wear.tiles.TileBuilders.Tile

/** Display only. Material3TileService binds layout and images into one resource response. */
class WatchTileService : Material3TileService(allowDynamicTheme = false) {
    override suspend fun MaterialScope.tileResponse(requestParams: TileRequest): Tile =
        WatchTileLayout.build(this@WatchTileService, requestParams.scope,
            requestParams.deviceConfiguration.screenWidthDp.toFloat(),
            requestParams.deviceConfiguration.screenHeightDp.toFloat(),
            WatchStore.read(this@WatchTileService), System.currentTimeMillis())
}
