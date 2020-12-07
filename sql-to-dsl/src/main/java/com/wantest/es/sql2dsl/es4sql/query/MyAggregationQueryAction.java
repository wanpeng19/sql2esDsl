package com.wantest.es.sql2dsl.es4sql.query;

import com.wantest.es.sql2dsl.es4sql.domain.*;
import com.wantest.es.sql2dsl.es4sql.domain.hints.Hint;
import com.wantest.es.sql2dsl.es4sql.domain.hints.HintType;
import com.wantest.es.sql2dsl.es4sql.exception.SqlParseException;
import com.wantest.es.sql2dsl.es4sql.query.maker.AggMaker;
import com.wantest.es.sql2dsl.es4sql.query.maker.QueryMaker;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.join.aggregations.ChildrenAggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.BucketOrder;
import org.elasticsearch.search.aggregations.bucket.nested.NestedAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.nested.ReverseNestedAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Transform SQL query to Elasticsearch aggregations query
 */
public class MyAggregationQueryAction extends MyQueryAction {

    private final Select select;
    private AggMaker aggMaker = new AggMaker();
    private SearchSourceBuilder request;

    public MyAggregationQueryAction(Select select) {
        super(select);
        this.select = select;
    }

    @Override
    public String explain(boolean isExact) throws SqlParseException, IOException {
        this.request = new SearchSourceBuilder();


        setWhere(select.getWhere(), isExact);
        AggregationBuilder lastAgg = null;

        for (List<Field> groupBy : select.getGroupBys()) {
            if (!groupBy.isEmpty()) {
                Field field = groupBy.get(0);


                //make groupby can reference to field alias
                lastAgg = getGroupAgg(field, select, isExact);

                if (lastAgg != null && lastAgg instanceof TermsAggregationBuilder && !(field instanceof MethodField)) {
                    //if limit size is too small, increasing shard  size is required
                    if (select.getRowCount() < 200) {
                        ((TermsAggregationBuilder) lastAgg).shardSize(2000);
                        for (Hint hint : select.getHints()) {
                            if (hint.getType() == HintType.SHARD_SIZE) {
                                if (hint.getParams() != null && hint.getParams().length != 0 && hint.getParams()[0] != null) {
                                    ((TermsAggregationBuilder) lastAgg).shardSize((Integer) hint.getParams()[0]);
                                }
                            }
                        }
                    }
                    if(select.getRowCount()>0) {
                        ((TermsAggregationBuilder) lastAgg).size(select.getRowCount());
                    }
                }
                request.aggregation(lastAgg);

                for (int i = 1; i < groupBy.size(); i++) {
                    field = groupBy.get(i);
                    AggregationBuilder subAgg = getGroupAgg(field, select, isExact);
                      //ES5.0 termsaggregation with size = 0 not supported anymore
//                    if (subAgg instanceof TermsAggregationBuilder && !(field instanceof MethodField)) {

//                        //((TermsAggregationBuilder) subAgg).size(0);
//                    }

                    if (field.isNested()) {
                        AggregationBuilder nestedBuilder = createNestedAggregation(field);

                        if (insertFilterIfExistsAfter(subAgg, groupBy, nestedBuilder, i + 1, isExact)) {
                            groupBy.remove(i + 1);
                            i++;
                        } else {
                            nestedBuilder.subAggregation(subAgg);
                        }

                        lastAgg.subAggregation(wrapNestedIfNeeded(nestedBuilder, field.isReverseNested()));
                    } else if (field.isChildren()) {
                        AggregationBuilder childrenBuilder = createChildrenAggregation(field);

                        if (insertFilterIfExistsAfter(subAgg, groupBy, childrenBuilder, i + 1, isExact)) {
                            groupBy.remove(i + 1);
                            i++;
                        } else {
                            childrenBuilder.subAggregation(subAgg);
                        }

                        lastAgg.subAggregation(childrenBuilder);
                    } else {
                        lastAgg.subAggregation(subAgg);
                    }

                    lastAgg = subAgg;
                }
            }
        }

        Map<String, KVValue> groupMap = aggMaker.getGroupMap();
        // add field
        if (select.getFields().size() > 0) {
            setFields(select.getFields());
            explanFields(request, select.getFields(), lastAgg);
        }

        // add order
        if (lastAgg != null && select.getOrderBys().size() > 0) {
            for (Order order : select.getOrderBys()) {
                KVValue temp = groupMap.get(order.getName());
                if (temp != null) {
                    TermsAggregationBuilder termsBuilder = (TermsAggregationBuilder) temp.value;
                    switch (temp.key) {
                        case "COUNT":
                            termsBuilder.order(BucketOrder.count(isASC(order)));
                            break;
                        case "KEY":
                            termsBuilder.order(BucketOrder.key(isASC(order)));
                            // add the sort to the request also so the results get sorted as well
                            request.sort(order.getName(), SortOrder.valueOf(order.getType()));
                            break;
                        case "FIELD":
                            termsBuilder.order(BucketOrder.aggregation(order.getName(), isASC(order)));
                            break;
                        default:
                            throw new SqlParseException(order.getName() + " can not to order");
                    }
                } else {
                    request.sort(order.getName(), SortOrder.valueOf(order.getType()));
                }
            }
        }

        setLimitFromHint(this.select.getHints());


        return request.toString();
    }
    
    private AggregationBuilder getGroupAgg(Field field, Select select2, boolean isExact) throws SqlParseException {
        boolean refrence = false;
        AggregationBuilder lastAgg = null;
        for (Field temp : select.getFields()) {
            if (temp instanceof MethodField && temp.getName().equals("script")) {
                MethodField scriptField = (MethodField) temp;
                for (KVValue kv : scriptField.getParams()) {
                    if (kv.value.equals(field.getName())) {
                        lastAgg = aggMaker.makeGroupAgg(scriptField, isExact);
                        refrence = true;
                        break;
                    }
                }
            }
        }

        if (!refrence) {
            lastAgg = aggMaker.makeGroupAgg(field, isExact);
        }
        
        return lastAgg;
    }

    private AggregationBuilder wrapNestedIfNeeded(AggregationBuilder nestedBuilder, boolean reverseNested) {
        if (!reverseNested) {
            return nestedBuilder;
        }
        if (reverseNested && !(nestedBuilder instanceof NestedAggregationBuilder)) {
            return nestedBuilder;
        }
        //we need to jump back to root
        return AggregationBuilders.reverseNested(nestedBuilder.getName() + "_REVERSED").subAggregation(nestedBuilder);
    }

    private AggregationBuilder createNestedAggregation(Field field) {
        AggregationBuilder nestedBuilder;

        String nestedPath = field.getNestedPath();

        if (field.isReverseNested()) {
            if (nestedPath == null || !nestedPath.startsWith("~")) {
                ReverseNestedAggregationBuilder reverseNestedAggregationBuilder = AggregationBuilders.reverseNested(getNestedAggName(field));
                if(nestedPath!=null){
                    reverseNestedAggregationBuilder.path(nestedPath);
                }
                return reverseNestedAggregationBuilder;
            }
            nestedPath = nestedPath.substring(1);
        }

        nestedBuilder = AggregationBuilders.nested(getNestedAggName(field),nestedPath);

        return nestedBuilder;
    }

    private AggregationBuilder createChildrenAggregation(Field field) {
        AggregationBuilder childrenBuilder;

        String childType = field.getChildType();

        childrenBuilder = new ChildrenAggregationBuilder(getChildrenAggName(field), childType);

        return childrenBuilder;
    }

    private String getNestedAggName(Field field) {
        String prefix;

        if (field instanceof MethodField) {
            String nestedPath = field.getNestedPath();
            if (nestedPath != null) {
                prefix = nestedPath;
            } else {
                prefix = field.getAlias();
            }
        } else {
            prefix = field.getName();
        }
        return prefix + "@NESTED";
    }

    private String getChildrenAggName(Field field) {
        String prefix;

        if (field instanceof MethodField) {
            String childType = field.getChildType();

            if (childType != null) {
                prefix = childType;
            } else {
                prefix = field.getAlias();
            }
        } else {
            prefix = field.getName();
        }

        return prefix + "@CHILDREN";
    }

    private boolean insertFilterIfExistsAfter(AggregationBuilder agg, List<Field> groupBy, AggregationBuilder builder, int nextPosition, boolean isExact) throws SqlParseException {
        if (groupBy.size() <= nextPosition) {
            return false;
        }
        Field filterFieldCandidate = groupBy.get(nextPosition);
        if (!(filterFieldCandidate instanceof MethodField)) {
            return false;
        }
        MethodField methodField = (MethodField) filterFieldCandidate;
        if (!methodField.getName().toLowerCase().equals("filter")) {
            return false;
        }
        builder.subAggregation(aggMaker.makeGroupAgg(filterFieldCandidate, isExact).subAggregation(agg));
        return true;
    }

    private AggregationBuilder updateAggIfNested(AggregationBuilder lastAgg, Field field) {
        if (field.isNested()) {
            lastAgg = AggregationBuilders.nested(field.getName() + "Nested",field.getNestedPath())
                    .subAggregation(lastAgg);
        }
        return lastAgg;
    }

    private boolean isASC(Order order) {
        return "ASC".equals(order.getType());
    }

    private void setFields(List<Field> fields) {
        if (select.getFields().size() > 0) {
            ArrayList<String> includeFields = new ArrayList<>();

            for (Field field : fields) {
                if (field != null) {
                    includeFields.add(field.getName());
                }
            }

            request.fetchSource(includeFields.toArray(new String[includeFields.size()]), null);
        }
    }

    private void explanFields(SearchSourceBuilder request, List<Field> fields, AggregationBuilder groupByAgg) throws SqlParseException, IOException {
        for (Field field : fields) {
            if (field instanceof MethodField) {


                AggregationBuilder makeAgg = aggMaker.makeFieldAgg((MethodField) field, groupByAgg);
                if (groupByAgg != null) {
                    groupByAgg.subAggregation(makeAgg);
                } else {
                    request.aggregation(makeAgg);
                }
            }  else {
                throw new SqlParseException("it did not support this field method " + field);
            }
        }
    }

    /**
     * Create filters based on
     * the Where clause.
     *
     * @param where the 'WHERE' part of the SQL query.
     * @throws SqlParseException
     */
    private void setWhere(Where where, boolean isExact) throws SqlParseException {
        if (where != null) {
            QueryBuilder whereQuery = QueryMaker.explan(where,this.select.isQuery,isExact);
            request.query(whereQuery);
        }
    }



    private void setLimitFromHint(List<Hint> hints) {
        int from = 0;
        int size = 0;
        for (Hint hint : hints) {
            if (hint.getType() == HintType.DOCS_WITH_AGGREGATION) {
                Integer[] params = (Integer[]) hint.getParams();
                if (params.length > 1) {
                    // if 2 or more are given, use the first as the from and the second as the size
                    // so it is the same as LIMIT from,size
                    // except written as /*! DOCS_WITH_AGGREGATION(from,size) */
                    from = params[0];
                    size = params[1];
                } else if (params.length == 1) {
                    // if only 1 parameter is given, use it as the size with a from of 0
                    size = params[0];
                }
                break;
            }
        }
        request.from(from);
        request.size(size);
    }
}
