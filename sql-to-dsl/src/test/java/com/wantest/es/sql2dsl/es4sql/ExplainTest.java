package com.wantest.es.sql2dsl.es4sql;

import com.wantest.es.sql2dsl.es4sql.SqlToDsl;
import com.wantest.es.sql2dsl.es4sql.exception.SqlParseException;
import org.junit.Test;

import java.io.IOException;

public class ExplainTest {

    @Test
    public void testMySql() throws IOException, SqlParseException {

        String sql = String.format("SELECT * FROM tbp_order where orderId >= '2019-07-22 14:11:19' or orderId >19045645454 group by area_code");
        System.out.println(sql);

//        System.out.println(SqlToDsl.toExactDsl(sql)); // 精确匹配
//        System.out.println(SqlToDsl.toPhraseDsl(sql)); // 模糊匹配
        String s = SqlToDsl.toEsCommomJson("bdp-uss", "1131", sql); //生成公共集群json


        System.out.println(s);
    }


}
