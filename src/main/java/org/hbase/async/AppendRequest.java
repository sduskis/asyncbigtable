/*
 * Copyright (C) 2015  The Async BigTable Authors.  All rights reserved.
 * This file is part of Async BigTable.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *   - Redistributions of source code must retain the above copyright notice,
 *     this list of conditions and the following disclaimer.
 *   - Redistributions in binary form must reproduce the above copyright notice,
 *     this list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *   - Neither the name of the StumbleUpon nor the names of its contributors
 *     may be used to endorse or promote products derived from this software
 *     without specific prior written permission.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package org.hbase.async;

/**
 * Appends data to one or more columns in BigTable, creating the columns if they
 * do not exist. The {@code value} is simply concatenated with whatever is 
 * already in the column. An append operation will acquire a lock on the row
 * so that all column operations are atomic with respect to writers. However
 * reads across the row are not atomic and therefore readers may see partially
 * completed operations.
 * <p>
 * Note that the results of the append operation
 * are not returned at this time. Issue a {@see GetRequest} to fetch the results.
 * <p>
 * <h1>A note on passing {@code byte} arrays in argument</h1>
 * None of the method that receive a {@code byte[]} in argument will copy it.
 * For more info, please refer to the documentation of {@link HBaseRpc}.
 * <h1>A note on passing {@code String}s in argument</h1>
 * All strings are assumed to use the platform's default charset.
 * <h1>A note on passing {@code timestamp}s in argument</h1>
 * BigTable orders all the writes based on timestamps from {@code AppendRequest}
 * irrespective of the actual order in which they're received or stored by
 * a RegionServer.  In other words, if you send a first {@code AppendRequest}
 * with timestamp T, and then later send another one for the same table,
 * key, family and qualifier, but with timestamp T - 1, then the second
 * write will look like it was applied before the first one when you read
 * this cell back from BigTable. When manually setting timestamps, it is thus
 * strongly recommended to use real UNIX timestamps in milliseconds, e.g.
 * from {@link System#currentTimeMillis}.
 * <p>
 * If you want to let BigTable set the timestamp on a write at the time it's
 * applied within the RegionServer, then use {@link KeyValue#TIMESTAMP_NOW}
 * as a timestamp.  The timestamp is set right before being written to the WAL
 * (Write Ahead Log).  Note however that this has a subtle consequence: if a
 * write succeeds from the server's point of view, but fails from the client's
 * point of view (maybe because the client got disconnected from the server
 * before the server could acknowledge the write), then if the client retries
 * the write it will create another version of the cell with a different
 * timestamp.
 * @since 1.7
 */
public final class AppendRequest extends BatchableRpc
  implements HBaseRpc.HasTable, HBaseRpc.HasKey, HBaseRpc.HasFamily,
             HBaseRpc.HasQualifiers, HBaseRpc.HasValues, HBaseRpc.IsEdit,
             /* legacy: */ HBaseRpc.HasQualifier, HBaseRpc.HasValue {
  
  /**
   * Invariants:
   *   - qualifiers.length == values.length
   *   - qualifiers.length > 0
   */
  private final byte[][] qualifiers;
  private final byte[][] values;
  
  /** Whether or not to return the result of the append request */
  private boolean return_result = false;

  /**
   * Constructor using current time.
   * <strong>These byte arrays will NOT be copied.</strong>
   * <p>
   * Note: If you want to set your own timestamp, use
   * {@link #AppendRequest(byte[], byte[], byte[], byte[], byte[], long)}
   * instead.  This constructor will let the RegionServer assign the timestamp
   * to this write at the time using {@link System#currentTimeMillis} right
   * before the write is persisted to the WAL.
   * @param table The table to edit.
   * @param key The key of the row to edit in that table.
   * @param family The column family to edit in that table.
   * @param qualifier The column qualifier to edit in that family.
   * @param value The value to store.
   */
  public AppendRequest(final byte[] table,
                    final byte[] key,
                    final byte[] family,
                    final byte[] qualifier,
                    final byte[] value) {
    this(table, key, family, qualifier, value, KeyValue.TIMESTAMP_NOW);
  }

  /**
   * Constructor for multiple columns using current time.
   * <strong>These byte arrays will NOT be copied.</strong>
   * <p>
   * Note: If you want to set your own timestamp, use
   * {@link #AppendRequest(byte[], byte[], byte[], byte[][], byte[][], long)}
   * instead.  This constructor will let the RegionServer assign the timestamp
   * to this write at the time using {@link System#currentTimeMillis} right
   * before the write is persisted to the WAL.
   * @param table The table to edit.
   * @param key The key of the row to edit in that table.
   * @param family The column family to edit in that table.
   * @param qualifiers The column qualifiers to edit in that family.
   * @param values The corresponding values to store.
   * @throws IllegalArgumentException if {@code qualifiers.length == 0}
   * or if {@code qualifiers.length != values.length}
   */
  public AppendRequest(final byte[] table,
                    final byte[] key,
                    final byte[] family,
                    final byte[][] qualifiers,
                    final byte[][] values) {
    this(table, key, family, qualifiers, values, KeyValue.TIMESTAMP_NOW, false);
  }

  /**
   * Constructor for a specific timestamp.
   * <strong>These byte arrays will NOT be copied.</strong>
   * @param table The table to edit.
   * @param key The key of the row to edit in that table.
   * @param family The column family to edit in that table.
   * @param qualifier The column qualifier to edit in that family.
   * @param value The value to store.
   * @param timestamp The timestamp to set on this edit.
   */
  public AppendRequest(final byte[] table,
                    final byte[] key,
                    final byte[] family,
                    final byte[] qualifier,
                    final byte[] value,
                    final long timestamp) {
    this(table, key, family, new byte[][] { qualifier }, 
        new byte[][] { value }, timestamp, false);
  }

  /**
   * Constructor for multiple columns with a specific timestamp.
   * <strong>These byte arrays will NOT be copied.</strong>
   * @param table The table to edit.
   * @param key The key of the row to edit in that table.
   * @param family The column family to edit in that table.
   * @param qualifiers The column qualifiers to edit in that family.
   * @param values The corresponding values to store.
   * @param timestamp The timestamp to set on this edit.
   * @throws IllegalArgumentException if {@code qualifiers.length == 0}
   * or if {@code qualifiers.length != values.length}
   */
  public AppendRequest(final byte[] table,
                    final byte[] key,
                    final byte[] family,
                    final byte[][] qualifiers,
                    final byte[][] values,
                    final long timestamp) {
    this(table, key, family, qualifiers, values, timestamp, false);
  }
  
  /**
   * Constructor from a {@link KeyValue}.
   * @param table The table to edit.
   * @param kv The {@link KeyValue} to store.
   */
  public AppendRequest(final byte[] table,
                     final KeyValue kv) {
    super(table, kv.key(), kv.family(), kv.timestamp(), RowLock.NO_LOCK);
    this.qualifiers = new byte[][] { kv.qualifier() };
    this.values = new byte[][] { kv.value() };
  }

  /** Private constructor.  */
  private AppendRequest(final byte[] table,
                     final byte[] key,
                     final byte[] family,
                     final byte[][] qualifiers,
                     final byte[][] values,
                     final long timestamp,
                     final boolean return_result) {
    super(table, key, family, timestamp, RowLock.NO_LOCK);
    KeyValue.checkFamily(family);
    
    if (qualifiers.length != values.length) {
      throw new IllegalArgumentException("Have " + qualifiers.length
        + " qualifiers and " + values.length + " values.  Should be equal.");
    } else if (qualifiers.length == 0) {
      throw new IllegalArgumentException("Need at least one qualifier/value.");
    }
    for (int i = 0; i < qualifiers.length; i++) {
      KeyValue.checkQualifier(qualifiers[i]);
      KeyValue.checkValue(values[i]);
    }
    this.qualifiers = qualifiers;
    this.values = values;
  }

  @Override
  public byte[] table() {
    return table;
  }

  @Override
  public byte[] key() {
    return key;
  }

  /**
   * Returns the first qualifier of the set of edits in this RPC.
   * {@inheritDoc}
   */
  @Override
  public byte[] qualifier() {
    return qualifiers[0];
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public byte[][] qualifiers() {
    return qualifiers;
  }

  /**
   * Returns the first value of the set of edits in this RPC.
   * {@inheritDoc}
   */
  @Override
  public byte[] value() {
    return values[0];
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public byte[][] values() {
    return values;
  }
  
  public String toString() {
    return super.toStringWithQualifiers("AppendRequest",
                                       family, qualifiers, values,
                                       ", timestamp=" + timestamp
                                       + ", lockid=" + lockid
                                       + ", durable=" + durable
                                       + ", return_result=" + return_result
                                       + ", bufferable=" + super.bufferable);
  }

  /** @param return_result Whether or not to fetch the results of the append
   * from HBase. */
  public void returnResult(boolean return_result) {
    this.return_result = return_result;
  }
  
  /** @return Whether or not to fetch the results of the append from HBase. */
  public boolean returnResult() {
    return return_result;
  }

}