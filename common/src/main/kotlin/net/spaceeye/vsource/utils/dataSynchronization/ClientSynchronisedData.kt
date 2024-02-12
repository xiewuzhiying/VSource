package net.spaceeye.vsource.utils.dataSynchronization

import dev.architectury.networking.NetworkManager
import net.minecraft.network.FriendlyByteBuf
import net.spaceeye.vsource.LOG
import net.spaceeye.vsource.networking.Serializable
import net.spaceeye.vsource.networking.S2CConnection
import net.spaceeye.vsource.utils.ClientClosable

abstract class ClientSynchronisedData<T: DataUnit>(id: String, getServerInstance: () -> ServerSynchronisedData<T>) : ClientClosable() {
    val serverRequestChecksumResponseConnection = id idWithConn ::ServerDataResponseConnection
    val serverDataUpdateRequestResponseConnection = id idWithConn ::ServerDataUpdateRequestResponseConnection
    val serverChecksumsUpdatedConnection = id idWithConn ::ServerChecksumsUpdatedConnection

    val dataRequestChecksumConnection = {getServerInstance().dataRequestChecksumConnection}
    val dataUpdateRequestConnection   = {getServerInstance().dataUpdateRequestConnection}

    //TODO change to synchronised map
    val serverChecksums = mutableMapOf<Long, MutableMap<Int, ByteArray>>()
    val clientChecksums = mutableMapOf<Long, MutableMap<Int, ByteArray>>()

    val cachedData = mutableMapOf<Long, MutableMap<Int, T>>()

    override fun close() {
        serverChecksums.clear()
        clientChecksums.clear()
        cachedData.clear()
    }

    fun tryPoolDataUpdate(page: Long): MutableMap<Int, T>? {
        if (!serverChecksums.containsKey(page)) {
            requestChecksumsUpdate(page)
            return cachedData[page]
        }
        if (clientChecksums.containsKey(page)
            && (clientChecksums[page]!!.size == serverChecksums[page]!!.size)
            && clientChecksums[page]!!.all {
                           serverChecksums[page]!!.containsKey(it.key)
                        && serverChecksums[page]!![it.key].contentEquals(it.value)
                        }
            ) {
            return cachedData[page]
        }
        requestUpdateData(page)
        return cachedData[page]
    }

    fun requestChecksumsUpdate(page: Long) {
        dataRequestChecksumConnection().sendToServer(ClientDataRequestPacket(page))
    }

    fun requestUpdateData(page: Long) {
        val serverPage = serverChecksums[page]!!
        val clientPage = clientChecksums.getOrDefault(page, mutableMapOf())

        val serverIds = serverPage.keys
        var clientIds = clientPage.keys

        clientIds.filter { !clientIds.containsAll(serverIds) }.forEach {clientPage.remove(it)}
        clientIds = clientPage.keys

        val toUpdate = serverPage.filter { (k, v) -> !clientPage[k].contentEquals(v) }.map { it.key }.toMutableList()

        dataUpdateRequestConnection().sendToServer(ClientDataUpdateRequestPacket(page, toUpdate))
    }

    class ServerDataResponseConnection<T: DataUnit>(id: String, val clientInstance: ClientSynchronisedData<T>): S2CConnection<ServerDataRequestResponsePacket>(id, "server_data_request_response_packet") {
        override fun clientHandler(buf: FriendlyByteBuf, context: NetworkManager.PacketContext) {
            val packet = ServerDataRequestResponsePacket(buf)
            if (!packet.pageExists) {
                clientInstance.serverChecksums.remove(packet.page)
                clientInstance.clientChecksums.remove(packet.page)
                clientInstance.cachedData     .remove(packet.page)
                return
            }

            clientInstance.serverChecksums[packet.page] = packet.checksums.toMap().toMutableMap()
            LOG("IM ServerDataResponseConnection")
        }
    }

    class ServerDataUpdateRequestResponseConnection<T: DataUnit>(id: String, val clientInstance: ClientSynchronisedData<T>): S2CConnection<ServerDataUpdateRequestResponsePacket<T>>(id, "server_data_update_request_response_packet") {
        override fun clientHandler(buf: FriendlyByteBuf, context: NetworkManager.PacketContext) {
            val packet = ServerDataUpdateRequestResponsePacket<T>(buf)
            if (!packet.pageExists) {
                clientInstance.serverChecksums.remove(packet.page)
                clientInstance.clientChecksums.remove(packet.page)
                clientInstance.cachedData     .remove(packet.page)
                return
            }
            val page = clientInstance.cachedData.getOrPut(packet.page) { mutableMapOf() }
            val checksumPage = clientInstance.clientChecksums.getOrPut(packet.page) { mutableMapOf() }
            packet.newData.forEach { (idx, item) ->
                page[idx] = item
                checksumPage[idx] = item.hash()
            }
            packet.nullData.forEach { idx ->
                page.remove(idx)
                checksumPage.remove(idx)
            }
            LOG("IM ServerDataUpdateRequestResponseConnection")
        }
    }

    class ServerChecksumsUpdatedConnection<T: DataUnit>(id: String, val clientInstance: ClientSynchronisedData<T>): S2CConnection<ServerChecksumsUpdatedPacket>(id, "server_checksums_updated_packet") {
        override fun clientHandler(buf: FriendlyByteBuf, context: NetworkManager.PacketContext) {
            val packet = ServerChecksumsUpdatedPacket(buf)
            val pageNum = packet.page
            val wasRemoved = packet.wasRemoved
            if (wasRemoved) {
                clientInstance.serverChecksums.remove(pageNum)
                clientInstance.clientChecksums.remove(pageNum)
                clientInstance.cachedData.remove(pageNum)
                LOG("IM ServerChecksumsUpdatedConnection")
                return
            }

            val page = clientInstance.serverChecksums.getOrPut(pageNum) { mutableMapOf() }
            packet.updatedIndices.forEach {
                page[it.first] = it.second
            }
            LOG("IM ServerChecksumsUpdatedConnection")
        }
    }

    infix fun <TT: Serializable> String.idWithConn(constructor: (String, ClientSynchronisedData<T>) -> S2CConnection<TT>): S2CConnection<TT> {
        val instance = constructor(this, this@ClientSynchronisedData)
        try { // Why? so that if it's registered on dedicated client/server it won't die
            NetworkManager.registerReceiver(instance.side, instance.id, instance.getHandler())
        } catch(e: NoSuchMethodError) {}
        return instance
    }
}