package com.ctrip.zeus.restful.resource;

import com.ctrip.zeus.lock.DbLockFactory;
import com.ctrip.zeus.lock.DistLock;
import com.ctrip.zeus.model.entity.*;
import com.ctrip.zeus.model.transform.DefaultJsonParser;
import com.ctrip.zeus.model.transform.DefaultSaxParser;
import com.ctrip.zeus.service.build.BuildInfoService;
import com.ctrip.zeus.service.build.BuildService;
import com.ctrip.zeus.service.build.NginxConfService;
import com.ctrip.zeus.service.build.impl.NginxConfServiceImpl;
import com.ctrip.zeus.service.model.AppRepository;
import com.ctrip.zeus.service.model.SlbRepository;
import com.ctrip.zeus.service.nginx.NginxService;
import com.ctrip.zeus.service.status.AppStatusService;
import com.ctrip.zeus.service.status.StatusService;
import com.ctrip.zeus.util.AssertUtils;
import com.netflix.config.DynamicIntProperty;
import com.netflix.config.DynamicPropertyFactory;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;

import javax.annotation.Resource;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.List;

/**
 * @author:xingchaowang
 * @date: 3/15/2015.
 */
@Component
@Path("/op")
public class ServerResource {

    @Resource
    StatusService statusService;
    @Resource
    private BuildService buildService;
    @Resource
    private BuildInfoService buildInfoService;
    @Resource
    private NginxService nginxAgentService;
    @Resource
    private SlbRepository slbClusterRepository;
    @Resource
    private NginxService nginxService;
    @Resource
    private AppStatusService appStatusService;
    @Resource
    private AppRepository appRepository;
    @Resource
    private NginxConfService nginxConfService;
    @Resource
    private DbLockFactory dbLockFactory;


    private static DynamicIntProperty lockTimeout = DynamicPropertyFactory.getInstance().getIntProperty("lock.timeout", 5000);



    @GET
    @Path("/upServer")
    public Response upServer(@Context HttpHeaders hh, @QueryParam("ip") String ip) throws Exception{
        String serverip = ip;

        //update status
        statusService.upServer(serverip);

        return serverOps(hh,serverip);

    }

    @GET
    @Path("/downServer")
    public Response downServer(@Context HttpHeaders hh, @QueryParam("ip") String ip) throws Exception{
        String serverip = ip;
        //update status
        statusService.downServer(serverip);
        return serverOps(hh,serverip);
    }

    private Response serverOps(HttpHeaders hh , String serverip)throws Exception{
        //get slb by serverip
        List<Slb> slblist = slbClusterRepository.listByAppServerAndAppName(serverip,null);
        AssertUtils.isNull(slblist,"[UpServer/DownServer] Can not find slb by server ip :["+serverip+"],Please check the configuration and server ip!");

        for (Slb slb : slblist)
        {
            String slbname = slb.getName();
            int ticket = buildInfoService.getTicket(slbname);

            boolean buildFlag = false;
            DistLock buildLock = dbLockFactory.newLock(slbname + "_build");
            try{
                buildLock.lock(lockTimeout.get());
                buildFlag =buildService.build(slbname,ticket);
            }finally {
                buildLock.unlock();
            }
            if (buildFlag) {
                DistLock writeLock = dbLockFactory.newLock(slbname + "_writeAndReload");
                try {
                    writeLock.lock(lockTimeout.get());
                    //Push Service
                    nginxAgentService.writeAllAndLoadAll(slbname);
                } finally {
                    writeLock.unlock();
                }
            }

        }

        ServerStatus ss = new ServerStatus().setIp(serverip).setUp(statusService.getServerStatus(serverip));
        List<String> applist = appRepository.listAppsByAppServer(serverip);

        if (applist!=null)
        {
            for (String name : applist)
            {
                ss.addAppName(name);
            }
        }

        if (MediaType.APPLICATION_XML_TYPE.equals(hh.getMediaType())) {
            return Response.status(200).entity(String.format(ServerStatus.XML, ss)).type(MediaType.APPLICATION_XML).build();
        } else {
            return Response.status(200).entity(String.format(ServerStatus.JSON, ss)).type(MediaType.APPLICATION_JSON).build();
        }
    }

    @GET
    @Path("/upMember")
    public Response upMember(@Context HttpHeaders hh, @QueryParam("appName") String appName, @QueryParam("ip") String ip)throws Exception
    {
        statusService.upMember(appName,ip);
        return memberOps(hh,appName,ip);
    }

    @GET
    @Path("/downMember")
    public Response downMember(@Context HttpHeaders hh, @QueryParam("appName") String appName, @QueryParam("ip") String ip)throws Exception
    {
        statusService.downMember(appName, ip);
        return memberOps(hh, appName, ip);
    }

    private Response memberOps(HttpHeaders hh,String appName,String ip)throws Exception{

        //get slb by appname and ip
        List<Slb> slblist = slbClusterRepository.listByAppServerAndAppName(ip,appName);
        AssertUtils.isNull(slblist,"Not find slb for appName ["+appName+"] and ip ["+ip+"]");

        for (Slb slb : slblist) {
            String slbname = slb.getName();
            //get ticket
            int ticket = buildInfoService.getTicket(slbname);

            boolean buildFlag = false;
            DistLock buildLock = dbLockFactory.newLock(slbname + "_build");
            try{
                buildLock.lock(lockTimeout.get());
                buildFlag =buildService.build(slbname,ticket);
            }finally {
                buildLock.unlock();
            }
            if (buildFlag) {
                DistLock writeLock = dbLockFactory.newLock(slbname + "_writeAndReload");
                try {
                    writeLock.lock(lockTimeout.get());
                    //push
                    if (nginxAgentService.writeALLToDisk(slbname)) {

                        List<DyUpstreamOpsData> dyUpstreamOpsDataList = nginxConfService.buildUpstream(slb, appName);
                        nginxAgentService.dyops(slbname, dyUpstreamOpsDataList);
                    } else {
                        throw new Exception("write all to disk failed!");
                    }
                } finally {
                    writeLock.unlock();
                }
            }
        }

        List<AppStatus> statuses = appStatusService.getAppStatus(appName);
        AppStatus appStatusList = new AppStatus().setAppName(appName).setSlbName("");
        for (AppStatus a : statuses)
        {
            appStatusList.setSlbName(appStatusList.getSlbName() + " " + a.getSlbName());
            for(AppServerStatus b : a.getAppServerStatuses())
            {
                appStatusList.addAppServerStatus(b);
            }
        }

        if (MediaType.APPLICATION_XML_TYPE.equals(hh.getMediaType())) {
            return Response.status(200).entity(String.format(AppStatus.XML, appStatusList)).type(MediaType.APPLICATION_XML).build();
        } else {
            return Response.status(200).entity(String.format(AppStatus.JSON, appStatusList)).type(MediaType.APPLICATION_JSON).build();
        }
    }

}

