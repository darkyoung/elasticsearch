/*
 * Licensed to Elastic Search and Shay Banon under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Elastic Search licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.index.query;

import org.apache.lucene.search.DeletionAwareConstantScoreQuery;
import org.apache.lucene.search.Filter;
import org.apache.lucene.search.FilteredQuery;
import org.apache.lucene.search.Query;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.lucene.search.Queries;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.index.cache.filter.support.CacheKeyFilter;

import java.io.IOException;

/**
 * @author kimchy (shay.banon)
 */
public class FilteredQueryParser implements QueryParser {

    public static final String NAME = "filtered";

    @Inject public FilteredQueryParser() {
    }

    @Override public String[] names() {
        return new String[]{NAME};
    }

    @Override public Query parse(QueryParseContext parseContext) throws IOException, QueryParsingException {
        XContentParser parser = parseContext.parser();

        Query query = null;
        Filter filter = null;
        float boost = 1.0f;
        boolean cache = false;
        CacheKeyFilter.Key cacheKey = null;

        String currentFieldName = null;
        XContentParser.Token token;
        while ((token = parser.nextToken()) != XContentParser.Token.END_OBJECT) {
            if (token == XContentParser.Token.FIELD_NAME) {
                currentFieldName = parser.currentName();
            } else if (token == XContentParser.Token.START_OBJECT) {
                if ("query".equals(currentFieldName)) {
                    query = parseContext.parseInnerQuery();
                } else if ("filter".equals(currentFieldName)) {
                    filter = parseContext.parseInnerFilter();
                }
            } else if (token.isValue()) {
                if ("boost".equals(currentFieldName)) {
                    boost = parser.floatValue();
                } else if ("_cache".equals(currentFieldName)) {
                    cache = parser.booleanValue();
                } else if ("_cache_key".equals(currentFieldName) || "_cacheKey".equals(currentFieldName)) {
                    cacheKey = new CacheKeyFilter.Key(parser.text());
                }
            }
        }
        if (query == null) {
            throw new QueryParsingException(parseContext.index(), "[filtered] requires 'query' element");
        }
        if (filter == null) {
            throw new QueryParsingException(parseContext.index(), "[filtered] requires 'filter' element");
        }

        // cache if required
        if (cache) {
            filter = parseContext.cacheFilter(filter, cacheKey);
        }

        // if its a match_all query, use constant_score
        if (Queries.isMatchAllQuery(query)) {
            Query q = new DeletionAwareConstantScoreQuery(filter);
            q.setBoost(boost);
            return q;
        }

        // TODO
        // With the way filtered queries work today, both query and filter advance (one at a time)
        // to get hits. Since all filters support random access, it might make sense to use that.
        // But, it make more sense to apply it down at the postings level then letting the query
        // construct doc ids and extract it.
        // This might be possible in lucene 4.0.
        // More info:
        //    - https://issues.apache.org/jira/browse/LUCENE-1536
        //    - http://chbits.blogspot.com/2010/09/fast-search-filters-using-flex.html


        FilteredQuery filteredQuery = new FilteredQuery(query, filter);
        filteredQuery.setBoost(boost);
        return filteredQuery;
    }
}