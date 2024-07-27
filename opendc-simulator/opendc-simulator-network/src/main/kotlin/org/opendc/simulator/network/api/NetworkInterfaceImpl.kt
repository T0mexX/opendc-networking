package org.opendc.simulator.network.api
//
//import kotlinx.coroutines.launch
//import kotlinx.coroutines.runBlocking
//import org.opendc.simulator.network.components.EndPointNode
//import org.opendc.simulator.network.components.INTERNET_ID
//import org.opendc.simulator.network.flow.FlowId
//import org.opendc.simulator.network.flow.NetFlow
//import org.opendc.simulator.network.utils.Kbps
//
//internal class NetworkInterfaceImpl(
//    private val node: EndPointNode,
//    private val netController: NetworkController,
//    override val owner: String = "unknown"
//): NetworkInterface {
//
//    override val nodeId: NodeId = node.id
//
//    private val subInterfaces = mutableListOf<NetworkInterface>()
//    private val flows = mutableMapOf<FlowId, NetFlow>()
//
//    override suspend fun startFlow(
//        destinationId: NodeId?,
//        demand: Kbps,
//        throughputChangeHandler: ((NetFlow, Kbps, Kbps) -> Unit)?
//    ): NetFlow? {
//        val newFlow: NetFlow? = netController.startFlow(
//            transmitterId = this.nodeId,
//            destinationId = destinationId ?: INTERNET_ID,
//            dataRateOnChangeHandler = throughputChangeHandler,
//        )
//
//        newFlow?.let { flows[newFlow.id] = newFlow }
//
//        return newFlow
//    }
//
//    override suspend fun stopFlow(id: FlowId) {
//        flows.remove(id)?.let {
//            netController.stopFlow(id)
//        } ?: NetworkInterface.log.error(
//            "network interface with owner '$owner' tried to stop flow which does not own"
//        )
//    }
//
//    override fun getMyFlow(id: FlowId): NetFlow? =
//        TODO()
//
//    override suspend fun fromInternet(
//        demand: Kbps,
//        dataRateOnChangeHandler: ((NetFlow, Kbps, Kbps) -> Unit)?
//    ): NetFlow {
//        val newFlow: NetFlow = netController.internetNetworkInterface.startFlowSus(
//            destinationId = this.nodeId,
//            demand = demand,
//            throughputChangeHandler = dataRateOnChangeHandler,
//        )!!
//
//        flows[newFlow.id] = newFlow
//
//        return newFlow
//    }
//
//    override fun getSubInterface(owner: String): NetworkInterface {
//        val newIface = NetworkInterfaceImpl(
//            node = this.node,
//            netController = this.netController,
//            owner = owner
//        )
//        subInterfaces.add(newIface)
//
//        return newIface
//    }
//
//    override fun close() {
//        subInterfaces.forEach { it.close() }
//        runBlocking {
//            flows.keys.forEach {
//                launch { netController.stopFlow(it) }
//            }
//        }
//    }
//}
