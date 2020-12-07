package com.wantest.es.sql2dsl.es4sql.query;

import com.wantest.es.sql2dsl.es4sql.exception.SqlParseException;
import com.wantest.es.sql2dsl.es4sql.domain.Query;

import java.io.IOException;
import java.util.ArrayList;

/**
 * Abstract class. used to transform Select object (Represents SQL query) to
 * SearchRequestBuilder (Represents ES query)
 */
public abstract class MyQueryAction {

	protected Query query;

	public MyQueryAction(Query query) {
		this.query = query;
	}

    private char[] fromArrayListToCharArray(ArrayList arrayList){
        char[] chars = new char[arrayList.size()];
        int i=0;
        for(Object item : arrayList){
            chars[i] = item.toString().charAt(0);
            i++;
        }
        return chars;
    }


    /**
	 * Prepare the request, and return ES request.
	 * @return ActionRequestBuilder (ES request)
	 * @throws SqlParseException
	 */
	public abstract String explain(boolean isExact) throws SqlParseException, IOException;
}
