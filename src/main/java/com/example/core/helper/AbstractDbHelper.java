package com.example.core.helper;

import com.example.core.anno.SqlHelper;
import com.example.core.entity.ColumnDefinition;
import com.example.core.entity.ConfigContext;
import eu.infomas.annotation.AnnotationDetector;
import lombok.Data;
import org.apache.commons.dbcp2.BasicDataSource;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.handlers.MapListHandler;
import org.apache.commons.lang3.*;

import javax.sql.DataSource;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * @Author LiuYue
 * @Date 2019/1/2
 * @Version 1.0
 */
@Data
abstract public class AbstractDbHelper {

    protected DataSource dataSource;
    protected QueryRunner queryRunner;

    protected ConfigContext context;


    private static DataSource initDataSource(ConfigContext context) {
        BasicDataSource dataSource = new BasicDataSource();
        dataSource.setDriverClassName(context.getDriver());
        dataSource.setUrl(context.getUrl());
        dataSource.setUsername(context.getUserName());
        dataSource.setPassword(context.getPassword());
        dataSource.setValidationQuery("select 1");
        return dataSource;
    }

    public static Map<String, Supplier<AbstractDbHelper>> CHOOSE = new HashMap<>();

    static {
        try {
            getScanAnno();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void getScanAnno() throws IOException {
        // 要扫描的包
        String packageName = "com.example.core.helper";
        new AnnotationDetector(new AnnotationDetector.TypeReporter() {
            @Override
            public void reportTypeAnnotation(Class<? extends Annotation> annotation, String className) {
                try {
                    Class<?> aClass = ClassUtils.getClass(className);
                    SqlHelper annotation1 = aClass.getAnnotation(SqlHelper.class);
                    String sqlName = annotation1.value();
                    CHOOSE.put(sqlName, () -> {
                        try {
                            return (AbstractDbHelper) aClass.newInstance();
                        } catch (Exception e1) {
                            throw new RuntimeException(e1);
                        }
                    });
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public Class<? extends Annotation>[] annotations() {
                return new Class[]{SqlHelper.class};
            }
        }).detect(packageName);
    }


    public static AbstractDbHelper of(ConfigContext context) {
        String url = context.getUrl();
        AbstractDbHelper dbHelper = null;
        dbHelper = CHOOSE.entrySet().stream()
                .filter(e -> StringUtils.containsIgnoreCase(url, e.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(MysqlHelper::new)
                .get();
        dbHelper.setContext(context);
        dbHelper.setDataSource(initDataSource(context));
        dbHelper.setQueryRunner(new QueryRunner(dbHelper.getDataSource()));
        return dbHelper;
    }

    abstract String descSql();

    protected List<Map<String, Object>> descTable() {
        return queryMapList(descSql(), null);
    }

    public List<Map<String, Object>> queryMapList(String sql, Object... params) {
        List<Map<String, Object>> fieldMapList;
        try {
            fieldMapList = queryRunner.query(sql, new MapListHandler(), params);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return fieldMapList;
    }


    public List<ColumnDefinition> covertColumnDefinition(List<Map<String, Object>> list) {
        List<ColumnDefinition> columnDefinitionList = new ArrayList<ColumnDefinition>();
        for (Map<String, Object> rowMap : list) {
            // 构建columnDefinition
            columnDefinitionList.add(buildColumnDefinition(rowMap));
        }
        return columnDefinitionList;
    }

    public List<ColumnDefinition> getMetaData() {
        return covertColumnDefinition(descTable());
    }

    abstract ColumnDefinition buildColumnDefinition(Map<String, Object> rowMap);


}
