package org.linguamesh.android

import org.linguamesh.android.core.CoreGateway
import org.linguamesh.android.core.NativeCoreGateway
import org.linguamesh.android.core.UnavailableCoreGateway

object CoreGatewayFactory {
    fun create(): CoreGateway = runCatching<CoreGateway> {
        NativeCoreGateway()
    }.getOrElse {
        UnavailableCoreGateway()
    }
}
