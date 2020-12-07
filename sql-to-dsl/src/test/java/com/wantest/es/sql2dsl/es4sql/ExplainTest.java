package com.wantest.es.sql2dsl.es4sql;

import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLExpr;
import com.alibaba.druid.util.JdbcConstants;
import com.wantest.es.sql2dsl.es4sql.exception.SqlParseException;
import org.junit.Test;

import java.io.IOException;

public class ExplainTest {

    @Test
    public void testMySql() throws IOException, SqlParseException {

//         String sql = String.format("SELECT * FROM my_index limit 1");
//         System.out.println(sql);
            String sql = "select * from student where id=3 group by name,id";
// //        System.out.println(SqlToDsl.toExactDsl(sql)); // 精确匹配
// //        System.out.println(SqlToDsl.toPhraseDsl(sql)); // 模糊匹配
         String s = SqlToDsl.toExactDsl(sql); //生成公共集群json
        System.out.println(s);
        String dbType = JdbcConstants.MYSQL;
        SQLExpr expr = SQLUtils.toSQLExpr("select * from student where id=3 group by name,id", dbType);
        System.out.println(expr);
        // System.out.println(s);
    }


}
