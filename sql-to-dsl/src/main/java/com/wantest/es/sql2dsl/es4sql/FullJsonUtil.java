package com.wantest.es.sql2dsl.es4sql;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.TimeZone;

/**
 * @Auther: 01375553
 * @Date: 2019/3/23 17:41
 * @Description: 此类转换出来的json是全量的，包含为空的字段，一般用户新增场景
 */
public class FullJsonUtil {


    private static ObjectMapper objectMapper = initObjectMapper();

    private static final String DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";

    /**
     * 解码json串成对象
     *
     * @param <T>
     * @param json
     * @param valueType
     * @return T
     */
    @SuppressWarnings("unchecked")
    public static <T> T decode(String json, Class<?> valueType) throws IOException, JsonParseException, JsonMappingException {
        return (T) objectMapper.readValue(json, valueType);
    }

    /**
     * object --> json
     * @param object 需要转换的对象
     * @return
     * String json字符串
     */
    public static String encode2json(Object object) throws JsonProcessingException {
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(object);
    }

    /**
     * json转对象
     *
     * @param json
     * @param type
     * @return
     * @throws IOException
     * @throws JsonParseException
     * @throws JsonMappingException
     */
    public static <T> T decode2(String json, TypeReference<T> type ) throws IOException, JsonParseException, JsonMappingException  {
        return objectMapper.readValue(json, type);
    }

    private static ObjectMapper initObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);
        objectMapper.configure(com.fasterxml.jackson.core.JsonGenerator.Feature.QUOTE_FIELD_NAMES, true);

        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.setTimeZone(TimeZone.getTimeZone("GMT+8"));
        objectMapper.setDateFormat(new SimpleDateFormat(DATE_TIME_FORMAT));

        return objectMapper;
    }
}
