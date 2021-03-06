package com.ctrip.zeus.service;


import com.ctrip.zeus.dal.core.GroupDao;
import com.ctrip.zeus.dal.core.NginxConfUpstreamDao;
import com.ctrip.zeus.dal.core.NginxConfUpstreamDo;
import com.ctrip.zeus.server.SlbAdminServer;
import com.ctrip.zeus.util.S;
import org.junit.BeforeClass;
import org.junit.Test;
import support.AbstractSpringTest;

import javax.annotation.Resource;
import java.io.File;
import java.util.Date;

/**
 * Created by fanqq on 2015/5/11.
 */
public class DalPerfTest extends AbstractSpringTest {

    static SlbAdminServer server;
    @Resource
    private GroupDao groupDao;
    @Resource
    private NginxConfUpstreamDao nginxConfUpstreamDao;

    @BeforeClass
    public static void setup() throws Exception {

        S.setPropertyDefaultValue("archaius.deployment.applicationId", "slb-admin");
        S.setPropertyDefaultValue("archaius.deployment.environment", "local");
        S.setPropertyDefaultValue("server.www.base-dir", new File("").getAbsolutePath() + "/src/main/www");
        S.setPropertyDefaultValue("server.temp-dir", new File("").getAbsolutePath() + "/target/temp");
        S.setPropertyDefaultValue("CONF_DIR", new File("").getAbsolutePath() + "/conf/local");

//        server = new SlbAdminServer();
//        server.start();
    }
    @Test
    public void perfTest()throws Exception{

        NginxConfUpstreamDo tmpdata = new NginxConfUpstreamDo()
                .setContent("upstream backend_VS_1007_App_1007 {\n" +
                        "    server 10.2.25.83:20004 weight=1 max_fails=10 fail_timeout=30;\n" +
                        "    server 10.2.25.93:20004 weight=1 max_fails=10 fail_timeout=30;\n" +
                        "    server 10.2.25.94:20004 weight=1 max_fails=10 fail_timeout=30;\n" +
                        "    server 10.2.25.95:20004 weight=1 max_fails=10 fail_timeout=30;\n" +
                        "    server 10.2.25.96:20004 weight=1 max_fails=10 fail_timeout=30;\n" +
                        "    check interval=2000 rise=1 fall=1 timeout=1000 type=http;\n" +
                        "    check_keepalive_requests 100;\n" +
                        "    check_http_send \"GET /checkHealth HTTP/1.0\\r\\n Connection: keep-alive\\r\\n Host: 1007.ctrip.com\\r\\n\\r\\n\";\n" +
                        "    check_http_expect_alive http_2xx http_3xx;\n" +
                        "    }\n");

        tmpdata.setSlbId(52).setVersion(0).setSlbVirtualServerId(3);
        nginxConfUpstreamDao.insert(tmpdata);

        long start = new Date().getTime();
        NginxConfUpstreamDo[] dos = new NginxConfUpstreamDo[10000];

        for (int i = 0 ; i < 10000 ; i ++)
        {
             tmpdata = new NginxConfUpstreamDo()
                    .setContent("upstream backend_VS_1007_App_1007 {\n" +
                            "    server 10.2.25.83:20004 weight=1 max_fails=10 fail_timeout=30;\n" +
                            "    server 10.2.25.93:20004 weight=1 max_fails=10 fail_timeout=30;\n" +
                            "    server 10.2.25.94:20004 weight=1 max_fails=10 fail_timeout=30;\n" +
                            "    server 10.2.25.95:20004 weight=1 max_fails=10 fail_timeout=30;\n" +
                            "    server 10.2.25.96:20004 weight=1 max_fails=10 fail_timeout=30;\n" +
                            "    check interval=2000 rise=1 fall=1 timeout=1000 type=http;\n" +
                            "    check_keepalive_requests 100;\n" +
                            "    check_http_send \"GET /checkHealth HTTP/1.0\\r\\n Connection: keep-alive\\r\\n Host: 1007.ctrip.com\\r\\n\\r\\n\";\n" +
                            "    check_http_expect_alive http_2xx http_3xx;\n" +
                            "    }\n");

            tmpdata.setSlbId(50+i).setVersion(0).setSlbVirtualServerId(i);
            dos[i]=tmpdata;
        }
        nginxConfUpstreamDao.insert(dos);
        long end = new Date().getTime();
        System.out.println("Time cost :"+(end-start));
    }
}
