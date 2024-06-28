# Network Playground
A sort of sandbox where you can create your own topology and start your own flows to test it.

> What I am working on:
> - optimizing fat-tree building.
> - refactoring flow handling.
> - adding tests (only 2 classes have auto tests for now `CustomNetwork` and `FatTreeNetwork`).
> - dynamic flow handling so that one can add/remove nodes and links, also useful to simulate failures, and turn off switches, useful for energy saving policies.

> Playground limitations:
> - switch/links/flows can only be added not deleted.
> - if one wants to import a network from JSON, the file path needs to be added as `file` field in `playground.kt`. If file does not exist, an empty `CustomNetwork` is created.

> JSON:
> - some examples of networks are provided in the resource folder with associated image.
> - both custom network and fat-tree topologies are deserializable.
> - for now refer to examples in the resource folder to understand the format.
> - working directory needs to be `<path to repo>/opendc-networking/opendc-simulator/opendc-simulator-network/src/main`.
> - exporting as json from playground not implemented yet

> What I added:
> - everything in main.kotlin (main.java files were already there)
> - `CustomNetworkTest` & `FatTreeNetworkTest` (none of the other tests)

## Components
### Nodes
#### Switch
- id (uniquely identifies a node in the network)
- port speed 
- number of ports

#### CoreSwitch
Same as switch but can start/receive flows.

#### Cluster
Same functionalities of a core switch for now, not yet integrated in any way with the existent experiments.

### Flows
#### EndToEndFlow
Represent a network flow from one `EndPointNode` to another.

#### Flow
>**NOTE:** probably changing name.

Represent the flow between a `Node`'s port and a `Link` (or vice-versa) for a specific `EndToEndFlow`.

### Link
- Connects 2 nodes.
- Its maximum bandwidth (for now) is equal to the minimum port speed of the 2 nodes.
- If incoming data exceeds max bandwidth each incoming flows receives a portion of the bandwidth which is proportional to its desired data rate.

### Energy Models
Swappable energy models for each component / node.
>**NOTE:** only 1 for now.

### Forwarding Policies
Swappable forwarding policy. topology aware / dynamic policies 
can be implemented by defining classes which extend `ForwardingPolicy` with network as a field.
>**NOTE:** only 1 policy for now.


## Playground Commands

### ADD_SWITCH
###### regex: `\\s*(c|core|)(?:s|switch)\\s+(\\d+)\\s+([\\d.]+)\\s+(\\d+)\\s*`
###### usage: `<core-option>switch <id> <port-speed in Kbps> <num-of-ports>`
###### examples:
`s 0 1000 4` &rarr; Switch(id=0, portSpeed=1000.0 Kbps, numOfPorts=4)

`cs 10 1000000 6` &rarr;  Switch(id=10, portSpeed=1,000,000 Kbps, numOfPorts=6) 

### ADD_FLOW
###### regex: `\\s*(?:flow|f)\\s+(\\d+)\\s*(?:->| )\\s*(\\d+)\\s+(\\d+)\\s*`
###### usage: `flow <sender-id> -> <destination-id> <datarate in Kbps>`
###### examples:
`flow 0 -> 3 10`, `f 0 3 10` &rarr;  EndToEndFlow(senderId=0, destId=3, desiredDataRate=10 Kbps)

### NEW_LINK
###### regex: `\\s*(?:l|link)\\s+(\\d+)\\s*[- ]\\s*(\\d+)\\s*`
###### usage: `link <nodeId1> - <nodeId2>`
###### examples:
`link 0 - 1`, `l 0 1` &rarr;  Link(sender=node(id=0), receiver=node(id=1)) & Link(sender=node(id=1), receiver=node(id=0))

### FLOWS
Shows active `EndToEndFlow`s.
###### regex: `(f|flows)`
###### examples:
```
> f
==== Flows ====
id   sender    dest      desired data rate   actual data rate    
0    0         3         1000.0              666.6666666666666   
1    0         3         2000.0              1333.3333333333333  
```

### ENERGY_REPORT
- For now only shows current consumption.
- Dynamic power consumption on default 50% port utilization.
- Line-cards not considered 
- turning off switches not implemented yet
- model defined for 10, 100, 1000 Mbps port speeds. Other speeds are poorly approximated.
###### regex: `energy-report|report|er|r`
###### examples:
```
> r
=== ENERGY REPORT ===
Current Energy Consumption: 1514.3349999999991W
=====================
```


