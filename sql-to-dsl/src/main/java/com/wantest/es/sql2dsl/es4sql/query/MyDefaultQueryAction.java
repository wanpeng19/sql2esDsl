package com.wantest.es.sql2dsl.es4sql.query;

import com.wantest.es.sql2dsl.es4sql.domain.*;
import com.wantest.es.sql2dsl.es4sql.exception.SqlParseException;
import com.wantest.es.sql2dsl.es4sql.query.maker.QueryMaker;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;

import java.util.ArrayList;
import java.util.List;

/**
 * Transform SQL query to standard Elasticsearch search query
 */
public class MyDefaultQueryAction extends MyQueryAction {

	private final Select select;
	private SearchSourceBuilder request;

	public MyDefaultQueryAction(Select select) {
		super(select);
		this.select = select;
	}


	@Override
	public String explain(boolean isExact) throws SqlParseException {
		this.request = new SearchSourceBuilder();
		setFields(select.getFields());

		setWhere(select.getWhere(), isExact);
		setSorts(select.getOrderBys());
		setLimit(select.getOffset(), select.getRowCount());

		return request.toString();
	}

	/**
	 * Set source filtering on a search request.
	 * 
	 * @param fields
	 *            list of fields to source filter.
	 */
	public void setFields(List<Field> fields) throws SqlParseException {
		if (select.getFields().size() > 0) {
			ArrayList<String> includeFields = new ArrayList<String>();
			ArrayList<String> excludeFields = new ArrayList<String>();

			for (Field field : fields) {
				if (field instanceof MethodField) {
					MethodField method = (MethodField) field;
					 if (method.getName().equalsIgnoreCase("include")) {
						for (KVValue kvValue : method.getParams()) {
							includeFields.add(kvValue.value.toString()) ;
						}
					} else if (method.getName().equalsIgnoreCase("exclude")) {
						for (KVValue kvValue : method.getParams()) {
							excludeFields.add(kvValue.value.toString()) ;
						}
					}
				} else if (field instanceof Field) {
					includeFields.add(field.getName());
				}
			}
			request.fetchSource(includeFields.toArray(new String[includeFields.size()]), excludeFields.toArray(new String[excludeFields.size()]));
		}
	}


	/**
	 * Create filters or queries based on the Where clause.
	 * 
	 * @param where
	 *            the 'WHERE' part of the SQL query.
	 * @throws SqlParseException
	 */
	private void setWhere(Where where, boolean isExact) throws SqlParseException {
		if (where != null) {
			BoolQueryBuilder boolQuery = QueryMaker.explan(where,this.select.isQuery, isExact);
			request.query(boolQuery);
		}
	}

	/**
	 * Add sorts to the elasticsearch query based on the 'ORDER BY' clause.
	 * 
	 * @param orderBys
	 *            list of Order object
	 */
	private void setSorts(List<Order> orderBys) {
		for (Order order : orderBys) {
            request.sort(order.getName(),SortOrder.fromString(order.getType()));
		}
	}

	/**
	 * Add from and size to the ES query based on the 'LIMIT' clause
	 * 
	 * @param from
	 *            starts from document at position from
	 * @param size
	 *            number of documents to return.
	 */
	private void setLimit(int from, int size) {
        request.from(from);

		if (size > -1) {
			request.size(size);
		}
	}

	public SearchSourceBuilder getRequestBuilder() {
		return request;
	}
}
