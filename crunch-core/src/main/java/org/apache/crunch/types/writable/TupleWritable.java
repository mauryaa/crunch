/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.crunch.types.writable;

import java.io.ByteArrayInputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.IOException;
import java.util.List;

import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.crunch.CrunchRuntimeException;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.io.WritableComparable;
import org.apache.hadoop.io.WritableFactories;
import org.apache.hadoop.io.WritableUtils;

/**
 * A straight copy of the TupleWritable implementation in the join package,
 * added here because of its package visibility restrictions.
 * 
 */
public class TupleWritable implements WritableComparable<TupleWritable> {

  private long written;
  private BytesWritable[] values;
  private List<Class<Writable>> writableClasses;
  
  /**
   * Create an empty tuple with no allocated storage for writables.
   */
  public TupleWritable() {
  }

  /**
   * Initialize tuple with storage; unknown whether any of them contain
   * &quot;written&quot; values.
   */
  public TupleWritable(BytesWritable[] vals) {
    written = 0L;
    values = vals;
  }

  public void setWritableClasses(List<Class<Writable>> writableClasses) {
    this.writableClasses = writableClasses;
  }
  
  /**
   * Return true if tuple has an element at the position provided.
   */
  public boolean has(int i) {
    return 0 != ((1 << i) & written);
  }

  /**
   * Get ith Writable from Tuple.
   */
  public BytesWritable get(int i) {
    return values[i];
  }

  /**
   * The number of children in this Tuple.
   */
  public int size() {
    return values.length;
  }

  /**
   * {@inheritDoc}
   */
  public boolean equals(Object other) {
    if (other instanceof TupleWritable) {
      TupleWritable that = (TupleWritable) other;
      if (this.size() != that.size() || this.written != that.written) {
        return false;
      }
      for (int i = 0; i < values.length; ++i) {
        if (!has(i))
          continue;
        if (!values[i].equals(that.get(i))) {
          return false;
        }
      }
      return true;
    }
    return false;
  }

  public int hashCode() {
    HashCodeBuilder builder = new HashCodeBuilder();
    builder.append(written);
    for (Writable v : values) {
      builder.append(v);
    }
    return builder.toHashCode();
  }

  /**
   * Convert Tuple to String as in the following.
   * <tt>[<child1>,<child2>,...,<childn>]</tt>
   */
  public String toString() {
    StringBuffer buf = new StringBuffer("[");
    for (int i = 0; i < values.length; ++i) {
      if (has(i)) {
        if (writableClasses != null) {
          Writable w = WritableFactories.newInstance(writableClasses.get(i));
          try {
            w.readFields(new DataInputStream(new ByteArrayInputStream(values[i].getBytes())));
          } catch (IOException e) {
            throw new CrunchRuntimeException(e);
          }
          buf.append(w.toString());
        } else {
          buf.append(values[i].toString());
        }
      }
      buf.append(",");
    }
    if (values.length != 0)
      buf.setCharAt(buf.length() - 1, ']');
    else
      buf.append(']');
    return buf.toString();
  }

  /**
   * Writes each Writable to <code>out</code>. TupleWritable format:
   * {@code
   *  <count><type1><type2>...<typen><obj1><obj2>...<objn>
   * }
   */
  public void write(DataOutput out) throws IOException {
    WritableUtils.writeVInt(out, values.length);
    WritableUtils.writeVLong(out, written);
    for (int i = 0; i < values.length; ++i) {
      if (has(i)) {
        values[i].write(out);
      }
    }
  }

  /**
   * {@inheritDoc}
   */
  public void readFields(DataInput in) throws IOException {
    int card = WritableUtils.readVInt(in);
    values = new BytesWritable[card];
    written = WritableUtils.readVLong(in);
    for (int i = 0; i < card; ++i) {
      if (has(i)) {
        values[i] = new BytesWritable();
        values[i].readFields(in);
      }
    }
  }

  /**
   * Record that the tuple contains an element at the position provided.
   */
  public void setWritten(int i) {
    written |= 1 << i;
  }

  /**
   * Record that the tuple does not contain an element at the position provided.
   */
  public void clearWritten(int i) {
    written &= -1 ^ (1 << i);
  }

  /**
   * Clear any record of which writables have been written to, without releasing
   * storage.
   */
  public void clearWritten() {
    written = 0L;
  }

  @Override
  public int compareTo(TupleWritable o) {
    for (int i = 0; i < values.length; ++i) {
      if (has(i) && !o.has(i)) {
        return 1;
      } else if (!has(i) && o.has(i)) {
        return -1;
      } else {
        Writable v1 = values[i];
        Writable v2 = o.values[i];
        if (v1 != v2 && (v1 != null && !v1.equals(v2))) {
          if (v1 instanceof WritableComparable && v2 instanceof WritableComparable) {
            int cmp = ((WritableComparable) v1).compareTo((WritableComparable) v2);
            if (cmp != 0) {
              return cmp;
            }
          } else {
            int cmp = v1.hashCode() - v2.hashCode();
            if (cmp != 0) {
              return cmp;
            }
          }
        }
      }
    }
    return values.length - o.values.length;
  }
}