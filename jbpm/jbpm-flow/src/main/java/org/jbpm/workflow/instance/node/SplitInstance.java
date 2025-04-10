/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.jbpm.workflow.instance.node;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.drools.core.common.InternalKnowledgeRuntime;
import org.jbpm.process.core.context.exclusive.ExclusiveGroup;
import org.jbpm.process.instance.ContextInstanceContainer;
import org.jbpm.process.instance.InternalProcessRuntime;
import org.jbpm.process.instance.context.exclusive.ExclusiveGroupInstance;
import org.jbpm.process.instance.impl.ConstraintEvaluator;
import org.jbpm.workflow.core.Constraint;
import org.jbpm.workflow.core.Node;
import org.jbpm.workflow.core.node.Split;
import org.jbpm.workflow.instance.NodeInstanceContainer;
import org.jbpm.workflow.instance.WorkflowRuntimeException;
import org.jbpm.workflow.instance.impl.NodeInstanceImpl;
import org.kie.api.definition.process.Connection;
import org.kie.api.definition.process.WorkflowElementIdentifier;
import org.kie.kogito.internal.process.runtime.KogitoNodeInstance;
import org.kie.kogito.internal.process.runtime.KogitoProcessInstance;

/**
 * Runtime counterpart of a split node.
 * 
 */
public class SplitInstance extends NodeInstanceImpl {

    private static final long serialVersionUID = 510l;

    protected Split getSplit() {
        return (Split) getNode();
    }

    public void internalTrigger(final KogitoNodeInstance from, String type) {
        if (!Node.CONNECTION_DEFAULT_TYPE.equals(type)) {
            throw new IllegalArgumentException(
                    "A Split only accepts default incoming connections!");
        }
        triggerTime = new Date();
        final Split split = getSplit();

        try {
            executeStrategy(split, type);
        } catch (WorkflowRuntimeException wre) {
            throw wre;
        } catch (Exception e) {
            throw new WorkflowRuntimeException(this, getProcessInstance(), "Unable to execute Split: " + e.getMessage(), e);
        }
    }

    protected void executeStrategy(Split split, String type) {
        // TODO make different strategies for each type
        switch (split.getType()) {
            case Split.TYPE_AND:
                triggerCompleted(Node.CONNECTION_DEFAULT_TYPE, true);
                break;
            case Split.TYPE_XOR:
                List<Connection> outgoing = split.getDefaultOutgoingConnections();
                int priority = Integer.MAX_VALUE;
                Connection selected = null;
                for (final Iterator<Connection> iterator = outgoing.iterator(); iterator.hasNext();) {
                    final Connection connection = iterator.next();
                    Collection<Constraint> constraints = split.getConstraints(connection);
                    if (constraints != null) {
                        for (Constraint constraint : constraints) {
                            if (constraint instanceof ConstraintEvaluator && constraint.getPriority() < priority && !constraint.isDefault()) {
                                try {
                                    if (((ConstraintEvaluator) constraint).evaluate(this,
                                            connection,
                                            constraint)) {
                                        selected = connection;
                                        priority = constraint.getPriority();
                                    }
                                } catch (RuntimeException e) {
                                    throw new RuntimeException(
                                            "Exception when trying to evaluate constraint "
                                                    + constraint.getName() + " in split "
                                                    + split.getName(),
                                            e);
                                }
                            }
                        }
                    }
                }
                ((NodeInstanceContainer) getNodeInstanceContainer()).removeNodeInstance(this);
                if (selected == null) {
                    for (final Iterator<Connection> iterator = outgoing.iterator(); iterator.hasNext();) {
                        final Connection connection = iterator.next();
                        if (split.isDefault(connection)) {
                            selected = connection;
                            break;
                        }
                    }
                }
                if (selected == null) {
                    throw new IllegalArgumentException("XOR split could not find at least one valid outgoing connection for split " + getSplit().getName());
                }
                if (!hasLoop(selected.getTo(), split)) {
                    setLevel(1);
                    ((NodeInstanceContainer) getNodeInstanceContainer()).setCurrentLevel(1);
                }
                triggerConnection(selected);
                break;
            case Split.TYPE_OR:
                ((NodeInstanceContainer) getNodeInstanceContainer()).removeNodeInstance(this);
                outgoing = split.getDefaultOutgoingConnections();
                boolean found = false;
                List<NodeInstanceTrigger> nodeInstances =
                        new ArrayList<>();
                List<Connection> outgoingCopy = new ArrayList<>(outgoing);
                while (!outgoingCopy.isEmpty()) {
                    priority = Integer.MAX_VALUE;
                    Connection selectedConnection = null;
                    ConstraintEvaluator selectedConstraint = null;
                    for (final Iterator<Connection> iterator = outgoingCopy.iterator(); iterator.hasNext();) {
                        final Connection connection = iterator.next();
                        Collection<Constraint> constraints = split.getConstraints(connection);
                        if (constraints != null) {
                            for (Constraint constraint : constraints) {
                                if (constraint instanceof ConstraintEvaluator && constraint.getPriority() < priority
                                        && !constraint.isDefault()) {
                                    priority = constraint.getPriority();
                                    selectedConnection = connection;
                                    selectedConstraint = (ConstraintEvaluator) constraint;
                                }
                            }
                        }
                    }
                    if (selectedConstraint == null) {
                        break;
                    }
                    if (selectedConstraint.evaluate(this,
                            selectedConnection,
                            selectedConstraint)) {
                        nodeInstances.add(new NodeInstanceTrigger(followConnection(selectedConnection), selectedConnection.getToType()));
                        found = true;
                    }
                    outgoingCopy.remove(selectedConnection);
                }

                for (NodeInstanceTrigger nodeInstance : nodeInstances) {
                    // stop if this process instance has been aborted / completed
                    if (getProcessInstance().getState() != KogitoProcessInstance.STATE_ACTIVE) {
                        return;
                    }
                    triggerNodeInstance(nodeInstance.getNodeInstance(), nodeInstance.getToType());
                }
                if (!found) {
                    final Iterator<Connection> iterator = outgoing.iterator();
                    while (!found && iterator.hasNext()) {
                        final Connection connection = iterator.next();
                        Collection<Constraint> constraints = split.getConstraints(connection);
                        if (constraints != null) {
                            for (Constraint constraint : constraints) {
                                if (constraint != null && constraint.isDefault()) {
                                    triggerConnection(connection);
                                    found = true;
                                    break;
                                }
                            }
                        }
                        if (!found && split.isDefault(connection)) {
                            triggerConnection(connection);
                            found = true;
                        }
                    }
                }
                if (!found) {
                    throw new IllegalArgumentException("OR split could not find at least one valid outgoing connection for split " + getSplit().getName());
                }
                break;
            case Split.TYPE_XAND:
                ((org.jbpm.workflow.instance.NodeInstanceContainer) getNodeInstanceContainer()).removeNodeInstance(this);
                org.kie.api.definition.process.Node node = getNode();
                List<Connection> connections = null;
                if (node != null) {
                    connections = node.getOutgoingConnections(type);
                }
                if (connections == null || connections.isEmpty()) {
                    ((org.jbpm.workflow.instance.NodeInstanceContainer) getNodeInstanceContainer())
                            .nodeInstanceCompleted(this, type);
                } else {
                    ExclusiveGroupInstance groupInstance = new ExclusiveGroupInstance();
                    org.kie.api.runtime.process.NodeInstanceContainer parent = getNodeInstanceContainer();
                    if (parent instanceof ContextInstanceContainer) {
                        ((ContextInstanceContainer) parent).addContextInstance(ExclusiveGroup.EXCLUSIVE_GROUP, groupInstance);
                    } else {
                        throw new IllegalArgumentException(
                                "An Exclusive AND is only possible if the parent is a context instance container");
                    }
                    Map<org.jbpm.workflow.instance.NodeInstance, String> nodeInstancesMap = new HashMap<>();
                    for (Connection connection : connections) {
                        nodeInstancesMap.put(followConnection(connection), connection.getToType());
                    }
                    for (KogitoNodeInstance nodeInstance : nodeInstancesMap.keySet()) {
                        groupInstance.addNodeInstance(nodeInstance);
                    }
                    for (Map.Entry<org.jbpm.workflow.instance.NodeInstance, String> entry : nodeInstancesMap.entrySet()) {
                        // stop if this process instance has been aborted / completed
                        if (getProcessInstance().getState() != KogitoProcessInstance.STATE_ACTIVE) {
                            return;
                        }
                        boolean hidden = false;
                        if (getNode().getMetaData().get("hidden") != null) {
                            hidden = true;
                        }
                        leaveTime = new Date();
                        InternalKnowledgeRuntime kruntime = getProcessInstance().getKnowledgeRuntime();
                        if (!hidden) {
                            ((InternalProcessRuntime) kruntime.getProcessRuntime())
                                    .getProcessEventSupport().fireBeforeNodeLeft(this, kruntime);
                        }
                        (entry.getKey()).trigger(this, entry.getValue());
                        if (!hidden) {
                            ((InternalProcessRuntime) kruntime.getProcessRuntime())
                                    .getProcessEventSupport().fireAfterNodeLeft(this, kruntime);
                        }
                    }
                }
                break;
            default:
                throw new IllegalArgumentException("Illegal split type " + split.getType());
        }
    }

    protected boolean hasLoop(org.kie.api.definition.process.Node startAt, final org.kie.api.definition.process.Node lookFor) {
        Set<WorkflowElementIdentifier> vistedNodes = new HashSet<>();

        return checkNodes(startAt, lookFor, vistedNodes);

    }

    protected boolean checkNodes(org.kie.api.definition.process.Node currentNode, final org.kie.api.definition.process.Node lookFor, Set<WorkflowElementIdentifier> vistedNodes) {
        List<Connection> connections = currentNode.getOutgoingConnections(Node.CONNECTION_DEFAULT_TYPE);

        for (Connection conn : connections) {
            org.kie.api.definition.process.Node nextNode = conn.getTo();
            if (nextNode == null) {
                continue;
            } else if (vistedNodes.contains(nextNode.getId())) {
                continue;
            } else {
                vistedNodes.add(nextNode.getId());
                if (nextNode.getId().equals(lookFor.getId())) {
                    return true;
                }

                boolean nestedCheck = checkNodes(nextNode, lookFor, vistedNodes);
                if (nestedCheck) {
                    return true;
                }

            }
        }

        return false;
    }

}
