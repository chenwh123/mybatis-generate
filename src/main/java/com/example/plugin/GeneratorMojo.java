package com.example.plugin;

import com.example.core.entity.ColumnDefinition;
import com.example.core.entity.ConfigContext;
import com.example.core.helper.AbstractDbHelper;
import com.example.core.service.Callback;
import com.example.core.util.FileUtil;
import com.example.core.util.VelocityUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.apache.velocity.app.VelocityEngine;

import java.io.File;
import java.util.List;
import java.util.Properties;

/**
 */

@Mojo(name = "generator")
public class GeneratorMojo extends AbstractMojo {

    @Parameter(property = "project.basedir", required = true, readonly = true)
    private File basedir;

    @Parameter(readonly = false, defaultValue = "")
    private String configDir = "";

    private String getSourcePath(){
        return String.format("%s/src/main/resources/", basedir.getAbsolutePath());
    }

    private String getOutputPath(){
        return String.format("%s/src/main/java/", basedir.getAbsolutePath());
    }


    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            //得到配置文件对象 将指定输出路径与读取资源文件路径
            ConfigContext configContext = new ConfigContext(StringUtils.appendIfMissing(getSourcePath() + configDir, "/", "/"), getOutputPath());

            System.out.printf("入参 basedir = %s , configDir = %s", basedir, configDir);

            //初始化DB工具类
            AbstractDbHelper dbHelper =  AbstractDbHelper.of(configContext);

            //元数据处理
            List<ColumnDefinition> columnDefinitionList = dbHelper.getMetaData();

            String rootPath = configContext.getOutputPath() + configContext.getTargetPackage() + "/";

            String serviceImplPath = configContext.getOutputPath() + configContext.getTargetPackage() + "/" + configContext.getTargetServiceImpl();
            doGenerator(configContext, columnDefinitionList, new Callback() {
                public void write(ConfigContext configContext, VelocityContext context) {
                    FileUtil.writeFile(rootPath + configContext.getTargetEntity(),                   //输出目录
                            String.format("%s.java", configContext.getTargetName()),    //文件名
                            VelocityUtil.render("entity.vm", context));                 //模板生成内容

                    FileUtil.writeFile(rootPath + configContext.getTargetService(),
                            String.format("%sService.java", configContext.getTargetName()),
                            VelocityUtil.render("contract.vm", context));

                    FileUtil.writeFile(rootPath + configContext.getTargetDao(),
                            String.format("%sMapper.java", configContext.getTargetName()),
                            VelocityUtil.render("dao.vm", context));

                    FileUtil.writeFile(serviceImplPath,
                            String.format("%sServiceImpl.java", configContext.getTargetName()),
                            VelocityUtil.render("service.vm", context));

                    FileUtil.writeFile(rootPath + configContext.getTargetController(),
                            String.format("%sController.java", configContext.getTargetName()),
                            VelocityUtil.render("controller.vm", context));

                    FileUtil.writeFile(getSourcePath() + configContext.getMapperXmlPath(),
                            String.format("%sMapper.xml", configContext.getTargetName()),
                            VelocityUtil.render("mapper.vm", context));
                }
            });
        } catch (Exception e){
            throw new MojoExecutionException("unable to generator codes of table.",e);
        }

    }

    public static void doGenerator(ConfigContext configContext, Object data, Callback callback) {
        //配置velocity的资源加载路径
        Properties velocityPros = new Properties();
        velocityPros.setProperty(VelocityEngine.FILE_RESOURCE_LOADER_PATH, configContext.getSourcePath());
        velocityPros.setProperty("input.encoding", "utf-8");
        velocityPros.setProperty("output.encoding", "utf-8");
        Velocity.init(velocityPros);


        //封装velocity数据
        VelocityContext context = new VelocityContext();
        context.put("table", configContext.getTargetTable());
        context.put("name", configContext.getTargetName());
        context.put("package", configContext.getTargetPackage());
        context.put("columns", data);
        context.put("entity", configContext.getTargetEntity());
        context.put("service", configContext.getTargetService());
        context.put("serviceImpl", configContext.getTargetServiceImpl());
        context.put("controller", configContext.getTargetController());
        context.put("dao", configContext.getTargetDao());


        callback.write(configContext, context);

    }


}
