/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.catalina.tribes.tipis;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.catalina.tribes.Channel;
import org.apache.catalina.tribes.ChannelException;
import org.apache.catalina.tribes.ChannelException.FaultyMember;
import org.apache.catalina.tribes.Member;
import org.apache.catalina.tribes.RemoteProcessException;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * All-to-all replication for a hash map implementation. Each node in the cluster will carry an identical copy of the
 * map.<br>
 * <br>
 * This map implementation doesn't have a background thread running to replicate changes. If you do have changes without
 * invoking put/remove then you need to invoke one of the following methods:
 * <ul>
 * <li><code>replicate(Object,boolean)</code> - replicates only the object that belongs to the key</li>
 * <li><code>replicate(boolean)</code> - Scans the entire map for changes and replicates data</li>
 * </ul>
 * the <code>boolean</code> value in the <code>replicate</code> method used to decide whether to only replicate objects
 * that implement the <code>ReplicatedMapEntry</code> interface or to replicate all objects. If an object doesn't
 * implement the <code>ReplicatedMapEntry</code> interface each time the object gets replicated the entire object gets
 * serialized, hence a call to <code>replicate(true)</code> will replicate all objects in this map that are using this
 * node as primary. <br>
 * <br>
 * <b>REMEMBER TO CALL <code>breakdown()</code> when you are done with the map to avoid memory leaks.</b><br>
 * <br>
 * TODO implement periodic sync/transfer thread<br>
 * TODO memberDisappeared, should do nothing except change map membership by default it relocates the primary objects
 *
 * @param <K> The type of Key
 * @param <V> The type of Value
 */
public class ReplicatedMap<K, V> extends AbstractReplicatedMap<K,V> {

    @Serial
    private static final long serialVersionUID = 1L;

    // Lazy init to support serialization
    private transient volatile Log log;

    // --------------------------------------------------------------------------
    // CONSTRUCTORS / DESTRUCTORS
    // --------------------------------------------------------------------------
    /**
     * Creates a new map
     *
     * @param owner           The map owner
     * @param channel         The channel to use for communication
     * @param timeout         long - timeout for RPC messages
     * @param mapContextName  String - unique name for this map, to allow multiple maps per channel
     * @param initialCapacity int - the size of this map, see HashMap
     * @param loadFactor      float - load factor, see HashMap
     * @param cls             Class loaders
     */
    public ReplicatedMap(MapOwner owner, Channel channel, long timeout, String mapContextName, int initialCapacity,
            float loadFactor, ClassLoader[] cls) {
        super(owner, channel, timeout, mapContextName, initialCapacity, loadFactor, Channel.SEND_OPTIONS_DEFAULT, cls,
                true);
    }

    /**
     * Creates a new map
     *
     * @param owner           The map owner
     * @param channel         The channel to use for communication
     * @param timeout         long - timeout for RPC messages
     * @param mapContextName  String - unique name for this map, to allow multiple maps per channel
     * @param initialCapacity int - the size of this map, see HashMap
     * @param cls             Class loaders
     */
    public ReplicatedMap(MapOwner owner, Channel channel, long timeout, String mapContextName, int initialCapacity,
            ClassLoader[] cls) {
        super(owner, channel, timeout, mapContextName, initialCapacity, DEFAULT_LOAD_FACTOR,
                Channel.SEND_OPTIONS_DEFAULT, cls, true);
    }

    /**
     * Creates a new map
     *
     * @param owner          The map owner
     * @param channel        The channel to use for communication
     * @param timeout        long - timeout for RPC messages
     * @param mapContextName String - unique name for this map, to allow multiple maps per channel
     * @param cls            Class loaders
     */
    public ReplicatedMap(MapOwner owner, Channel channel, long timeout, String mapContextName, ClassLoader[] cls) {
        super(owner, channel, timeout, mapContextName, DEFAULT_INITIAL_CAPACITY, DEFAULT_LOAD_FACTOR,
                Channel.SEND_OPTIONS_DEFAULT, cls, true);
    }

    /**
     * Creates a new map
     *
     * @param owner          The map owner
     * @param channel        The channel to use for communication
     * @param timeout        long - timeout for RPC messages
     * @param mapContextName String - unique name for this map, to allow multiple maps per channel
     * @param cls            Class loaders
     * @param terminate      boolean - Flag for whether to terminate this map that failed to start.
     */
    public ReplicatedMap(MapOwner owner, Channel channel, long timeout, String mapContextName, ClassLoader[] cls,
            boolean terminate) {
        super(owner, channel, timeout, mapContextName, DEFAULT_INITIAL_CAPACITY, DEFAULT_LOAD_FACTOR,
                Channel.SEND_OPTIONS_DEFAULT, cls, terminate);
    }

    // ------------------------------------------------------------------------------
    // METHODS TO OVERRIDE
    // ------------------------------------------------------------------------------
    @Override
    protected int getStateMessageType() {
        return AbstractReplicatedMap.MapMessage.MSG_STATE_COPY;
    }

    @Override
    protected int getReplicateMessageType() {
        return AbstractReplicatedMap.MapMessage.MSG_COPY;
    }

    @Override
    protected Member[] publishEntryInfo(Object key, Object value) throws ChannelException {
        if (!(key instanceof Serializable && value instanceof Serializable)) {
            return new Member[0];
        }
        // select a backup node
        Member[] backup = getMapMembers();

        if (backup == null || backup.length == 0) {
            return null;
        }

        try {
            // publish the data out to all nodes
            MapMessage msg = new MapMessage(getMapContextName(), MapMessage.MSG_COPY, false, (Serializable) key,
                    (Serializable) value, null, channel.getLocalMember(false), backup);

            getChannel().send(backup, msg, getChannelSendOptions());
        } catch (ChannelException e) {
            FaultyMember[] faultyMembers = e.getFaultyMembers();
            if (faultyMembers.length == 0) {
                throw e;
            }
            List<Member> faulty = new ArrayList<>();
            for (FaultyMember faultyMember : faultyMembers) {
                if (!(faultyMember.getCause() instanceof RemoteProcessException)) {
                    faulty.add(faultyMember.getMember());
                }
            }
            Member[] realFaultyMembers = faulty.toArray(new Member[0]);
            if (realFaultyMembers.length != 0) {
                backup = excludeFromSet(realFaultyMembers, backup);
                if (backup.length == 0) {
                    throw e;
                } else {
                    if (getLog().isWarnEnabled()) {
                        getLog().warn(sm.getString("replicatedMap.unableReplicate.completely", key,
                                Arrays.toString(backup), Arrays.toString(realFaultyMembers)), e);
                    }
                }
            }
        }
        return backup;
    }

    @Override
    public void memberDisappeared(Member member) {
        Log log = getLog();
        synchronized (mapMembers) {
            boolean removed = (mapMembers.remove(member) != null);
            if (!removed) {
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("replicatedMap.member.disappeared.unknown", member));
                }
                return; // the member was not part of our map.
            }
        }
        if (log.isInfoEnabled()) {
            log.info(sm.getString("replicatedMap.member.disappeared", member));
        }
        long start = System.currentTimeMillis();
        for (Entry<K,MapEntry<K,V>> e : innerMap.entrySet()) {
            MapEntry<K,V> entry = innerMap.get(e.getKey());
            if (entry == null) {
                continue;
            }
            if (entry.isPrimary()) {
                try {
                    Member[] backup = getMapMembers();
                    if (backup.length > 0) {
                        MapMessage msg = new MapMessage(getMapContextName(), MapMessage.MSG_NOTIFY_MAPMEMBER, false,
                                (Serializable) entry.getKey(), null, null, channel.getLocalMember(false), backup);
                        getChannel().send(backup, msg, getChannelSendOptions());
                    }
                    entry.setBackupNodes(backup);
                    entry.setPrimary(channel.getLocalMember(false));
                } catch (ChannelException x) {
                    log.error(sm.getString("replicatedMap.unable.relocate", entry.getKey()), x);
                }
            } else if (member.equals(entry.getPrimary())) {
                entry.setPrimary(null);
            }

            if (entry.getPrimary() == null && entry.isCopy() && entry.getBackupNodes() != null &&
                    entry.getBackupNodes().length > 0 &&
                    entry.getBackupNodes()[0].equals(channel.getLocalMember(false))) {
                try {
                    entry.setPrimary(channel.getLocalMember(false));
                    entry.setBackup(false);
                    entry.setProxy(false);
                    entry.setCopy(false);
                    Member[] backup = getMapMembers();
                    if (backup.length > 0) {
                        MapMessage msg = new MapMessage(getMapContextName(), MapMessage.MSG_NOTIFY_MAPMEMBER, false,
                                (Serializable) entry.getKey(), null, null, channel.getLocalMember(false), backup);
                        getChannel().send(backup, msg, getChannelSendOptions());
                    }
                    entry.setBackupNodes(backup);
                    if (mapOwner != null) {
                        mapOwner.objectMadePrimary(entry.getKey(), entry.getValue());
                    }

                } catch (ChannelException x) {
                    log.error(sm.getString("replicatedMap.unable.relocate", entry.getKey()), x);
                }
            }

        } // while
        long complete = System.currentTimeMillis() - start;
        if (log.isInfoEnabled()) {
            log.info(sm.getString("replicatedMap.relocate.complete", Long.toString(complete)));
        }
    }

    @Override
    public void mapMemberAdded(Member member) {
        if (member.equals(getChannel().getLocalMember(false))) {
            return;
        }
        boolean memberAdded = false;
        synchronized (mapMembers) {
            if (!mapMembers.containsKey(member)) {
                mapMembers.put(member, Long.valueOf(System.currentTimeMillis()));
                memberAdded = true;
            }
        }
        if (memberAdded) {
            synchronized (stateMutex) {
                Member[] backup = getMapMembers();
                for (Entry<K,MapEntry<K,V>> e : innerMap.entrySet()) {
                    MapEntry<K,V> entry = innerMap.get(e.getKey());
                    if (entry == null) {
                        continue;
                    }
                    if (entry.isPrimary() && !inSet(member, entry.getBackupNodes())) {
                        entry.setBackupNodes(backup);
                    }
                }
            }
        }
    }


    private Log getLog() {
        if (log == null) {
            synchronized (this) {
                if (log == null) {
                    log = LogFactory.getLog(ReplicatedMap.class);
                }
            }
        }
        return log;
    }
}