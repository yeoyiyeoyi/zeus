package com.ctrip.zeus.nginx;

import com.ctrip.zeus.model.entity.App;
import com.ctrip.zeus.model.entity.Slb;
import com.ctrip.zeus.model.entity.VirtualServer;

import java.io.IOException;

/**
 * @author:xingchaowang
 * @date: 3/9/2015.
 */
public interface NginxOperator {

    void writeNginxConf(String path, String conf) throws IOException;

    void writeServerConf(String path, String conf) throws IOException;

    void writeUpstreamsConf(String path, String conf) throws IOException;

    String reloadConf(String command);
}