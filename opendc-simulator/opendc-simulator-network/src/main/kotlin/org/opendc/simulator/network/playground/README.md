# Network Playground
A sort of sandbox where you can create your own topology and start your own network flows to test it.

> What I am working on:
> - [ ] optimizing fat-tree building.
> - [x] refactoring flow handling.
> - [ ] adding tests (only 2 classes have auto tests for now `CustomNetwork` and `FatTreeNetwork`).
>   - [x] link
>   - [ ] switch
>   - [ ] port
>   - [ ] routing table
>   - [ ] forwarding
>   - [x] fat tree
>   - [x] custom topology
>   - [ ] flow filterer
>   - [ ] forwarding policies
> - [x] dynamic flow handling so that one can add/remove nodes and links, also useful to simulate failures, and turn off switches, useful for energy saving policies.
> - [ ] integrating modules:
>   - [ ] find a good way networking simulation can be handled in combination with legacy features with as little changes as possible.
>   - [ ] define the dependency between compute and networking workloads.

> Playground limitations:
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

&nbsp;  
## Main Components
### Nodes
##### Switch
- id (uniquely identifies a node in the network)
- port speed 
- number of ports

##### CoreSwitch
Same as switch but can start/receive flows.

### Flows
##### EndToEndFlow
Represent a network flow from one `EndPointNode` to another.

##### Flow
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
>**NOTE:** only 2 policy for now.


&nbsp;  
## Playground Commands

### ADD_SWITCH
###### regex: `\\s*(c|core|)(?:s|switch)\\s+(\\d+)\\s+([\\d.]+)\\s+(\\d+)\\s*`
###### usage: `<core-option>switch <id> <port-speed in Kbps> <num-of-ports>`
###### examples:
`s 0 1000 4` &rarr; Switch(id=0, portSpeed=1000.0 Kbps, numOfPorts=4)

`cs 10 1000000 6` &rarr;  Switch(id=10, portSpeed=1,000,000 Kbps, numOfPorts=6) 

### RM_NODE
###### regex: `\\s*rm\\s+(?:n|node)\\s+(\\d+)\\s*`
###### usage: `rm node 1`
###### examples:
```commandline
f
==== Flows ====
id   sender    dest      desired data rate   actual data rate    
0    0         3         1000.0              666.6666666666666   
1    0         3         2000.0              1333.3333333333333  
2    3         0         5000.0              2000.0              

rm n 1
10:00:25.848 [main] INFO RM_NODE -- node successfully removed

f
==== Flows ====
id   sender    dest      desired data rate   actual data rate    
0    0         3         1000.0              333.3333333333333   
1    0         3         2000.0              666.6666666666666   
2    3         0         5000.0              1000.0
```


### ADD_FLOW
###### regex: `\\s*(?:flow|f)\\s+(\\d+)\\s*(?:->| )\\s*(\\d+)\\s+(\\d+)\\s*`
###### usage: `flow <sender-id> -> <destination-id> <datarate in Kbps>`
###### examples:
`flow 0 -> 3 10`, `f 0 3 10` &rarr;  EndToEndFlow(senderId=0, destId=3, desiredDataRate=10 Kbps)


### RM_FLOW
Removes a flow from the network. All other flows are adjusted based on each node forwarding policy.
###### regex: `\\s*rm\\s+(?:flow|f)\\s+(\\d+)`
###### usage: `rm flow <flow-id>`
###### examples:
`rm flow 0`, `rm f 0` &rarr;  removes EndToEndFlow(flowId=0, senderId=_, destId=_, desiredDataRate=_ Kbps)

### NEW_LINK
###### regex: `\\s*(?:l|link)\\s+(\\d+)\\s*[- ]\\s*(\\d+)\\s*`
###### usage: `link <nodeId1> - <nodeId2>`
###### examples:
`link 0 - 1`, `l 0 1` &rarr;  Link(sender=node(id=0), receiver=node(id=1)) & Link(sender=node(id=1), receiver=node(id=0))


### RM_LINK
Removes deployed link. All flows are adjusted to adapt to the new topology, based on each node forwarding policy.
###### regex: `\\s*rm\\s+(?:l|link)\\s+(\\d+)\\s*[- ]\\s*(\\d+)\\s*`
###### usage: `rm link <nodeId1> - <nodeId2>`
###### examples:
`rm link 0 - 1`, `rm l 0 1` &rarr;  removes Link(sender=node(id=0), receiver=node(id=1)) & Link(sender=node(id=1), receiver=node(id=0))

### FLOWS
Shows active `EndToEndFlow`s.
###### regex: `(f|flows)`
###### examples:
```commandline
> f
==== Flows ====
id   sender    dest      desired data rate   actual data rate    
0    0         3         1000.0              666.6666666666666   
1    0         3         2000.0              1333.3333333333333  
```

### FLOWS_OF
Shows active flows through a single `Node`.
###### regex: `(?:flows|f) (\\d+)`
###### examples:
```commandline
f 1

==== Flows in node 1 ====
id             in (Kbps)      out (Kbps)     throughput out (Kbps)
0              333.333333     333.333333     333.333333     
1              666.666667     666.666667     666.666667     
2              1000.000000    1000.000000    1000.000000    
```

```commandline
f 3

==== Flows in node 3 ====
id             in (Kbps)      out (Kbps)     throughput out (Kbps)
0              666.666667     0.000000       0.000000       
1              1333.333333    0.000000       0.000000       
2              0.000000       5000.000000    2000.000000
```
### ENERGY_REPORT
- For now only shows current consumption.
- Dynamic power consumption on default 50% port utilization.
- Line-cards not considered 
- turning off switches not implemented yet
- model defined for 10, 100, 1000 Mbps port speeds. Other speeds are poorly approximated.
###### regex: `energy-report|report|er|r`
###### examples:
```commandline
r
=== ENERGY REPORT ===
Current Energy Consumption: 1514.3349999999991W
=====================
```


