package org.apache.lucene.facet.associations;

import java.io.IOException;

import org.apache.lucene.facet.params.CategoryListParams;
import org.apache.lucene.facet.search.FacetArrays;
import org.apache.lucene.facet.search.FacetRequest;
import org.apache.lucene.facet.search.FacetsAggregator;
import org.apache.lucene.facet.search.FacetsCollector.MatchingDocs;
import org.apache.lucene.facet.taxonomy.TaxonomyReader;
import org.apache.lucene.index.BinaryDocValues;
import org.apache.lucene.util.BytesRef;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * A {@link FacetsAggregator} which computes the weight of a category as the sum
 * of the float values associated with it in the result documents. Assumes that
 * the association encoded for each ordinal is {@link CategoryFloatAssociation}.
 * 
 * @lucene.experimental
 */
public class SumFloatAssociationFacetsAggregator implements FacetsAggregator {

  private final BytesRef bytes = new BytesRef(32);
  
  @Override
  public void aggregate(MatchingDocs matchingDocs, CategoryListParams clp, FacetArrays facetArrays) throws IOException {
    BinaryDocValues dv = matchingDocs.context.reader().getBinaryDocValues(clp.field + CategoryFloatAssociation.ASSOCIATION_LIST_ID);
    if (dv == null) {
      return; // no float associations in this reader
    }
    
    final int length = matchingDocs.bits.length();
    final float[] values = facetArrays.getFloatArray();
    int doc = 0;
    while (doc < length && (doc = matchingDocs.bits.nextSetBit(doc)) != -1) {
      dv.get(doc, bytes);
      if (bytes.length == 0) {
        continue; // no associations for this document
      }

      // aggreate float association values for ordinals
      int bytesUpto = bytes.offset + bytes.length;
      int pos = bytes.offset;
      while (pos < bytesUpto) {
        int ordinal = ((bytes.bytes[pos++] & 0xFF) << 24) | ((bytes.bytes[pos++] & 0xFF) << 16)
            | ((bytes.bytes[pos++] & 0xFF) <<  8) | (bytes.bytes[pos++] & 0xFF);
        
        int value = ((bytes.bytes[pos++] & 0xFF) << 24) | ((bytes.bytes[pos++] & 0xFF) << 16)
            | ((bytes.bytes[pos++] & 0xFF) <<  8) | (bytes.bytes[pos++] & 0xFF);

        values[ordinal] += Float.intBitsToFloat(value);
      }
      
      ++doc;
    }
  }

  @Override
  public boolean requiresDocScores() {
    return false;
  }

  private float rollupValues(int ordinal, int[] children, int[] siblings, float[] scores) {
    float Value = 0f;
    while (ordinal != TaxonomyReader.INVALID_ORDINAL) {
      float childValue = scores[ordinal];
      childValue += rollupValues(children[ordinal], children, siblings, scores);
      scores[ordinal] = childValue;
      Value += childValue;
      ordinal = siblings[ordinal];
    }
    return Value;
  }

  @Override
  public void rollupValues(FacetRequest fr, int ordinal, int[] children, int[] siblings, FacetArrays facetArrays) {
    float[] values = facetArrays.getFloatArray();
    values[ordinal] += rollupValues(children[ordinal], children, siblings, values);
  }
  
}
