package com.halo.drivers.heycyan.codec

class HeyCyanPacketFramer {

    fun frame(
        command: EncodedCommand
    ): Result<ByteArray> {
        /*
         * IMPORTANT:
         *
         * Header/length structure is documented as:
         *
         * [AA][LENGTH][COMMAND][PAYLOAD][CHECKSUM]
         *
         * but the exact checksum algorithm is still unresolved.
         *
         * Do not invent it.
         */
        return Result.failure(
            ProtocolNotEstablishedException(
                "HeyCyan packet checksum/framing is not fully established"
            )
        )
    }
}

class ProtocolNotEstablishedException(
    message: String
) : IllegalStateException(message)