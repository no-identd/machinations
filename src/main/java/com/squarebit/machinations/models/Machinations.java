package com.squarebit.machinations.models;

import java.util.*;
import java.util.stream.Collectors;

public class Machinations {
    public static final String DEFAULT_RESOURCE_NAME = "";

    private Configs configs = new Configs();
    private Set<Element> elements;
    private Map<String, Element> elementById;

    private int previousSimulatedTime = -1;
    private int time = -1;  // The time index now, negative means no simulation step has been executed.
    private int remainedActionPoints = -1;
    private Set<Node> activeNodes = new HashSet<>();
    private Set<Node> automaticNodes;
    private boolean terminated = false;

    public Machinations() {
        this.elements = new HashSet<>();
        this.elementById = new HashMap<>();
    }

    /**
     * Gets configs.
     *
     * @return the configs
     */
    public Configs getConfigs() {
        return configs;
    }

    /**
     * Gets time.
     *
     * @return the time
     */
    public int getTime() {
        return time;
    }

    /**
     * Is terminated boolean.
     *
     * @return the boolean
     */
    public boolean isTerminated() {
        return terminated;
    }

    /**
     * Find by id abstract element.
     *
     * @param id the id
     * @return the abstract element
     */
    public Element findById(String id) {
        return this.elementById.get(id);
    }

    /**
     * Gets elements.
     *
     * @return the elements
     */
    public Set<Element> getElements() {
        return elements;
    }

    /**
     * Addition element.
     *
     * @param element the element
     * @throws Exception the exception
     */
    protected void addElement(Element element) throws Exception {
        if (!this.elementById.containsKey(element.getId())) {
            this.elements.add(element);
            this.elementById.put(element.getId(), element);
            element.machinations = this;
        }
        else {
            throw new Exception(String.format("An element with id %s is already existed.", element.getId()));
        }
    }

    /**
     * Performs one simulation step.
     */
    public boolean simulateOneTimeStep() {
        this.initializeIfNeeded();

        if (this.previousSimulatedTime < this.time) {
            this.doSimulateOneTimeStep();
            this.previousSimulatedTime = this.time;
        }

        if (configs.getTimeMode() == TimeMode.TURN_BASED) {
            if (this.remainedActionPoints <= 0) {
                this.remainedActionPoints = this.configs.getActionPointsPerTurn();
                this.time++;
            }
        }
        else
            this.time++;

        return (!this.terminated);
    }

    private void doSimulateOneTimeStep() {
        // 1. fire the active elements, and find out which ones needs to be fired.
        // 2. try to satisfy fire requests.
        // 3. fire those satisfied.
        // 4. repeat until no more active elements in this time step.
        Set<Node> activeNodes = getActiveNodes();

        Set<Node> nodes = activeNodes.stream()
                .filter(Node::activate)
                .filter(Node::isEnabled)
                .collect(Collectors.toSet());

        Set<GraphElement> nextElements = this.fireNodes(nodes);
        while (!nextElements.isEmpty()) {
            // Fire the next connections
            Set<ResourceConnection> connections = nextElements.stream()
                    .filter(e -> e instanceof ResourceConnection).map(e -> (ResourceConnection)e)
                    .collect(Collectors.toSet());
            fireResourceConnections(connections);

            // Fire the next nodes
            nodes = nextElements.stream()
                    .filter(e -> e instanceof Node).map(e -> (Node)e)
                    .collect(Collectors.toSet());
            nextElements = fireNodes(nodes);
        }
    }

    private void fireResourceConnections(Set<ResourceConnection> connections) {
        connections.forEach(c -> {
            ResourceSet requiredResource = c.fire();
            ResourceSet extracted = c.getFrom().extract(requiredResource);

            if (extracted.size() > 0) {
                c.getTo().receive(extracted);
            }
        });
    }

    /**
     * Fires a set of nodes and return the set of graph elements to be fired next.
     * @param nodes the node to be fired
     * @return the graph elements to be fired next
     */
    private Set<GraphElement> fireNodes(Set<Node> nodes) {
        Set<FireRequirement> fireRequirements = nodes.stream()
                .map(Node::getFireRequirement)
                .collect(Collectors.toSet());

        // Try to resolve requirements.
        Map<ResourceConnection, ResourceSet> actualFlows = resolveFireRequirements(fireRequirements);

        // Filter those requirements actually satisfied
        Set<FireRequirement> satisfiedRequirements = fireRequirements.stream()
                .filter(r -> {
                    Set<ResourceConnection> connections = r.getConnections();

                    return (connections.isEmpty() ||
                            (r.isRequiringAll() && actualFlows.keySet().containsAll(connections)) ||
                            (!r.isRequiringAll() && connections.stream().anyMatch(actualFlows::containsKey)));
                }).collect(Collectors.toSet());

        // Fire the nodes.
        Set<ResourceConnection> firedOutgoingConnections = satisfiedRequirements.stream().map(r -> {
            Set<ResourceConnection> connections = r.getConnections();

            Map<ResourceConnection, ResourceSet> incomingFlows = new HashMap<>();
            actualFlows.entrySet().stream()
                    .filter(e -> connections.contains(e.getKey()))
                    .forEach(e -> incomingFlows.put(e.getKey(), e.getValue()));

            if (r.getTarget() instanceof End)
                this.terminated = true;

            return r.getTarget().fire(incomingFlows);
        }).flatMap(Set::stream).collect(Collectors.toSet());

        ///////////////////////
        // --> Triggers.
        Map<Node, List<ResourceConnection>> firedConnectionByTarget =
                firedOutgoingConnections.stream().collect(Collectors.groupingBy(ResourceConnection::getTo));

        // Trigger owners are those having all input connections satisfied.
        Set<Node> triggerOwners = firedConnectionByTarget.entrySet().stream()
                .filter(e -> e.getKey().getIncomingConnections().containsAll(e.getValue()))
                .map(Map.Entry::getKey).collect(Collectors.toSet());

        // Add those nodes previously fired but do not have any input requirements.
        triggerOwners.addAll(
                satisfiedRequirements.stream()
                        .filter(r -> r.getTarget().getIncomingConnections().size() == 0)
                        .map(FireRequirement::getTarget)
                        .collect(Collectors.toSet())
        );

        // Elements will be fired are target of triggers.
        Set<GraphElement> firedNextElements = triggerOwners.stream()
                .flatMap(o -> o.activateTriggers().stream()).map(Trigger::getTarget)
                .collect(Collectors.toSet());

        /////////////////
        // --> Activators
        Map<Node, List<Activator>> activatorsByTarget = elements.stream()
                .filter(e -> e instanceof Node).map(e -> (Node)e)
                .flatMap(n -> n.getActivators().stream())
                .collect(Collectors.groupingBy(Activator::getTarget));

        activatorsByTarget.forEach((target, activators) -> {
            boolean shouldActivate = activators.stream()
                    .map(Activator::evaluate).reduce(true, (a, b) -> a && b);

            if (shouldActivate && target.isEnabled() && target.activate()) {
                firedNextElements.add(target);
            }
        });

        return firedNextElements;
    }

    /**
     * Tries to resolves give node fire requirements and return the actual flows.
     *
     * @param fireRequirements the requirement set
     * @return the set of resources actually need to pass through the connections.
     */
    private Map<ResourceConnection, ResourceSet> resolveFireRequirements(Set<FireRequirement> fireRequirements) {
        // The required connections to be fired.
        Set<ResourceConnection> requiredConnections = fireRequirements.stream()
                .flatMap(r -> r.getConnections().stream())
                .collect(Collectors.toSet());

        // Active connections, grouped by providing node.
        Map<Node, List<ResourceConnection>> requiredConnectionsByProvider = requiredConnections.stream()
                .collect(Collectors.groupingBy(ResourceConnection::getFrom));

        // The required resources for each node.
        Map<ResourceConnection, ResourceSet> requiredResources = new HashMap<>();
        requiredConnections.forEach(c -> requiredResources.put(c, c.fire()));

        // The set of actual flow.
        Map<ResourceConnection, ResourceSet> actualFlows = new HashMap<>();

        // Get resource snapshot for each provider node.
        Map<Node, ResourceSet> resourceSnapShots = new HashMap<>();
        requiredConnectionsByProvider.forEach((n, c) -> resourceSnapShots.put(n, n.getResources().copy()));

        // Calculate the actual flow.
        fireRequirements.forEach(requirement ->
                requirement.getConnections().forEach(c -> {
                    ResourceSet snapshot = resourceSnapShots.get(c.getFrom());
                    ResourceSet requiredResource = requiredResources.get(c);
                    ResourceSet extracted = snapshot.remove(requiredResource);

                    if (extracted.size() > 0) {
                        if (requirement.isRequiringAll() && !requiredResource.isSubSetOf(extracted))
                            snapshot.add(extracted);
                        else
                            actualFlows.put(c, extracted);
                    }
                })
        );

        // Determines requirements actually satisfied.
        if (configs.getTimeMode() == TimeMode.SYNCHRONOUS) {
            // synchronous time mode require each provider node to provide all or none dependent connections.
            Set<ResourceConnection> nonFlows = requiredConnectionsByProvider.entrySet().stream()
                    .map(e -> {
                        List<ResourceConnection> dependentConnections = e.getValue();
                        if (!actualFlows.keySet().containsAll(dependentConnections))
                            return dependentConnections;
                        else
                            return Collections.<ResourceConnection>emptyList();
                    }).flatMap(List::stream).collect(Collectors.toSet());

            // Remove those non-flow due to sync-time constraint.
            nonFlows.forEach(actualFlows::remove);
        }

        return actualFlows;
    }

    /**
     * Gets the set of nodes to be activated at current time step.
     * @return the active node set
     */
    private Set<Node> getActiveNodes() {
        Set<Node> nodes = new HashSet<>(this.automaticNodes);

        if (time == 0)
            nodes.addAll(elements.stream()
                    .filter(e -> e instanceof Node &&
                            ((Node)e).getActivationMode() == ActivationMode.STARTING_ACTION
                    ).map(e -> (Node)e).collect(Collectors.toSet()));

        return nodes;
    }

    private boolean doSimulateOneTimeStepOld() {
        if (this.terminated)
            return false;

        // --> STEP 1: try to resolve activation requirements
        Set<FireRequirement> fireRequirements = this.activeNodes.stream()
                .filter(Node::isEnabled)
                .map(Node::getFireRequirement)
                .collect(Collectors.toSet());

        Set<ResourceConnection> requiredConnections = fireRequirements.stream()
                .flatMap(r -> r.getConnections().stream())
                .collect(Collectors.toSet());

//        Map<ResourceConnection, Integer> requiredFlows = new HashMap<>();
//        requiredConnections.forEach(c -> requiredFlows.put(c, c.getFlowRateValue()));

        Map<ResourceConnection, ResourceSet> requiredResources = new HashMap<>();
        requiredConnections.forEach(c -> requiredResources.put(c, c.fire()));

        // Active connections, grouped by providing node.
        Map<Node, List<ResourceConnection>> requiredConnectionsByProvider = requiredConnections.stream()
                .collect(Collectors.groupingBy(ResourceConnection::getFrom));

        // The set of actual flow.
        Map<ResourceConnection, ResourceSet> actualFlows = new HashMap<>();

        // Get resource snapshot for each provider node.
        Map<Node, ResourceSet> resourceSnapShots = new HashMap<>();
        requiredConnectionsByProvider.forEach((n, c) -> resourceSnapShots.put(n, n.getResources().copy()));

        // Calculate the actual flow.
        fireRequirements.forEach(requirement ->
                requirement.getConnections().forEach(c -> {
                    ResourceSet snapshot = resourceSnapShots.get(c.getFrom());
                    ResourceSet requiredResource = requiredResources.get(c);
                    ResourceSet extracted = snapshot.remove(requiredResource);

                    if (extracted.size() > 0) {
                        if (requirement.isRequiringAll() && !requiredResource.isSubSetOf(extracted))
                            snapshot.add(extracted);
                        else
                            actualFlows.put(c, extracted);
                    }
                })
        );

        // Determines requirements actually satisfied.
        if (configs.getTimeMode() == TimeMode.SYNCHRONOUS) {
            // synchronous time mode require each provider node to provide all or none dependent connections.
            Set<ResourceConnection> nonFlows = requiredConnectionsByProvider.entrySet().stream()
                    .map(e -> {
                        List<ResourceConnection> dependentConnections = e.getValue();
                        if (!actualFlows.keySet().containsAll(dependentConnections))
                            return dependentConnections;
                        else
                            return Collections.<ResourceConnection>emptyList();
                    }).flatMap(List::stream).collect(Collectors.toSet());

            // Remove those non-flow due to sync-time constraint.
            nonFlows.forEach(actualFlows::remove);
        }

        // Triggers the target nodes meeting requirements.
        Set<FireRequirement> satisfiedRequirements = fireRequirements.stream()
            .filter(r -> {
                Set<ResourceConnection> connections = r.getConnections();

                return (connections.isEmpty() ||
                        (r.isRequiringAll() && actualFlows.keySet().containsAll(connections)) ||
                        (!r.isRequiringAll() && connections.stream().anyMatch(actualFlows::containsKey)));
            }).collect(Collectors.toSet());

        // Activates the nodes.
        Set<ResourceConnection> firedOutgoingConnections = satisfiedRequirements.stream().map(r -> {
            Set<ResourceConnection> connections = r.getConnections();

            Map<ResourceConnection, ResourceSet> incomingFlows = new HashMap<>();
            actualFlows.entrySet().stream()
                    .filter(e -> connections.contains(e.getKey()))
                    .forEach(e -> incomingFlows.put(e.getKey(), e.getValue()));

            if (r.getTarget() instanceof End)
                this.terminated = true;

            return r.getTarget().fire(incomingFlows);
        }).flatMap(Set::stream).collect(Collectors.toSet());

        // --> STEP 2: triggers.
        Map<Node, List<ResourceConnection>> firedConnectionByTarget =
                firedOutgoingConnections.stream().collect(Collectors.groupingBy(ResourceConnection::getTo));

        // Trigger owners are those having all input connections satisfied.
        Set<Node> triggerOwners = firedConnectionByTarget.entrySet().stream()
                .filter(e -> e.getKey().getIncomingConnections().containsAll(e.getValue()))
                .map(Map.Entry::getKey).collect(Collectors.toSet());

        // Add those nodes previously fired but do not have any input requirements.
        triggerOwners.addAll(
                satisfiedRequirements.stream()
                        .filter(r -> r.getTarget().getIncomingConnections().size() == 0)
                        .map(FireRequirement::getTarget)
                        .collect(Collectors.toSet())
        );

        // Elements will be triggered.
        Set<Element> triggeredElements = triggerOwners.stream()
                .flatMap(o -> o.activateTriggers().stream()).map(Trigger::getTarget)
                .collect(Collectors.toSet());

        doTrigger(triggeredElements);

        // --> STEP 3: activators
        this.updateNodeEnablingStates();

        // --> Clean up, update active node set
        this.activeNodes.clear();
        this.activeNodes.addAll(this.automaticNodes);

        return true;
    }

    private void updateNodeEnablingStates() {
        Map<Node, List<Activator>> activatorsByTarget = elements.stream()
                .filter(e -> e instanceof Node).map(e -> (Node)e)
                .flatMap(n -> n.getActivators().stream())
                .collect(Collectors.groupingBy(Activator::getTarget));

        activatorsByTarget.forEach((target, activators) -> {
                boolean newState = activators.stream().map(Activator::evaluate).reduce(true, (a, b) -> a && b);
                target.setEnabled(newState);

            if (target instanceof End && target.isEnabled())
                this.terminated = true;
        });
    }

    private void doTrigger(Set<Element> triggeredElements) {
        if (triggeredElements.size() == 0)
            return;

        Set<Trigger> chainedTriggers = new HashSet<>();

        triggeredElements.forEach(e -> {
            if (e instanceof ResourceConnection) {
                ResourceConnection connection = (ResourceConnection)e;
                activateConnection(connection);
            }
            else if (e instanceof Node) {
                Node node = (Node)e;
                activateNode(node);

                if (node.getIncomingConnections().size() == 0)
                    chainedTriggers.addAll(node.getTriggers());
            }
        });

        doTrigger(chainedTriggers.stream().map(Trigger::getTarget).collect(Collectors.toSet()));
    }

    private void activateConnection(ResourceConnection connection) {
        // int rate = connection.fire();
        // ResourceSet resource = connection.getFrom().getResources().remove(rate);
        // connection.getTo().receive(resource);
    }

    private void activateNode(Node node) {
        if (node instanceof End)
            this.terminated = true;

        FireRequirement requirement = node.getFireRequirement();

        // Active connections, grouped by providing node.
        Set<Node> providers =
                requirement.getConnections().stream()
                .map(ResourceConnection::getFrom)
                .collect(Collectors.toSet());

        Map<ResourceConnection, ResourceSet> requiredResources = new HashMap<>();
        requirement.getConnections().forEach(c -> requiredResources.put(c, c.fire()));

        // The set of actual flow.
        Map<ResourceConnection, ResourceSet> actualFlows = new HashMap<>();

        // Get resource snapshot for each provider node.
        Map<Node, ResourceSet> resourceSnapShots = new HashMap<>();
        providers.forEach(n -> resourceSnapShots.put(n, n.getResources().copy()));

        requirement.getConnections().forEach(c -> {
            ResourceSet snapshot = resourceSnapShots.get(c.getFrom());
            ResourceSet requiredResource = requiredResources.get(c);
            ResourceSet extracted = snapshot.remove(requiredResource);

            if (extracted.size() > 0) {
                if (requirement.isRequiringAll() && !requiredResource.isSubSetOf(extracted))
                    snapshot.add(extracted);
                else
                    actualFlows.put(c, extracted);
            }
        });

        Set<ResourceConnection> connections = requirement.getConnections();
        if (connections.isEmpty() ||
                (requirement.isRequiringAll() && actualFlows.keySet().containsAll(connections)) ||
                (!requirement.isRequiringAll() && connections.stream().anyMatch(actualFlows::containsKey)))
        {
            Map<ResourceConnection, ResourceSet> incomingFlows = new HashMap<>();
            actualFlows.entrySet().stream()
                    .filter(e -> connections.contains(e.getKey()))
                    .forEach(e -> incomingFlows.put(e.getKey(), e.getValue()));

            requirement.getTarget().fire(incomingFlows);
        }
    }

    private Set<ResourceConnection> getActiveConnection(Node node) {
        FlowMode mode = node.getFlowMode();
        if (mode == FlowMode.AUTOMATIC) {
            if (node.getIncomingConnections().size() == 0)
                return node.getOutgoingConnections();
            else
                return node.getIncomingConnections();
        }
        else if (mode == FlowMode.PULL_ALL || mode == FlowMode.PULL_ANY)
            return node.getIncomingConnections();
        else
            return node.getOutgoingConnections();
    }

    /**
     * Initializes the context if needed before simulation.
     */
    protected void initializeIfNeeded() {
        // already simulated at least one step, no need to initialize.
        if (this.time >= 0)
            return;

        this.automaticNodes = elements.stream()
                .filter(e -> e instanceof Node && (((Node)e).getActivationMode() == ActivationMode.AUTOMATIC)
                ).map(e -> (Node)e).collect(Collectors.toSet());

        Set<Node> startingNodes = elements.stream()
                .filter(e -> e instanceof Node &&
                        ((Node)e).getActivationMode() == ActivationMode.STARTING_ACTION
                ).map(e -> (Node)e).collect(Collectors.toSet());

        this.activeNodes.clear();
        this.activeNodes.addAll(this.automaticNodes);
        this.activeNodes.addAll(startingNodes);

//        this.updateNodeEnablingStates();

        // Reset the action points of this turn.
        this.remainedActionPoints = configs.getActionPointsPerTurn();

        this.time = 0;
        this.previousSimulatedTime = -1;
    }

    public void run(int maxTime) {
        int time = 0;
        boolean isStopped = false;

        Set<Node> automaticNodes = elements.stream()
                .filter(e -> e instanceof Node &&
                        ((Node)e).getActivationMode() == ActivationMode.AUTOMATIC
                ).map(e -> (Node)e).collect(Collectors.toSet());

        Set<Node> startingNodes = elements.stream()
                .filter(e -> e instanceof Node &&
                        ((Node)e).getActivationMode() == ActivationMode.STARTING_ACTION
                ).map(e -> (Node)e).collect(Collectors.toSet());

        Set<Node> activeNodes = new HashSet<>();
        activeNodes.addAll(automaticNodes);
        activeNodes.addAll(startingNodes);

        Set<Node> nextActiveNodes = new HashSet<>();

        while ((maxTime < 0 || time < maxTime) && !isStopped) {
            final int currentTime = time;

            // Activate each node.
            // activeNodes.forEach(node -> node.fire(currentTime));

            // Clear "next" set.
            nextActiveNodes.clear();

//            // Find next fire-able elements by triggers.
//            Set<Element> elements = activeNodes.stream().map(node -> {
//                boolean connectionsActivated =
//                        node.getIncomingConnections().stream()
//                                .allMatch(c -> c.getLastActivatedTime() == currentTime);
//
//                if (connectionsActivated)
//                    return node.getTriggers();
//                else
//                    return Collections.<Trigger>emptySet();
//            }).flatMap(Set::stream).map(Trigger::getTarget).collect(Collectors.toSet());

            // Advance the time.
            time++;
        }
    }
}
