package com.wantest.es.sql2dsl.es4sql;

import com.alibaba.druid.sql.ast.SQLExpr;
import com.alibaba.druid.sql.ast.expr.SQLQueryExpr;
import com.alibaba.druid.sql.parser.ParserException;
import com.alibaba.druid.sql.parser.SQLExprParser;
import com.alibaba.druid.sql.parser.Token;
import com.wantest.es.sql2dsl.es4sql.domain.Select;
import com.wantest.es.sql2dsl.es4sql.exception.SqlParseException;
import com.wantest.es.sql2dsl.es4sql.parse.ElasticSqlExprParser;
import com.wantest.es.sql2dsl.es4sql.parse.SqlParser;
import com.wantest.es.sql2dsl.es4sql.query.MyAggregationQueryAction;
import com.wantest.es.sql2dsl.es4sql.query.MyDefaultQueryAction;
import com.wantest.es.sql2dsl.es4sql.query.MyQueryAction;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * @Auther: 01375553
 * @Date: 2019/3/29 14:35
 * @Description:
 */
public class SqlToDsl {

    public static String toExactDsl(String sql) throws SqlParseException, IOException {
      return convert(sql, true);
    }

    public static String toPhraseDsl(String sql) throws SqlParseException, IOException {
        return convert(sql, false);
    }

    private static String convert(String sql, boolean isExact) throws SqlParseException, IOException {
        Select select = toSelect(sql);
        MyQueryAction myQueryAction;
        if(select.isAgg){
            myQueryAction = new MyAggregationQueryAction(select);
        } else {
            myQueryAction = new MyDefaultQueryAction(select);
        }
        String dsl = myQueryAction.explain(isExact);
        return dsl;
    }


    public static String toEsCommomJson(String app, String searchId, String sql) throws SqlParseException, IOException {
        String dsl = convert(sql, true);
        Map queryMap = FullJsonUtil.decode(dsl, Map.class);
        queryMap.put("query", queryMap.get("query"));

        Map dslMap = new HashMap();
        dslMap.put("dsl", queryMap);

        Map commonDslMap = toCommonDslMap(app, searchId);
        commonDslMap.put("params", dslMap);

        String jsonPretty = FullJsonUtil.encode2json(commonDslMap);
        return jsonPretty;
    }



    private static Select toSelect(String sql) throws SqlParseException {
        SQLQueryExpr sqlExpr = (SQLQueryExpr) toSqlExpr(sql);
        return new SqlParser().parseSelect(sqlExpr);
    }

    private static SQLExpr toSqlExpr(String sql) {
        SQLExprParser parser = new ElasticSqlExprParser(sql);
        SQLExpr expr = parser.expr();

        if (parser.getLexer().token() != Token.EOF) {
            throw new ParserException("illegal sql expr : " + sql);
        }
        return expr;
    }

    private static Map toCommonDslMap(String app, String searchId){
        Map outsideDsl = new HashMap();
        outsideDsl.put("app", app);
        outsideDsl.put("searchId", Integer.valueOf(searchId));
        return outsideDsl;

    }
}
